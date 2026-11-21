package ru.theairblow.turnable.platform

/** A client that cannot connect until the user solves the platform's captcha by hand. */
data class CaptchaRequest(
    val instanceId: String,
    val name: String,
    /** Port of the service's local token endpoint for this solve. */
    val port: Int,
    /** The call page to open. */
    val url: String,
    val deadlineSec: Long,
) {
    val id: String get() = instanceId
}

/** A newer core release for the local service [serviceId]. */
data class CoreUpdate(val serviceId: String, val serviceName: String, val version: String)

/** Surfaces what the app is doing outside its own window (Android notifications). */
interface PlatformNotifier {
    /** Called whenever the number of connected clients changes. */
    fun status(connected: Int, total: Int)

    fun captchaRequired(request: CaptchaRequest)

    fun captchaCleared(instanceId: String)

    /** A newer core release can be installed; the user answers through the app or the notification. */
    fun updateAvailable(update: CoreUpdate) = Unit

    fun updateCleared() = Unit

    object None : PlatformNotifier {
        override fun status(connected: Int, total: Int) = Unit
        override fun captchaRequired(request: CaptchaRequest) = Unit
        override fun captchaCleared(instanceId: String) = Unit
    }
}
