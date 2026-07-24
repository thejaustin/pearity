package com.thejaustin.pearity.data.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thejaustin.pearity.data.model.*
import com.thejaustin.pearity.shizuku.RootHelper
import com.thejaustin.pearity.shizuku.ShizukuHelper
import com.thejaustin.pearity.viewmodel.ConnectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "pearity")

class SettingsRepository(private val context: Context) {

    val allSettings: List<PearitySetting> = SettingCatalogue.all

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * Reading system/secure/global settings needs no permission, so always go
     * through the ContentResolver; only ShellCommand accessors need a shell.
     */
    suspend fun readCurrentValue(setting: PearitySetting, mode: ConnectionMode): String? =
        withContext(Dispatchers.IO) {
            val cr: ContentResolver = context.contentResolver
            try {
                when (val acc = setting.accessor) {
                    is SettingAccessor.SystemSetting -> Settings.System.getString(cr, acc.key)
                    is SettingAccessor.SecureSetting -> Settings.Secure.getString(cr, acc.key)
                    is SettingAccessor.GlobalSetting -> Settings.Global.getString(cr, acc.key)
                    is SettingAccessor.ShellCommand  ->
                        runPrivilegedCommand(acc.readCmd, mode).getOrNull()
                            ?.let { parseShellReadOutput(acc.readCmd, it) }
                }
            } catch (e: Exception) {
                null
            }
        }

    /** Normalise shell read output: "null" means unset, `wm density` prints prose. */
    private fun parseShellReadOutput(readCmd: String, raw: String): String? {
        if (raw.isBlank() || raw == "null") return null
        if (readCmd.startsWith("wm density")) {
            // "Physical density: 450" optionally followed by "Override density: 420"
            val override = Regex("Override density: (\\d+)").find(raw)?.groupValues?.get(1)
            return override ?: "reset"
        }
        return raw
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    suspend fun applyValue(setting: PearitySetting, value: String, mode: ConnectionMode): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (setting.requiresShizuku && !hasWriteSecureSettings()) {
                    val cmd = buildShellCommand(setting, value)
                    runPrivilegedCommand(cmd, mode).map { }
                } else if (setting.requiresShizuku) {
                    // WRITE_SECURE_SETTINGS granted (e.g. via adb) — write directly
                    val cr = context.contentResolver
                    val ok = when (val acc = setting.accessor) {
                        is SettingAccessor.SystemSetting -> Settings.System.putString(cr, acc.key, value)
                        is SettingAccessor.SecureSetting -> Settings.Secure.putString(cr, acc.key, value)
                        is SettingAccessor.GlobalSetting -> Settings.Global.putString(cr, acc.key, value)
                        is SettingAccessor.ShellCommand  ->
                            return@withContext runPrivilegedCommand(
                                buildShellCommand(setting, value), mode
                            ).map { }
                    }
                    if (ok) Result.success(Unit)
                    else Result.failure(Exception("putString returned false"))
                } else {
                    // Runtime-grantable WRITE_SETTINGS path
                    if (!Settings.System.canWrite(context)) {
                        return@withContext Result.failure(
                            Exception("WRITE_SETTINGS permission not granted")
                        )
                    }

                    val cr = context.contentResolver
                    val ok = when (val acc = setting.accessor) {
                        is SettingAccessor.SystemSetting -> Settings.System.putString(cr, acc.key, value)
                        else -> return@withContext Result.failure(
                            IllegalStateException("Non-system setting requires Shizuku/Root/ADB")
                        )
                    }
                    if (ok) Result.success(Unit)
                    else Result.failure(Exception("putString returned false"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun runPrivilegedCommand(command: String, mode: ConnectionMode): Result<String> {
        return when (mode) {
            ConnectionMode.ROOT     -> RootHelper.runCommand(command)
            ConnectionMode.SHIZUKU  -> ShizukuHelper.runCommand(command)
            ConnectionMode.ADB_RISH -> ShizukuHelper.runCommandViaRish(command)
            ConnectionMode.AUTO     -> {
                when {
                    RootHelper.isAvailable      -> RootHelper.runCommand(command)
                    ShizukuHelper.hasPermission -> ShizukuHelper.runCommand(command)
                    else                        -> ShizukuHelper.runCommandViaRish(command)
                }
            }
        }
    }

    /**
     * Returns true if a setting is supported given the current connection state.
     */
    fun isSupported(setting: PearitySetting, mode: ConnectionMode): Boolean {
        if (!setting.requiresShizuku) return true // WRITE_SETTINGS is usually available eventually
        if (hasWriteSecureSettings()) return true

        return when (mode) {
            ConnectionMode.ROOT     -> RootHelper.isAvailable
            ConnectionMode.SHIZUKU  -> ShizukuHelper.hasPermission
            ConnectionMode.ADB_RISH -> true // assume rish exists or let it fail with error
            ConnectionMode.AUTO     -> RootHelper.isAvailable || ShizukuHelper.hasPermission
        }
    }

    private fun hasWriteSecureSettings(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun buildShellCommand(setting: PearitySetting, value: String): String =
        when (val acc = setting.accessor) {
            is SettingAccessor.SystemSetting -> "settings put system ${acc.key} $value"
            is SettingAccessor.SecureSetting -> "settings put secure ${acc.key} $value"
            is SettingAccessor.GlobalSetting -> "settings put global ${acc.key} $value"
            is SettingAccessor.ShellCommand  -> acc.writeCmd.replace("{value}", value)
        }

    // ─── Persistence (DataStore) ──────────────────────────────────────────────

    private fun customKey(id: String)  = stringPreferencesKey("custom_$id")
    private fun stateKey(id: String)   = stringPreferencesKey("state_$id")

    suspend fun saveCustomValue(id: String, value: String) {
        context.dataStore.edit { it[customKey(id)] = value }
    }

    suspend fun loadCustomValue(id: String): String? =
        context.dataStore.data.map { it[customKey(id)] }.first()

    suspend fun saveSettingState(id: String, state: SettingState) {
        context.dataStore.edit { it[stateKey(id)] = state.name }
    }

    suspend fun loadSettingState(id: String): SettingState =
        context.dataStore.data
            .map { it[stateKey(id)]?.let(SettingState::valueOf) ?: SettingState.CUSTOM }
            .first()
}
