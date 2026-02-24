# Pearity

**iOS parity for One UI** — a native Android app that lets you match individual Android/Samsung settings to their iOS defaults, one toggle at a time.

> "Pear" (not Apple) + "parity" (matching iOS defaults) = Pearity

---

## What it does

Pearity gives you a card for each tweakable system setting. Every card has a **three-state toggle**:

| State | Meaning |
|---|---|
| **Android** | Restore the Android / Samsung One UI default |
| **Custom** | Your personal value, captured the first time Pearity reads the setting |
| **iOS** | Apply the iOS default for that setting |

You choose which settings to touch — nothing is changed without your explicit interaction.

---

## Prerequisites

Pearity writes privileged system settings, so it needs one of:

### Shizuku (recommended, no root required)
1. Install [Shizuku](https://github.com/RikkaApps/Shizuku) from the Play Store or GitHub Releases.
2. Start the Shizuku service (via wireless ADB or root, one-time setup).
3. Open Pearity — it will prompt you to grant the Shizuku permission.

### Root
If your device is rooted (Magisk / KernelSU), Pearity can use `su` directly. Select **Root** in the connection mode picker inside Settings.

### ADB / rish
Advanced users can use the `rish` binary from Shizuku without the Shizuku daemon running in the background. Place `rish` at `~/rish` in Termux or `/data/local/tmp/rish` and select **ADB / rish** in Settings.

---

## Installation

1. Download the latest APK from [Releases](../../releases).
2. On your device, enable **Install unknown apps** for your file manager or browser.
3. Open the APK to install.
4. Launch **Pearity** and follow the in-app prompts.

> Requires Android 12 (API 31) or later. Primarily tested on Samsung One UI.

---

## Settings catalogue

Pearity currently manages **47 settings** across **11 categories**:

### Animations
| Setting | Android default | iOS default |
|---|---|---|
| Window Animation Speed | 1.0× | 0.5× |
| Transition Animation Speed | 1.0× | 0.5× |
| Animator Duration Scale | 1.0× | 0.5× |

### Text
| Setting | Android default | iOS default |
|---|---|---|
| Font Size | 1.0× | 1.0× |
| Bold Text | off (0) | on (300) |

### Sound
| Setting | Android default | iOS default |
|---|---|---|
| Touch Sounds | on | off |
| Lock / Unlock Sound | on | on |
| Keyboard Click Sound | off | off |
| Charging Sound | on | on |
| Vibrate on Ring | on | on |

### Haptics
| Setting | Android default | iOS default |
|---|---|---|
| Haptic Feedback | on | on |
| Keyboard Haptics | on | off |
| Haptic on Error | off | on |
| Vibrate on Clear Notifications | on | off |

### Display
| Setting | Android default | iOS default |
|---|---|---|
| Battery % in Status Bar | off | on |
| Pulse Notification Light | on | off |
| App Icon Badges | on | on |
| Night Mode / True Tone | off | off |
| Peak Refresh Rate | 60 Hz | 120 Hz |
| Minimum Refresh Rate | 60 Hz | 10 Hz |
| Adaptive Color / True Tone | off | on |
| Background Blur Effects | on | on |

### Navigation
| Setting | Android default | iOS default |
|---|---|---|
| Gesture Hint (Home Bar) | on | on |
| Home Bar Width | 1.0× | 1.5× |
| Back Gesture Sensitivity | 1.0× | 1.0× |

### Keyboard
| Setting | Android default | iOS default |
|---|---|---|
| Auto-Correction | on | on |
| Predictive Text | on | on |

### Accessibility
| Setting | Android default | iOS default |
|---|---|---|
| Reduce Motion | off | off |
| Pointer / Scroll Speed | 0 | 0 |
| Increase Contrast | off | off |
| Smart Invert / Color Inversion | off | off |
| Color Filters | off | off |
| One-Handed Mode | off | off |

### Lock Screen
| Setting | Android default | iOS default |
|---|---|---|
| Lock Screen Media Controls | on | on |
| Lock Immediately on Sleep | 5000 ms | 0 ms |
| Notifications on Lock Screen | on | on |
| Show Notification Content | on | on |

### Samsung One UI
| Setting | Android default | iOS default |
|---|---|---|
| Clock Position | right | left |
| Edge Panels | on | off |
| Edge Lighting | off | off |
| Smart Stay | off | off |
| Raise to Wake | on | on |
| Double-Tap to Wake | on | on |
| Always-On Display (One UI) | off | on (Pro) |
| Reduce Animations (One UI) | off | off |
| High Touch Sensitivity | off | off |
| Notification Vibration Intensity | 3 | 3 |
| Ring Vibration Intensity | 3 | 3 |

### System
| Setting | Android default | iOS default |
|---|---|---|
| Automatic Brightness | on | on |
| Screen Timeout | 15 000 ms | 30 000 ms |
| Location Accuracy Mode | 3 | 3 |
| Always-On Display | off | on (Pro) |

---

## Building from source

Pearity is built entirely through **GitHub Actions** — no Android Studio required.

### Prerequisites
- A GitHub account with the repo forked or cloned
- Java 17 (handled automatically by the CI workflow)

### Local build (Termux)
```bash
# Install Gradle (one-time)
pkg install gradle

# Build debug APK
cd pearity
gradle assembleDebug --no-daemon
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### CI build (GitHub Actions)
Every push to `main` automatically:
1. Compiles the debug APK
2. Uploads it as a build artifact
3. Publishes / updates the `v1.0.0-alpha01` pre-release on GitHub Releases

The workflow is defined in [`.github/workflows/build.yml`](.github/workflows/build.yml).

---

## Architecture

```
PearitySetting (data model)
    └─ SettingCatalogue.all  (47 settings, hardcoded catalogue)
            │
            ▼
    SettingsRepository
        ├─ reads current value via shell / Settings provider
        └─ writes new value via selected ConnectionMode:
               AUTO → tries Shizuku, falls back to root, then rish
               SHIZUKU → Shizuku API + rish binary
               ROOT → su shell
               ADB_RISH → rish binary only
            │
            ▼
    MainViewModel  (StateFlow → UiState)
            │
            ▼
    Compose UI
        ├─ HomeScreen  — LazyColumn of SettingCards
        │       └─ SettingCard → TriStateToggle (Android / Custom / iOS)
        └─ SettingsScreen — connection mode picker, status cards
```

**Tech stack**: Kotlin · Jetpack Compose · Material 3 · Shizuku API 13 · DataStore Preferences

---

## Disclaimer

Pearity modifies system settings using privileged APIs. While all changes are reversible (tap the "Android" state to restore defaults), use at your own risk. Some Samsung One UI keys may not exist on all firmware versions — Pearity skips settings it cannot read.

---

## License

MIT — see [LICENSE](LICENSE) for details.
