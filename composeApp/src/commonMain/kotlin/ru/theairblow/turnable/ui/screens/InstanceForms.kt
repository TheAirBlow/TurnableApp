package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.theairblow.turnable.config.Choices
import ru.theairblow.turnable.config.ClientConfig
import ru.theairblow.turnable.config.ClientRoute
import ru.theairblow.turnable.config.ConfigJson
import ru.theairblow.turnable.config.RelayServerConfig
import ru.theairblow.turnable.config.configDisplayName
import ru.theairblow.turnable.config.missingFields
import ru.theairblow.turnable.config.splitConfigs
import ru.theairblow.turnable.proto.StartClientRequest
import ru.theairblow.turnable.proto.StartServerRequest
import ru.theairblow.turnable.service.generateServiceKeyPair
import ru.theairblow.turnable.subscription.listenAddrsFor
import ru.theairblow.turnable.subscription.suggestedPorts
import ru.theairblow.turnable.ui.components.DropdownField
import ru.theairblow.turnable.ui.components.ErrorBanner
import ru.theairblow.turnable.ui.components.FormSection
import ru.theairblow.turnable.ui.components.PaneTopBar
import ru.theairblow.turnable.ui.components.SectionCard
import ru.theairblow.turnable.ui.components.SwitchRow
import ru.theairblow.turnable.ui.components.TextInput
import ru.theairblow.turnable.ui.components.rememberClipboardReader
import ru.theairblow.turnable.ui.theme.TurnableTheme
import ru.theairblow.turnable.viewmodel.AppViewModel
import kotlin.io.encoding.Base64

private enum class ConfigMode(val label: String) { FORM("Form"), RAW("Paste") }

@Composable
private fun ModeSwitch(mode: ConfigMode, rawLabel: String, onChange: (ConfigMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ConfigMode.entries.forEachIndexed { i, m ->
            SegmentedButton(mode == m, { onChange(m) }, SegmentedButtonDefaults.itemShape(i, ConfigMode.entries.size)) {
                Text(if (m == ConfigMode.RAW) rawLabel else m.label)
            }
        }
    }
}

/** Shared name / instance ID / autostart fields. */
@Composable
private fun InstanceIdentityFields(
    name: String, onName: (String) -> Unit,
    id: String, onId: (String) -> Unit,
    autostart: Boolean, onAutostart: (Boolean) -> Unit,
    nameHint: String,
) {
    TextInput(name, onName, "Name", supporting = nameHint)
    TextInput(id, { onId(it.filterNot { c -> c == '/' || c == '\\' }) }, "Instance ID (optional)", mono = true,
        supporting = "Leave empty to let the service generate one")
    SwitchRow("Autostart", autostart, onAutostart, "Restore this instance when the service restarts")
}

/** Neutral note listing required fields that are still empty. */
@Composable
private fun MissingHint(missing: List<String>) {
    if (missing.isEmpty()) return
    Text(
        "Still needed: " + missing.joinToString(", "),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A banner stating whether the service accepted the config. */
@Composable
private fun ValidationResult(valid: Boolean?, error: String?) {
    when {
        error != null -> ErrorBanner(error)
        valid == true -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, null, tint = TurnableTheme.status.online, modifier = Modifier.size(18.dp))
            Text("The service accepts this config", style = MaterialTheme.typography.bodyMedium, color = TurnableTheme.status.online)
        }
    }
}

/** Validates [config] with the service after typing pauses. */
@Composable
private fun rememberValidation(config: String, validate: suspend (String) -> Pair<Boolean, String>): Pair<Boolean?, String?> {
    var state by remember { mutableStateOf<Pair<Boolean?, String?>>(null to null) }
    LaunchedEffect(config) {
        if (config.isBlank()) { state = null to null; return@LaunchedEffect }
        delay(500)
        val (ok, err) = validate(config)
        state = ok to err.ifBlank { null }?.takeIf { !ok }
    }
    return state
}

// ─── Server ───────────────────────────────────────────────────────────────────

