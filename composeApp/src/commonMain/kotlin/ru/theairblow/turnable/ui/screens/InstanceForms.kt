package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Surface
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.config.Choices
import ru.theairblow.turnable.config.ClientConfig
import ru.theairblow.turnable.config.ClientRoute
import ru.theairblow.turnable.config.ConfigJson
import ru.theairblow.turnable.config.MissingField
import ru.theairblow.turnable.config.RelayServerConfig
import ru.theairblow.turnable.config.configDisplayName
import ru.theairblow.turnable.config.parseClientConfig
import ru.theairblow.turnable.config.missingFields
import ru.theairblow.turnable.config.splitConfigs
import ru.theairblow.turnable.proto.StartClientRequest
import ru.theairblow.turnable.proto.StartServerRequest
import ru.theairblow.turnable.proto.InstanceInfo
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
import turnable.composeapp.generated.resources.*
import kotlin.io.encoding.Base64

private enum class ConfigMode { FORM, RAW }

@Composable
private fun ModeSwitch(mode: ConfigMode, rawLabel: String, onChange: (ConfigMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ConfigMode.entries.forEachIndexed { i, m ->
            SegmentedButton(mode == m, { onChange(m) }, SegmentedButtonDefaults.itemShape(i, ConfigMode.entries.size)) {
                Text(if (m == ConfigMode.RAW) rawLabel else stringResource(Res.string.form_mode_form))
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
) {
    TextInput(name, onName, stringResource(Res.string.form_name))
    TextInput(
        id, { onId(it.filterNot { c -> c == '/' || c == '\\' }) }, stringResource(Res.string.form_instance_id),
        mono = true,
    )
    SwitchRow(
        stringResource(Res.string.form_autostart), autostart, onAutostart,
        stringResource(Res.string.form_autostart_hint),
    )
}

private fun MissingField.labelRes(): StringResource = when (this) {
    MissingField.CALL_ID -> Res.string.missing_call_id
    MissingField.USER_UUID -> Res.string.missing_user_uuid
    MissingField.GATEWAY -> Res.string.missing_gateway
    MissingField.PEERS -> Res.string.missing_peers
    MissingField.SERVER_PUBLIC_KEY -> Res.string.missing_server_public_key
    MissingField.ROUTE_ID -> Res.string.missing_route_id
    MissingField.PUBLIC_IP -> Res.string.missing_public_ip
    MissingField.LISTEN_ADDRESS -> Res.string.missing_listen_address
    MissingField.KEY_PAIR -> Res.string.missing_key_pair
}

/** Scrolling form body with a fixed note under the top bar. */
@Composable
private fun FormBody(padding: PaddingValues, note: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        if (note != null) Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
        )
    }
}

/** Note listing required fields that are still empty, or null when none are. */
@Composable
private fun missingSubtitle(missing: List<MissingField>): String? {
    if (missing.isEmpty()) return null
    val labels = missing.map { stringResource(it.labelRes()) }
    return stringResource(Res.string.form_still_needed, labels.joinToString(", "))
}

/** A banner stating whether the service accepted the config. */
@Composable
internal fun ValidationResult(valid: Boolean?, error: String?) {
    when {
        error != null -> ErrorBanner(error)
        valid == true -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.CheckCircle, null, tint = TurnableTheme.status.online, modifier = Modifier.size(18.dp))
            Text(
                stringResource(Res.string.form_config_accepted),
                style = MaterialTheme.typography.bodyMedium, color = TurnableTheme.status.online,
            )
        }
    }
}

