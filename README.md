# Turnable

A Kotlin Multiplatform (Compose) app for [Turnable](https://github.com/TheAirBlow/Turnable), a VPN core that tunnels traffic through platform call infrastructure. It runs on Android and desktop.

By default it is a plain client: it starts an on-device Turnable service, imports your provider's subscription as a group of clients, and lets you switch any number of them on at once. Advanced mode in Settings adds tabs for managing services, servers and providers.

## Features
- Subscriptions shown as groups, with usage, announcements and support links
- Manual captcha handling with notifications, and an in-app solve page on Android
- Local and remote services over the encrypted service protocol (v3)
- Turnable v0.8.4 or newer, or a custom core binary

## Building
Requires a JDK and, for Android, the Android SDK.

```bash
./gradlew :composeApp:run              # desktop
./gradlew :androidApp:assembleDebug    # Android debug APK
./gradlew :composeApp:jvmTest          # tests
```

Set `TURNABLE_BIN` to a built Turnable binary to include the tests that talk to a real service. See [the build guide](BUILD_AND_DEPLOY.md) for packaging and releases.

## License
[GNU General Public License v2.0](LICENSE)