@Composable
fun CreateServerPane(vm: AppViewModel, onClose: () -> Unit, onCreated: (String) -> Unit) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(ConfigMode.FORM) }
    var name by remember { mutableStateOf("") }
    var instanceId by remember { mutableStateOf("") }
    var autostart by remember { mutableStateOf(true) }
    var providerUuid by remember { mutableStateOf(providers.firstOrNull()?.uuid ?: "") }
    var form by remember { mutableStateOf(RelayServerConfig()) }
    var raw by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val config = if (mode == ConfigMode.FORM) ConfigJson.encodeToString(RelayServerConfig.serializer(), form) else raw.trim()
    val missing = if (mode == ConfigMode.FORM) form.missingFields() else emptyList()
    val (valid, validationError) = rememberValidation(if (missing.isEmpty()) config else "") { c ->
        vm.request { validateServerConfig(c) }.fold({ it.valid to it.error }, { false to (it.message ?: "") })
    }
    val canStart = providerUuid.isNotBlank() && config.isNotBlank() && missing.isEmpty() && !submitting

    Scaffold(topBar = {
        PaneTopBar("New server", onBack = onClose, busy = submitting) {
            TextButton(enabled = canStart, onClick = {
                scope.launch {
                    submitting = true; error = null
                    vm.request {
                        startServer(StartServerRequest(config, instanceId.trim(), name.trim(), autostart, providerUuid))
                    }.onSuccess { vm.refresh(); onCreated(it) }.onFailure { error = it.message }
                    submitting = false
                }
            }) { Text("Start") }
        }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { ErrorBanner(it) { error = null } }
            InstanceIdentityFields(name, { name = it }, instanceId, { instanceId = it }, autostart, { autostart = it }, "e.g. Main relay")

            FormSection("Provider")
            if (providers.isEmpty()) {
                ErrorBanner("Create a provider first (Providers tab). It holds the routes and users this server serves.")
            } else {
                DropdownField(
                    "Provider", providers.firstOrNull { it.uuid == providerUuid }, providers, { providerUuid = it?.uuid.orEmpty() },
                    describe = { it?.name ?: "Select…" },
                )
            }

            FormSection("Configuration")
            ModeSwitch(mode, "JSON") { mode = it }
            if (mode == ConfigMode.FORM) ServerConfigFields(form) { form = it }
            else TextInput(raw, { raw = it }, "Server config JSON", mono = true, singleLine = false, minLines = 8)
            MissingHint(missing)
            ValidationResult(valid, validationError)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ServerConfigFields(c: RelayServerConfig, onChange: (RelayServerConfig) -> Unit) {
    DropdownField("Platform", c.platformId, Choices.platforms, { onChange(c.copy(platformId = it)) })
    TextInput(c.callId, { onChange(c.copy(callId = it.trim())) }, "Call ID", mono = true, supporting = "Platform call or meeting ID")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(c.publicIp, { onChange(c.copy(publicIp = it.trim())) }, "Public IP / host", Modifier.weight(1f))
        TextInput(c.listenAddr, { onChange(c.copy(listenAddr = it.trim())) }, "Listen address", Modifier.weight(1f), mono = true)
    }
    DropdownField("Protocol", c.proto, Choices.protos, { onChange(c.copy(proto = it)) }, hint = Choices::describeProto)
    DropdownField("Cloak", c.cloak, Choices.cloaks, { onChange(c.copy(cloak = it)) })
    SectionCard(title = "Server key pair", icon = Icons.Default.Key) {
        Text(
            "Clients use the public key to encrypt the handshake.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = {
            val (pub, priv) = generateServiceKeyPair()
            onChange(c.copy(pubKey = Base64.encode(pub), privKey = Base64.encode(priv)))
        }) { Text(if (c.pubKey.isBlank()) "Generate" else "Regenerate") }
        TextInput(c.pubKey, { onChange(c.copy(pubKey = it.trim())) }, "Public key", mono = true)
        TextInput(c.privKey, { onChange(c.copy(privKey = it.trim())) }, "Private key", mono = true)
    }
}

// ─── Client ───────────────────────────────────────────────────────────────────