/** Validates [config] with the service after typing pauses. */
@Composable
internal fun rememberValidation(config: String, validate: suspend (String) -> Pair<Boolean, String>): Pair<Boolean?, String?> {
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
    val selectPlaceholder = stringResource(Res.string.form_select_placeholder)

    Scaffold(topBar = {
        PaneTopBar(stringResource(Res.string.form_server_new_title), onBack = onClose, busy = submitting) {
            TextButton(enabled = canStart, onClick = {
                scope.launch {
                    submitting = true; error = null
                    vm.request {
                        startServer(StartServerRequest(config, instanceId.trim(), name.trim(), autostart, providerUuid))
                    }.onSuccess { vm.refresh(); onCreated(it) }.onFailure { error = it.message }
                    submitting = false
                }
            }) { Text(stringResource(Res.string.action_start)) }
        }
    }) { padding ->
        FormBody(padding, missingSubtitle(missing)) {
            error?.let { ErrorBanner(it) { error = null } }
            InstanceIdentityFields(
                name, { name = it }, instanceId, { instanceId = it }, autostart, { autostart = it },
            )

            FormSection(stringResource(Res.string.form_provider_section))
            if (providers.isEmpty()) {
                ErrorBanner(stringResource(Res.string.form_no_provider_error))
            } else {
                DropdownField(
                    stringResource(Res.string.form_provider), providers.firstOrNull { it.uuid == providerUuid }, providers,
                    { providerUuid = it?.uuid.orEmpty() },
                    describe = { it?.name ?: selectPlaceholder },
                )
            }

            FormSection(stringResource(Res.string.form_configuration))
            ModeSwitch(mode, stringResource(Res.string.form_mode_json)) { mode = it }
            if (mode == ConfigMode.FORM) ServerConfigFields(form) { form = it }
            else TextInput(
                raw, { raw = it }, stringResource(Res.string.form_server_config_json),
                mono = true, singleLine = false, minLines = 8,
            )
            ValidationResult(valid, validationError)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ServerConfigFields(c: RelayServerConfig, onChange: (RelayServerConfig) -> Unit) {
    DropdownField(stringResource(Res.string.form_platform), c.platformId, Choices.platforms, { onChange(c.copy(platformId = it)) })
    TextInput(
        c.callId, { onChange(c.copy(callId = it.trim())) }, stringResource(Res.string.form_call_id), mono = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(
            c.publicIp, { onChange(c.copy(publicIp = it.trim())) }, stringResource(Res.string.form_public_ip),
            Modifier.weight(1f),
        )
        TextInput(
            c.listenAddr, { onChange(c.copy(listenAddr = it.trim())) }, stringResource(Res.string.form_listen_address),
            Modifier.weight(1f), mono = true,
        )
    }
    DropdownField(
        stringResource(Res.string.form_protocol), c.proto, Choices.protos, { onChange(c.copy(proto = it)) },
    )
    DropdownField(stringResource(Res.string.form_cloak), c.cloak, Choices.cloaks, { onChange(c.copy(cloak = it)) })
    SectionCard(title = stringResource(Res.string.form_server_key_pair), icon = Icons.Default.Key) {
        Text(
            stringResource(Res.string.form_server_key_pair_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = {
            val (pub, priv) = generateServiceKeyPair()
            onChange(c.copy(pubKey = Base64.encode(pub), privKey = Base64.encode(priv)))
        }) {
            Text(stringResource(if (c.pubKey.isBlank()) Res.string.action_generate else Res.string.form_regenerate))
        }
        TextInput(c.pubKey, { onChange(c.copy(pubKey = it.trim())) }, stringResource(Res.string.form_public_key), mono = true)
        TextInput(c.privKey, { onChange(c.copy(privKey = it.trim())) }, stringResource(Res.string.form_private_key), mono = true)
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
            vm.notify(getString(Res.string.client_started_of, started, pasted.size))
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
        PaneTopBar(stringResource(Res.string.client_new_title), onBack = onClose, busy = submitting) {
            TextButton(enabled = config.isNotBlank() && missing.isEmpty() && (bulk || addrOk) && !submitting, onClick = { start() }) {
                Text(
                    if (bulk) stringResource(Res.string.client_start_count, pasted.size)
                    else stringResource(Res.string.action_start),
                )
            }
        }
    }) { padding ->
        FormBody(padding, missingSubtitle(missing)) {
            error?.let { ErrorBanner(it) { error = null } }
            ModeSwitch(mode, stringResource(Res.string.form_mode_link_json)) { mode = it }
            if (mode == ConfigMode.RAW) {
                TextInput(
                    raw, { raw = it }, stringResource(Res.string.client_raw_label),
                    mono = true, singleLine = false, minLines = 5,
                    trailing = {
                        IconButton(onClick = { raw = readClipboard() }) {
                            Icon(Icons.Default.ContentPaste, stringResource(Res.string.action_paste))
                        }
                    },
                )
            } else {
                ClientConfigFields(form) { form = it }
            }
            if (!bulk) ValidationResult(valid, validationError)

            FormSection(stringResource(Res.string.form_instance_section))
            if (bulk) {
                Text(
                    stringResource(Res.string.client_bulk_detected, pasted.size),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    stringResource(Res.string.form_autostart), autostart, { autostart = it },
                    stringResource(Res.string.client_bulk_autostart_hint),
                )
            } else {
                InstanceIdentityFields(
                    name, { name = it }, instanceId, { instanceId = it }, autostart, { autostart = it },
                )
                TextInput(
                    listenAddr, { listenAddr = it.trim(); addrEdited = true }, stringResource(Res.string.client_listen_address),
                    mono = true, isError = !addrOk,
                    trailing = {
                        IconButton(onClick = { listenAddr = vm.newClientListenAddr(); addrEdited = true }) {
                            Icon(Icons.Default.Shuffle, stringResource(Res.string.client_new_random_port))
                        }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ClientConfigFields(c: ClientConfig, onChange: (ClientConfig) -> Unit) {
    val relay = c.type == "relay"
    DropdownField(
        stringResource(Res.string.client_connection_type), c.type, Choices.clientTypes, { onChange(c.copy(type = it)) },
    )
    DropdownField(stringResource(Res.string.form_platform), c.platformId, Choices.platforms, { onChange(c.copy(platformId = it)) })
    TextInput(c.callId, { onChange(c.copy(callId = it.trim())) }, stringResource(Res.string.form_call_id), mono = true)
    TextInput(c.userUuid, { onChange(c.copy(userUuid = it.trim())) }, stringResource(Res.string.client_user_uuid), mono = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextInput(
            c.gateway, { onChange(c.copy(gateway = it.trim())) }, stringResource(Res.string.client_gateway),
            Modifier.weight(0.7f), mono = true,
        )
        TextInput(
            c.peers.takeIf { it > 0 }?.toString().orEmpty(),
            { onChange(c.copy(peers = it.filter(Char::isDigit).take(3).toIntOrNull() ?: 0)) },
            stringResource(Res.string.client_peers), Modifier.weight(0.3f), numeric = true,
        )
    }
    if (relay) {
        TextInput(c.pubKey, { onChange(c.copy(pubKey = it.trim())) }, stringResource(Res.string.client_server_public_key), mono = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                stringResource(Res.string.form_protocol), c.proto, Choices.protos, { onChange(c.copy(proto = it)) },
                Modifier.weight(1f),
            )
            DropdownField(
                stringResource(Res.string.form_encryption), c.encryption, Choices.encryptions,
                { onChange(c.copy(encryption = it)) }, Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField(
                stringResource(Res.string.form_mask), c.mask.ifBlank { "on" }, Choices.masks,
                { onChange(c.copy(mask = if (it == "on") "" else it)) }, Modifier.weight(1f),
            )
            DropdownField(
                stringResource(Res.string.form_cloak), c.cloak.ifBlank { "none" }, Choices.cloaks,
                { onChange(c.copy(cloak = it)) }, Modifier.weight(1f),
            )
        }
    }
    TextInput(c.name, { onChange(c.copy(name = it)) }, stringResource(Res.string.client_display_name))

    FormSection(stringResource(Res.string.form_routes))
    c.routes.forEachIndexed { i, r ->
        fun set(route: ClientRoute) = onChange(c.copy(routes = c.routes.toMutableList().also { it[i] = route }))
        SectionCard(title = stringResource(Res.string.form_route_n, i + 1), action = {
            if (c.routes.size > 1) IconButton(onClick = { onChange(c.copy(routes = c.routes.filterIndexed { j, _ -> j != i })) }, Modifier.size(32.dp)) {
                Icon(Icons.Default.RemoveCircleOutline, stringResource(Res.string.form_remove_route), tint = MaterialTheme.colorScheme.error)
            }
        }) {
            TextInput(r.routeId, { set(r.copy(routeId = it.trim())) }, stringResource(Res.string.form_route_id), mono = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    stringResource(Res.string.form_socket), r.socket, Choices.sockets, { set(r.copy(socket = it)) },
                    Modifier.weight(1f),
                )
                DropdownField(
                    stringResource(Res.string.form_transport), r.transport.ifBlank { "none" }, Choices.transports,
                    { set(r.copy(transport = if (it == "none") "" else it)) }, Modifier.weight(1f),
                )
            }
        }
    }
    TextButton(onClick = { onChange(c.copy(routes = c.routes + ClientRoute(transport = c.routes.firstOrNull()?.transport ?: "mpath"))) }) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.form_add_route))
    }
    if (c.routes.map { it.transport == "mpath" }.distinct().size > 1) {
        ErrorBanner(stringResource(Res.string.client_mpath_mixed))
    }
}

private val listenAddrPattern = Regex("""^[^\s:]+:\d{1,5}$|^\[[0-9a-fA-F:]+]:\d{1,5}$""")

/** Edits a client's name, config and listen addresses; a changed config or address list restarts it. */
@Composable
fun EditClientPane(
    vm: AppViewModel, info: InstanceInfo, listenAddrs: List<String>, currentConfig: String,
    onClose: () -> Unit, onSave: (name: String, addrs: List<String>, config: String?) -> Unit,
) {
    val original = currentConfig.trim()
    // A JSON config opens straight in the form so the first frame is not built twice
    val parsed = remember(original) { if (original.startsWith("{")) parseClientConfig(original) else null }
    var mode by remember { mutableStateOf(if (parsed != null) ConfigMode.FORM else ConfigMode.RAW) }
    var raw by remember { mutableStateOf(original) }
    var initialForm by remember { mutableStateOf(parsed) }
    var form by remember { mutableStateOf(parsed ?: ClientConfig()) }
    var name by remember { mutableStateOf(info.name) }
    var addrText by remember { mutableStateOf(listenAddrs.joinToString("\n")) }

    // Links are converted to JSON by the service so the form can show them
    LaunchedEffect(original) {
        if (parsed != null) return@LaunchedEffect
        val json = vm.request { convertClientConfig(original) }.getOrNull()
        parseClientConfig(json.orEmpty())?.let { initialForm = it; form = it; mode = ConfigMode.FORM }
    }

    fun switchTo(target: ConfigMode) {
        if (target == ConfigMode.FORM && mode == ConfigMode.RAW) parseClientConfig(raw)?.let { form = it }
        if (target == ConfigMode.RAW && mode == ConfigMode.FORM && form != initialForm) {
            raw = ConfigJson.encodeToString(ClientConfig.serializer(), form)
        }
        mode = target
    }

    val config = if (mode == ConfigMode.FORM) ConfigJson.encodeToString(ClientConfig.serializer(), form) else raw.trim()
    val configChanged = if (mode == ConfigMode.FORM) form != initialForm else config != original
    val missing = if (mode == ConfigMode.FORM) form.missingFields() else emptyList()
    val (valid, validationError) = rememberValidation(if (configChanged && missing.isEmpty()) config else "") { c ->
        vm.request { validateClientConfig(c) }.fold({ it.valid to it.error }, { false to (it.message ?: "") })
    }
    val addrs = addrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val addrsChanged = addrs != listenAddrs
    val addrsValid = addrs.all { listenAddrPattern.matches(it) && it.substringAfterLast(':').toInt() in 0..65535 }
    val canSave = name.isNotBlank() && addrsValid && missing.isEmpty() && (!configChanged || valid == true) &&
        (name != info.name || addrsChanged || configChanged)

    Scaffold(topBar = {
        PaneTopBar(stringResource(Res.string.instance_edit_title, info.displayName), onBack = onClose) {
            TextButton(enabled = canSave, onClick = {
                // A new config restarts the client, so the addresses are passed along to keep them
                onSave(name.trim(), if (addrsChanged || configChanged) addrs else emptyList(), if (configChanged) config else null)
            }) { Text(stringResource(Res.string.action_save)) }
        }
    }) { padding ->
        FormBody(padding, missingSubtitle(missing)) {
            ModeSwitch(mode, stringResource(Res.string.form_mode_link_json), ::switchTo)
            if (mode == ConfigMode.RAW) {
                TextInput(raw, { raw = it }, stringResource(Res.string.client_raw_label), mono = true, singleLine = false, minLines = 5)
            } else {
                ClientConfigFields(form) { form = it }
            }
            if (configChanged) ValidationResult(valid, validationError)

            FormSection(stringResource(Res.string.form_instance_section))
            TextInput(name, { name = it }, stringResource(Res.string.form_name), isError = name.isBlank())
            TextInput(
                addrText, { addrText = it }, stringResource(Res.string.instance_listen_addresses),
                mono = true, singleLine = false, minLines = 2, isError = !addrsValid,
                supporting = if (!addrsValid) stringResource(Res.string.instance_listen_error) else null,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
