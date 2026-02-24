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
import com.thejaustin.pearity.shizuku.ShizukuHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "pearity")

class SettingsRepository(private val context: Context) {

    // ─── Setting catalogue ────────────────────────────────────────────────────
    // Values filled in after research agent completes; stubs present for
    // categories we are confident about right now.

    val allSettings: List<PearitySetting> = listOf(

        // ── Animations ────────────────────────────────────────────────────────
        PearitySetting(
            id                  = "window_animation_scale",
            title               = "Window Animation Speed",
            subtitle            = "How fast windows open/close. iOS ≈ 0.5×",
            category            = SettingCategory.ANIMATIONS,
            accessor            = SettingAccessor.GlobalSetting("window_animation_scale"),
            androidDefaultValue = "1.0",
            iosDefaultValue     = "0.5",
            unit                = "×",
        ),
        PearitySetting(
            id                  = "transition_animation_scale",
            title               = "Transition Animation Speed",
            subtitle            = "Screen-to-screen transition speed. iOS ≈ 0.5×",
            category            = SettingCategory.ANIMATIONS,
            accessor            = SettingAccessor.GlobalSetting("transition_animation_scale"),
            androidDefaultValue = "1.0",
            iosDefaultValue     = "0.5",
            unit                = "×",
        ),
        PearitySetting(
            id                  = "animator_duration_scale",
            title               = "Animator Duration Scale",
            subtitle            = "UI element animation timing. iOS ≈ 0.5×",
            category            = SettingCategory.ANIMATIONS,
            accessor            = SettingAccessor.GlobalSetting("animator_duration_scale"),
            androidDefaultValue = "1.0",
            iosDefaultValue     = "0.5",
            unit                = "×",
        ),

        // ── Text & Font ───────────────────────────────────────────────────────
        PearitySetting(
            id                  = "font_scale",
            title               = "Font Size",
            subtitle            = "System-wide text scale. iOS 'Large' (default) ≈ 1.0×",
            category            = SettingCategory.TEXT,
            accessor            = SettingAccessor.SystemSetting("font_scale"),
            androidDefaultValue = "1.0",
            iosDefaultValue     = "1.0",
            requiresShizuku     = false,
            unit                = "×",
        ),
        PearitySetting(
            id                  = "bold_text",
            title               = "Bold Text",
            subtitle            = "iOS Bold Text accessibility option. Android: font_weight_adjustment",
            category            = SettingCategory.TEXT,
            accessor            = SettingAccessor.SecureSetting("font_weight_adjustment"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",   // iOS default is OFF; set "300" to mirror iOS Bold ON
        ),

        // ── Sound ─────────────────────────────────────────────────────────────
        PearitySetting(
            id                  = "touch_sounds",
            title               = "Touch Sounds",
            subtitle            = "Audio feedback on taps. iOS default: off",
            category            = SettingCategory.SOUND,
            accessor            = SettingAccessor.SystemSetting("sound_effects_enabled"),
            androidDefaultValue = "1",
            iosDefaultValue     = "0",
            requiresShizuku     = false,
        ),
        PearitySetting(
            id                  = "lock_sound",
            title               = "Lock / Unlock Sound",
            subtitle            = "Sound when locking the screen. iOS default: on",
            category            = SettingCategory.SOUND,
            accessor            = SettingAccessor.SystemSetting("lockscreen_sounds_enabled"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
            requiresShizuku     = false,
        ),
        PearitySetting(
            id                  = "keyboard_sound",
            title               = "Keyboard Click Sound",
            subtitle            = "Audio on key presses. iOS default: off",
            category            = SettingCategory.SOUND,
            accessor            = SettingAccessor.SystemSetting("sound_on_keypress"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
            requiresShizuku     = false,
        ),
        PearitySetting(
            id                  = "charging_sound",
            title               = "Charging Sound",
            subtitle            = "Sound when charger is connected. iOS default: on",
            category            = SettingCategory.SOUND,
            accessor            = SettingAccessor.GlobalSetting("charging_sounds_enabled"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
        ),

        // ── Haptics ───────────────────────────────────────────────────────────
        PearitySetting(
            id                  = "haptic_feedback",
            title               = "Haptic Feedback",
            subtitle            = "Vibration on UI interactions. iOS default: on",
            category            = SettingCategory.HAPTICS,
            accessor            = SettingAccessor.SystemSetting("haptic_feedback_enabled"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
            requiresShizuku     = false,
        ),
        PearitySetting(
            id                  = "keyboard_haptics",
            title               = "Keyboard Haptics",
            subtitle            = "Vibration on key presses. iOS 16+ default: off",
            category            = SettingCategory.HAPTICS,
            accessor            = SettingAccessor.SystemSetting("haptic_feedback_keyboard"),
            androidDefaultValue = "1",
            iosDefaultValue     = "0",
            requiresShizuku     = false,
        ),

        // ── Display ───────────────────────────────────────────────────────────
        PearitySetting(
            id                  = "display_density",
            title               = "Display Density",
            subtitle            = "Screen DPI. Reset to 'default' to match Samsung stock",
            category            = SettingCategory.DISPLAY,
            accessor            = SettingAccessor.ShellCommand(
                readCmd  = "wm density",
                writeCmd = "wm density {value}",
            ),
            androidDefaultValue = "default",
            iosDefaultValue     = "default",   // TODO: populate from research
        ),

        // ── Accessibility ─────────────────────────────────────────────────────
        PearitySetting(
            id                  = "reduce_motion",
            title               = "Reduce Motion",
            subtitle            = "Minimises all animations to near-zero, mirroring iOS Reduce Motion",
            category            = SettingCategory.ACCESSIBILITY,
            accessor            = SettingAccessor.GlobalSetting("transition_animation_scale"),
            androidDefaultValue = "1.0",
            iosDefaultValue     = "0.01",
            unit                = "×",
        ),
        PearitySetting(
            id                  = "pointer_speed",
            title               = "Pointer / Scroll Speed",
            subtitle            = "Mouse/trackpad pointer speed. iOS: 0 (neutral)",
            category            = SettingCategory.ACCESSIBILITY,
            accessor            = SettingAccessor.SystemSetting("pointer_speed"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
            requiresShizuku     = false,
        ),

        // ── Keyboard ─────────────────────────────────────────────────────────
        PearitySetting(
            id                  = "autocorrect",
            title               = "Auto-Correction",
            subtitle            = "Keyboard auto-corrects words. iOS default: on",
            category            = SettingCategory.KEYBOARD,
            accessor            = SettingAccessor.SecureSetting("spell_checker_enabled"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
        ),
        PearitySetting(
            id                  = "predictive_text",
            title               = "Predictive Text",
            subtitle            = "Word suggestions above keyboard. iOS default: on",
            category            = SettingCategory.KEYBOARD,
            accessor            = SettingAccessor.SecureSetting("input_method_auto_fill"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
        ),

        // ── Navigation ────────────────────────────────────────────────────────
        PearitySetting(
            id                  = "back_gesture_inset_scale",
            title               = "Back Gesture Sensitivity",
            subtitle            = "Width of screen edge that triggers back swipe",
            category            = SettingCategory.NAVIGATION,
            accessor            = SettingAccessor.SecureSetting("back_gesture_inset_scale_left"),
            androidDefaultValue = "1.0",
            iosDefaultValue     = "1.0",
            unit                = "×",
        ),

        // ── Accessibility ─────────────────────────────────────────────────────
        PearitySetting(
            id                  = "high_text_contrast",
            title               = "Increase Contrast",
            subtitle            = "Higher text/UI contrast (mirrors iOS Increase Contrast). Default: off",
            category            = SettingCategory.ACCESSIBILITY,
            accessor            = SettingAccessor.SecureSetting("high_text_contrast_enabled"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),
        PearitySetting(
            id                  = "color_inversion",
            title               = "Smart Invert / Color Inversion",
            subtitle            = "Invert display colors. iOS Smart Invert default: off",
            category            = SettingCategory.ACCESSIBILITY,
            accessor            = SettingAccessor.SecureSetting("accessibility_display_inversion_enabled"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),

        // ── Display ───────────────────────────────────────────────────────────
        PearitySetting(
            id                  = "battery_percentage",
            title               = "Battery Percentage in Status Bar",
            subtitle            = "Show numeric % next to battery icon. iOS 16+ default: on",
            category            = SettingCategory.DISPLAY,
            accessor            = SettingAccessor.SecureSetting("status_bar_show_battery_percent"),
            androidDefaultValue = "0",
            iosDefaultValue     = "1",
        ),
        PearitySetting(
            id                  = "notification_badging",
            title               = "App Icon Badges",
            subtitle            = "Badge counts on app icons. iOS default: on",
            category            = SettingCategory.DISPLAY,
            accessor            = SettingAccessor.SecureSetting("notification_badging"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
        ),
        PearitySetting(
            id                  = "night_display_auto",
            title               = "Night Mode / True Tone",
            subtitle            = "Scheduled blue-light reduction (analogous to iOS Night Shift)",
            category            = SettingCategory.DISPLAY,
            accessor            = SettingAccessor.SecureSetting("night_display_auto_mode"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),

        // ── System ────────────────────────────────────────────────────────────
        PearitySetting(
            id                  = "auto_brightness",
            title               = "Automatic Brightness",
            subtitle            = "Adaptive brightness (mirrors iOS auto-brightness). Default: on",
            category            = SettingCategory.SYSTEM,
            accessor            = SettingAccessor.SystemSetting("screen_brightness_mode"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
            requiresShizuku     = false,
        ),
        PearitySetting(
            id                  = "screen_off_timeout",
            title               = "Screen Timeout",
            subtitle            = "iOS default: 30s (30000ms). Samsung One UI default: 15s",
            category            = SettingCategory.SYSTEM,
            accessor            = SettingAccessor.SystemSetting("screen_off_timeout"),
            androidDefaultValue = "15000",
            iosDefaultValue     = "30000",
            requiresShizuku     = false,
            unit                = "ms",
        ),
        PearitySetting(
            id                  = "location_mode",
            title               = "Location Accuracy Mode",
            subtitle            = "High accuracy GPS. iOS default: on when in use",
            category            = SettingCategory.SYSTEM,
            accessor            = SettingAccessor.SecureSetting("location_mode"),
            androidDefaultValue = "3",
            iosDefaultValue     = "3",
        ),
        PearitySetting(
            id                  = "doze_always_on",
            title               = "Always-On Display",
            subtitle            = "AOD when screen off. iOS 16+ Pro default: on. Others: off",
            category            = SettingCategory.SYSTEM,
            accessor            = SettingAccessor.SecureSetting("doze_always_on"),
            androidDefaultValue = "0",
            iosDefaultValue     = "1",    // iPhone 14 Pro+ default is ON
        ),

        // ── Lock Screen ───────────────────────────────────────────────────────
        PearitySetting(
            id                  = "lock_grace_period",
            title               = "Lock Immediately on Sleep",
            subtitle            = "iOS locks immediately when screen turns off. Android waits 5s by default.",
            category            = SettingCategory.LOCK_SCREEN,
            accessor            = SettingAccessor.SecureSetting("lock_screen_lock_after_timeout"),
            androidDefaultValue = "5000",
            iosDefaultValue     = "0",
            unit                = "ms",
        ),
        PearitySetting(
            id                  = "lock_screen_show_notifications",
            title               = "Notifications on Lock Screen",
            subtitle            = "Show notifications on lock screen. iOS default: on",
            category            = SettingCategory.LOCK_SCREEN,
            accessor            = SettingAccessor.SecureSetting("lock_screen_show_notifications"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
        ),
        PearitySetting(
            id                  = "lock_screen_allow_private",
            title               = "Show Notification Content",
            subtitle            = "Reveal message/sender text on lock screen. iOS 'Show Previews': Always",
            category            = SettingCategory.LOCK_SCREEN,
            accessor            = SettingAccessor.SecureSetting("lock_screen_allow_private_notifications"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
        ),

        // ── Display (continued) ───────────────────────────────────────────────
        PearitySetting(
            id                  = "peak_refresh_rate",
            title               = "Peak Refresh Rate",
            subtitle            = "Max screen Hz. iOS ProMotion default: 120Hz. Matches adaptive display.",
            category            = SettingCategory.DISPLAY,
            accessor            = SettingAccessor.GlobalSetting("peak_refresh_rate"),
            androidDefaultValue = "60.0",
            iosDefaultValue     = "120.0",
            unit                = "Hz",
        ),
        PearitySetting(
            id                  = "min_refresh_rate",
            title               = "Minimum Refresh Rate",
            subtitle            = "Floor Hz for adaptive display. iOS ProMotion: 10Hz (LTPO adaptive).",
            category            = SettingCategory.DISPLAY,
            accessor            = SettingAccessor.GlobalSetting("min_refresh_rate"),
            androidDefaultValue = "60.0",
            iosDefaultValue     = "10.0",
            unit                = "Hz",
        ),
        PearitySetting(
            id                  = "display_white_balance",
            title               = "Adaptive Color / True Tone",
            subtitle            = "Auto-adjust white balance to match ambient light. iOS True Tone: on",
            category            = SettingCategory.DISPLAY,
            accessor            = SettingAccessor.SecureSetting("display_white_balance_enabled"),
            androidDefaultValue = "0",
            iosDefaultValue     = "1",
        ),
        PearitySetting(
            id                  = "window_blur",
            title               = "Background Blur Effects",
            subtitle            = "Frosted-glass blur in sheets/panels. iOS blur: on. Disable = Reduce Transparency.",
            category            = SettingCategory.DISPLAY,
            accessor            = SettingAccessor.GlobalSetting("disable_window_blur"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",   // iOS default = blur ON → disable_window_blur = 0
        ),

        // ── Accessibility (continued) ─────────────────────────────────────────
        PearitySetting(
            id                  = "color_filter",
            title               = "Color Filters",
            subtitle            = "Grayscale / color blindness filter. iOS Color Filters default: off",
            category            = SettingCategory.ACCESSIBILITY,
            accessor            = SettingAccessor.SecureSetting("accessibility_display_daltonizer_enabled"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),
        PearitySetting(
            id                  = "one_handed_mode",
            title               = "One-Handed Mode",
            subtitle            = "Shrinks screen for one-thumb reach. iOS Reachability default: off",
            category            = SettingCategory.ACCESSIBILITY,
            accessor            = SettingAccessor.SecureSetting("one_handed_enabled"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),

        // ── Samsung One UI ────────────────────────────────────────────────────
        PearitySetting(
            id                  = "status_bar_clock_pos",
            title               = "Clock Position",
            subtitle            = "Status bar clock side. iOS: left (0). Samsung default: right (1).",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("status_bar_clock"),
            androidDefaultValue = "1",   // right
            iosDefaultValue     = "0",   // left
        ),
        PearitySetting(
            id                  = "edge_panels",
            title               = "Edge Panels",
            subtitle            = "Slide-out panels from screen edge. No iOS equivalent → off for iOS parity.",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("edge_panel_enabled"),
            androidDefaultValue = "1",
            iosDefaultValue     = "0",
        ),
        PearitySetting(
            id                  = "edge_lighting",
            title               = "Edge Lighting",
            subtitle            = "Edge glow on incoming notifications. No iOS equivalent → off.",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("edge_lighting_enabled"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),
        PearitySetting(
            id                  = "smart_stay",
            title               = "Smart Stay",
            subtitle            = "Screen stays on while you look at it. iOS has no equivalent → off.",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("smart_stay_enabled"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),
        PearitySetting(
            id                  = "raise_to_wake",
            title               = "Raise to Wake",
            subtitle            = "Lift phone to wake display. iOS default: on",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("raise_to_wake_enabled"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
        ),
        PearitySetting(
            id                  = "tap_to_wake",
            title               = "Double-Tap to Wake",
            subtitle            = "Double-tap screen to wake. iOS single-taps; Samsung double-taps. iOS default: on",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("double_tap_to_wake_enabled"),
            androidDefaultValue = "1",
            iosDefaultValue     = "1",
        ),
        PearitySetting(
            id                  = "samsung_always_on",
            title               = "Always-On Display (One UI)",
            subtitle            = "Samsung AOD setting. iOS Pro default: on. Samsung default: off.",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("always_on_display_enabled"),
            androidDefaultValue = "0",
            iosDefaultValue     = "1",
        ),
        PearitySetting(
            id                  = "samsung_reduce_animations",
            title               = "Reduce Animations (One UI)",
            subtitle            = "Samsung-specific motion reduction. iOS Reduce Motion default: off",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("reduce_animations"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),
        PearitySetting(
            id                  = "touch_sensitivity",
            title               = "High Touch Sensitivity",
            subtitle            = "Increased sensitivity for screen protectors. iOS has standard sensitivity → off.",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SecureSetting("touch_sensitivity_mode"),
            androidDefaultValue = "0",
            iosDefaultValue     = "0",
        ),
        PearitySetting(
            id                  = "notification_vibration_intensity",
            title               = "Notification Vibration Intensity",
            subtitle            = "Samsung vibration strength for notifications. iOS: medium (3).",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SystemSetting("notification_vibration_intensity"),
            androidDefaultValue = "3",
            iosDefaultValue     = "3",
            requiresShizuku     = false,
        ),
        PearitySetting(
            id                  = "ring_vibration_intensity",
            title               = "Ring Vibration Intensity",
            subtitle            = "Samsung vibration strength for calls. iOS: medium (3).",
            category            = SettingCategory.SAMSUNG,
            accessor            = SettingAccessor.SystemSetting("ring_vibration_intensity"),
            androidDefaultValue = "3",
            iosDefaultValue     = "3",
            requiresShizuku     = false,
        ),
    )

    // ─── Read ─────────────────────────────────────────────────────────────────

    fun readCurrentValue(setting: PearitySetting): String? {
        val cr: ContentResolver = context.contentResolver
        return try {
            when (val acc = setting.accessor) {
                is SettingAccessor.SystemSetting  -> Settings.System.getString(cr, acc.key)
                is SettingAccessor.SecureSetting  -> Settings.Secure.getString(cr, acc.key)
                is SettingAccessor.GlobalSetting  -> Settings.Global.getString(cr, acc.key)
                is SettingAccessor.ShellCommand   -> ShizukuHelper.runCommand(acc.readCmd).getOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    suspend fun applyValue(setting: PearitySetting, value: String): Result<Unit> {
        return try {
            if (setting.requiresShizuku) {
                val cmd = buildShellCommand(setting, value)
                ShizukuHelper.runCommand(cmd).map { }
            } else {
                // Runtime-grantable WRITE_SETTINGS path
                val cr = context.contentResolver
                val ok = when (val acc = setting.accessor) {
                    is SettingAccessor.SystemSetting -> Settings.System.putString(cr, acc.key, value)
                    else -> return Result.failure(
                        IllegalStateException("Non-system setting requires Shizuku")
                    )
                }
                if (ok) Result.success(Unit)
                else Result.failure(Exception("putString returned false — WRITE_SETTINGS granted?"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
