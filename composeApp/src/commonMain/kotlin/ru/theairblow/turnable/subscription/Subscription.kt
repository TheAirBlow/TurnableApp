package ru.theairblow.turnable.subscription

import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.getString
import ru.theairblow.turnable.config.percentDecode
import ru.theairblow.turnable.service.HttpResponse
import ru.theairblow.turnable.service.httpGet
import turnable.composeapp.generated.resources.*
import kotlin.io.encoding.Base64
import kotlin.random.Random

/**
 * A subscription URL serving turnable:// client links (plain or base64, one per line), as Frostbite does
 * for user agents containing "Turnable-Android". Its links become client instances on [serviceId],
 * shown together as one group in the clients list.
 */
@Serializable
data class Subscription(
    val id: String,
    val serviceId: String,
    val url: String,
    /** User-chosen name; falls back to the panel's Profile-Title. */
    val name: String = "",
    val title: String = "",
    /** Auto-update period; 0 disables it. Initialized from Profile-Update-Interval. */
    val updateHours: Int = 0,
    val lastUpdatedSec: Long = 0,
    /** Last update try, successful or not; failed updates retry after [RETRY_AFTER_FAILURE_SEC]. */
    val lastAttemptSec: Long = 0,
    val lastError: String = "",
    /** The panel's Announce header, shown on the group. */
    val announce: String = "",
    val supportUrl: String = "",
    val webPageUrl: String = "",
    val usage: SubscriptionUsage? = null,
    val entries: List<SubscriptionEntry> = emptyList(),
) {
    val displayName: String get() = name.ifBlank { title.ifBlank { url.substringAfter("://").substringBefore('/') } }
}

const val RETRY_AFTER_FAILURE_SEC = 15 * 60L

/** Local ports reserved per entry: a client listens on its base port plus one per extra route. */
const val PORTS_PER_ENTRY = 8

/** One link of a subscription and the client instance created for it. [port] is the base port, chosen once and kept. */
@Serializable
data class SubscriptionEntry(val key: String, val instanceId: String, val port: Int, val name: String)

/** Parsed Subscription-Userinfo header; [total]/[expire] are 0 when unlimited. */
@Serializable
data class SubscriptionUsage(val upload: Long = 0, val download: Long = 0, val total: Long = 0, val expire: Long = 0)

/** What a subscription URL returned. */
class FetchedSubscription(
    val links: List<SubscriptionLink>,
    val title: String?,
    val updateHours: Int?,
    val usage: SubscriptionUsage?,
    val announce: String?,
    val supportUrl: String?,
    val webPageUrl: String?,
)

/** A turnable:// link with the stable [key] used to match it across updates. [suggestedPorts] come from its `ports` parameter. */
class SubscriptionLink(val key: String, val name: String, val url: String, val suggestedPorts: List<Int> = emptyList()) {
    /** Listen addresses for a client on [basePort]; routes without a suggested port follow the base port. */
    fun listenAddrs(basePort: Int): List<String> = listenAddrsFor(suggestedPorts, basePort)
}

/** Loopback listen addresses honoring a link's [suggested] ports; routes without one follow [basePort]. */
fun listenAddrsFor(suggested: List<Int>, basePort: Int): List<String> =
    if (suggested.isEmpty()) listOf("127.0.0.1:$basePort")
    else suggested.mapIndexed { i, p -> "127.0.0.1:${if (p > 0) p else basePort + i}" }

suspend fun fetchSubscription(url: String): FetchedSubscription {
    val resp = httpGet(url)
    if (resp.code !in 200..299) throw IllegalStateException(getString(Res.string.subscription_http_error, resp.code))
    val links = parseLinks(resp.body).mapIndexed { i, link ->
        if (link.name.isBlank()) SubscriptionLink(link.key, getString(Res.string.subscription_link_n, i + 1), link.url, link.suggestedPorts)
        else link
    }
    if (links.isEmpty()) throw IllegalStateException(getString(Res.string.subscription_no_links))
    return FetchedSubscription(
        links, resp.encodedHeader("Profile-Title"), resp.header("Profile-Update-Interval")?.trim()?.toIntOrNull(), resp.userInfo(),
        resp.encodedHeader("Announce"), resp.header("Support-Url")?.trim()?.takeIf { it.isNotEmpty() },
        resp.header("Profile-Web-Page-Url")?.trim()?.takeIf { it.isNotEmpty() },
    )
}

private fun decodeMaybeBase64(body: String): String {
    val text = body.trim()
    if ("://" in text) return text
    return runCatching { Base64.decode(text.filterNot(Char::isWhitespace)).decodeToString() }.getOrDefault(text)
}

/** Keys come from the link's name (fragment) so a link keeps its instance and port when its settings change. */
internal fun parseLinks(body: String): List<SubscriptionLink> {
    val seen = mutableMapOf<String, Int>()
    return decodeMaybeBase64(body).lines()
        .map { it.trim() }
        .filter { it.startsWith("turnable://", ignoreCase = true) }
        .mapIndexed { i, link ->
            val name = percentDecode(link.substringAfter('#', "")).trim()
            val base = name.ifBlank { "link-$i" }
            val n = seen.getOrElse(base) { 0 }.also { seen[base] = it + 1 }
            SubscriptionLink(if (n == 0) base else "$base#$n", name, link, suggestedPorts(link))
        }
}

/** The `ports` query parameter of a link: one entry per route, 0 or empty meaning "no suggestion". */
fun suggestedPorts(link: String): List<Int> {
    if (!link.startsWith("turnable://", ignoreCase = true)) return emptyList()
    val query = link.substringBefore('#').substringAfter('?', "")
    val value = query.split('&').firstOrNull { it.startsWith("ports=") }?.removePrefix("ports=") ?: return emptyList()
    return percentDecode(value).split(',').map { it.trim().toIntOrNull()?.takeIf { p -> p in 1..65535 } ?: 0 }
}

/** Headers like Profile-Title and Announce may be sent as "base64:<text>". */
private fun HttpResponse.encodedHeader(name: String): String? {
    val raw = header(name)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (raw.startsWith("base64:")) runCatching { Base64.decode(raw.removePrefix("base64:")).decodeToString() }.getOrNull()
    else raw
}

private fun HttpResponse.userInfo(): SubscriptionUsage? {
    val raw = header("Subscription-Userinfo") ?: return null
    val values = raw.split(';').mapNotNull { part ->
        val (k, v) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
        k.trim().lowercase() to (v.trim().toLongOrNull() ?: 0L)
    }.toMap()
    return SubscriptionUsage(values["upload"] ?: 0, values["download"] ?: 0, values["total"] ?: 0, values["expire"] ?: 0)
}

/** A random base port whose whole [PORTS_PER_ENTRY] block avoids [taken]. */
fun randomLocalPort(taken: Set<Int>): Int {
    while (true) {
        val p = Random.nextInt(20000, 60000)
        if ((p until p + PORTS_PER_ENTRY).none { it in taken }) return p
    }
}
