package ru.theairblow.turnable.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.config.splitConfigs
import ru.theairblow.turnable.platform.CaptchaRequest
import ru.theairblow.turnable.proto.InstanceInfo
import ru.theairblow.turnable.proto.InstanceStatus
import ru.theairblow.turnable.proto.InstanceType
import ru.theairblow.turnable.subscription.Subscription
import ru.theairblow.turnable.ui.components.AdaptiveListDetail
import ru.theairblow.turnable.ui.components.AppUpdateBanner
import ru.theairblow.turnable.ui.components.CoreUpdateBanner
import ru.theairblow.turnable.ui.components.EmptyState
import ru.theairblow.turnable.ui.components.IconTile
import ru.theairblow.turnable.ui.components.ListCard
import ru.theairblow.turnable.ui.components.PaneTopBar
import ru.theairblow.turnable.ui.components.StatusDot
import ru.theairblow.turnable.ui.components.rememberClipboardReader
import ru.theairblow.turnable.ui.util.formatBytes
import ru.theairblow.turnable.ui.util.formatDuration
import ru.theairblow.turnable.ui.util.nowUnixSeconds
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.ConnectionState
import turnable.composeapp.generated.resources.*

private sealed interface ClientPane {
    data object Create : ClientPane
    data class Detail(val id: String) : ClientPane
    data class Logs(val id: String) : ClientPane
}

/** Clients of one subscription, or the ones that belong to none when [subscription] is null. */
private class ClientGroup(val subscription: Subscription?, val clients: List<InstanceInfo>) {
    val id: String get() = subscription?.id ?: "other"
}

private fun groupClients(clients: List<InstanceInfo>, subscriptions: List<Subscription>): List<ClientGroup> {
    val owner = subscriptions.flatMap { s -> s.entries.map { it.instanceId to s.id } }.toMap()
    val groups = subscriptions.map { s -> ClientGroup(s, clients.filter { owner[it.id] == s.id }) }
    val loose = clients.filter { it.id !in owner }
    return groups + listOfNotNull(ClientGroup(null, loose).takeIf { loose.isNotEmpty() })
}

private val InstanceInfo.isRunning: Boolean
    get() = status == InstanceStatus.INSTANCE_STATUS_STARTED || status == InstanceStatus.INSTANCE_STATUS_STARTING

/**
 * The client list: subscriptions show up as groups, and any number of clients can be connected at once,
 * each on its own local port.
 */
