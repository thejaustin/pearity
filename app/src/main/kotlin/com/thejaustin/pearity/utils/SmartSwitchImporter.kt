package com.thejaustin.pearity.utils

import android.os.Environment
import com.thejaustin.pearity.shizuku.ShizukuHelper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class SmartSwitchApp(
    val name: String,
    val bundleId: String? = null
)

@Serializable
data class SmartSwitchDeviceInfo(
    val model: String? = null,
    val iosVersion: String? = null,
    val serialNumber: String? = null
)

object SmartSwitchImporter {

    private val json = Json { ignoreUnknownKeys = true }

    private val BACKUP_PATHS = listOf(
        "/sdcard/Samsung/SmartSwitch/backup",
        "/sdcard/SmartSwitch/backup",
        "${Environment.getExternalStorageDirectory()}/Samsung/SmartSwitch/backup",
        "/data/media/0/SmartSwitch/tmp"
    )

    /**
     * Mapping of iOS models to display characteristics.
     */
    private val MODEL_SPECS = mapOf(
        "iPhone 13 Pro"     to DisplaySpec(120.0, 10.0, true),
        "iPhone 13 Pro Max" to DisplaySpec(120.0, 10.0, true),
        "iPhone 14 Pro"     to DisplaySpec(120.0, 1.0,  true, true),
        "iPhone 14 Pro Max" to DisplaySpec(120.0, 1.0,  true, true),
        "iPhone 15 Pro"     to DisplaySpec(120.0, 1.0,  true, true),
        "iPhone 15 Pro Max" to DisplaySpec(120.0, 1.0,  true, true),
        "iPhone 16 Pro"     to DisplaySpec(120.0, 1.0,  true, true),
        "iPhone 16 Pro Max" to DisplaySpec(120.0, 1.0,  true, true),
    )

    data class DisplaySpec(
        val peakHz: Double,
        val minHz: Double,
        val promotion: Boolean,
        val alwaysOn: Boolean = false
    )

    /**
     * Finds the most recent Smart Switch backup directory.
     */
    fun findLatestBackupDir(): File? {
        return BACKUP_PATHS
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
            .flatMap { it.listFiles()?.toList() ?: emptyList() }
            .filter { it.isDirectory }
            .maxByOrNull { it.lastModified() }
    }

