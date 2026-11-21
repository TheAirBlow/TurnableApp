package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.theairblow.turnable.config.Choices
import ru.theairblow.turnable.config.ConfigJson
import ru.theairblow.turnable.config.ProviderConfig
import ru.theairblow.turnable.config.generateClientConfig
import ru.theairblow.turnable.config.parseServerConfig
import ru.theairblow.turnable.config.prettyJson
import ru.theairblow.turnable.proto.InstanceInfo
import ru.theairblow.turnable.proto.InstanceType
import ru.theairblow.turnable.proto.ProviderInfo
import ru.theairblow.turnable.proto.RouteInfo
import ru.theairblow.turnable.proto.UserInfo
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
import ru.theairblow.turnable.ui.components.StatusKind
import ru.theairblow.turnable.ui.components.SwitchRow
import ru.theairblow.turnable.ui.components.TextInput
import ru.theairblow.turnable.ui.components.rememberCopier
import ru.theairblow.turnable.ui.theme.MonoFamily
import ru.theairblow.turnable.ui.util.shortId
import ru.theairblow.turnable.viewmodel.AppViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private sealed interface ProviderPane {
    data object Add : ProviderPane
    data class Detail(val uuid: String) : ProviderPane
}

@OptIn(ExperimentalUuidApi::class)
private fun newUuid() = Uuid.random().toString()