@Composable
fun ClientsScreen(vm: AppViewModel, openServices: () -> Unit) {
    val all by vm.instances.collectAsStateWithLifecycle()
    val clients = all.filter { it.type == InstanceType.INSTANCE_TYPE_CLIENT }
    var pane by remember { mutableStateOf<ClientPane?>(null) }

    ConnectionGate(vm, openServices) {
        AdaptiveListDetail(
            detailKey = pane,
            onCloseDetail = { pane = (pane as? ClientPane.Logs)?.let { ClientPane.Detail(it.id) } },
            list = {
                ClientList(vm, clients, selectedId = (pane as? ClientPane.Detail)?.id,
                    onOpen = { pane = ClientPane.Detail(it.id) }, onCreate = { pane = ClientPane.Create })
            },
            placeholder = { EmptyState(Icons.Default.SettingsEthernet, stringResource(Res.string.clients_select)) },
        ) { p ->
            when (p) {
                ClientPane.Create -> CreateClientPane(vm, onClose = { pane = null }, onCreated = { pane = ClientPane.Detail(it) })
                is ClientPane.Detail -> {
                    val info = clients.firstOrNull { it.id == p.id }
                    if (info == null) LaunchedEffect(p.id) { pane = null }
                    else InstanceDetail(vm, info, onBack = { pane = null }, onLogs = { pane = ClientPane.Logs(info.id) })
                }
                is ClientPane.Logs -> LogsPane(
                    vm, title = stringResource(Res.string.clients_logs_title, clients.firstOrNull { it.id == p.id }?.displayName ?: p.id),
                    onBack = { pane = ClientPane.Detail(p.id) }, instanceId = p.id,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientList(vm: AppViewModel, clients: List<InstanceInfo>, selectedId: String?, onOpen: (InstanceInfo) -> Unit, onCreate: () -> Unit) {
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val listen by vm.clientListen.collectAsStateWithLifecycle()
    val prefs by vm.prefsData.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val captchas by vm.captchas.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val appUpdate by vm.appUpdate.collectAsStateWithLifecycle()
    val installing by vm.updating.collectAsStateWithLifecycle()
    val download by vm.coreDownload.collectAsStateWithLifecycle()
    val appInstalling by vm.installingApp.collectAsStateWithLifecycle()
    val appDownload by vm.appDownload.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val readClipboard = rememberClipboardReader()
    val shownAppUpdate = appUpdate?.takeIf { it.version != prefs.dismissedAppUpdate }
    val banners: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shownAppUpdate?.let { AppUpdateBanner(it, appInstalling, appDownload, vm::installAppUpdate, vm::dismissAppUpdate) }
            update?.let { CoreUpdateBanner(it, installing, download, shownAppUpdate != null, vm::applyUpdate, vm::dismissUpdate) }
        }
    }
    val serviceId = (connection as? ConnectionState.Connected)?.serviceId
    val groups = groupClients(clients, prefs.subscriptions.filter { it.serviceId == serviceId })
    val grouped = groups.any { it.subscription != null }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    var addMenu by remember { mutableStateOf(false) }
    var addingSubscription by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var removing by remember { mutableStateOf<Subscription?>(null) }
    val updating = syncing.isNotEmpty()

    fun importClipboard() = scope.launch {
        val text = readClipboard().trim()
        val links = splitConfigs(text).filter { it.startsWith("turnable://", ignoreCase = true) }
        when {
            links.isNotEmpty() -> {
                val (started, failures) = vm.importClientLinks(links)
                vm.notify(
                    if (failures.isEmpty()) getPluralString(Res.plurals.clients_added, started, started)
                    else failures.first(),
                )
            }
            text.startsWith("http://") || text.startsWith("https://") -> addingSubscription = text
            else -> vm.notify(getString(Res.string.clients_clipboard_none))
        }
    }

    Scaffold(
        topBar = {
            PaneTopBar(
                stringResource(Res.string.clients_title),
                if (clients.isEmpty()) null else stringResource(
                    Res.string.clients_connected_of,
                    clients.count { it.status == InstanceStatus.INSTANCE_STATUS_STARTED }, clients.size,
                ),
                busy = refreshing || updating,
            ) {
                if (grouped) IconButton(onClick = {
                    scope.launch { vm.syncAllSubscriptions(); vm.notify(getString(Res.string.clients_subscriptions_updated)) }
                }, enabled = !updating) {
                    Icon(Icons.Default.Sync, stringResource(Res.string.clients_update_all_desc))
                }
            }
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(onClick = { addMenu = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(Res.string.clients_add)) })
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.clients_menu_subscription_url)) }, leadingIcon = { Icon(Icons.Default.RssFeed, null) },
                        onClick = { addMenu = false; addingSubscription = "" },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.clients_menu_import)) }, leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                        onClick = { addMenu = false; importClipboard() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.clients_menu_manual)) }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { addMenu = false; onCreate() },
                    )
                }
            }
        },
    ) { padding ->
        if (clients.isEmpty() && !grouped) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    Icons.Default.SettingsEthernet, stringResource(Res.string.clients_empty_title),
                    stringResource(Res.string.clients_empty_body),
                ) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { addingSubscription = readClipboard().trim().takeIf { it.startsWith("http") }.orEmpty() }) {
                            Icon(Icons.Default.RssFeed, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.clients_add_subscription))
                        }
                        FilledTonalButton(onClick = { importClipboard() }) {
                            Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.clients_paste_link))
                        }
                    }
                }
                banners(Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).padding(16.dp))
            }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (shownAppUpdate != null || update != null) item(key = "updates") { banners(Modifier.animateItem()) }
            items(captchas, key = { "captcha-${it.id}" }) { c ->
                CaptchaBanner(c, onSolve = { vm.openCaptcha(c.instanceId) }, modifier = Modifier.animateItem())
            }
            groups.forEach { group ->
                if (grouped) item(key = "group-${group.id}") {
                    GroupHeader(
                        group, expanded = group.id !in collapsed,
                        syncing = group.subscription?.id in syncing,
                        onToggle = { collapsed = if (group.id in collapsed) collapsed - group.id else collapsed + group.id },
                        onSync = group.subscription?.let { sub ->
                            { scope.launch { vm.syncSubscription(sub.id).onSuccess { vm.notify(getString(Res.string.clients_subscription_updated, sub.displayName)) } } }
                        },
                        onConnectAll = { scope.launch { vm.setGroupRunning(group.clients, true) } },
                        onDisconnectAll = { scope.launch { vm.setGroupRunning(group.clients, false) } },
                        onEdit = { editing = group.subscription },
                        onRemove = { removing = group.subscription },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (!grouped || group.id !in collapsed) {
                    if (grouped && group.clients.isEmpty()) item(key = "empty-${group.id}") {
                        Text(
                            stringResource(Res.string.clients_group_empty),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    items(group.clients, key = { it.id }) { info ->
                        ClientRow(
                            info, listen[info.id]?.joinToString(", ")?.ifEmpty { null },
                            selected = info.id == selectedId,
                            needsCaptcha = captchas.any { it.instanceId == info.id },
                            onClick = { onOpen(info) },
                            onToggle = { scope.launch { vm.toggleInstance(info) } },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    addingSubscription?.let { initial ->
        SubscriptionDialog(null, initialUrl = initial, onDismiss = { addingSubscription = null }) { name, url, _ ->
            vm.addSubscription(url, name).fold({ addingSubscription = null; null }, { it.message ?: getString(Res.string.clients_could_not_add) })
        }
    }
    editing?.let { sub ->
        SubscriptionDialog(sub, onDismiss = { editing = null }) { name, url, hours ->
            vm.editSubscription(sub.id, name, url, hours)
            editing = null
            null
        }
    }
    removing?.let { sub ->
        RemoveSubscriptionDialog(sub, onDismiss = { removing = null }) { removeClients ->
            scope.launch { vm.removeSubscription(sub.id, removeClients) }
        }
    }
}

@Composable
private fun ClientRow(
    info: InstanceInfo,
    address: String?,
    selected: Boolean,
    needsCaptcha: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = if (needsCaptcha) stringResource(Res.string.status_captcha_needed) else info.statusLabel
    ListCard(
        title = info.displayName,
        subtitle = listOfNotNull(status, address).joinToString(" · "),
        selected = selected,
        onClick = onClick,
        leading = { Box(Modifier.size(42.dp), Alignment.Center) { StatusDot(if (needsCaptcha) ru.theairblow.turnable.ui.components.StatusKind.Failed else info.statusKind, size = 12.dp) } },
        trailing = { Switch(checked = info.isRunning, onCheckedChange = { onToggle() }) },
        modifier = modifier,
    )
}

/** Heading of a group: the subscription it came from with its usage and announcement, or "Other clients". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupHeader(
    group: ClientGroup,
    expanded: Boolean,
    syncing: Boolean,
    onToggle: () -> Unit,
    onSync: (() -> Unit)?,
    onConnectAll: () -> Unit,
    onDisconnectAll: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sub = group.subscription
    val uriHandler = LocalUriHandler.current
    var menu by remember { mutableStateOf(false) }
    val online = group.clients.count { it.status == InstanceStatus.INSTANCE_STATUS_STARTED }
    Surface(onClick = onToggle, modifier = modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (sub != null) Icons.Default.RssFeed else Icons.Default.SettingsEthernet, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        sub?.displayName ?: stringResource(Res.string.clients_other_group),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(Res.plurals.clients_group_summary, group.clients.size, group.clients.size, online),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (syncing) CircularProgressIndicator(Modifier.size(40.dp).padding(10.dp), strokeWidth = 2.dp)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.clients_group_actions)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (onSync != null) DropdownMenuItem(
                            text = { Text(stringResource(Res.string.clients_update_now)) }, leadingIcon = { Icon(Icons.Default.Sync, null) },
                            onClick = { menu = false; onSync() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.clients_connect_all)) }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                            onClick = { menu = false; onConnectAll() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.clients_disconnect_all)) }, leadingIcon = { Icon(Icons.Default.Stop, null) },
                            onClick = { menu = false; onDisconnectAll() },
                        )
                        if (sub != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_remove)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onRemove() },
                            )
                        }
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    stringResource(if (expanded) Res.string.clients_collapse else Res.string.clients_expand),
                    Modifier.padding(end = 12.dp),
                )
            }
            if (sub != null && expanded) Column(Modifier.padding(end = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sub.usage?.let { u ->
                    val used = u.upload + u.download
                    val now = nowUnixSeconds()
                    val usedText = if (u.total > 0) {
                        stringResource(Res.string.clients_usage_of, formatBytes(used), formatBytes(u.total))
                    } else stringResource(Res.string.clients_usage_used, formatBytes(used))
                    val expiry = when {
                        u.expire <= 0 -> ""
                        u.expire <= now -> stringResource(Res.string.clients_usage_expired)
                        else -> stringResource(Res.string.clients_usage_expires_in, formatDuration(u.expire - now))
                    }
                    Text(usedText + expiry, style = MaterialTheme.typography.bodySmall)
                    if (u.total > 0) LinearProgressIndicator(progress = { (used.toFloat() / u.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                if (sub.announce.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Campaign, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(sub.announce, style = MaterialTheme.typography.bodySmall)
                }
                if (sub.webPageUrl.isNotBlank() || sub.supportUrl.isNotBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sub.webPageUrl.isNotBlank()) AssistChip(
                        onClick = { runCatching { uriHandler.openUri(sub.webPageUrl) } },
                        label = { Text(stringResource(Res.string.clients_account_page)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)) },
                    )
                    if (sub.supportUrl.isNotBlank()) AssistChip(
                        onClick = { runCatching { uriHandler.openUri(sub.supportUrl) } },
                        label = { Text(stringResource(Res.string.clients_support)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(18.dp)) },
                    )
                }
                if (sub.lastError.isNotBlank()) Text(sub.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Pinned above the list while a client waits for a hand-solved captcha. */
@Composable
internal fun CaptchaBanner(request: CaptchaRequest, onSolve: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    stringResource(Res.string.status_captcha_needed),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    request.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Button(onClick = onSolve) { Text(stringResource(Res.string.action_solve)) }
        }
    }
}
