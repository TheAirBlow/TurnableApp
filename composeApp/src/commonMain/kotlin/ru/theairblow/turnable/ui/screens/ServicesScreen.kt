package ru.theairblow.turnable.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.data.ServiceConfig
import ru.theairblow.turnable.data.ServiceType
import ru.theairblow.turnable.service.generateServiceKeyPair
import ru.theairblow.turnable.ui.components.AdaptiveListDetail
import ru.theairblow.turnable.ui.components.ConfirmDialog
import ru.theairblow.turnable.ui.components.DropdownField
import ru.theairblow.turnable.ui.components.EmptyState
import ru.theairblow.turnable.ui.components.ErrorBanner
import ru.theairblow.turnable.ui.components.FormSection
import ru.theairblow.turnable.ui.components.IconTile
import ru.theairblow.turnable.ui.components.InfoRow
import ru.theairblow.turnable.ui.components.ListCard
import ru.theairblow.turnable.ui.components.PaneTopBar
import ru.theairblow.turnable.ui.components.SectionCard
import ru.theairblow.turnable.ui.components.StatusBadge
import ru.theairblow.turnable.ui.components.CustomCoreRow
import ru.theairblow.turnable.ui.components.SwitchRow
import ru.theairblow.turnable.service.MIN_CORE_VERSION
import ru.theairblow.turnable.ui.components.StatusKind
import ru.theairblow.turnable.ui.components.TextInput
import ru.theairblow.turnable.ui.components.rememberCopier
import ru.theairblow.turnable.ui.theme.MonoFamily
import ru.theairblow.turnable.ui.theme.TurnableTheme
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.ConnectionState
import turnable.composeapp.generated.resources.*
import kotlin.io.encoding.Base64
import kotlin.random.Random

private sealed interface ServicePane {
    data object Add : ServicePane
    data class Detail(val id: String) : ServicePane
    data class Edit(val id: String) : ServicePane
    data class Logs(val id: String) : ServicePane
}

/** Status of a saved service as seen from the app. */
internal data class ServiceStatus(val kind: StatusKind, val label: String)

@Composable
internal fun rememberServiceStatus(vm: AppViewModel, svc: ServiceConfig): ServiceStatus {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val running by vm.localRunning.collectAsStateWithLifecycle()
    val starting by vm.localStarting.collectAsStateWithLifecycle()
    val active = connection.activeServiceId == svc.id
    return when {
        svc.id in starting -> ServiceStatus(StatusKind.Pending, stringResource(Res.string.status_starting))
        active && connection is ConnectionState.Connected ->
            ServiceStatus(StatusKind.Online, stringResource(Res.string.status_connected))
        active && connection is ConnectionState.Connecting ->
            ServiceStatus(StatusKind.Pending, stringResource(Res.string.status_connecting))
        active && connection is ConnectionState.Failed ->
            ServiceStatus(StatusKind.Failed, stringResource(Res.string.status_unreachable))
        svc.type == ServiceType.LOCAL && svc.id in running ->
            ServiceStatus(StatusKind.Offline, stringResource(Res.string.status_running))
        else -> ServiceStatus(
            StatusKind.Offline,
            stringResource(if (svc.type == ServiceType.LOCAL) Res.string.status_stopped else Res.string.status_idle),
        )
    }
}

