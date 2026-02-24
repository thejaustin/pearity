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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "pearity")

class SettingsRepository(private val context: Context) {

    val allSettings: List<PearitySetting> = SettingCatalogue.all

    // ─── Read ─────────────────────────────────────────────────────────────────

    fun readCurrentValue(setting: PearitySetting, mode: ConnectionMode): String? {
        val cr: ContentResolver = context.contentResolver
        return try {
            when (val acc = setting.accessor) {
                is SettingAccessor.SystemSetting  -> Settings.System.getString(cr, acc.key)
                is SettingAccessor.SecureSetting  -> {
                    if (canWriteSettingsDirectly()) Settings.Secure.getString(cr, acc.key)
                    else runPrivilegedCommand("settings get secure ${acc.key}", mode).getOrNull()
                }
                is SettingAccessor.GlobalSetting  -> {
                    if (canWriteSettingsDirectly()) Settings.Global.getString(cr, acc.key)
                    else runPrivilegedCommand("settings get global ${acc.key}", mode).getOrNull()
                }
                is SettingAccessor.ShellCommand   -> runPrivilegedCommand(acc.readCmd, mode).getOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    suspend fun applyValue(setting: PearitySetting, value: String, mode: ConnectionMode): Result<Unit> {
        return try {
            if (setting.requiresShizuku) {
                val cmd = buildShellCommand(setting, value)
                runPrivilegedCommand(cmd, mode).map { }
            } else {
                // Runtime-grantable WRITE_SETTINGS path
                if (!Settings.System.canWrite(context)) {
                    return Result.failure(Exception("WRITE_SETTINGS permission not granted"))
                }

                val cr = context.contentResolver
                val ok = when (val acc = setting.accessor) {
                    is SettingAccessor.SystemSetting -> Settings.System.putString(cr, acc.key, value)
                    else -> return Result.failure(
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
                    RootHelper.isAvailable    -> RootHelper.runCommand(command)
                    ShizukuHelper.hasPermission -> ShizukuHelper.runCommand(command)
                    else                       -> ShizukuHelper.runCommandViaRish(command)
                }
            }
        }
    }

    /**
     * Returns true if a setting is supported given the current connection state.
     */
    fun isSupported(setting: PearitySetting, mode: ConnectionMode): Boolean {
        if (!setting.requiresShizuku) return true // WRITE_SETTINGS is usually available eventually

        return when (mode) {
            ConnectionMode.ROOT     -> RootHelper.isAvailable
            ConnectionMode.SHIZUKU  -> ShizukuHelper.hasPermission
            ConnectionMode.ADB_RISH -> true // assume rish exists or let it fail with error
            ConnectionMode.AUTO     -> RootHelper.isAvailable || ShizukuHelper.hasPermission
        }
    }

    private fun canWriteSettingsDirectly(): Boolean =
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
