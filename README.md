# Dynamic Island (Android)

Kotlin + Jetpack Compose overlay app with three modes (notch, pill, punch hole), auto-hide,
a customizable idle shape, notifications / media / charging, and a settings screen.

## Where the files go

```
app/build.gradle.kts                                   -> replace (or merge) your module file
app/src/main/AndroidManifest.xml                       -> replace (or merge) your manifest
app/src/main/java/com/example/island/*.kt              -> copy all 11 Kotlin files
```

If your package is not `com.example.island`, either rename the package in Android Studio
(right click the package -> Refactor -> Rename) or change the `package` line in every
Kotlin file plus `namespace` / `applicationId` in `app/build.gradle.kts`.
Delete the template's own `MainActivity.kt` and `ui/theme` files if they clash.

## Root `build.gradle.kts` plugins

The app module uses the Compose compiler plugin (Kotlin 2.0+). Your root file needs:

```kotlin
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

If your project uses a version catalog (`libs.versions.toml`) just make sure the same three
plugins are applied in the app module.

## First run

1. Install and open the app. Onboarding asks for "Display over other apps" and
   "Notification access" (required), plus battery, auto-start and notifications (recommended).
2. Press Continue. The island service starts and the idle shape appears.
3. Use the "Try it" buttons at the bottom of settings to test each content type.
4. Pick Punch hole mode and drag the camera calibration sliders until the circle covers the camera.

## Notes

- Sideloading on Android 13+: if "Notification access" is greyed out, open the app's info page,
  tap the three dot menu and choose "Allow restricted settings".
- `QUERY_ALL_PACKAGES` and the battery exemption are fine for GitHub releases but Google Play
  restricts both.
- Overlays cannot draw above the lock screen, that is an Android limitation.
