package ru.theairblow.turnable.service

/** Sent with every HTTP request; panels such as Frostbite recognize the "Turnable-Android" prefix and serve turnable:// links. */
const val USER_AGENT = "Turnable-Android/$APP_VERSION"

class HttpResponse(val code: Int, val headers: Map<String, String>, val body: String) {
    /** Case-insensitive header lookup. */
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/** GET [url] with the app's user agent. Throws on network errors, not on HTTP error codes. */
expect suspend fun httpGet(url: String, accept: String? = null): HttpResponse
