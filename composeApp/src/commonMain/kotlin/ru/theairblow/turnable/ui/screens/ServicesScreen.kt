package ru.theairblow.turnable.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
        svc.id in starting -> ServiceStatus(StatusKind.Pending, "Starting")
        active && connection is ConnectionState.Connected -> ServiceStatus(StatusKind.Online, "Connected")
        active && connection is ConnectionState.Connecting -> ServiceStatus(StatusKind.Pending, "Connecting")
        active && connection is ConnectionState.Failed -> ServiceStatus(StatusKind.Failed, "Unreachable")
        svc.type == ServiceType.LOCAL && svc.id in running -> ServiceStatus(StatusKind.Offline, "Running")
        else -> ServiceStatus(StatusKind.Offline, if (svc.type == ServiceType.LOCAL) "Stopped" else "Idle")
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
        list = {
            ServiceList(
                vm, prefs.services,
                selectedId = (pane as? ServicePane.Detail)?.id,
                onOpen = { pane = ServicePane.Detail(it.id) },
                onAdd = { pane = ServicePane.Add },
            )
        },
        placeholder = { EmptyState(Icons.Default.Hub, "Select a service", "Its connection and controls will show up here.") },
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
            is ServicePane.Logs -> LogsPane(vm, title = "Service logs", onBack = { pane = ServicePane.Detail(p.id) })
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
        topBar = { PaneTopBar("Services") },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add service") })
        },
    ) { padding ->
        if (services.isEmpty()) {
            EmptyState(
                Icons.Default.Hub, "No services yet",
                "Run a Turnable service on this device, or connect to one running elsewhere.",
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
                    subtitle = svc.subtitle,
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
    val scope = rememberCoroutineScope()
    val copy = rememberCopier()
    var confirmDelete by remember { mutableStateOf(false) }

    val isLocal = svc.type == ServiceType.LOCAL
    val isRunning = svc.id in running
    val isStarting = svc.id in starting
    val active = connection.activeServiceId == svc.id
    val connected = active && connection is ConnectionState.Connected

    Scaffold(
        topBar = {
            PaneTopBar(svc.name, svc.subtitle, onBack = onBack) {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
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
                        Text(if (isLocal) "Local service" else "Remote service", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isLocal) (if (isRunning) "Process running" else "Process stopped") else "${svc.host}:${svc.port}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusBadge(status.kind, status.label)
                }
                val failure = (connection as? ConnectionState.Failed)?.takeIf { active }
                if (failure != null) ErrorBanner(failure.message + (failure.retryInSec?.let { " · retrying in ${it}s" } ?: ""))
                if (connected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val servers = instances.count { !it.isClient }
                        val clients = instances.count { it.isClient }
                        Text(
                            "$servers server${if (servers == 1) "" else "s"} · $clients client${if (clients == 1) "" else "s"} · " +
                                "${providers.size} provider${if (providers.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    when {
                        isStarting -> Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Starting…")
                        }
                        isLocal && !isRunning -> Button(onClick = { vm.selectService(svc.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Start & connect")
                        }
                        connected -> OutlinedButton(onClick = vm::disconnect, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LinkOff, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Disconnect")
                        }
                        else -> Button(onClick = { vm.selectService(svc.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (active && connection is ConnectionState.Failed) "Retry" else "Connect")
                        }
                    }
                    if (isLocal && isRunning) FilledTonalButton(onClick = { vm.stopLocalService(svc.id) }) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Stop")
                    }
                }
            }

            SectionCard(title = "Details") {
                if (isLocal) {
                    InfoRow("Version", if (svc.customCorePath.isNotBlank()) "Custom core" else svc.version.ifBlank { "Newest installed" })
                    InfoRow("Captcha", if (svc.forceManualCaptcha) "Always manual" else "Automatic, manual when it fails")
                    InfoRow("Socket", vm.localSocketPath(svc.id), mono = true, copyable = true)
                } else {
                    InfoRow("Host", svc.host)
                    InfoRow("Port", svc.port.toString())
                    InfoRow(
                        "Auth key", if (svc.clientPubKeyB64.isBlank()) "None (service must allow anonymous)" else svc.clientPubKeyB64.take(16) + "…",
                        mono = svc.clientPubKeyB64.isNotBlank(),
                    )
                    InfoRow("Service key", if (svc.serverPubKeyB64.isBlank()) "Not pinned" else svc.serverPubKeyB64.take(16) + "…", mono = svc.serverPubKeyB64.isNotBlank())
                }
                InfoRow("ID", svc.id, mono = true)
            }

            if (!isLocal && svc.clientPubKeyB64.isNotBlank()) SectionCard(title = "Authorize this app", icon = Icons.Default.Key) {
                Text(
                    "Add this public key to allowed_keys in the service's service.json.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { copy(svc.clientPubKeyB64, "Public key") }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy public key")
                }
            }

            if (isLocal) LocalOutputCard(vm, svc.id, isRunning)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLogs, enabled = connected, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Article, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Live logs")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) ConfirmDialog(
        title = "Remove ${svc.name}?",
        text = if (isLocal) "The local service process will be stopped. Its saved instances stay on this device and come back if you add it again with the same ID."
        else "The service itself is not affected; only this saved connection is removed.",
        confirmLabel = "Remove",
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
        title = "Process output",
        icon = Icons.Default.Terminal,
        action = { IconButton(onClick = { refreshTick++ }, Modifier.size(32.dp)) { Icon(Icons.Default.Refresh, "Refresh", Modifier.size(18.dp)) } },
    ) {
        val colors = TurnableTheme.status
        androidx.compose.material3.Surface(color = colors.logBackground, shape = MaterialTheme.shapes.medium) {
            SelectionContainer {
                Text(
                    output, color = colors.logText, fontFamily = MonoFamily, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp),
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
    var versions by remember { mutableStateOf<List<String>>(emptyList()) }
    var versionsError by remember { mutableStateOf<String?>(null) }
    var loadingVersions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val copy = rememberCopier()

    fun loadVersions() = scope.launch {
        loadingVersions = true
        vm.fetchAvailableVersions()
            .onSuccess { list ->
                versions = list; versionsError = null
                if (version.isBlank()) list.firstOrNull()?.let { version = it }
            }
            .onFailure { versionsError = "Couldn't load releases: ${it.message}" }
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
            PaneTopBar(if (existing == null) "Add service" else "Edit service", onBack = onClose) {
                TextButton(enabled = canSave, onClick = {
                    val id = existing?.id ?: Random.nextLong().toULong().toString(16)
                    onSave(
                        when (type) {
                            ServiceType.LOCAL -> ServiceConfig(id, name.trim(), ServiceType.LOCAL, version = version.trim(), customCorePath = customCore, forceManualCaptcha = forceManual)
                            ServiceType.REMOTE -> ServiceConfig(
                                id, name.trim(), ServiceType.REMOTE, host = host.trim(), port = portValue ?: 0,
                                clientPubKeyB64 = pubKey.trim(), clientPrivKeyB64 = privKey.trim(), serverPubKeyB64 = serverKey.trim(),
                            )
                        }
                    )
                }) { Text("Save") }
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
                ) { Text("On this device") }
                SegmentedButton(type == ServiceType.REMOTE, { type = ServiceType.REMOTE }, SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { SegmentedButtonDefaults.Icon(type == ServiceType.REMOTE) { Icon(Icons.Default.Cloud, null, Modifier.size(18.dp)) } },
                ) { Text("Remote") }
            }
            TextInput(name, { name = it }, "Name", supporting = if (type == ServiceType.LOCAL) "e.g. Phone tunnels" else "e.g. Frankfurt VPS")

            if (type == ServiceType.LOCAL) {
                FormSection("Turnable release")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (versions.isNotEmpty()) {
                        DropdownField("Version", version, versions, { version = it }, Modifier.weight(1f),
                            hint = { if (it == versions.first()) "Latest release" else null })
                    } else {
                        TextInput(version, { version = it }, "Version tag", Modifier.weight(1f), mono = true, supporting = "e.g. v1.4.0")
                    }
                    if (loadingVersions) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { loadVersions() }) { Icon(Icons.Default.Refresh, "Reload releases") }
                }
                versionsError?.let { ErrorBanner(it) { versionsError = null } }
                Text(
                    "The release is downloaded on first start. The service keeps its instances and providers on this device.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Releases before $MIN_CORE_VERSION aren't supported. A custom core binary skips that check.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (customCore.isNotBlank()) InfoRow("Core file", customCore, mono = true)
                CustomCoreRow(existing?.id ?: "new", customCore, { customCore = it })
                FormSection("Captcha")
                SwitchRow(
                    "Always solve captchas manually", forceManual, { forceManual = it },
                    "Skips the automatic attempt for every client of this service",
                )
            } else {
                FormSection("Address")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(host, { host = it.trim() }, "Host", Modifier.weight(0.68f))
                    TextInput(port, { port = it.filter(Char::isDigit).take(5) }, "Port", Modifier.weight(0.32f), numeric = true,
                        isError = port.isNotEmpty() && (portValue == null || portValue !in 1..65535))
                }
                FormSection("Authentication")
                Text(
                    "Needed when the service has a key pair. Generate one here, then add the public key to the service's allowed_keys.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val (pub, priv) = generateServiceKeyPair()
                        pubKey = Base64.encode(pub); privKey = Base64.encode(priv)
                    }) { Icon(Icons.Default.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Generate key pair") }
                    AnimatedVisibility(pubKey.isNotBlank() && keyValid) {
                        OutlinedButton(onClick = { copy(pubKey.trim(), "Public key") }) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy public key")
                        }
                    }
                }
                TextInput(pubKey, { pubKey = it }, "Client public key (base64)", mono = true, singleLine = false,
                    isError = !keyValid, supporting = if (!keyValid) "Not a valid ML-KEM-768 public key" else null)
                TextInput(privKey, { privKey = it }, "Client private key (base64)", mono = true, singleLine = false,
                    isError = !privKeyValid, supporting = if (!privKeyValid) "Needed to prove this app owns the public key" else "Stays on this device")
                TextInput(serverKey, { serverKey = it }, "Pinned service public key (optional)", mono = true, singleLine = false,
                    isError = !serverKeyValid, supporting = if (!serverKeyValid) "Not a valid ML-KEM-768 public key" else "Refuses to connect to a service with a different key")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