@Composable
fun ServicesScreen(vm: AppViewModel) {
    val prefs by vm.prefsData.collectAsStateWithLifecycle()
    var pane by remember { mutableStateOf<ServicePane?>(null) }
    val scope = rememberCoroutineScope()

    // Close panes whose service was removed
    LaunchedEffect(prefs.services, pane) {
        val id = when (val p = pane) {
            is ServicePane.Detail -> p.id
            is ServicePane.Edit -> p.id
            is ServicePane.Logs -> p.id
            else -> null
        }
        if (id != null && prefs.services.none { it.id == id }) pane = null
    }

    AdaptiveListDetail(
        detailKey = pane,
        onCloseDetail = {
            pane = when (val p = pane) {
                is ServicePane.Edit -> ServicePane.Detail(p.id)
                is ServicePane.Logs -> ServicePane.Detail(p.id)
                else -> null
            }
        },
        parent = {
            when (it) {
                is ServicePane.Edit -> ServicePane.Detail(it.id)
                is ServicePane.Logs -> ServicePane.Detail(it.id)
                else -> null
            }
        },
        list = {
            ServiceList(
                vm, prefs.services,
                selectedId = (pane as? ServicePane.Detail)?.id,
                onOpen = { pane = ServicePane.Detail(it.id) },
                onAdd = { pane = ServicePane.Add },
            )
        },
        placeholder = {
            EmptyState(
                Icons.Default.Hub, stringResource(Res.string.services_select),
                stringResource(Res.string.services_select_body),
            )
        },
    ) { p ->
        when (p) {
            ServicePane.Add -> ServiceForm(vm, existing = null, onClose = { pane = null }) { cfg ->
                scope.launch { vm.saveService(cfg); pane = ServicePane.Detail(cfg.id) }
            }
            is ServicePane.Edit -> prefs.services.firstOrNull { it.id == p.id }?.let { svc ->
                ServiceForm(vm, existing = svc, onClose = { pane = ServicePane.Detail(p.id) }) { cfg ->
                    scope.launch { vm.saveService(cfg); pane = ServicePane.Detail(cfg.id) }
                }
            }
            is ServicePane.Detail -> prefs.services.firstOrNull { it.id == p.id }?.let { svc ->
                ServiceDetail(
                    vm, svc,
                    onBack = { pane = null },
                    onEdit = { pane = ServicePane.Edit(svc.id) },
                    onLogs = { pane = ServicePane.Logs(svc.id) },
                    onDeleted = { pane = null },
                )
            }
            is ServicePane.Logs -> {
                val svc = prefs.services.firstOrNull { it.id == p.id }
                LogsPane(
                    vm, title = stringResource(Res.string.services_service_logs), onBack = { pane = ServicePane.Detail(p.id) },
                    verbose = svc?.verbose.takeIf { svc?.type == ServiceType.LOCAL },
                )
            }
        }
    }
}

// ─── List ─────────────────────────────────────────────────────────────────────