@Composable
fun CreateClientPane(vm: AppViewModel, onClose: () -> Unit, onCreated: (String) -> Unit) {
    val readClipboard = rememberClipboardReader()
    var mode by remember { mutableStateOf(ConfigMode.RAW) }
    var name by remember { mutableStateOf("") }
    var instanceId by remember { mutableStateOf("") }
    var autostart by remember { mutableStateOf(true) }
    // Random once, here; passed explicitly so the service keeps it across restarts
    var listenAddr by remember { mutableStateOf(vm.newClientListenAddr()) }
    var addrEdited by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf(readClipboard().takeIf { "turnable://" in it || it.trimStart().startsWith("{") }.orEmpty()) }
    var form by remember { mutableStateOf(ClientConfig(routes = listOf(ClientRoute(transport = "mpath")), peers = 1, proto = "dtls", encryption = "handshake", cloak = "none")) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val pasted = if (mode == ConfigMode.RAW) splitConfigs(raw) else emptyList()
    val bulk = pasted.size > 1
    val config = if (mode == ConfigMode.FORM) ConfigJson.encodeToString(ClientConfig.serializer(), form) else pasted.firstOrNull().orEmpty()
    val missing = if (mode == ConfigMode.FORM) form.missingFields() else emptyList()
    val (valid, validationError) = rememberValidation(if (bulk || missing.isNotEmpty()) "" else config) { c ->
        vm.request { validateClientConfig(c) }.fold({ it.valid to it.error }, { false to (it.message ?: "") })
    }
    val addrOk = listenAddr.substringAfterLast(':', "").toIntOrNull()?.let { it in 0..65535 } == true

    fun start() = scope.launch {
        submitting = true; error = null
        if (bulk) {
            // Each pasted config gets its own random port and the name from its link
            var started = 0
            val failures = mutableListOf<String>()
            pasted.forEach { c ->
                val addrs = listenAddrsFor(suggestedPorts(c), vm.newClientListenAddr().substringAfterLast(':').toInt())
                vm.request { startClient(StartClientRequest(c, addrs, name = configDisplayName(c).orEmpty(), autostart = autostart)) }
                    .onSuccess { started++ }.onFailure { failures += it.message.orEmpty() }
            }
            vm.refresh()
            vm.notify("Started $started of ${pasted.size} clients")
            if (failures.isEmpty()) onClose() else error = failures.distinct().joinToString("\n")
        } else {
            val finalName = name.trim().ifEmpty { configDisplayName(config).orEmpty() }
            // A link's own port suggestions apply unless the user picked an address
            val addrs = if (addrEdited) listOf(listenAddr.trim()) else listenAddrsFor(suggestedPorts(config), listenAddr.substringAfterLast(':').toInt())
            vm.request { startClient(StartClientRequest(config, addrs, instanceId.trim(), finalName, autostart)) }
                .onSuccess { vm.refresh(); onCreated(it) }.onFailure { error = it.message }
        }
        submitting = false
    }

    Scaffold(topBar = {
        PaneTopBar("New client", onBack = onClose, busy = submitting) {
            TextButton(enabled = config.isNotBlank() && missing.isEmpty() && (bulk || addrOk) && !submitting, onClick = { start() }) {
                Text(if (bulk) "Start ${pasted.size}" else "Start")
            }
        }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { ErrorBanner(it) { error = null } }
            ModeSwitch(mode, "Link / JSON") { mode = it }
            if (mode == ConfigMode.RAW) {
                TextInput(raw, { raw = it }, "turnable:// link(s) or JSON", mono = true, singleLine = false, minLines = 5,
                    supporting = "One config per line; several lines start several clients",
                    trailing = { IconButton(onClick = { raw = readClipboard() }) { Icon(Icons.Default.ContentPaste, "Paste") } })
            } else {
                ClientConfigFields(form) { form = it }
            }
            MissingHint(missing)
            if (!bulk) ValidationResult(valid, validationError)

            FormSection("Instance")
            if (bulk) {
                Text(
                    "${pasted.size} configs detected. Each client gets its own random local port and the name from its link.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow("Autostart", autostart, { autostart = it }, "Restore these clients when the service restarts")
            } else {
                InstanceIdentityFields(name, { name = it }, instanceId, { instanceId = it }, autostart, { autostart = it },
                    "Defaults to the name in the config")
                TextInput(
                    listenAddr, { listenAddr = it.trim(); addrEdited = true }, "Local listen address", mono = true, isError = !addrOk,
                    supporting = if (!addrEdited && suggestedPorts(config).any { it > 0 }) "The link suggests its own ports, which are used unless you change this"
                    else "First route listens here; further routes use the next ports",
                    trailing = { IconButton(onClick = { listenAddr = vm.newClientListenAddr(); addrEdited = true }) { Icon(Icons.Default.Shuffle, "New random port") } },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClientConfigFields(c: ClientConfig, onChange: (ClientConfig) -> Unit) {
    val relay = c.type == "relay"
    DropdownField("Connection type", c.type, Choices.clientTypes, { onChange(c.copy(type = it)) },
        hint = { if (it == "direct") "Straight to the destination, not recommended" else null })
    DropdownField("Platform", c.platformId, Choices.platforms, { onChange(c.copy(platformId = it)) })
    TextInput(c.callId, { onChange(c.copy(callId = it.trim())) }, "Call ID", mono = true)
    TextInput(c.userUuid, { onChange(c.copy(userUuid = it.trim())) }, "User UUID", mono = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(c.gateway, { onChange(c.copy(gateway = it.trim())) }, "Gateway (host:port)", Modifier.weight(0.7f), mono = true)
        TextInput(c.peers.takeIf { it > 0 }?.toString().orEmpty(), { onChange(c.copy(peers = it.filter(Char::isDigit).take(3).toIntOrNull() ?: 0)) },
            "Peers", Modifier.weight(0.3f), numeric = true)
    }
    if (relay) {
        TextInput(c.pubKey, { onChange(c.copy(pubKey = it.trim())) }, "Server public key", mono = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField("Protocol", c.proto, Choices.protos, { onChange(c.copy(proto = it)) }, Modifier.weight(1f))
            DropdownField("Encryption", c.encryption, Choices.encryptions, { onChange(c.copy(encryption = it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField("Mask", c.mask.ifBlank { "on" }, Choices.masks, { onChange(c.copy(mask = if (it == "on") "" else it)) }, Modifier.weight(1f))
            DropdownField("Cloak", c.cloak.ifBlank { "none" }, Choices.cloaks, { onChange(c.copy(cloak = it)) }, Modifier.weight(1f))
        }
    }
    TextInput(c.name, { onChange(c.copy(name = it)) }, "Display name (in config)")

    FormSection("Routes")
    c.routes.forEachIndexed { i, r ->
        fun set(route: ClientRoute) = onChange(c.copy(routes = c.routes.toMutableList().also { it[i] = route }))
        SectionCard(title = "Route ${i + 1}", action = {
            if (c.routes.size > 1) IconButton(onClick = { onChange(c.copy(routes = c.routes.filterIndexed { j, _ -> j != i })) }, Modifier.size(32.dp)) {
                Icon(Icons.Default.RemoveCircleOutline, "Remove route", tint = MaterialTheme.colorScheme.error)
            }
        }) {
            TextInput(r.routeId, { set(r.copy(routeId = it.trim())) }, "Route ID", mono = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("Socket", r.socket, Choices.sockets, { set(r.copy(socket = it)) }, Modifier.weight(1f))
                DropdownField("Transport", r.transport.ifBlank { "none" }, Choices.transports,
                    { set(r.copy(transport = if (it == "none") "" else it)) }, Modifier.weight(1f), hint = Choices::describeTransport)
            }
        }
    }
    TextButton(onClick = { onChange(c.copy(routes = c.routes + ClientRoute(transport = c.routes.firstOrNull()?.transport ?: "mpath"))) }) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add route")
    }
    if (c.routes.map { it.transport == "mpath" }.distinct().size > 1) {
        ErrorBanner("mpath routes can't be mixed with other transports in one config")
    }
}
