package ru.theairblow.turnable

import ru.theairblow.turnable.config.RelayServerConfig
import ru.theairblow.turnable.config.generateClientConfig
import ru.theairblow.turnable.config.splitConfigs
import ru.theairblow.turnable.proto.RouteInfo
import ru.theairblow.turnable.proto.UserInfo
import ru.theairblow.turnable.subscription.PORTS_PER_ENTRY
import ru.theairblow.turnable.subscription.listenAddrsFor
import ru.theairblow.turnable.subscription.parseLinks
import ru.theairblow.turnable.subscription.randomLocalPort
import ru.theairblow.turnable.subscription.suggestedPorts
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigParsingTest {
    @Test
    fun subscriptionAcceptsPlainAndBase64AndSkipsForeignLinks() {
        val body = "vless://x@h:1#Other\nturnable://a:b@vk.com/wg?type=relay#Home%20WG\nturnable://c:d@vk.com/wg#Home%20WG\n"
        for (b in listOf(body, Base64.encode(body.encodeToByteArray()))) {
            val links = parseLinks(b)
            assertEquals(listOf("Home WG", "Home WG#1"), links.map { it.key })
            assertEquals("Home WG", links[0].name)
        }
    }

    @Test
    fun suggestedPortsFollowRouteOrderAndFillGapsFromTheBasePort() {
        val link = "turnable://a:b@vk.com/wg/udp/mpath/https/tcp?type=relay&ports=,9443#Name"
        assertEquals(listOf(0, 9443), suggestedPorts(link))
        assertEquals(listOf("127.0.0.1:30000", "127.0.0.1:9443"), listenAddrsFor(suggestedPorts(link), 30000))
        assertEquals(listOf("127.0.0.1:30000"), listenAddrsFor(suggestedPorts("turnable://a:b@vk.com/wg"), 30000))
        assertEquals(emptyList(), suggestedPorts("{\"note\": \"?ports=1\"}"))
    }

    @Test
    fun randomPortBlocksNeverOverlapTakenPorts() {
        val taken = (20000 until 40000).filter { it % 100 < 50 }.toSet()
        repeat(200) {
            val p = randomLocalPort(taken)
            assertEquals(emptyList(), (p until p + PORTS_PER_ENTRY).filter { it in taken })
        }
    }

    @Test
    fun splitConfigsKeepsJsonWholeAndIgnoresBracesInStrings() {
        val text = "turnable://one\n{\n  \"name\": \"a } b\",\n  \"x\": {\"y\": 1}\n}\nturnable://two"
        val parts = splitConfigs(text)
        assertEquals(3, parts.size)
        assertEquals("turnable://two", parts[2])
    }

    @Test
    fun generatedClientConfigMirrorsGo() {
        val server = RelayServerConfig(callId = "123", pubKey = "PUB", publicIp = "203.0.113.45", listenAddr = "0.0.0.0:56000")
        val user = UserInfo("uuid", listOf("wg", "https"), "relay", peers = 0)
        val cfg = generateClientConfig(server, user, listOf(
            RouteInfo("wg", socket = "udp", transport = "none", name = "WG"),
            RouteInfo("https", socket = "tcp", transport = "", encryption = "full"),
        ))
        assertEquals("203.0.113.45:56000", cfg.gateway)
        assertEquals(listOf("", "mpath"), cfg.routes.map { it.transport })
        assertEquals("full", cfg.encryption)
        assertEquals(1, cfg.peers)
        assertEquals("WG", cfg.name)
    }
}
