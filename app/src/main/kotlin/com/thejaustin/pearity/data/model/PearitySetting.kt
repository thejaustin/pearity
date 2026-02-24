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
)
