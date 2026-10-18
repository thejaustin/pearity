package com.thejaustin.pearity.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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

    /** Cap for backup-file reads: real iosApps/devInfo JSONs are a few KB; anything huge is not ours. */
    private const val MAX_FILE_BYTES = 1_000_000L

    private fun File.readTextCapped(): String? =
        if (length() in 1..MAX_FILE_BYTES) readText() else null

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
            val content = appsFile.readTextCapped() ?: return emptyList()
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
            val content = devInfoFile.readTextCapped() ?: return null
            json.decodeFromString<SmartSwitchDeviceInfo>(content)
        } catch (e: Exception) {
            null
        }
    }

    // ─── SAF (document tree) import ──────────────────────────────────────────

    data class TreeImportResult(
        val apps: List<SmartSwitchApp>,
        val deviceInfo: SmartSwitchDeviceInfo?,
        val displayPath: String,
    )

    /**
     * Parse a Smart Switch backup from a user-selected document tree.
     * Accepts either the backup folder itself or a parent containing a
     * "SmartSwitch" subfolder. Returns null if neither JSON file is found.
     */
    fun parseFromTree(context: Context, treeUri: Uri): TreeImportResult? {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val files = findBackupJsonFiles(context, treeUri, rootId, depth = 0) ?: return null
        val (appsJson, devInfoJson) = files

        val apps = appsJson?.let {
            try { json.decodeFromString<List<SmartSwitchApp>>(it) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
        val devInfo = devInfoJson?.let {
            try { json.decodeFromString<SmartSwitchDeviceInfo>(it) } catch (e: Exception) { null }
        }

        if (apps.isEmpty() && devInfo == null) return null
        return TreeImportResult(apps, devInfo, treeUri.lastPathSegment ?: treeUri.toString())
    }

    /** Returns (iosApps.json content, devInfo.json content) or null if not found. */
    private fun findBackupJsonFiles(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        depth: Int,
    ): Pair<String?, String?>? {
        if (depth > 2) return null

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        var appsContent: String? = null
        var devInfoContent: String? = null
        val subDirs = mutableListOf<String>()

        try {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(0)
                    val name = cursor.getString(1)
                    val mime = cursor.getString(2)
                    when {
                        name == "iosApps.json" -> appsContent = readDocument(context, treeUri, docId)
                        name == "devInfo.json" -> devInfoContent = readDocument(context, treeUri, docId)
                        mime == DocumentsContract.Document.MIME_TYPE_DIR &&
                            name.equals("SmartSwitch", ignoreCase = true) -> subDirs.add(docId)
                    }
                }
            }
        } catch (e: Exception) {
            return null
        }

        if (appsContent != null || devInfoContent != null) {
            return appsContent to devInfoContent
        }
        for (dirId in subDirs) {
            findBackupJsonFiles(context, treeUri, dirId, depth + 1)?.let { return it }
        }
        return null
    }

    private fun readDocument(context: Context, treeUri: Uri, docId: String): String? =
        try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.openInputStream(docUri)?.use { stream ->
                // Bounded read (InputStream.readNBytes needs API 33; minSdk is 31):
                // a user-picked tree could contain arbitrarily large files
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var overflow = false
                while (true) {
                    val n = stream.read(buf)
                    if (n == -1) break
                    if (out.size() + n > MAX_FILE_BYTES) { overflow = true; break }
                    out.write(buf, 0, n)
                }
                if (overflow) null else out.toByteArray().decodeToString()
            }
        } catch (e: Exception) {
            null
        }

    /**
     * Deep scan for iOS system settings by locating Manifest.db and
     * extracting the raw Plist files if they exist in the backup.
     */
    fun deepScanAccessibility(backupDir: File): Map<String, String> {
        val results = mutableMapOf<String, String>()

        // 2026-07-25: verified against sha1(domain + "-" + relativePath) — the file-ID scheme
        // iOS backups actually use. Each hash below was independently recomputed; only the two
        // marked VERIFIED matched. The rest never matched any plausible domain/path combination
        // tried and are almost certainly fabricated — kept only because they fail safe (file
        // just won't exist at that path, scanPlist no-ops) pending real backup data to correct
        // them against. Do not trust an UNVERIFIED hash as evidence the feature "works".

        // Accessibility Plist — VERIFIED: sha1("HomeDomain-Library/Preferences/com.apple.Accessibility.plist")
        val accessibilityHash = "c351e3034ca117560d7c5731c2a8d6841dc8e034"
        val scanAccessibility = { content: String ->
            if (content.contains("ReduceMotionEnabled")) results["reduce_motion"] = "0.01"
            if (content.contains("BoldText")) results["bold_text"] = "300"
            if (content.contains("DarkenSystemColors")) results["high_text_contrast"] = "1"
            if (content.contains("ReachabilityEnabled")) results["one_handed_mode"] = "1"
            if (content.contains("InvertColorsEnabled")) results["color_inversion"] = "1"
            if (content.contains("GrayscaleEnabled") || content.contains("ColorFilterEnabled")) results["color_filter"] = "1"
        }
        scanPlist(backupDir, accessibilityHash, scanAccessibility)

        // Sounds/Haptics Plist — VERIFIED: sha1("HomeDomain-Library/Preferences/com.apple.preferences.sounds.plist")
        val soundsHash = "5aa7f3aea039363f747932d1e41107857132e382"
        scanPlist(backupDir, soundsHash) { content ->
            if (content.contains("keyboard")) results["keyboard_sound"] = "1" // Typically if present and true
            if (content.contains("lock")) results["lock_sound"] = "1"
        }

        // Keyboard Plist — UNVERIFIED: doesn't match HomeDomain or 14 other plausible domain
        // prefixes for Library/Preferences/com.apple.TextInput.plist.
        val keyboardHash = "120300958145781a8b13d7890f5c880f08985c49"
        scanPlist(backupDir, keyboardHash) { content ->
            if (content.contains("KeyboardShowPredictions")) results["predictive_text"] = "1"
            if (content.contains("KeyboardInlinePredictionEnabled")) results["autocorrect"] = "1"
        }

        // Springboard Plist — UNVERIFIED: same as above, no matching domain prefix found.
        val springboardHash = "969966144e13d5b0d0246a482b9a7c64a32e2b34"
        scanPlist(backupDir, springboardHash) { content ->
            if (content.contains("SBShowBatteryPercentage")) results["battery_percentage"] = "1"
        }

        // CoreBrightness Plist — UNVERIFIED: same as above, no matching domain prefix found.
        val coreBrightnessHash = "c93064737d6e467d020d222254b08722b5e28a9a"
        scanPlist(backupDir, coreBrightnessHash) { content ->
            if (content.contains("CBColorAdaptationEnabled")) results["display_white_balance"] = "1"
            if (content.contains("BlueReductionEnabled")) results["night_display_auto"] = "1"
            if (content.contains("AutoBrightnessEnable")) results["auto_brightness"] = "1"
        }

        // UniversalAccess Plist — UNVERIFIED: same as above, no matching domain prefix found.
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
                file.readTextCapped()?.let(action)
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
            // ProMotion only exists on iPhone 13 Pro and later — iPhone 11/12 Pro are 60Hz,
            // so the "Pro" fallback must check the generation number too.
            val generation = Regex("iPhone (\\d+)").find(model)?.groupValues?.get(1)?.toIntOrNull()
            val fallback = if (model.contains("Pro") && generation != null && generation >= 13)
                DisplaySpec(120.0, 10.0, true) else null
            val spec = MODEL_SPECS[model] ?: fallback
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
}