@Composable
private fun ServiceList(
    vm: AppViewModel,
    services: List<ServiceConfig>,
    selectedId: String?,
    onOpen: (ServiceConfig) -> Unit,
    onAdd: () -> Unit,
) {
    Scaffold(
        topBar = { PaneTopBar(stringResource(Res.string.services_title)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd, icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(Res.string.services_add)) },
            )
        },
    ) { padding ->
        if (services.isEmpty()) {
            EmptyState(
                Icons.Default.Hub, stringResource(Res.string.services_empty_title),
                stringResource(Res.string.services_empty_body),
                Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(services, key = { it.id }) { svc ->
                val status = rememberServiceStatus(vm, svc)
                ListCard(
                    title = svc.name,
                    subtitle = svc.displaySubtitle(),
                    selected = svc.id == selectedId,
                    onClick = { onOpen(svc) },
                    leading = { IconTile(if (svc.type == ServiceType.LOCAL) Icons.Default.PhoneAndroid else Icons.Default.Cloud) },
                    trailing = { StatusBadge(status.kind, status.label) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

// ─── Detail ───────────────────────────────────────────────────────────────────

@Composable
private fun ServiceDetail(
    vm: AppViewModel,
    svc: ServiceConfig,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLogs: () -> Unit,
    onDeleted: () -> Unit,
) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val running by vm.localRunning.collectAsStateWithLifecycle()
    val starting by vm.localStarting.collectAsStateWithLifecycle()
    val instances by vm.instances.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val status = rememberServiceStatus(vm, svc)
    val coreVersion = rememberCoreVersion(vm, svc)
    val scope = rememberCoroutineScope()
    val copy = rememberCopier()
    var confirmDelete by remember { mutableStateOf(false) }

    val isLocal = svc.type == ServiceType.LOCAL
    val isRunning = svc.id in running
    val isStarting = svc.id in starting
    val active = connection.activeServiceId == svc.id
    val connected = active && connection is ConnectionState.Connected
    val publicKeyLabel = stringResource(Res.string.service_public_key_label)

    Scaffold(
        topBar = {
            PaneTopBar(svc.name, svc.displaySubtitle(), onBack = onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(Res.string.action_edit)) }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, stringResource(Res.string.action_remove), tint = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Hero: status + the one action that matters right now
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(if (isLocal) Icons.Default.PhoneAndroid else Icons.Default.Cloud)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            stringResource(if (isLocal) Res.string.service_local else Res.string.service_remote),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (isLocal) stringResource(
                                if (isRunning) Res.string.status_process_running else Res.string.status_process_stopped,
                            ) else "${svc.host}:${svc.port}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(status.kind, status.label)
                }
                val failure = (connection as? ConnectionState.Failed)?.takeIf { active }
                if (failure != null) ErrorBanner(
                    failure.message + (failure.retryInSec?.let { stringResource(Res.string.service_retrying_in, it) } ?: ""),
                )
                if (connected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val servers = instances.count { !it.isClient }
                        val clients = instances.count { it.isClient }
                        val counts = listOf(
                            pluralStringResource(Res.plurals.service_counts_servers, servers, servers),
                            pluralStringResource(Res.plurals.service_counts_clients, clients, clients),
                            pluralStringResource(Res.plurals.service_counts_providers, providers.size, providers.size),
                        ).joinToString(" · ")
                        Text(
                            counts,
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    when {
                        isStarting -> Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                            Text(stringResource(Res.string.service_starting))
                        }
                        isLocal && !isRunning -> Button(onClick = { vm.selectService(svc.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.service_start_connect))
                        }
                        connected -> OutlinedButton(onClick = vm::disconnect, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LinkOff, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.action_disconnect))
                        }
                        else -> Button(onClick = { vm.selectService(svc.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (active && connection is ConnectionState.Failed) Res.string.action_retry
                                    else Res.string.action_connect,
                                ),
                            )
                        }
                    }
                    if (isLocal && isRunning) FilledTonalButton(onClick = { vm.stopLocalService(svc.id) }) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_stop))
                    }
                }
            }

            SectionCard(title = stringResource(Res.string.service_details)) {
                if (isLocal) {
                    InfoRow(
                        stringResource(Res.string.service_version),
                        if (svc.customCorePath.isNotBlank()) stringResource(Res.string.service_custom_core) else coreVersion,
                    )
                    InfoRow(
                        stringResource(Res.string.service_captcha),
                        stringResource(
                            if (svc.forceManualCaptcha) Res.string.service_captcha_always_manual
                            else Res.string.service_captcha_automatic,
                        ),
                    )
                    InfoRow(
                        stringResource(Res.string.service_logging),
                        stringResource(if (svc.verbose) Res.string.service_logging_verbose else Res.string.service_logging_normal),
                    )
                    InfoRow(stringResource(Res.string.service_socket), vm.localSocketPath(svc.id), mono = true, copyable = true)
                } else {
                    InfoRow(stringResource(Res.string.service_host), svc.host)
                    InfoRow(stringResource(Res.string.service_port), svc.port.toString())
                    InfoRow(
                        stringResource(Res.string.service_auth_key),
                        if (svc.clientPubKeyB64.isBlank()) stringResource(Res.string.service_auth_none)
                        else svc.clientPubKeyB64.take(16) + "…",
                        mono = svc.clientPubKeyB64.isNotBlank(),
                    )
                    InfoRow(
                        stringResource(Res.string.service_key),
                        if (svc.serverPubKeyB64.isBlank()) stringResource(Res.string.service_key_not_pinned)
                        else svc.serverPubKeyB64.take(16) + "…",
                        mono = svc.serverPubKeyB64.isNotBlank(),
                    )
                }
                InfoRow(stringResource(Res.string.service_id), svc.id, mono = true)
            }

            if (!isLocal && svc.clientPubKeyB64.isNotBlank()) SectionCard(
                title = stringResource(Res.string.service_authorize_title), icon = Icons.Default.Key,
            ) {
                Text(
                    stringResource(Res.string.service_authorize_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { copy(svc.clientPubKeyB64, publicKeyLabel) }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.service_copy_public_key))
                }
            }

            if (isLocal) LocalOutputCard(vm, svc.id, isRunning)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLogs, enabled = connected, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Article, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.service_live_logs))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = stringResource(Res.string.service_remove_title, svc.name),
        text = stringResource(
            if (isLocal) Res.string.service_remove_local_body else Res.string.service_remove_remote_body,
        ),
        confirmLabel = stringResource(Res.string.action_remove),
        onConfirm = { scope.launch { vm.removeService(svc.id); onDeleted() } },
        onDismiss = { confirmDelete = false },
    )
}

