package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.theairblow.turnable.data.DEFAULT_SERVICE_ID
import ru.theairblow.turnable.proto.InstanceInfo
import ru.theairblow.turnable.proto.InstanceStatus
import ru.theairblow.turnable.ui.components.EmptyState
import ru.theairblow.turnable.ui.components.StatusKind
import ru.theairblow.turnable.ui.components.DownloadBar
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.ConnectionState

val InstanceInfo.statusKind: StatusKind
    get() = when (status) {
        InstanceStatus.INSTANCE_STATUS_STARTED -> StatusKind.Online
        InstanceStatus.INSTANCE_STATUS_STARTING -> StatusKind.Pending
        InstanceStatus.INSTANCE_STATUS_FAILED -> StatusKind.Failed
        else -> StatusKind.Offline
    }

/** Clients fall back to STARTING while reconnecting, so the label says so. */
val InstanceInfo.statusLabel: String
    get() = when (status) {
        InstanceStatus.INSTANCE_STATUS_STARTED -> if (isClient) "Connected" else "Running"
        InstanceStatus.INSTANCE_STATUS_STARTING -> if (isClient) "Connecting" else "Starting"
        InstanceStatus.INSTANCE_STATUS_FAILED -> "Failed"
        InstanceStatus.INSTANCE_STATUS_STOPPED -> "Stopped"
        InstanceStatus.INSTANCE_STATUS_UNSPECIFIED -> "Unknown"
    }

val InstanceInfo.isClient: Boolean get() = type == ru.theairblow.turnable.proto.InstanceType.INSTANCE_TYPE_CLIENT

val InstanceInfo.displayName: String get() = name.ifBlank { id }

/**
 * Shows [content] only while connected to a service; otherwise explains why not and offers the way forward.
 * Simple mode has one on-device service, so "nothing connected" just means it is still coming up.
 */
@Composable
fun ConnectionGate(vm: AppViewModel, openServices: () -> Unit, content: @Composable () -> Unit) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val prefs by vm.prefsData.collectAsStateWithLifecycle()
    val starting by vm.localStarting.collectAsStateWithLifecycle()
    val download by vm.coreDownload.collectAsStateWithLifecycle()
    val serviceName = connection.activeServiceId?.let { id -> prefs.services.firstOrNull { it.id == id }?.name }
    val advanced = prefs.advancedMode
    when (val c = connection) {
        is ConnectionState.Connected -> content()
        is ConnectionState.Connecting -> EmptyState(
            Icons.Default.Hub, if (advanced) "Connecting to ${serviceName ?: "service"}…" else "Connecting…",
            if (c.attempt > 1) "Attempt ${c.attempt}" else null,
        ) { CircularProgressIndicator(Modifier.size(28.dp)) }
        is ConnectionState.Failed -> EmptyState(
            Icons.Default.CloudOff, if (advanced) "Can't reach ${serviceName ?: "the service"}" else "Turnable service isn't running",
            c.message + (c.retryInSec?.let { "\nRetrying in ${it}s" } ?: ""),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (advanced) vm.reconnect() else vm.selectService(DEFAULT_SERVICE_ID) }) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Retry now")
                }
                FilledTonalButton(onClick = openServices) { Text(if (advanced) "Services" else "Settings") }
            }
        }
        ConnectionState.Idle -> if (starting.isNotEmpty()) EmptyState(
            Icons.Default.Hub, if (download != null) "Setting up Turnable…" else "Starting Turnable…",
            if (download != null) "Downloading the Turnable service, this happens once." else null,
        ) {
            val d = download
            if (d != null) DownloadBar(d, Modifier.widthIn(max = 320.dp)) else CircularProgressIndicator(Modifier.size(28.dp))
        }
        else EmptyState(
            Icons.Default.Hub, if (advanced) "No service connected" else "Turnable is stopped",
            if (advanced) "Pick or add a Turnable service to manage its servers, clients and providers." else null,
        ) {
            Button(onClick = { if (advanced) openServices() else vm.selectService(DEFAULT_SERVICE_ID) }) {
                Text(if (advanced) "Open services" else "Start")
            }
        }
    }
}
