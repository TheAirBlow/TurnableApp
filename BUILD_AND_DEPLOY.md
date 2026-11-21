# Turnable - Build and Deployment Guide

## Changing the App Icon

### Android Icons
Replace the icon files in `/composeApp/src/androidMain/res/`:

- **mipmap directories** (one PNG per dpi):
  - `mipmap-mdpi/ic_launcher.png` (48x48)
  - `mipmap-hdpi/ic_launcher.png` (72x72)
  - `mipmap-xhdpi/ic_launcher.png` (96x96)  
  - `mipmap-xxhdpi/ic_launcher.png` (144x144)
  - `mipmap-xxxhdpi/ic_launcher.png` (192x192)

- **Round icon variants** (optional, for devices that support rounded icons):
  - `mipmap-*/ic_launcher_round.png` (same sizes)

- **Adaptive icon** (Android 8+, `/mipmap-anydpi-v26/ic_launcher.xml`):
  - Points to background and foreground drawables
  - Background: `/drawable/ic_launcher_background.xml`
  - Foreground: `/drawable-v24/ic_launcher_foreground.xml` or a PNG

### Desktop/JVM Icon
JVM desktop app uses system tray icon. Can be configured in the Gradle desktop configuration if needed.

## Building the Project

### Prerequisites
- Android SDK (API 28+) installed
- JDK 11+
- Gradle 8.x

### Build Commands

#### Build for Android (APK)
```bash
./gradlew :composeApp:assembleDebug      # Debug APK
./gradlew :composeApp:assembleRelease    # Release APK (unsigned)
./gradlew :composeApp:bundleRelease      # Android App Bundle
```

APK output: `composeApp/build/outputs/apk/debug/` or `release/`

#### Build for Desktop (JVM/Compose)

**Run directly:**
```bash
./gradlew run
```

**Build distributable packages:**
```bash
./gradlew :composeApp:createDistributable
```

Output: `composeApp/build/compose/binaries/main/dist/`

**Platform-specific builds:**
```bash
./gradlew :composeApp:packageDmg       # macOS .dmg
./gradlew :composeApp:packageMsi       # Windows .msi
./gradlew :composeApp:packageDeb       # Linux .deb
```

#### Build for Multiple Platforms
```bash
./gradlew build
```

### Development Build

For faster builds during development, use:
```bash
./gradlew :composeApp:jvmRun           # Run JVM version
./gradlew hotRunJvm                     # Run with hot reload enabled
```

## Package Name

The application package is `ru.theairblow.turnable`. This is used for:
- Android application ID
- Java namespace
- Android preferences storage
- File paths

If you need to change it again:
1. Update `composeApp/build.gradle.kts` (namespace, applicationId, mainClass, packageName)
2. Rename Kotlin source directories from `/src/{platform}/kotlin/ru/theairblow/turnable/` to your new package
3. Update all Kotlin `package` and `import` statements
4. Update JVM preferences node name in `AppPreferences.jvm.kt`

## Version Management

Current version: **1.0** (1.0.0 in compose desktop)

To update:
1. Edit `composeApp/build.gradle.kts`:
   - `versionCode` (Android)
   - `versionName` (Android, e.g., "1.0", "1.1")
   - `packageVersion` (Desktop, e.g., "1.0.0", "1.1.0")

2. Version must match pattern: MAJOR.MINOR[.PATCH]

## Signing Releases (Android)

For Play Store distribution, you need to sign APKs/Bundles:

```bash
./gradlew bundleRelease -Pandroid.enableOnDemandModules=false
```

Then sign with your keystore using Google Play Console or manually with `jarsigner`.

## Testing Before Release

```bash
# Test debug build on connected device
./gradlew :composeApp:installDebug
adb shell am start -n ru.theairblow.turnable/.MainActivity

# Test desktop release build
./gradlew :composeApp:packageMsi (or packageDeb on Linux)
```


## Releases

[The CI workflow](.github/workflows/ci.yml) runs on every push and pull request to main. A push whose commit subject contains `(major)`, `(minor)` or `(patch)` bumps the latest tag accordingly, builds the Android APK and the Linux, Windows and macOS installers, and publishes them as a GitHub release.

The APK is signed when these repository secrets are set, and left unsigned otherwise:
- `ANDROID_KEYSTORE_B64`: the keystore file, base64 encoded
- `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`

Locally, `-PappVersion=1.2.3` and `-PappVersionCode=10203` override the version baked into a build.
