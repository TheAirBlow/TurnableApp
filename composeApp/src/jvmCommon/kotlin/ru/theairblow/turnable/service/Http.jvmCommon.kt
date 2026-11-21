package ru.theairblow.turnable.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private const val MAX_BODY = 4 * 1024 * 1024

/** Opens a connection with the app's user agent and sane timeouts. */
internal fun openHttp(url: String): HttpURLConnection =
    (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", USER_AGENT)
        instanceFollowRedirects = true
        connectTimeout = 15_000
        readTimeout = 30_000
    }

actual suspend fun httpGet(url: String, accept: String?): HttpResponse = withContext(Dispatchers.IO) {
    val conn = openHttp(url)
    try {
        accept?.let { conn.setRequestProperty("Accept", it) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val bytes = stream?.use { it.readNBytes(MAX_BODY) } ?: ByteArray(0)
        val headers = conn.headerFields.filterKeys { it != null }.mapValues { it.value.joinToString(", ") }
        HttpResponse(code, headers, bytes.decodeToString())
    } finally {
        conn.disconnect()
    }
}
