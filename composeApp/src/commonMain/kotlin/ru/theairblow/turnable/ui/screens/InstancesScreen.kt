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
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
import turnable.composeapp.generated.resources.*

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
            placeholder = { EmptyState(Icons.Default.Dns, stringResource(Res.string.servers_select)) },
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
                    vm, title = stringResource(Res.string.clients_logs_title, instances.firstOrNull { it.id == p.id }?.displayName ?: p.id),
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
        topBar = {
            PaneTopBar(stringResource(Res.string.servers_title), busy = refreshing) {
                IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, stringResource(Res.string.action_refresh)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate, icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(Res.string.servers_new)) },
            )
        },
    ) { padding ->
        if (instances.isEmpty()) {
            EmptyState(
                Icons.Default.Dns, stringResource(Res.string.servers_empty_title),
                stringResource(Res.string.servers_empty_body), Modifier.padding(padding),
            )
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
                    subtitle = info.id.shortId() + if (info.autostart) stringResource(Res.string.instance_autostart_suffix) else "",
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
    val configLabel = stringResource(Res.string.config_label)

    Scaffold(
        topBar = {
            PaneTopBar(
                info.displayName,
                stringResource(if (info.isClient) Res.string.instance_kind_client else Res.string.instance_kind_server),
                onBack = onBack, busy = busy,
            ) {
                IconButton(onClick = onLogs) { Icon(Icons.AutoMirrored.Filled.Article, stringResource(Res.string.instance_logs)) }
                IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, stringResource(Res.string.action_edit)) }
                IconButton(onClick = { confirmRemove = true }) {
                    Icon(Icons.Default.Delete, stringResource(Res.string.action_remove), tint = MaterialTheme.colorScheme.error)
                }
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
                            stringResource(Res.string.instance_from_group, group.displayName),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(info.statusKind, info.statusLabel)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (running) {
                        OutlinedButton(onClick = { run { restartInstance(info.id) } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Sync, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.action_restart))
                        }
                        FilledTonalButton(onClick = { run { stopInstance(info.id, keep = true) } }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.action_stop))
                        }
                    } else {
                        Button(onClick = { run { restartInstance(info.id) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.action_start))
                        }
                    }
                }
                SwitchRow(
                    stringResource(Res.string.form_autostart), info.autostart, { on -> run { updateInstance(info.id, autostart = on) } },
                    stringResource(Res.string.instance_autostart_hint),
                )
                InfoRow(stringResource(Res.string.instance_id), info.id, mono = true, copyable = true)
            }
            error?.let { ErrorBanner(it) }

            val d = details
            if (info.isClient && d != null) ClientEndpointsCard(vm, d)
            if (!info.isClient && running) ServerStatsSection(vm, info)

            if (d != null && d.config.isNotBlank()) {
                SectionCard(title = stringResource(Res.string.instance_configuration), action = {
                    IconButton(onClick = { copy(d.config, configLabel) }, Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, stringResource(Res.string.instance_copy_config), Modifier.size(18.dp))
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
        title = stringResource(Res.string.instance_remove_title, info.displayName),
        text = stringResource(Res.string.instance_remove_body) +
            (if (group != null) stringResource(Res.string.instance_remove_group_suffix, group.displayName) else "") +
            stringResource(Res.string.instance_remove_stop_hint),
        confirmLabel = stringResource(Res.string.action_remove),
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
        title = { Text(stringResource(Res.string.instance_edit_title, info.displayName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(name, { name = it }, stringResource(Res.string.form_name), isError = name.isBlank())
                if (info.isClient) TextInput(
                    addrText, { addrText = it }, stringResource(Res.string.instance_listen_addresses),
                    mono = true, singleLine = false, minLines = 2, isError = !addrsValid,
                    supporting = if (!addrsValid) stringResource(Res.string.instance_listen_error)
                    else stringResource(Res.string.instance_listen_hint),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && addrsValid && (name != info.name || addrsChanged),
                onClick = { onSave(name.trim(), if (addrsChanged) addrs else emptyList()) },
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Local ports a client listens on, plus share actions for its config. */
@Composable
private fun ClientEndpointsCard(vm: AppViewModel, d: GetInstanceResponse) {
    val copy = rememberCopier()
    val scope = rememberCoroutineScope()
    SectionCard(title = stringResource(Res.string.instance_local_endpoints), icon = Icons.Default.Link) {
        if (d.listenAddrs.isEmpty()) {
            Text(
                stringResource(Res.string.instance_default_endpoint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        d.listenAddrs.forEachIndexed { i, addr ->
            InfoRow(stringResource(Res.string.instance_route_n, i + 1), addr, mono = true, copyable = true)
        }
        Text(
            stringResource(Res.string.instance_endpoints_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {
                scope.launch {
                    vm.request { convertClientConfig(d.config) }
                        .onSuccess {
                            copy(
                                if (d.config.trimStart().startsWith("{")) it else d.config,
                                getString(Res.string.config_url_label),
                            )
                        }
                        .onFailure { vm.notify(it.message ?: getString(Res.string.instance_conversion_failed)) }
                }
            }, label = { Text(stringResource(Res.string.instance_copy_as_url)) }, leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(18.dp)) })
            AssistChip(onClick = {
                scope.launch {
                    val jsonLabel = getString(Res.string.config_json_label)
                    if (d.config.trimStart().startsWith("{")) copy(d.config, jsonLabel)
                    else vm.request { convertClientConfig(d.config) }
                        .onSuccess { copy(prettyJson(it), jsonLabel) }
                        .onFailure { vm.notify(it.message ?: getString(Res.string.instance_conversion_failed)) }
                }
            }, label = { Text(stringResource(Res.string.instance_copy_as_json)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)) })
        }
    }
}

@Composable
internal fun OutlinedIconButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text)
    }
}
