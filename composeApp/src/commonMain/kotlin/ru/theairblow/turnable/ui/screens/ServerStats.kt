package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import ru.theairblow.turnable.proto.GetStatsResponse
import ru.theairblow.turnable.proto.InstanceInfo
import ru.theairblow.turnable.proto.SessionStats
import ru.theairblow.turnable.proto.Usage
import ru.theairblow.turnable.proto.UserStats
import ru.theairblow.turnable.ui.components.ConfirmDialog
import ru.theairblow.turnable.ui.components.ErrorBanner
import ru.theairblow.turnable.ui.components.SectionCard
import ru.theairblow.turnable.ui.components.StatTile
import ru.theairblow.turnable.ui.components.StatusDot
import ru.theairblow.turnable.ui.components.StatusKind
import ru.theairblow.turnable.ui.theme.MonoFamily
import ru.theairblow.turnable.ui.util.formatAgo
import ru.theairblow.turnable.ui.util.formatBytes
import ru.theairblow.turnable.ui.util.formatDuration
import ru.theairblow.turnable.ui.util.nowUnixSeconds
import ru.theairblow.turnable.ui.util.shortId
import ru.theairblow.turnable.viewmodel.AppViewModel

private const val STATS_POLL_MS = 3000L

private fun Usage?.label() = "↑ ${formatBytes(this?.up ?: 0)}  ↓ ${formatBytes(this?.down ?: 0)}"

