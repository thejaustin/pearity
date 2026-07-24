package com.thejaustin.pearity.data.model

// ─── Categories ──────────────────────────────────────────────────────────────

enum class SettingCategory(val displayName: String, val emoji: String) {
    ANIMATIONS   ("Animations",        "⚡"),
    DISPLAY      ("Display",           "🖥"),
    TEXT         ("Text & Font",       "T"),
    SOUND        ("Sound",             "🔊"),
    HAPTICS      ("Haptics",           "📳"),
    KEYBOARD     ("Keyboard",          "⌨"),
    NAVIGATION   ("Navigation",        "◀"),
    ACCESSIBILITY("Accessibility",     "♿"),
    LOCK_SCREEN  ("Lock Screen",       "🔒"),
    SAMSUNG      ("Samsung One UI",    "🌙"),
    SYSTEM       ("System",            "⚙"),
}

// ─── Three-state toggle ───────────────────────────────────────────────────────

enum class SettingState {
    /** Canonical Android / Samsung One UI default */
    ANDROID_DEFAULT,
    /** User's own value, captured on first launch or updated by the user */
    CUSTOM,
    /** The iOS default for this setting */
    IOS,
}

// ─── Confidence in the accessor key ───────────────────────────────────────────

/**
 * How sure we are that toggling [PearitySetting.accessor] actually produces the described
 * effect. Android's Settings provider accepts writes to *any* key with no schema validation,
 * so a write "succeeding" never proves the key does anything — this is set by hand from
 * source/doc review, not derived from anything at runtime.
 */
enum class KeyConfidence {
    /** Confirmed against AOSP source, official docs, or an equivalent primary source. */
    VERIFIED,
    /** Real, documented key, but with a known caveat (per-app override, deprecated). */
    COMMUNITY,
    /** Best-effort guess; unconfirmed the key exists or does anything at all. */
    UNVERIFIED,
}

// ─── How the setting is accessed / written ────────────────────────────────────

sealed class SettingAccessor {
    /** Settings.System.* — writable with WRITE_SETTINGS (runtime-grantable) */
    data class SystemSetting(val key: String) : SettingAccessor()

    /** Settings.Secure.* — needs WRITE_SECURE_SETTINGS → Shizuku/ADB */
    data class SecureSetting(val key: String) : SettingAccessor()

    /** Settings.Global.* — needs WRITE_SECURE_SETTINGS → Shizuku/ADB */
    data class GlobalSetting(val key: String) : SettingAccessor()

    /**
     * Raw shell command pair.
     * [readCmd]  must print the current value to stdout.
     * [writeCmd] accepts {value} placeholder substituted at runtime.
     */
    data class ShellCommand(
        val readCmd: String,
        val writeCmd: String,
    ) : SettingAccessor()
}

// ─── Setting definition ───────────────────────────────────────────────────────

data class PearitySetting(
    val id: String,

    /** Short label shown on the card */
    val title: String,

    /** One-line description shown below the title */
    val subtitle: String,

    val category: SettingCategory,
    val accessor: SettingAccessor,

    /** Canonical Android / Samsung One UI default */
    val androidDefaultValue: String,

    /** Value that produces iOS-equivalent behaviour */
    val iosDefaultValue: String,

    /**
     * True  → write requires Shizuku (or ADB fallback).
     * False → writable via WRITE_SETTINGS permission only.
     */
    val requiresShizuku: Boolean = true,

    /** Display unit appended to values in the value chips (e.g. "×", "sp", "dp") */
    val unit: String = "",

    /** How sure we are [accessor] names a real key — see [KeyConfidence]. */
    val confidence: KeyConfidence = KeyConfidence.VERIFIED,
)
