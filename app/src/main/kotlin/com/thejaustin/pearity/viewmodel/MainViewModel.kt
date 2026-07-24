package com.thejaustin.pearity.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thejaustin.pearity.data.model.PearitySetting
import com.thejaustin.pearity.data.model.SettingState
import com.thejaustin.pearity.data.repository.SettingsRepository
import com.thejaustin.pearity.shizuku.RootHelper
import com.thejaustin.pearity.shizuku.ShizukuHelper
import com.thejaustin.pearity.utils.SmartSwitchApp
import com.thejaustin.pearity.utils.SmartSwitchDeviceInfo
import com.thejaustin.pearity.utils.SmartSwitchImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── UI models ────────────────────────────────────────────────────────────────

data class SettingUiState(
    val setting: PearitySetting,

    /** The live value read from the device right now */
    val currentValue: String?,

    /**
     * The value when the user first launched Pearity (or last pressed "Save as Custom").
     * This is what CUSTOM state restores to.
     */
    val customValue: String?,

    val state: SettingState = SettingState.CUSTOM,
    val isApplying: Boolean  = false,
    val error: String?       = null,
    val supported: Boolean   = true,
)

data class MainUiState(
    val settingsByCategory: Map<String, List<SettingUiState>> = emptyMap(),
    val shizukuAvailable: Boolean   = false,
    val shizukuPermission: Boolean  = false,
    val rootAvailable: Boolean      = false,
    val writeSettingsGranted: Boolean = false,
    val isLoading: Boolean          = true,
    val connectionMode: ConnectionMode = ConnectionMode.AUTO,
    val smartSwitchBackupFound: Boolean = false,
    val smartSwitchBackupDir: String? = null,
    val smartSwitchApps: List<SmartSwitchApp> = emptyList(),
    val smartSwitchDeviceInfo: SmartSwitchDeviceInfo? = null,
    val searchQuery: String = "",
)