@Composable
fun ProvidersScreen(vm: AppViewModel, openServices: () -> Unit) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var pane by remember { mutableStateOf<ProviderPane?>(null) }

    ConnectionGate(vm, openServices) {
        AdaptiveListDetail(
            detailKey = pane,
            onCloseDetail = { pane = null },
            list = {
                Scaffold(
                    topBar = {
                        PaneTopBar("Providers", busy = refreshing) {
                            IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                        }
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(onClick = { pane = ProviderPane.Add }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New provider") })
                    },
                ) { padding ->
                    if (providers.isEmpty()) {
                        EmptyState(
                            Icons.Default.Inventory2, "No providers",
                            "A provider holds the routes (destinations) and users that servers accept.", Modifier.padding(padding),
                        )
                        return@Scaffold
                    }
                    LazyColumn(
                        Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(providers, key = { it.uuid }) { p ->
                            ListCard(
                                title = p.name.ifBlank { p.uuid }, subtitle = p.uuid.shortId(),
                                selected = (pane as? ProviderPane.Detail)?.uuid == p.uuid,
                                onClick = { pane = ProviderPane.Detail(p.uuid) },
                                leading = { IconTile(Icons.Default.Inventory2) },
                                trailing = { if (p.inUse) StatusBadge(StatusKind.Online, "In use") },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            },
            placeholder = { EmptyState(Icons.Default.Inventory2, "Select a provider") },
        ) { p ->
            when (p) {
                ProviderPane.Add -> AddProviderPane(vm, onClose = { pane = null }, onCreated = { pane = ProviderPane.Detail(it) })
                is ProviderPane.Detail -> {
                    val info = providers.firstOrNull { it.uuid == p.uuid }
                    if (info == null) LaunchedEffect(p.uuid) { pane = null }
                    else ProviderDetail(vm, info, onBack = { pane = null })
                }
            }
        }
    }
}

// ─── Add ──────────────────────────────────────────────────────────────────────

@Composable
private fun AddProviderPane(vm: AppViewModel, onClose: () -> Unit, onCreated: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("raw") }
    var path by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val config = when (type) {
        "json" -> ConfigJson.encodeToString(ProviderConfig.serializer(), ProviderConfig("json", path = path.trim()))
        else -> raw.trim().ifBlank { """{"type":"raw","routes":[],"users":[]}""" }
    }

    Scaffold(topBar = {
        PaneTopBar("New provider", onBack = onClose, busy = submitting) {
            TextButton(enabled = name.isNotBlank() && !submitting && (type != "json" || path.isNotBlank()), onClick = {
                scope.launch {
                    submitting = true; error = null
                    val check = vm.request { validateProviderConfig(config) }.getOrNull()
                    if (check != null && !check.valid) {
                        error = check.error.ifBlank { "Invalid provider config" }
                    } else {
                        vm.request { addProvider(uuid.trim(), name.trim(), config) }
                            .onSuccess { vm.refresh(); onCreated(it) }.onFailure { error = it.message }
                    }
                    submitting = false
                }
            }) { Text("Create") }
        }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { ErrorBanner(it) { error = null } }
            TextInput(name, { name = it }, "Name")
            TextInput(uuid, { uuid = it.trim() }, "UUID (optional)", mono = true, supporting = "Generated by the service when empty")
            DropdownField("Type", type, listOf("raw", "json"), { type = it }, hint = {
                if (it == "raw") "Routes and users are stored by the service" else "Read from a JSON file on the service host"
            })
            if (type == "json") TextInput(path, { path = it }, "File path on the service host", mono = true)
            else TextInput(raw, { raw = it }, "Initial config JSON (optional)", mono = true, singleLine = false, minLines = 5,
                supporting = "Leave empty to start with no routes or users and add them afterwards")
        }
    }
}

// ─── Detail ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderDetail(vm: AppViewModel, provider: ProviderInfo, onBack: () -> Unit) {
    var routes by remember(provider.uuid) { mutableStateOf<List<RouteInfo>>(emptyList()) }
    var users by remember(provider.uuid) { mutableStateOf<List<UserInfo>>(emptyList()) }
    var config by remember(provider.uuid) { mutableStateOf("") }
    var error by remember(provider.uuid) { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    var editRoute by remember { mutableStateOf<RouteInfo?>(null) }
    var editUser by remember { mutableStateOf<UserInfo?>(null) }
    var shareUser by remember { mutableStateOf<UserInfo?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleteRoute by remember { mutableStateOf<RouteInfo?>(null) }
    var deleteUser by remember { mutableStateOf<UserInfo?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(provider.uuid, reloadTick) {
        vm.request { Triple(listRoutes(provider.uuid), listUsers(provider.uuid), getProvider(provider.uuid).config) }
            .onSuccess { (r, u, c) -> routes = r; users = u; config = c; error = null }
            .onFailure { error = it.message }
    }
    fun mutate(block: suspend ru.theairblow.turnable.service.TurnableServiceClient.() -> Unit) = scope.launch {
        vm.mutate(block)
        reloadTick++
    }

    Scaffold(topBar = {
        PaneTopBar(provider.name.ifBlank { provider.uuid }, if (provider.inUse) "Used by a server" else "Not used", onBack = onBack) {
            IconButton(onClick = { reloadTick++ }) { Icon(Icons.Default.Refresh, "Reload") }
            IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, "Rename") }
            IconButton(onClick = { confirmDelete = true }, enabled = !provider.inUse) {
                Icon(Icons.Default.Delete, "Delete", tint = if (provider.inUse) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)
            }
        }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { ErrorBanner(it) }
            SectionCard { InfoRow("UUID", provider.uuid, mono = true, copyable = true) }

            SectionCard(title = "Routes (${routes.size})", icon = Icons.Default.AltRoute, action = {
                IconButton(onClick = { editRoute = RouteInfo() }, Modifier.size(32.dp)) { Icon(Icons.Default.Add, "Add route") }
            }) {
                if (routes.isEmpty()) Text("No routes. A route is a destination users can tunnel to.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                routes.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EntityRow(
                        title = r.name.ifBlank { r.id },
                        lines = listOf("${r.id} · ${r.address}:${r.port}", "${r.socket} · ${r.transport.ifBlank { "mpath" }} · ${r.encryption.ifBlank { "handshake" }}"),
                        onEdit = { editRoute = r }, onDelete = { deleteRoute = r },
                    )
                }
            }

            SectionCard(title = "Users (${users.size})", icon = Icons.Default.Person, action = {
                IconButton(onClick = { editUser = UserInfo(uuid = newUuid(), type = "relay", peers = 1) }, Modifier.size(32.dp)) {
                    Icon(Icons.Default.PersonAdd, "Add user")
                }
            }) {
                if (users.isEmpty()) Text("No users. Each user gets a UUID that works like a password.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                users.forEachIndexed { i, u ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EntityRow(
                        title = u.uuid, mono = true,
                        lines = listOf(
                            "${u.type} · ${u.peers} peer(s)" + if (u.forceturn) " · force TURN" else "",
                            "Routes: " + u.allowedRoutes.joinToString(", ").ifEmpty { "none" },
                        ),
                        onEdit = { editUser = u }, onDelete = { deleteUser = u },
                        extra = { IconButton(onClick = { shareUser = u }) { Icon(Icons.Default.Share, "Share client config") } },
                    )
                }
            }

            if (config.isNotBlank()) SectionCard(title = "Raw config") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    SelectionContainer {
                        Text(
                            remember(config) { prettyJson(config) }, fontFamily = MonoFamily, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (renaming) RenameProviderDialog(
        provider.name, onDismiss = { renaming = false },
        onSave = { name -> renaming = false; mutate { updateProvider(provider.uuid, name = name) } },
    )
    editRoute?.let { r ->
        RouteDialog(r, isNew = routes.none { it.id == r.id && r.id.isNotEmpty() }, onDismiss = { editRoute = null }) { route ->
            editRoute = null
            mutate { addRoute(provider.uuid, route) }
        }
    }
    editUser?.let { u ->
        UserDialog(u, routes, onDismiss = { editUser = null }) { user ->
            editUser = null
            mutate { addUser(provider.uuid, user) }
        }
    }
    shareUser?.let { u -> ShareConfigDialog(vm, u, routes, onDismiss = { shareUser = null }) }
    deleteRoute?.let { r ->
        ConfirmDialog("Delete route ${r.id}?", "Users lose access to this destination.", "Delete",
            onConfirm = { mutate { deleteRoute(provider.uuid, r.id) } }, onDismiss = { deleteRoute = null })
    }
    deleteUser?.let { u ->
        ConfirmDialog("Delete user?", "${u.uuid} will be disconnected and can't connect again.", "Delete",
            onConfirm = { mutate { deleteUser(provider.uuid, u.uuid) } }, onDismiss = { deleteUser = null })
    }
    if (confirmDelete) ConfirmDialog(
        "Delete ${provider.name}?", "All of its routes and users are removed.", "Delete",
        onConfirm = { scope.launch { vm.mutate { deleteProvider(provider.uuid) }.onSuccess { onBack() } } },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun RenameProviderDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename provider") },
        text = { TextInput(name, { name = it }, "Name") },
        confirmButton = { Button(enabled = name.isNotBlank() && name != current, onClick = { onSave(name.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EntityRow(
    title: String,
    lines: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    mono: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontFamily = if (mono) MonoFamily else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        extra()
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun RouteDialog(initial: RouteInfo, isNew: Boolean, onDismiss: () -> Unit, onSave: (RouteInfo) -> Unit) {
    var r by remember { mutableStateOf(initial.copy(transport = initial.transport.ifBlank { "mpath" }, encryption = initial.encryption.ifBlank { "handshake" }, socket = initial.socket.ifBlank { "udp" })) }
    var port by remember { mutableStateOf(initial.port.takeIf { it > 0 }?.toString().orEmpty()) }
    val portValue = port.toIntOrNull()?.takeIf { it in 1..65535 }
    val tcpWithoutTransport = r.socket == "tcp" && r.transport == "none"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add route" else "Edit route") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(r.id, { r = r.copy(id = it.trim()) }, "Route ID", mono = true, supporting = if (!isNew) "Same ID replaces the route" else null)
                TextInput(r.name, { r = r.copy(name = it) }, "Display name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(r.address, { r = r.copy(address = it.trim()) }, "Destination IP", Modifier.weight(0.65f), mono = true)
                    TextInput(port, { port = it.filter(Char::isDigit).take(5) }, "Port", Modifier.weight(0.35f), numeric = true)
                }
                DropdownField("Socket", r.socket, Choices.sockets, { r = r.copy(socket = it) })
                DropdownField("Transport", r.transport, Choices.transports, { r = r.copy(transport = it) }, hint = Choices::describeTransport)
                DropdownField("Encryption", r.encryption, Choices.encryptions, { r = r.copy(encryption = it) },
                    hint = { if (r.transport == "mpath") "mpath always encrypts everything" else null })
                if (tcpWithoutTransport) ErrorBanner("TCP routes need a transport (mpath or kcp)")
            }
        },
        confirmButton = {
            Button(enabled = r.id.isNotBlank() && r.address.isNotBlank() && portValue != null && !tcpWithoutTransport,
                onClick = { onSave(r.copy(port = portValue ?: 0)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserDialog(initial: UserInfo, routes: List<RouteInfo>, onDismiss: () -> Unit, onSave: (UserInfo) -> Unit) {
    var u by remember { mutableStateOf(initial) }
    var peers by remember { mutableStateOf(initial.peers.coerceAtLeast(1).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("User") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(u.uuid, { u = u.copy(uuid = it.trim()) }, "UUID", mono = true, supporting = "Acts as the user's password",
                    trailing = { IconButton(onClick = { u = u.copy(uuid = newUuid()) }) { Icon(Icons.Default.Shuffle, "Generate") } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("Type", u.type.ifBlank { "relay" }, Choices.userTypes, { u = u.copy(type = it) }, Modifier.weight(0.6f))
                    TextInput(peers, { peers = it.filter(Char::isDigit).take(3) }, "Peers", Modifier.weight(0.4f), numeric = true)
                }
                if (u.type == "p2p") SwitchRow("Force TURN", u.forceturn, { u = u.copy(forceturn = it) })
                FormSection("Allowed routes")
                if (routes.isEmpty()) Text("Add routes to the provider first.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    routes.forEach { r ->
                        val on = r.id in u.allowedRoutes
                        FilterChip(selected = on, onClick = {
                            u = u.copy(allowedRoutes = if (on) u.allowedRoutes - r.id else u.allowedRoutes + r.id)
                        }, label = { Text(r.name.ifBlank { r.id }) })
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = u.uuid.isNotBlank() && (peers.toIntOrNull() ?: 0) >= 1, onClick = { onSave(u.copy(peers = peers.toInt())) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Builds a client config for [user] from a running server's config, like `turnable config generate`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareConfigDialog(vm: AppViewModel, user: UserInfo, routes: List<RouteInfo>, onDismiss: () -> Unit) {
    val instances by vm.instances.collectAsStateWithLifecycle()
    val servers = instances.filter { it.type == InstanceType.INSTANCE_TYPE_SERVER }
    var server by remember { mutableStateOf<InstanceInfo?>(servers.firstOrNull()) }
    val allowed = routes.filter { it.id in user.allowedRoutes }
    var picked by remember { mutableStateOf(allowed.map { it.id }.toSet()) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val copy = rememberCopier()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share client config") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (servers.isEmpty()) ErrorBanner("Start a server that uses this provider first")
                else DropdownField("Server", server, servers, { server = it; result = null }, describe = { it?.displayName ?: "Select…" })
                FormSection("Routes")
                if (allowed.isEmpty()) Text("This user has no allowed routes.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowed.forEach { r ->
                        FilterChip(selected = r.id in picked, onClick = { picked = if (r.id in picked) picked - r.id else picked + r.id; result = null },
                            label = { Text(r.name.ifBlank { r.id }) })
                    }
                }
                error?.let { ErrorBanner(it) }
                result?.let {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        SelectionContainer { Text(it, fontFamily = MonoFamily, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = server != null && picked.isNotEmpty(), onClick = {
                val srv = server ?: return@Button
                scope.launch {
                    error = null
                    val outcome = runCatching {
                        val cfg = parseServerConfig(vm.request { getInstance(srv.id) }.getOrThrow().config)
                            ?: error("Couldn't read the server's config")
                        val json = ConfigJson.encodeToString(
                            ru.theairblow.turnable.config.ClientConfig.serializer(),
                            generateClientConfig(cfg, user, allowed.filter { it.id in picked }),
                        )
                        vm.request { convertClientConfig(json) }.getOrThrow()
                    }
                    outcome.onSuccess { result = it; copy(it, "Client link") }.onFailure { error = it.message }
                }
            }) { Text(if (result == null) "Generate & copy" else "Copy again") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