/** Live stats of a server instance: totals, users and sessions, with kick controls. Polls while shown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerStatsSection(vm: AppViewModel, info: InstanceInfo) {
    var stats by remember(info.id) { mutableStateOf<GetStatsResponse?>(null) }
    var sessions by remember(info.id) { mutableStateOf<List<SessionStats>>(emptyList()) }
    var error by remember(info.id) { mutableStateOf<String?>(null) }
    var reloadTick by remember { mutableStateOf(0) }
    var kick by remember { mutableStateOf<KickTarget?>(null) }
    var confirmCollect by remember { mutableStateOf(false) }
    var collected by remember { mutableStateOf<Map<String, Usage>?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        vm.request { getStats(info.id) to listSessions(info.id) }
            .onSuccess { (s, l) -> stats = s; sessions = l.sortedByDescending { it.since }; error = null }
            .onFailure { error = it.message }
    }

    LaunchedEffect(info.id, reloadTick) {
        while (true) {
            load()
            delay(STATS_POLL_MS)
        }
    }
    // Tunnel open/close events make the change visible immediately instead of on the next poll
    LaunchedEffect(info.id) {
        vm.sessionEvents.filter { it.instanceId == info.id }.collect { load() }
    }

    error?.let { ErrorBanner("Stats unavailable: $it") }
    val s = stats ?: return
    val totals = s.totals

    SectionCard(title = "Traffic", icon = Icons.Default.Insights, action = {
        IconButton(onClick = { confirmCollect = true }, Modifier.size(32.dp)) { Icon(Icons.Default.Tune, "Collect usage", Modifier.size(18.dp)) }
    }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tile = Modifier.widthIn(min = 132.dp).weight(1f)
            StatTile("Uploaded", formatBytes(totals?.usage?.up ?: 0), tile, Icons.Default.ArrowUpward)
            StatTile("Downloaded", formatBytes(totals?.usage?.down ?: 0), tile, Icons.Default.ArrowDownward)
            StatTile("Online users", (totals?.users ?: 0).toString(), tile, Icons.Default.Group)
            StatTile("Sessions", "${totals?.sessions ?: 0} · ${totals?.channels ?: 0} ch", tile, Icons.Default.Cable)
            StatTile("Uptime", totals?.started?.takeIf { it > 0 }?.let { formatDuration(nowUnixSeconds() - it) } ?: "—", tile, Icons.Default.Schedule)
        }
        Text(
            "Counted from the server's side: up is what clients sent into the tunnel.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(title = "Users (${s.users.size})", icon = Icons.Default.Group) {
        if (s.users.isEmpty()) Text("No user has connected since the server started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.users.sortedWith(compareByDescending<UserStats> { it.online }.thenByDescending { it.lastSeen }).forEachIndexed { i, u ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatsRow(
                online = u.online,
                title = u.uuid,
                lines = listOfNotNull(
                    if (u.online) "${u.sessions} session(s) · ${u.channels} channel(s)" else "Last seen ${formatAgo(u.lastSeen)}",
                    u.usage.label(),
                    u.ips.takeIf { it.isNotEmpty() }?.joinToString(", "),
                ),
                onKick = if (u.online) ({ kick = KickTarget.User(u.uuid) }) else null,
            )
        }
    }

    SectionCard(title = "Live sessions (${sessions.size})", icon = Icons.Default.Cable) {
        if (sessions.isEmpty()) Text("Nobody is connected right now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sessions.forEachIndexed { i, ss ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            StatsRow(
                online = true,
                title = "${ss.userUuid.shortId()} → ${ss.routeId}",
                lines = listOf(
                    "${ss.addr} · ${formatDuration(nowUnixSeconds() - ss.since)} · ${ss.channels} ch",
                    ss.usage.label(),
                ),
                onKick = { kick = KickTarget.Session(ss.id) },
            )
        }
    }

    kick?.let { target ->
        ConfirmDialog(
            title = if (target is KickTarget.User) "Kick user?" else "Kick session?",
            text = when (target) {
                is KickTarget.User -> "Closes every tunnel of ${target.uuid}. They can reconnect unless you remove them from the provider."
                is KickTarget.Session -> "Closes the tunnels of this session. The client will usually reconnect."
            },
            confirmLabel = "Kick",
            onConfirm = {
                scope.launch {
                    vm.request {
                        when (target) {
                            is KickTarget.User -> kickUser(info.id, target.uuid)
                            is KickTarget.Session -> kickSession(info.id, target.id)
                        }
                    }.onSuccess { n -> vm.notify("Closed $n tunnel${if (n == 1) "" else "s"}") }
                        .onFailure { vm.notify(it.message ?: "Kick failed") }
                    reloadTick++
                }
            },
            onDismiss = { kick = null },
        )
    }

    if (confirmCollect) ConfirmDialog(
        title = "Collect usage?",
        text = "Returns what each user moved since the previous collect and starts counting again. " +
            "Panels use this for billing; collecting here means a panel won't see this traffic.",
        confirmLabel = "Collect",
        destructive = false,
        onConfirm = {
            scope.launch {
                vm.request { getStats(info.id, collect = true) }
                    .onSuccess { collected = it.collected }
                    .onFailure { vm.notify(it.message ?: "Collect failed") }
            }
        },
        onDismiss = { confirmCollect = false },
    )

    collected?.let { map ->
        AlertDialog(
            onDismissRequest = { collected = null },
            title = { Text("Usage since last collect") },
            text = {
                if (map.isEmpty()) Text("No traffic.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(map.entries.sortedByDescending { it.value.up + it.value.down }.toList()) { (uuid, usage) ->
                        Column {
                            Text(uuid, fontFamily = MonoFamily, style = MaterialTheme.typography.bodySmall)
                            Text(usage.label(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { collected = null }) { Text("Done") } },
        )
    }
}

private sealed interface KickTarget {
    data class User(val uuid: String) : KickTarget
    data class Session(val id: String) : KickTarget
}

@Composable
private fun StatsRow(online: Boolean, title: String, lines: List<String>, onKick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (online) StatusKind.Online else StatusKind.Offline)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontFamily = MonoFamily, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onKick != null) IconButton(onClick = onKick) { Icon(Icons.Default.PersonOff, "Kick", tint = MaterialTheme.colorScheme.error) }
    }
}
