package ru.theairblow.turnable.data

import kotlinx.serialization.Serializable
import ru.theairblow.turnable.subscription.Subscription

@Serializable
enum class ServiceType { LOCAL, REMOTE }

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** A Turnable service the app can manage: one it runs itself (local) or one reached over TCP (remote). */
@Serializable
data class ServiceConfig(
    val id: String,
    val name: String,
    val type: ServiceType,
    // Remote only
    val host: String = "",
    val port: Int = 0,
    /** Our ML-KEM-768 public key (base64); must be in the service's `allowed_keys` when it requires auth. */
    val clientPubKeyB64: String = "",
    /** Matching private key seed (base64); the handshake needs it to prove we own [clientPubKeyB64]. */
    val clientPrivKeyB64: String = "",
    /** When set, the connection fails unless the service presents exactly this ML-KEM-768 public key (base64). */
    val serverPubKeyB64: String = "",
    // Local only
    /** Turnable release tag to run, e.g. `v1.2.3`; blank means the latest release. */
    val version: String = "",
    /** A Turnable binary to run instead of a downloaded release; bypasses the minimum version check. */
    val customCorePath: String = "",
    /** Skips automatic captcha solving, so every connection asks for a manual solve. */
    val forceManualCaptcha: Boolean = false,
) {
    val subtitle: String
        get() = when (type) {
            ServiceType.LOCAL -> "This device · " + if (customCorePath.isNotBlank()) "custom core" else version.ifBlank { "latest release" }
            ServiceType.REMOTE -> "$host:$port"
        }
}

/** The on-device service that simple mode runs; created on first launch. */
const val DEFAULT_SERVICE_ID = "local"

@Serializable
data class AppPreferencesData(
    val services: List<ServiceConfig> = emptyList(),
    val selectedServiceId: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoConnect: Boolean = true,
    /** Starts the clients that were running when the app last closed. */
    val restoreRunning: Boolean = true,
    val runningClients: Set<String> = emptySet(),
    val subscriptions: List<Subscription> = emptyList(),
    /** Shows the services, servers and providers tabs; off keeps the app a plain client. */
    val advancedMode: Boolean = false,
    /** Checks for new core releases from time to time and asks before installing one. */
    val autoUpdate: Boolean = true,
    val lastUpdateCheckSec: Long = 0,
    /** Release the user said no to, so it isn't offered again. */
    val dismissedUpdate: String = "",
)
