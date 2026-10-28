# Dynamic Island for Android

A customizable, iPhone-style "dynamic island" for Android. It draws a small shape at the top of your screen that expands to show notifications, media controls and charging status, then tucks itself away again.

![Android 9+](https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)


## Features

**Three modes**

| Mode | Look | Animation |
| --- | --- | --- |
| **Notch** | Hangs flush from the top edge, centered | Grows to both sides |
| **Pill** | Floats just below the top edge, centered | Grows to both sides |
| **Punch hole** | Wraps around the front camera cutout | Grows from left to right |

**What it shows**

- **Notifications** with the app name, title, text and icon. Tap to open the app that sent it.
- **Media** with album art, title, artist and previous / play-pause / next buttons. Works with any player that publishes a media session. Tap to open the player.
- **Charging** with a battery bar when you plug in or unplug.

**Behavior**

- Hides itself after a delay you choose and leaves a small idle shape on screen. Tap the idle shape to bring the island back, long press it to open settings.
- Swipe the open island up, left or right to dismiss it. Long press it to close it.
- Hides in landscape so it stays out of videos and games (optional).
- Comes back automatically after a reboot.

**Customization** (everything updates live while you drag a slider)

- Expanded width, height and corner radius
- Idle shape: size, opacity, color, or hide it completely
- Position offsets, plus a separate shift for where the island sits when expanded
- Punch hole calibration (X, Y, diameter) so the shape sits exactly over your camera
- Hide delay and animation speed
- Choose which content types appear
- Background, text, accent and idle colors, with presets and hex input
- Material You theming for the app itself on Android 12+

## Requirements

- Android 9 (API 28) or newer
- Developed and tested on a realme 7 Pro running Android 12. Other devices should work but are untested, especially their camera cutout positions and manufacturer battery management.

## Install

1. Download the latest APK from the [Releases](../../releases) page.
2. Open it on your phone and allow installs from your browser or file manager if asked.
3. Open the app and follow the setup screen.

## Permissions

The setup screen walks you through these. Nothing leaves your phone, and the app does not request internet access.

| Permission | Why it is needed | Required |
| --- | --- | --- |
| Display over other apps | Draw the island on top of other apps | Yes |
| Notification access | Read notifications and the active media session | Yes |
| Unrestricted battery | Stop Android from killing the background service | Recommended |
| Auto-start (realme / Oppo / OnePlus) | Survive background cleaning and reboots on ColorOS | Recommended |
| Post notifications (Android 13+) | Show the small "island is running" notification | Recommended |

Declared in the manifest as well: foreground service (keeps the island alive), receive boot completed (restart after reboot), and query all packages (read other apps' names and open them from a tap).

**Sideloading on Android 13 or newer:** if "Notification access" is greyed out, open the app's info page, tap the three-dot menu and choose **Allow restricted settings**, then try again.

## Setting up the punch hole mode

1. Open **Settings** in the app and pick **Punch hole**.
2. Open the **Position** card.
3. Drag **Left edge (X)**, **Top edge (Y)** and **Camera diameter** until the idle circle covers your camera exactly.

The defaults are estimates for the realme 7 Pro. Use the **Preview** buttons at the bottom of the settings screen to trigger a notification, media or charging card without waiting for a real one.

## Build from source

You need Android Studio (recent enough to ship Android Gradle Plugin 9.x), JDK 17 or newer, and the Android SDK platform 37.

```bash
git https://github.com/ViCiAsh/Oryvia.git
cd Oryvia
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. You can also open the folder in Android Studio and press **Run**.

| Component | Version |
| --- | --- |
| Android Gradle Plugin | 9.4.x (built-in Kotlin) |
| Gradle | 9.6 |
| Kotlin Compose compiler plugin | 2.4.x |
| Compose BOM | 2026.08.00 |
| `minSdk` / `targetSdk` / `compileSdk` | 28 / 34 / 37 |

## How it works

- **Overlay:** a foreground service adds a Jetpack Compose view straight to the `WindowManager` as a `TYPE_APPLICATION_OVERLAY` window. The window wraps its content, so it is only as big as the island and does not block touches elsewhere. Notch and pill windows are center-gravity (they grow both ways); the punch hole window is pinned left at the camera (it grows rightward).
- **Compose inside a service:** a service has no lifecycle Compose understands, so `IslandService` implements the lifecycle, saved-state and view-model-store owners itself and attaches them to the view.
- **Notifications and media:** a `NotificationListenerService` forwards new notifications and tracks the active `MediaSession`. Playback buttons use the session's transport controls.
- **Charging:** a broadcast receiver listens for power connected and disconnected events.
- **Settings:** stored with Jetpack DataStore. The service observes the same store, which is why changes show up instantly.

### Project layout

```
app/src/main/java/com/damweeb/oryvia/
  MainActivity.kt                 Switches between onboarding and settings
  OnboardingScreen.kt             Permission setup
  SettingsScreen.kt               Settings UI
  Components.kt                   Theme, collapsible cards, sliders, color picker
  IslandService.kt                Foreground service, overlay window, timers, gestures' actions
  IslandUi.kt                     The island itself (shapes, animations, content)
  IslandNotificationListener.kt   Notifications and media sessions
  IslandModels.kt                 Content types, UI state, event bus
  SettingsRepository.kt           Settings model and DataStore storage
  Perms.kt                        Permission checks and settings shortcuts
  BootReceiver.kt                 Restart after reboot
```

## Known limitations

- Android does not allow overlays above the lock screen, so the island only appears once the phone is unlocked.
- The auto-start shortcut for ColorOS tries several known settings screens and falls back to the app info page. It cannot detect whether auto-start is on.
- Some manufacturers kill background apps aggressively. If the island disappears after a while, grant the battery and auto-start permissions and lock the app in the recent apps list.
- Swiping only hides the island. It does not clear the notification from the notification shade.
- Notification text is shown on a single line.

## Troubleshooting

**The island does not appear.** Check that "Display over other apps" is on, the island switch is enabled in the app, and the app is not hidden because the phone is in landscape with "Hide in landscape" enabled.

**Notifications or music do not show.** Notification access must be enabled. If it already is, toggle it off and on, or reopen the app, which asks Android to rebind the listener.

**The punch hole shape is off-center.** Recalibrate it in Settings, Position.

**It stops working after a few hours.** See the battery and auto-start notes under Known limitations.

## Privacy

The app has no internet permission, no analytics and no accounts. Notification content is only read to display it on the island and is never stored or sent anywhere.

## Contributing

Issues and pull requests are welcome. If you are reporting a device-specific problem, include your phone model, Android version and a screenshot of how the island looks.

## License

See the [LICENSE](LICENSE) file.