enum class ConnectionMode { AUTO, ROOT, SHIZUKU, ADB_RISH }

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    private val _ui = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _ui.asStateFlow()

    /** Source of truth for all settings; settingsByCategory is derived from this. */
    private var _allSettings: List<SettingUiState> = emptyList()

    init { load() }

    // ── Initialise ────────────────────────────────────────────────────────────

    private fun load() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true)
            val mode = _ui.value.connectionMode

            val (states, backup) = withContext(Dispatchers.IO) {
                RootHelper.refreshAvailability()

                // Check Smart Switch backup (best-effort; needs all-files access to work)
                val backupDir = SmartSwitchImporter.findLatestBackupDir()
                val apps = backupDir?.let { SmartSwitchImporter.parseIosApps(it) } ?: emptyList()
                val devInfo = backupDir?.let { SmartSwitchImporter.parseDeviceInfo(it) }

                val list = repo.allSettings
                    .sortedBy { it.category.ordinal }
                    .map { s ->
                        val live = repo.readCurrentValue(s, mode)
                        // Load persisted custom value; fall back to the live value on first run
                        var custom = repo.loadCustomValue(s.id)
                        if (custom == null && live != null) {
                            repo.saveCustomValue(s.id, live)
                            custom = live
                        }
                        SettingUiState(
                            setting      = s,
                            currentValue = live,
                            customValue  = custom,
                            state        = repo.loadSettingState(s.id),
                            supported    = repo.isSupported(s, mode),
                        )
                    }
                list to Triple(backupDir, apps, devInfo)
            }

            _allSettings = states
            val (backupDir, apps, devInfo) = backup

            _ui.value = _ui.value.copy(
                settingsByCategory = grouped(),
                shizukuAvailable   = ShizukuHelper.isAvailable,
                shizukuPermission  = ShizukuHelper.hasPermission,
                rootAvailable      = RootHelper.isAvailable,
                writeSettingsGranted = android.provider.Settings.System.canWrite(getApplication()),
                isLoading          = false,
                smartSwitchBackupFound = backupDir != null,
                smartSwitchBackupDir = backupDir?.absolutePath,
                smartSwitchApps = apps,
                smartSwitchDeviceInfo = devInfo,
            )
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun onSearchQueryChanged(query: String) {
        _ui.value = _ui.value.copy(searchQuery = query, settingsByCategory = grouped(query))
    }

    /** Group the master list into display order, optionally filtered by query. */
    private fun grouped(query: String = _ui.value.searchQuery): Map<String, List<SettingUiState>> {
        val q = query.lowercase()
        val filtered = if (q.isEmpty()) {
            _allSettings
        } else {
            _allSettings.filter {
                it.setting.title.lowercase().contains(q) ||
                it.setting.subtitle.lowercase().contains(q) ||
                it.setting.category.displayName.lowercase().contains(q)
            }
        }
        return filtered.groupBy { it.setting.category.displayName }
    }

    // ── Smart Switch Import ──────────────────────────────────────────────────

    fun importSmartSwitchData() {
        viewModelScope.launch {
            val backupDir = _ui.value.smartSwitchBackupDir
                ?.let { java.io.File(it) }
                ?.takeIf { it.isDirectory }
            val suggestions = withContext(Dispatchers.IO) {
                SmartSwitchImporter.suggestSettings(
                    _ui.value.smartSwitchApps,
                    _ui.value.smartSwitchDeviceInfo,
                    backupDir,
                )
            }
            suggestions.forEach { (id, value) -> applySuggestion(id, value) }
        }
    }

    /**
     * Apply a suggested value. If it matches the catalogue's iOS default the card
     * lands on the iOS state; otherwise the value becomes the CUSTOM baseline.
     */
    private suspend fun applySuggestion(settingId: String, value: String) {
        val entry = findEntry(settingId) ?: return
        if (value == entry.setting.iosDefaultValue) {
            applyStateInternal(settingId, SettingState.IOS)
        } else {
            update(settingId) { it.copy(isApplying = true, error = null) }
            val result = repo.applyValue(entry.setting, value, _ui.value.connectionMode)
            if (result.isSuccess) {
                repo.saveCustomValue(settingId, value)
                repo.saveSettingState(settingId, SettingState.CUSTOM)
                update(settingId) {
                    it.copy(
                        isApplying = false,
                        state = SettingState.CUSTOM,
                        currentValue = value,
                        customValue = value,
                    )
                }
            } else {
                update(settingId) {
                    it.copy(isApplying = false, error = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    /**
     * Import Smart Switch data from a user-selected document tree.
     */
    fun importSmartSwitchFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    SmartSwitchImporter.parseFromTree(getApplication(), uri)
                } catch (e: Exception) {
                    null
                }
            } ?: return@launch // Not a valid backup

            _ui.value = _ui.value.copy(
                smartSwitchBackupFound = true,
                smartSwitchBackupDir = result.displayPath,
                smartSwitchApps = result.apps,
                smartSwitchDeviceInfo = result.deviceInfo,
            )

            // Auto-import after successful selection
            importSmartSwitchData()
        }
    }

    // ── Apply a state change ──────────────────────────────────────────────────

    fun applyState(settingId: String, newState: SettingState) {
        viewModelScope.launch { applyStateInternal(settingId, newState) }
    }

    private suspend fun applyStateInternal(settingId: String, newState: SettingState) {
        val entry   = findEntry(settingId) ?: return
        val setting = entry.setting

        // Optimistically mark as applying
        update(settingId) { it.copy(isApplying = true, error = null) }

        val targetValue = when (newState) {
            SettingState.ANDROID_DEFAULT -> setting.androidDefaultValue
            SettingState.CUSTOM          -> entry.customValue ?: setting.androidDefaultValue
            SettingState.IOS             -> setting.iosDefaultValue
        }

        val result = repo.applyValue(setting, targetValue, _ui.value.connectionMode)
        if (result.isSuccess) {
            repo.saveSettingState(settingId, newState)
            update(settingId) {
                it.copy(isApplying = false, state = newState, currentValue = targetValue)
            }
        } else {
            update(settingId) {
                it.copy(isApplying = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    /** Save the current live value as the user's CUSTOM baseline */
    fun saveCurrentAsCustom(settingId: String) {
        viewModelScope.launch {
            val entry = findEntry(settingId) ?: return@launch
            val live  = entry.currentValue ?: return@launch
            repo.saveCustomValue(settingId, live)
            update(settingId) { it.copy(customValue = live) }
        }
    }

    // ── Shizuku ───────────────────────────────────────────────────────────────

    fun refreshShizuku() {
        viewModelScope.launch {
            val rootAvailable = withContext(Dispatchers.IO) { RootHelper.refreshAvailability() }
            val mode = _ui.value.connectionMode
            _allSettings = _allSettings.map {
                it.copy(supported = repo.isSupported(it.setting, mode))
            }
            _ui.value = _ui.value.copy(
                shizukuAvailable  = ShizukuHelper.isAvailable,
                shizukuPermission = ShizukuHelper.hasPermission,
                rootAvailable     = rootAvailable,
                writeSettingsGranted = android.provider.Settings.System.canWrite(getApplication()),
                settingsByCategory = grouped(),
            )
        }
    }

    fun requestShizukuPermission() {
        ShizukuHelper.requestPermission(REQUEST_CODE_SHIZUKU)
    }

    fun requestWriteSettingsPermission(activity: android.app.Activity) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = android.net.Uri.parse("package:" + activity.packageName)
        activity.startActivity(intent)
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _ui.value = _ui.value.copy(connectionMode = mode)
        load() // Refresh with new mode
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findEntry(id: String) =
        _allSettings.find { it.setting.id == id }

    private fun update(id: String, transform: (SettingUiState) -> SettingUiState) {
        _allSettings = _allSettings.map { if (it.setting.id == id) transform(it) else it }
        _ui.value = _ui.value.copy(settingsByCategory = grouped())
    }

    companion object {
        const val REQUEST_CODE_SHIZUKU = 1001
    }
}
