# Turnable App &nbsp;·&nbsp; [🇷🇺 RU](README_RU.md)
Turnable App is the Android and desktop client for [Turnable](https://github.com/TheAirBlow/Turnable), a VPN core that tunnels TCP and UDP traffic through platform call infrastructure. The app runs a local core service, connects to remote services, and keeps client and server instances in one place. It is written in Kotlin with Compose Multiplatform.

> [!NOTE]
> The app controls a Turnable core, it does not replace one. A local service downloads the matching core release from GitHub on first start, and a remote service has to be running already.

## Features
1. Clients grouped by subscription, with usage, expiry, announcements and support links
2. Plain client mode by default, with services, servers and providers behind an advanced switch
3. Local services that download and run Turnable releases, or any custom core binary
4. English and Russian UI that follows the system language

## How it works
The app is a controller. The Turnable core does the tunneling, either as a service process on the same device or as a service reached over the network. Both use service protocol v3.

```mermaid
flowchart LR
    UI[Compose UI] --> VM[AppViewModel]
    VM -->|unix socket| Local[Local service process]
    VM -->|TCP| Remote[Remote service]
    Local --> CoreL[Turnable core]
    Remote --> CoreR[Turnable core]
    CoreL --> Instances[Clients and servers]
    CoreR --> Instances
```

On first launch the app creates the `local` service. When a service enables authentication, its `allowed_keys` must contain the app's public key. The app can also pin the service's public key and refuse to connect to anything else. The app runs in plain mode my default, which starts the local service and only shows Clients and Settings. Turn on Advanced mode in Settings to manage services, servers and providers

Desktop data lives in `~/.turnable`, or in `$TURNABLE_HOME` when set. On Android it stays in the app's private storage. Setting up the core itself, including service keys and server configs, is covered in the [Turnable documentation](https://github.com/TheAirBlow/Turnable/tree/main/docs).

## Requirements
- Turnable v0.8.4 or newer for local services. A custom core binary skips this check.
- Internet access on first start, to download the core.
- For remote services, a reachable service address.

## Building
Pre-built packages are on the [releases page](https://github.com/TheAirBlow/TurnableApp/releases): an Android APK, Linux `.deb`, Windows `.msi` and macOS `.dmg`.

Building requires JDK 21 and, for Android, the Android SDK with API 37. The Android target is pinned to SDK 28 because newer targets block `exec()` of the downloaded core.

```bash
./gradlew :composeApp:run              # desktop
./gradlew :androidApp:assembleDebug    # Android debug APK
./gradlew :composeApp:jvmTest          # tests
```

Set `TURNABLE_BIN` to a Turnable binary to also run the interop tests against a real service. Packaging, signing, icons and localization are covered in [the build guide](BUILD_AND_DEPLOY.md).

## Setup
1. Install the app from the releases page, or build it from source.
2. Open the Clients tab and add a subscription URL from your provider, or paste a `turnable://` link. The add menu has both.
3. Start the clients you need. Point your VPN or proxy client at the listen address shown for each one.

## Limitations
- There is no iOS app.
- Desktop has no embedded browser for captchas, so they are solved in the system browser with the service's userscript.
- The client forms cover relay and direct connections. P2P is not offered yet, the core lists it as work in progress.

## Credits
- [Turnable](https://github.com/TheAirBlow/Turnable) - the core and service protocol this app drives.
- [vk-turn-proxy](https://github.com/cacggghp/vk-turn-proxy) - the original project Turnable is partially based on.

## License
[GNU General Public License v2.0](LICENSE)
