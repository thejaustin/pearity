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
     * Query iOS backup's Manifest.db SQLite database to find plist files dynamically.
     * Returns a map of plist filename (e.g. "com.apple.Accessibility.plist") to the on-disk File.
     */
    private fun queryManifestDb(backupDir: File): Map<String, File> {
        val manifestFile = File(backupDir, "Manifest.db")
        if (!manifestFile.exists() || !manifestFile.isFile) return emptyMap()

        val foundPlists = mutableMapOf<String, File>()
        var db: android.database.sqlite.SQLiteDatabase? = null
        var cursor: android.database.Cursor? = null
        try {
            db = android.database.sqlite.SQLiteDatabase.openDatabase(
                manifestFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            cursor = db.rawQuery(
                "SELECT fileID, relativePath FROM Files WHERE relativePath LIKE '%.plist'",
                null
            )
            val fileIdIdx = cursor.getColumnIndex("fileID")
            val pathIdx = cursor.getColumnIndex("relativePath")
            if (fileIdIdx != -1 && pathIdx != -1) {
                while (cursor.moveToNext()) {
                    val fileId = cursor.getString(fileIdIdx) ?: continue
                    val relPath = cursor.getString(pathIdx) ?: continue
                    val fileName = relPath.substringAfterLast('/')

                    val prefix = if (fileId.length >= 2) fileId.substring(0, 2) else ""
                    val fileNested = File(backupDir, "$prefix/$fileId")
                    val fileFlat = File(backupDir, fileId)
                    val resolvedFile = when {
                        fileNested.exists() -> fileNested
                        fileFlat.exists() -> fileFlat
                        else -> null
                    }
                    if (resolvedFile != null) {
                        foundPlists[fileName] = resolvedFile
                    }
                }
            }
        } catch (e: Exception) {
            // Manifest.db may be encrypted or corrupted; fail safe to hash-based fallback
        } finally {
            try { cursor?.close() } catch (_: Exception) {}
            try { db?.close() } catch (_: Exception) {}
        }
        return foundPlists
    }

    /**
     * Deep scan for iOS system settings by inspecting Manifest.db (preferred) or
     * falling back to verified/best-effort SHA1 file hashes.
     */
    fun deepScanAccessibility(backupDir: File): Map<String, String> {
        val results = mutableMapOf<String, String>()

        val scanAccessibility = { content: String ->
            if (content.contains("ReduceMotionEnabled")) results["reduce_motion"] = "0.01"
            if (content.contains("BoldText")) results["bold_text"] = "300"
            if (content.contains("DarkenSystemColors")) results["high_text_contrast"] = "1"
            if (content.contains("ReachabilityEnabled")) results["one_handed_mode"] = "1"
            if (content.contains("InvertColorsEnabled")) results["color_inversion"] = "1"
            if (content.contains("GrayscaleEnabled") || content.contains("ColorFilterEnabled")) results["color_filter"] = "1"
        }

        val scanSounds = { content: String ->
            if (content.contains("keyboard")) results["keyboard_sound"] = "1"
            if (content.contains("lock")) results["lock_sound"] = "1"
        }

        val scanKeyboard = { content: String ->
            if (content.contains("KeyboardShowPredictions")) results["predictive_text"] = "1"
            if (content.contains("KeyboardInlinePredictionEnabled")) results["autocorrect"] = "1"
        }

        val scanSpringboard = { content: String ->
            if (content.contains("SBShowBatteryPercentage")) results["battery_percentage"] = "1"
        }

        val scanBrightness = { content: String ->
            if (content.contains("CBColorAdaptationEnabled")) results["display_white_balance"] = "1"
            if (content.contains("BlueReductionEnabled")) results["night_display_auto"] = "1"
            if (content.contains("AutoBrightnessEnable")) results["auto_brightness"] = "1"
        }

        val scanUniversalAccess = { content: String ->
            if (content.contains("reduceTransparency")) results["window_blur"] = "1"
            if (content.contains("increaseContrast")) results["high_text_contrast"] = "1"
        }

        // 1. Try dynamic Manifest.db lookup first
        val manifestPlists = queryManifestDb(backupDir)

        fun scanOrFallback(fileName: String, fallbackHash: String, scanner: (String) -> Unit) {
            val file = manifestPlists[fileName]
            if (file != null) {
                try {
                    file.readTextCapped()?.let(scanner)
                    return
                } catch (_: Exception) {}
            }
            scanPlist(backupDir, fallbackHash, scanner)
        }

        scanOrFallback("com.apple.Accessibility.plist", "c351e3034ca117560d7c5731c2a8d6841dc8e034", scanAccessibility)
        scanOrFallback("com.apple.preferences.sounds.plist", "5aa7f3aea039363f747932d1e41107857132e382", scanSounds)
        scanOrFallback("com.apple.TextInput.plist", "120300958145781a8b13d7890f5c880f08985c49", scanKeyboard)
        scanOrFallback("com.apple.springboard.plist", "969966144e13d5b0d0246a482b9a7c64a32e2b34", scanSpringboard)
        scanOrFallback("com.apple.CoreBrightness.plist", "c93064737d6e467d020d222254b08722b5e28a9a", scanBrightness)
        scanOrFallback("com.apple.universalaccess.plist", "3277714151a66287959080b0372df03d29188048", scanUniversalAccess)

        return results
    }

    private fun scanPlist(backupDir: File, hash: String, action: (String) -> Unit) {
        val prefix = if (hash.length >= 2) hash.substring(0, 2) else ""
        val fileNested = File(backupDir, "$prefix/$hash")
        val fileFlat = File(backupDir, hash)
        val file = if (fileNested.exists()) fileNested else fileFlat
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
