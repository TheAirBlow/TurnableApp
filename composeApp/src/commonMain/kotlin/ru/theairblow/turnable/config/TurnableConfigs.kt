@file:OptIn(ExperimentalSerializationApi::class)

package ru.theairblow.turnable.config

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.theairblow.turnable.proto.RouteInfo
import ru.theairblow.turnable.proto.UserInfo

// JSON shapes of Turnable configs (see turnable/docs/server/CONFIG.md and docs/client/CONFIG.md).

/** Known option values, with the recommended one first. */
object Choices {
    val platforms = listOf("vk.com")
    val protos = listOf("dtls", "srtp", "none")
    val cloaks = listOf("none")
    val sockets = listOf("udp", "tcp")
    val transports = listOf("mpath", "kcp", "sctp", "none")
    val encryptions = listOf("handshake", "full")
    val userTypes = listOf("relay", "p2p")
    val clientTypes = listOf("relay", "direct")
    val masks = listOf("on", "off")
}

/** A required field a form still leaves empty. The UI supplies the label. */
enum class MissingField { CALL_ID, USER_UUID, GATEWAY, PEERS, SERVER_PUBLIC_KEY, ROUTE_ID, PUBLIC_IP, LISTEN_ADDRESS, KEY_PAIR }

/** Defaults are dropped from the output, except fields marked @EncodeDefault that the service needs to see. */
val ConfigJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** A single `relay` server, as passed to StartServerRequest.config. */
@Serializable
data class RelayServerConfig(
    @EncodeDefault val type: String = "relay",
    @EncodeDefault @SerialName("platform_id") val platformId: String = "vk.com",
    @SerialName("call_id") val callId: String = "",
    @SerialName("pub_key") val pubKey: String = "",
    @SerialName("priv_key") val privKey: String = "",
    @EncodeDefault val proto: String = "dtls",
    @EncodeDefault @SerialName("listen_addr") val listenAddr: String = "0.0.0.0:56000",
    @SerialName("public_ip") val publicIp: String = "",
    @EncodeDefault val cloak: String = "none",
)

@Serializable
data class ClientRoute(
    @SerialName("route_id") val routeId: String = "",
    @EncodeDefault val socket: String = "udp",
    val transport: String = "",
)

@Serializable
data class ClientConfig(
    @EncodeDefault val type: String = "relay",
    @EncodeDefault @SerialName("platform_id") val platformId: String = "vk.com",
    @SerialName("user_uuid") val userUuid: String = "",
    @SerialName("call_id") val callId: String = "",
    val routes: List<ClientRoute> = emptyList(),
    val name: String = "",
    val gateway: String = "",
    val proto: String = "",
    val cloak: String = "",
    val peers: Int = 0,
    val encryption: String = "",
    @SerialName("pub_key") val pubKey: String = "",
    val mask: String = "",
    /** Suggested local ports, comma-separated in route order. */
    val ports: String = "",
)

/** Provider config accepted by AddProviderRequest: `raw` embeds routes/users, `json` points at a file on the service host. */
@Serializable
data class ProviderConfig(
    @EncodeDefault val type: String = "raw",
    val path: String = "",
    val routes: List<JsonElement>? = null,
    val users: List<JsonElement>? = null,
)

fun parseServerConfig(text: String): RelayServerConfig? =
    runCatching { ConfigJson.decodeFromString<RelayServerConfig>(text) }.getOrNull()

fun parseClientConfig(text: String): ClientConfig? =
    runCatching { ConfigJson.decodeFromString<ClientConfig>(text) }.getOrNull()

/** Re-indents a JSON document for display; returns [text] unchanged if it isn't JSON. */
fun prettyJson(text: String): String =
    runCatching { ConfigJson.encodeToString(JsonElement.serializer(), ConfigJson.parseToJsonElement(text)) }.getOrDefault(text)

/**
 * Builds the client config a user needs to reach [server] over [routes];
 * mirrors relay.Handler.GetClientConfig in the Go implementation.
 */