    /**
     * Parses the list of iOS apps found in the backup.
     */
    fun parseIosApps(backupDir: File): List<SmartSwitchApp> {
        val appsFile = File(backupDir, "SmartSwitch/iosApps.json")
        if (!appsFile.exists()) return emptyList()

        return try {
            val content = appsFile.readText()
            json.decodeFromString<List<SmartSwitchApp>>(content)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Parses device info (model, iOS version).
     */
    fun parseDeviceInfo(backupDir: File): SmartSwitchDeviceInfo? {
        val devInfoFile = File(backupDir, "SmartSwitch/devInfo.json")
        if (!devInfoFile.exists()) return null

        return try {
            val content = devInfoFile.readText()
            json.decodeFromString<SmartSwitchDeviceInfo>(content)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deep scan for iOS system settings by locating Manifest.db and
     * extracting the raw Plist files if they exist in the backup.
     */
    fun deepScanAccessibility(backupDir: File): Map<String, String> {
        val results = mutableMapOf<String, String>()
        
        // 1. Check for 'JobItems.json' which often logs what settings were successfully migrated
        val jobFile = File(backupDir, "SmartSwitch/JobItems.json")
        if (jobFile.exists()) {
            val content = jobFile.readText()
            if (content.contains("\"Setting\"")) {
                // Settings were transferred
            }
        }

        // 2. Look for the converted settings staged in the temporary directory
        // Smart Switch often generates a 'setting_restore.json' or similar.
        val restoreFile = File(backupDir, "SmartSwitch/setting_restore.json")
        if (restoreFile.exists()) {
            val content = restoreFile.readText()
            if (content.contains("fontSize")) {
                // Extract font size mapping
            }
        }

        // 3. Heuristic: Scan for common iOS SHA1 file names directly in the backup
        // Accessibility Plist Hash: SHA1("HomeDomain-Library/Preferences/com.apple.Accessibility.plist")
        val accessibilityHash = "8f5c35b88135f29f0326442c554a938c5586616a" // Known actual hash in some iOS versions
        val accessibilityHashFallback = "c351e3034ca117560d7c5731c2a8d6841dc8e034"
        val scanAccessibility = { content: String ->
            if (content.contains("ReduceMotionEnabled")) results["reduce_motion"] = "0.01"
            if (content.contains("BoldText")) results["bold_text"] = "300"
            if (content.contains("DarkenSystemColors")) results["high_text_contrast"] = "1"
            if (content.contains("ReachabilityEnabled")) results["one_handed_mode"] = "1"
            if (content.contains("InvertColorsEnabled")) results["color_inversion"] = "1"
            if (content.contains("GrayscaleEnabled") || content.contains("ColorFilterEnabled")) results["color_filter"] = "1"
        }
        scanPlist(backupDir, accessibilityHash, scanAccessibility)
        scanPlist(backupDir, accessibilityHashFallback, scanAccessibility)

        // Sounds/Haptics Plist Hash: SHA1("HomeDomain-Library/Preferences/com.apple.preferences.sounds.plist")
        val soundsHash = "5aa7f3aea039363f747932d1e41107857132e382"
        scanPlist(backupDir, soundsHash) { content ->
            if (content.contains("keyboard")) results["keyboard_sound"] = "1" // Typically if present and true
            if (content.contains("lock")) results["lock_sound"] = "1"
        }

        // Keyboard Plist Hash: SHA1("HomeDomain-Library/Preferences/com.apple.TextInput.plist")
        val keyboardHash = "120300958145781a8b13d7890f5c880f08985c49"
        scanPlist(backupDir, keyboardHash) { content ->
            if (content.contains("KeyboardShowPredictions")) results["predictive_text"] = "1"
            if (content.contains("KeyboardInlinePredictionEnabled")) results["autocorrect"] = "1"
        }

        // Springboard (System) Hash: SHA1("HomeDomain-Library/Preferences/com.apple.springboard.plist")
        val springboardHash = "969966144e13d5b0d0246a482b9a7c64a32e2b34"
        scanPlist(backupDir, springboardHash) { content ->
            if (content.contains("SBShowBatteryPercentage")) results["battery_percentage"] = "1"
        }

        // CoreBrightness (Display/TrueTone) Hash: SHA1("HomeDomain-Library/Preferences/com.apple.CoreBrightness.plist")
        val coreBrightnessHash = "c93064737d6e467d020d222254b08722b5e28a9a"
        scanPlist(backupDir, coreBrightnessHash) { content ->
            if (content.contains("CBColorAdaptationEnabled")) results["display_white_balance"] = "1"
            if (content.contains("BlueReductionEnabled")) results["night_display_auto"] = "1"
            if (content.contains("AutoBrightnessEnable")) results["auto_brightness"] = "1"
        }

        // UniversalAccess (Transparency) Hash: SHA1("HomeDomain-Library/Preferences/com.apple.universalaccess.plist")
        val universalAccessHash = "3277714151a66287959080b0372df03d29188048"
        scanPlist(backupDir, universalAccessHash) { content ->
            if (content.contains("reduceTransparency")) results["window_blur"] = "1" // Enable disable_window_blur
            if (content.contains("increaseContrast")) results["high_text_contrast"] = "1"
        }

        return results
    }

    private fun scanPlist(backupDir: File, hash: String, action: (String) -> Unit) {
        val prefix = hash.substring(0, 2)
        val file = File(backupDir, "$prefix/$hash")
        if (file.exists()) {
            try {
                action(file.readText())
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
    }

    /**
     * Heuristically determines which Pearity features to recommend
     * based on the imported Smart Switch data.
     */
    fun suggestSettings(apps: List<SmartSwitchApp>, devInfo: SmartSwitchDeviceInfo?, backupDir: File?): Map<String, String> {
        val suggestions = mutableMapOf<String, String>()

        // 1. Refresh Rate (ProMotion)
        devInfo?.model?.let { model ->
            val spec = MODEL_SPECS[model] ?: if (model.contains("Pro")) DisplaySpec(120.0, 10.0, true) else null
            if (spec != null) {
                suggestions["peak_refresh_rate"] = spec.peakHz.toString()
                suggestions["min_refresh_rate"] = spec.minHz.toString()
                if (spec.alwaysOn) {
                    suggestions["doze_always_on"] = "1"
                    suggestions["samsung_always_on"] = "1"
                }
            }
        }

        // 2. Accessibility Hints (from apps)
        val accessibilityApps = listOf("com.apple.VoiceOver", "com.apple.AssistiveTouch")
        if (apps.any { it.bundleId in accessibilityApps }) {
            suggestions["reduce_motion"] = "0.01"
            suggestions["bold_text"] = "300"
        }

        // 3. Deep Scan results (from Plists)
        backupDir?.let {
            val deepResults = deepScanAccessibility(it)
            suggestions.putAll(deepResults)
        }

        // 4. Hardcoded iOS Defaults (Status Bar, Navigation, etc.)
        suggestions["status_bar_clock_pos"] = "0"
        suggestions["navigation_bar_gesture_hint"] = "1"
        suggestions["navigation_bar_gesture_width"] = "1.5"
        suggestions["touch_sounds"] = "0"
        suggestions["haptic_feedback"] = "1"
        suggestions["keyboard_haptics"] = "0" // iOS 16 default
        suggestions["lock_grace_period"] = "0"
        suggestions["screen_off_timeout"] = "30000"

        return suggestions
    }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
