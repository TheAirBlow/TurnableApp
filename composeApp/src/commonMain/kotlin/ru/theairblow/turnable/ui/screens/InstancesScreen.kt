package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import kotlinx.coroutines.launch
import ru.theairblow.turnable.config.prettyJson
import ru.theairblow.turnable.proto.GetInstanceResponse
import ru.theairblow.turnable.proto.InstanceInfo
import ru.theairblow.turnable.proto.InstanceStatus
import ru.theairblow.turnable.proto.InstanceType
import ru.theairblow.turnable.subscription.Subscription
import ru.theairblow.turnable.ui.components.AdaptiveListDetail
import ru.theairblow.turnable.ui.components.ConfirmDialog
import ru.theairblow.turnable.ui.components.EmptyState
import ru.theairblow.turnable.ui.components.ErrorBanner
import ru.theairblow.turnable.ui.components.IconTile
import ru.theairblow.turnable.ui.components.InfoRow
import ru.theairblow.turnable.ui.components.ListCard
import ru.theairblow.turnable.ui.components.PaneTopBar
import ru.theairblow.turnable.ui.components.SectionCard
import ru.theairblow.turnable.ui.components.StatusBadge
import ru.theairblow.turnable.ui.components.SwitchRow
import ru.theairblow.turnable.ui.components.TextInput
import ru.theairblow.turnable.ui.components.rememberCopier
import ru.theairblow.turnable.ui.theme.MonoFamily
import ru.theairblow.turnable.ui.util.formatBytes
import ru.theairblow.turnable.ui.util.shortId
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.ConnectionState

private sealed interface InstancePane {
    data object Create : InstancePane
    data class Detail(val id: String) : InstancePane
    data class Logs(val id: String) : InstancePane
}

/** Servers of the connected service. */
@Composable
fun ServersScreen(vm: AppViewModel, openServices: () -> Unit) {
    val all by vm.instances.collectAsStateWithLifecycle()
    val instances = all.filter { it.type == InstanceType.INSTANCE_TYPE_SERVER }
    var pane by remember { mutableStateOf<InstancePane?>(null) }

    ConnectionGate(vm, openServices) {
        AdaptiveListDetail(
            detailKey = pane,
            onCloseDetail = { pane = (pane as? InstancePane.Logs)?.let { InstancePane.Detail(it.id) } },
            list = {
                ServerList(vm, instances, selectedId = (pane as? InstancePane.Detail)?.id,
                    onOpen = { pane = InstancePane.Detail(it.id) }, onCreate = { pane = InstancePane.Create })
            },
            placeholder = { EmptyState(Icons.Default.Dns, "Select a server") },
        ) { p ->
            when (p) {
                InstancePane.Create -> CreateServerPane(vm, onClose = { pane = null }, onCreated = { pane = InstancePane.Detail(it) })
                is InstancePane.Detail -> {
                    val info = instances.firstOrNull { it.id == p.id }
                    if (info == null) {
                        // Stopped elsewhere (or just removed): nothing left to show
                        LaunchedEffect(p.id) { pane = null }
                    } else {
                        InstanceDetail(vm, info, onBack = { pane = null }, onLogs = { pane = InstancePane.Logs(info.id) })
                    }
                }
                is InstancePane.Logs -> LogsPane(
                    vm, title = "${instances.firstOrNull { it.id == p.id }?.displayName ?: p.id} logs",
                    onBack = { pane = InstancePane.Detail(p.id) }, instanceId = p.id,
                )
            }
        }
    }
}

