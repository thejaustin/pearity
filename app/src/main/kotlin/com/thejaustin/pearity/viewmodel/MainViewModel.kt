package com.thejaustin.pearity.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thejaustin.pearity.data.model.PearitySetting
import com.thejaustin.pearity.data.model.SettingState
import com.thejaustin.pearity.data.repository.SettingsRepository
import com.thejaustin.pearity.shizuku.ShizukuHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
)

data class MainUiState(
    val settingsByCategory: Map<String, List<SettingUiState>> = emptyMap(),
    val shizukuAvailable: Boolean   = false,
    val shizukuPermission: Boolean  = false,
    val isLoading: Boolean          = true,
    val connectionMode: ConnectionMode = ConnectionMode.SHIZUKU,
)

enum class ConnectionMode { SHIZUKU, ADB_RISH }

// ─── ViewModel ────────────────────────────────────────────────────────────────

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    private val _ui = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _ui.asStateFlow()

    init { load() }

    // ── Initialise ────────────────────────────────────────────────────────────

    private fun load() {
        viewModelScope.launch {
            val states = repo.allSettings.map { s ->
                val live   = repo.readCurrentValue(s)
                // Load persisted custom value; fall back to the live value on first run
                val custom = repo.loadCustomValue(s.id) ?: live
                if (custom != null && repo.loadCustomValue(s.id) == null) {
                    repo.saveCustomValue(s.id, custom)
                }
                val state = repo.loadSettingState(s.id)
                SettingUiState(
                    setting      = s,
                    currentValue = live,
                    customValue  = custom,
                    state        = state,
                )
            }

            _ui.value = MainUiState(
                settingsByCategory = states.groupBy { it.setting.category.displayName },
                shizukuAvailable   = ShizukuHelper.isAvailable,
                shizukuPermission  = ShizukuHelper.hasPermission,
                isLoading          = false,
            )
        }
    }

    // ── Apply a state change ──────────────────────────────────────────────────

    fun applyState(settingId: String, newState: SettingState) {
        viewModelScope.launch {
            val entry   = findEntry(settingId) ?: return@launch
            val setting = entry.setting

            // Optimistically mark as applying
            update(settingId) { it.copy(isApplying = true, error = null) }

            val targetValue = when (newState) {
                SettingState.ANDROID_DEFAULT -> setting.androidDefaultValue
                SettingState.CUSTOM          -> entry.customValue ?: setting.androidDefaultValue
                SettingState.IOS             -> setting.iosDefaultValue
            }

            val result = repo.applyValue(setting, targetValue)
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
        _ui.value = _ui.value.copy(
            shizukuAvailable  = ShizukuHelper.isAvailable,
            shizukuPermission = ShizukuHelper.hasPermission,
        )
    }

    fun requestShizukuPermission() {
        ShizukuHelper.requestPermission(REQUEST_CODE_SHIZUKU)
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _ui.value = _ui.value.copy(connectionMode = mode)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun allEntries() =
        _ui.value.settingsByCategory.values.flatten()

    private fun findEntry(id: String) =
        allEntries().find { it.setting.id == id }

    private fun update(id: String, transform: (SettingUiState) -> SettingUiState) {
        _ui.value = _ui.value.copy(
            settingsByCategory = _ui.value.settingsByCategory.mapValues { (_, list) ->
                list.map { if (it.setting.id == id) transform(it) else it }
            },
        )
    }

    companion object {
        const val REQUEST_CODE_SHIZUKU = 1001
    }
}