/** Tail of the local process's stdout/stderr — the only place startup failures show up. */
@Composable
private fun LocalOutputCard(vm: AppViewModel, id: String, running: Boolean) {
    var output by remember { mutableStateOf("") }
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(id, running, refreshTick) {
        while (true) {
            output = vm.localServiceOutput(id).lines().takeLast(40).joinToString("\n").trim()
            if (!running) break
            delay(3000)
        }
    }
    if (output.isBlank()) return
    SectionCard(
        title = stringResource(Res.string.service_process_output),
        icon = Icons.Default.Terminal,
        action = {
            IconButton(onClick = { refreshTick++ }, Modifier.size(32.dp)) {
                Icon(Icons.Default.Refresh, stringResource(Res.string.action_refresh), Modifier.size(18.dp))
            }
        },
    ) {
        val colors = TurnableTheme.status
        androidx.compose.material3.Surface(color = colors.logBackground, shape = MaterialTheme.shapes.medium) {
            SelectionContainer {
                Text(
                    output, color = colors.logText, fontFamily = MonoFamily, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
    }
}

// ─── Add / edit ───────────────────────────────────────────────────────────────

private const val ML_KEM_768_PUBLIC_KEY_SIZE = 1184
private const val ML_KEM_SEED_SIZE = 64

@Composable
private fun ServiceForm(vm: AppViewModel, existing: ServiceConfig?, onClose: () -> Unit, onSave: (ServiceConfig) -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: ServiceType.LOCAL) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf(existing?.port?.takeIf { it > 0 }?.toString() ?: "") }
    var pubKey by remember { mutableStateOf(existing?.clientPubKeyB64 ?: "") }
    var privKey by remember { mutableStateOf(existing?.clientPrivKeyB64 ?: "") }
    var serverKey by remember { mutableStateOf(existing?.serverPubKeyB64 ?: "") }
    var version by remember { mutableStateOf(existing?.version ?: "") }
    var customCore by remember { mutableStateOf(existing?.customCorePath ?: "") }
    var forceManual by remember { mutableStateOf(existing?.forceManualCaptcha ?: false) }
    var verbose by remember { mutableStateOf(existing?.verbose ?: false) }
    var versions by remember { mutableStateOf<List<String>>(emptyList()) }
    var versionsError by remember { mutableStateOf<String?>(null) }
    var loadingVersions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val copy = rememberCopier()
    val publicKeyLabel = stringResource(Res.string.service_public_key_label)

    fun loadVersions() = scope.launch {
        loadingVersions = true
        vm.fetchAvailableVersions()
            .onSuccess { list ->
                versions = list; versionsError = null
                if (version.isBlank()) list.firstOrNull()?.let { version = it }
            }
            .onFailure { versionsError = getString(Res.string.service_releases_error, it.message.orEmpty()) }
        loadingVersions = false
    }
    LaunchedEffect(type) { if (type == ServiceType.LOCAL && versions.isEmpty()) loadVersions() }

    val portValue = port.toIntOrNull()
    val keyValid = pubKey.isBlank() || runCatching { Base64.decode(pubKey.trim()).size == ML_KEM_768_PUBLIC_KEY_SIZE }.getOrDefault(false)
    val privKeyValid = pubKey.isBlank() || runCatching { Base64.decode(privKey.trim()).size == ML_KEM_SEED_SIZE }.getOrDefault(false)
    val serverKeyValid = serverKey.isBlank() || runCatching { Base64.decode(serverKey.trim()).size == ML_KEM_768_PUBLIC_KEY_SIZE }.getOrDefault(false)
    val canSave = name.isNotBlank() && when (type) {
        ServiceType.LOCAL -> true
        ServiceType.REMOTE -> host.isNotBlank() && portValue != null && portValue in 1..65535 && keyValid && privKeyValid && serverKeyValid
    }

    Scaffold(
        topBar = {
            PaneTopBar(
                stringResource(if (existing == null) Res.string.service_add_title else Res.string.service_edit_title),
                onBack = onClose,
            ) {
                TextButton(enabled = canSave, onClick = {
                    val id = existing?.id ?: Random.nextLong().toULong().toString(16)
                    onSave(
                        when (type) {
                            ServiceType.LOCAL -> ServiceConfig(id, name.trim(), ServiceType.LOCAL, version = version.trim(), customCorePath = customCore, forceManualCaptcha = forceManual, verbose = verbose)
                            ServiceType.REMOTE -> ServiceConfig(
                                id, name.trim(), ServiceType.REMOTE, host = host.trim(), port = portValue ?: 0,
                                clientPubKeyB64 = pubKey.trim(), clientPrivKeyB64 = privKey.trim(), serverPubKeyB64 = serverKey.trim(),
                            )
                        }
                    )
                }) { Text(stringResource(Res.string.action_save)) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (existing == null) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(type == ServiceType.LOCAL, { type = ServiceType.LOCAL }, SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { SegmentedButtonDefaults.Icon(type == ServiceType.LOCAL) { Icon(Icons.Default.PhoneAndroid, null, Modifier.size(18.dp)) } },
                ) { Text(stringResource(Res.string.service_type_on_device)) }
                SegmentedButton(type == ServiceType.REMOTE, { type = ServiceType.REMOTE }, SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { SegmentedButtonDefaults.Icon(type == ServiceType.REMOTE) { Icon(Icons.Default.Cloud, null, Modifier.size(18.dp)) } },
                ) { Text(stringResource(Res.string.service_type_remote)) }
            }
            TextInput(
                name, { name = it }, stringResource(Res.string.form_name),
                supporting = stringResource(
                    if (type == ServiceType.LOCAL) Res.string.service_name_hint_local else Res.string.service_name_hint_remote,
                ),
            )

            if (type == ServiceType.LOCAL) {
                FormSection(stringResource(Res.string.service_release_section))
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (versions.isNotEmpty()) {
                        DropdownField(
                            stringResource(Res.string.service_version_field), version, versions, { version = it }, Modifier.weight(1f),
                            hint = { if (it == versions.first()) stringResource(Res.string.service_latest_release) else null },
                        )
                    } else {
                        TextInput(
                            version, { version = it }, stringResource(Res.string.service_version_tag),
                            Modifier.weight(1f), mono = true, supporting = stringResource(Res.string.service_version_tag_hint),
                        )
                    }
                    // Same box for both states and as tall as the field, so the button lines up with it and doesn't resize
                    Box(Modifier.size(48.dp, 56.dp), Alignment.Center) {
                        if (loadingVersions) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { loadVersions() }) {
                            Icon(Icons.Default.Refresh, stringResource(Res.string.service_reload_releases))
                        }
                    }
                }
                versionsError?.let { ErrorBanner(it) { versionsError = null } }
                Text(
                    stringResource(Res.string.service_release_download_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(Res.string.service_min_version_hint, MIN_CORE_VERSION),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (customCore.isNotBlank()) InfoRow(stringResource(Res.string.service_core_file), customCore, mono = true)
                CustomCoreRow(existing?.id ?: "new", customCore, { customCore = it })
                FormSection(stringResource(Res.string.service_captcha_section))
                SwitchRow(
                    stringResource(Res.string.service_force_manual), forceManual, { forceManual = it },
                    stringResource(Res.string.service_force_manual_hint),
                )
                FormSection(stringResource(Res.string.service_logging))
                SwitchRow(stringResource(Res.string.service_verbose), verbose, { verbose = it }, stringResource(Res.string.service_verbose_hint))
            } else {
                FormSection(stringResource(Res.string.service_address_section))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(host, { host = it.trim() }, stringResource(Res.string.service_host_field), Modifier.weight(0.68f))
                    TextInput(
                        port, { port = it.filter(Char::isDigit).take(5) }, stringResource(Res.string.service_port_field),
                        Modifier.weight(0.32f), numeric = true,
                        isError = port.isNotEmpty() && (portValue == null || portValue !in 1..65535),
                    )
                }
                FormSection(stringResource(Res.string.service_auth_section))
                Text(
                    stringResource(Res.string.service_auth_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val (pub, priv) = generateServiceKeyPair()
                        pubKey = Base64.encode(pub); privKey = Base64.encode(priv)
                    }) {
                        Icon(Icons.Default.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.service_generate_key_pair))
                    }
                    AnimatedVisibility(pubKey.isNotBlank() && keyValid) {
                        OutlinedButton(onClick = { copy(pubKey.trim(), publicKeyLabel) }) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.service_copy_public_key))
                        }
                    }
                }
                TextInput(
                    pubKey, { pubKey = it }, stringResource(Res.string.service_client_pub_key),
                    mono = true, singleLine = false, isError = !keyValid,
                    supporting = if (!keyValid) stringResource(Res.string.service_invalid_key) else null,
                )
                TextInput(
                    privKey, { privKey = it }, stringResource(Res.string.service_client_priv_key),
                    mono = true, singleLine = false, isError = !privKeyValid,
                    supporting = if (!privKeyValid) stringResource(Res.string.service_priv_key_hint)
                    else stringResource(Res.string.service_priv_key_stays),
                )
                TextInput(
                    serverKey, { serverKey = it }, stringResource(Res.string.service_pinned_key),
                    mono = true, singleLine = false, isError = !serverKeyValid,
                    supporting = if (!serverKeyValid) stringResource(Res.string.service_invalid_key)
                    else stringResource(Res.string.service_pinned_key_hint),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