@Composable
private fun ServerList(vm: AppViewModel, instances: List<InstanceInfo>, selectedId: String?, onOpen: (InstanceInfo) -> Unit, onCreate: () -> Unit) {
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { PaneTopBar("Servers", busy = refreshing) { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") } } },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New server") })
        },
    ) { padding ->
        if (instances.isEmpty()) {
            EmptyState(Icons.Default.Dns, "No servers", "A server accepts tunnels from users of one of your providers.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(instances, key = { it.id }) { info ->
                ListCard(
                    title = info.displayName,
                    subtitle = info.id.shortId() + if (info.autostart) " · autostart" else "",
                    selected = info.id == selectedId,
                    onClick = { onOpen(info) },
                    leading = { IconTile(Icons.Default.Dns) },
                    trailing = { StatusBadge(info.statusKind, info.statusLabel) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
internal fun InstanceDetail(vm: AppViewModel, info: InstanceInfo, onBack: () -> Unit, onLogs: () -> Unit) {
    var details by remember(info.id) { mutableStateOf<GetInstanceResponse?>(null) }
    var error by remember(info.id) { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val copy = rememberCopier()
    val prefs by vm.prefsData.collectAsStateWithLifecycle()
    val group = remember(prefs.subscriptions, info.id) { vm.subscriptionOf(info.id) }

    // Status changes arrive as new InstanceInfo; reload the details with them
    LaunchedEffect(info) {
        vm.request { getInstance(info.id) }.onSuccess { details = it; error = null }.onFailure { error = it.message }
    }

    fun run(block: suspend ru.theairblow.turnable.service.TurnableServiceClient.() -> Unit) = scope.launch {
        busy = true
        vm.mutate(block)
        busy = false
    }

    val running = info.status == InstanceStatus.INSTANCE_STATUS_STARTED || info.status == InstanceStatus.INSTANCE_STATUS_STARTING

    Scaffold(
        topBar = {
            PaneTopBar(info.displayName, if (info.isClient) "Client" else "Server", onBack = onBack, busy = busy) {
                IconButton(onClick = onLogs) { Icon(Icons.AutoMirrored.Filled.Article, "Logs") }
                IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(if (info.isClient) Icons.Default.SettingsEthernet else Icons.Default.Dns)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(info.displayName, style = MaterialTheme.typography.titleMedium)
                        if (group != null) Text(
                            "From ${group.displayName}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(info.statusKind, info.statusLabel)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (running) {
                        OutlinedButton(onClick = { run { restartInstance(info.id) } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Restart")
                        }
                        FilledTonalButton(onClick = { run { stopInstance(info.id, keep = true) } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Stop")
                        }
                    } else {
                        Button(onClick = { run { restartInstance(info.id) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Start")
                        }
                    }
                }
                SwitchRow(
                    "Autostart", info.autostart, { on -> run { updateInstance(info.id, autostart = on) } },
                    "Start when the service starts",
                )
                InfoRow("ID", info.id, mono = true, copyable = true)
            }
            error?.let { ErrorBanner(it) }

            val d = details
            if (info.isClient && d != null) ClientEndpointsCard(vm, d)
            if (!info.isClient && running) ServerStatsSection(vm, info)

            if (d != null && d.config.isNotBlank()) {
                SectionCard(title = "Configuration", action = {
                    IconButton(onClick = { copy(d.config, "Config") }, Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, "Copy config", Modifier.size(18.dp))
                    }
                }) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        SelectionContainer {
                            Text(
                                remember(d.config) { prettyJson(d.config) }, fontFamily = MonoFamily, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editing) EditInstanceDialog(
        info, details?.listenAddrs.orEmpty(),
        onDismiss = { editing = false },
        onSave = { name, addrs ->
            editing = false
            run { updateInstance(info.id, name = name.takeIf { it != info.name }, listenAddrs = addrs) }
        },
    )
    if (confirmRemove) ConfirmDialog(
        title = "Remove ${info.displayName}?",
        text = "It is stopped and deleted together with its saved config, so it won't come back after a restart. " +
            (if (group != null) "It belongs to ${group.displayName}, which brings it back on its next update. " else "") +
            "Use Stop to keep it listed instead.",
        confirmLabel = "Remove",
        onConfirm = { scope.launch { vm.mutate { stopInstance(info.id) }.onSuccess { onBack() } } },
        onDismiss = { confirmRemove = false },
    )
}

private val listenAddrPattern = Regex("""^[^\s:]+:\d{1,5}$|^\[[0-9a-fA-F:]+]:\d{1,5}$""")

/** Renames an instance and, for clients, changes the local addresses it listens on (which restarts it). */
@Composable
private fun EditInstanceDialog(info: InstanceInfo, listenAddrs: List<String>, onDismiss: () -> Unit, onSave: (name: String, addrs: List<String>) -> Unit) {
    var name by remember { mutableStateOf(info.name) }
    var addrText by remember { mutableStateOf(listenAddrs.joinToString("\n")) }
    val addrs = addrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val addrsChanged = addrs != listenAddrs
    val addrsValid = addrs.all { listenAddrPattern.matches(it) && it.substringAfterLast(':').toInt() in 0..65535 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${info.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, "Name", isError = name.isBlank())
                if (info.isClient) TextInput(
                    addrText, { addrText = it }, "Listen addresses", mono = true, singleLine = false, minLines = 2, isError = !addrsValid,
                    supporting = if (!addrsValid) "One host:port per line" else "One per line, one per route. Changing them restarts the client",
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && addrsValid && (name != info.name || addrsChanged),
                onClick = { onSave(name.trim(), if (addrsChanged) addrs else emptyList()) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Local ports a client listens on, plus share actions for its config. */
@Composable
private fun ClientEndpointsCard(vm: AppViewModel, d: GetInstanceResponse) {
    val copy = rememberCopier()
    val scope = rememberCoroutineScope()
    SectionCard(title = "Local endpoints", icon = Icons.Default.Link) {
        if (d.listenAddrs.isEmpty()) {
            Text("Default (127.0.0.1, random port)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        d.listenAddrs.forEachIndexed { i, addr -> InfoRow("Route ${i + 1}", addr, mono = true, copyable = true) }
        Text(
            "Point your VPN app (e.g. WireGuard in NekoBox, MTU 1280) at these addresses.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {
                scope.launch {
                    vm.request { convertClientConfig(d.config) }
                        .onSuccess { converted -> copy(if (d.config.trimStart().startsWith("{")) converted else d.config, "Config URL") }
                        .onFailure { vm.notify(it.message ?: "Conversion failed") }
                }
            }, label = { Text("Copy as URL") }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(18.dp)) })
            AssistChip(onClick = {
                scope.launch {
                    if (d.config.trimStart().startsWith("{")) copy(d.config, "Config JSON")
                    else vm.request { convertClientConfig(d.config) }
                        .onSuccess { copy(prettyJson(it), "Config JSON") }
                        .onFailure { vm.notify(it.message ?: "Conversion failed") }
                }
            }, label = { Text("Copy as JSON") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) })
        }
    }
}

@Composable
internal fun OutlinedIconButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text)
    }
}