fun generateClientConfig(server: RelayServerConfig, user: UserInfo, routes: List<RouteInfo>): ClientConfig {
    require(routes.isNotEmpty()) { "Pick at least one route" }
    routes.forEach { r -> require(r.id in user.allowedRoutes) { "User is not allowed to use route ${r.id}" } }
    val port = server.listenAddr.substringAfterLast(':', "")
    require(server.publicIp.isNotBlank() && port.isNotBlank()) { "The server needs public_ip and listen_addr" }
    return ClientConfig(
        type = user.type.ifBlank { "relay" },
        platformId = server.platformId,
        userUuid = user.uuid,
        callId = server.callId,
        routes = routes.map { r ->
            ClientRoute(r.id, r.socket, when (r.transport) { "" -> "mpath"; "none" -> ""; else -> r.transport })
        },
        name = routes.mapNotNull { it.name.ifBlank { null } }.joinToString(", "),
        gateway = "${server.publicIp}:$port",
        proto = server.proto,
        cloak = server.cloak,
        peers = maxOf(user.peers, 1),
        encryption = if (routes.any { it.encryption == "full" }) "full" else "handshake",
        pubKey = server.pubKey,
    )
}

/** Splits pasted text into separate configs: one per line, with multi-line JSON objects kept whole. */
fun splitConfigs(text: String): List<String> {
    val results = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    var inString = false
    var escaped = false
    fun flush() {
        current.toString().trim().takeIf { it.isNotEmpty() }?.let(results::add)
        current.clear()
    }
    for (ch in text) {
        current.append(ch)
        if (inString) {
            when {
                escaped -> escaped = false
                ch == '\\' -> escaped = true
                ch == '"' -> inString = false
            }
            continue
        }
        when (ch) {
            '"' -> if (depth > 0) inString = true
            '{' -> depth++
            '}' -> if (--depth == 0) flush()
            '\n' -> if (depth == 0) flush()
        }
    }
    flush()
    return results
}

/** The display name a config carries: the URL fragment or the JSON `name`. */
fun configDisplayName(config: String): String? {
    val text = config.trim()
    val name = if (text.startsWith("{")) parseClientConfig(text)?.name
    else percentDecode(text.substringAfter('#', ""))
    return name?.trim()?.takeIf { it.isNotEmpty() }
}

/** Decodes %XX escapes (UTF-8). */
fun percentDecode(s: String): String {
    if ('%' !in s) return s
    val out = ArrayList<Byte>(s.length)
    var i = 0
    while (i < s.length) {
        val hex = if (s[i] == '%' && i + 2 <= s.lastIndex) s.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            out += hex.toByte(); i += 3
        } else {
            s[i].toString().encodeToByteArray().forEach { out += it }; i++
        }
    }
    return out.toByteArray().decodeToString()
}

/** Required fields still empty in a client form; empty when the config can be sent for validation. */
fun ClientConfig.missingFields(): List<MissingField> = buildList {
    if (callId.isBlank()) add(MissingField.CALL_ID)
    if (userUuid.isBlank()) add(MissingField.USER_UUID)
    if (gateway.isBlank()) add(MissingField.GATEWAY)
    if (peers < 1) add(MissingField.PEERS)
    if (type == "relay" && pubKey.isBlank()) add(MissingField.SERVER_PUBLIC_KEY)
    if (routes.isEmpty() || routes.any { it.routeId.isBlank() }) add(MissingField.ROUTE_ID)
}

/** Required fields still empty in a server form. */
fun RelayServerConfig.missingFields(): List<MissingField> = buildList {
    if (callId.isBlank()) add(MissingField.CALL_ID)
    if (publicIp.isBlank()) add(MissingField.PUBLIC_IP)
    if (listenAddr.isBlank()) add(MissingField.LISTEN_ADDRESS)
    if (pubKey.isBlank() || privKey.isBlank()) add(MissingField.KEY_PAIR)
}
