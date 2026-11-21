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
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.data.DEFAULT_SERVICE_ID
import ru.theairblow.turnable.data.ServiceConfig
import ru.theairblow.turnable.data.ServiceType
import ru.theairblow.turnable.proto.InstanceInfo
import ru.theairblow.turnable.proto.InstanceStatus
import ru.theairblow.turnable.ui.components.EmptyState
import ru.theairblow.turnable.ui.components.StatusKind
import ru.theairblow.turnable.ui.components.DownloadBar
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.ConnectionState
import turnable.composeapp.generated.resources.*

val InstanceInfo.statusKind: StatusKind
    get() = when (status) {
        InstanceStatus.INSTANCE_STATUS_STARTED -> StatusKind.Online
        InstanceStatus.INSTANCE_STATUS_STARTING -> StatusKind.Pending
        InstanceStatus.INSTANCE_STATUS_FAILED -> StatusKind.Failed
        else -> StatusKind.Offline
    }

/** Clients fall back to STARTING while reconnecting, so the label says so. */
val InstanceInfo.statusLabel: String
    @Composable get() = when (status) {
        InstanceStatus.INSTANCE_STATUS_STARTED ->
            stringResource(if (isClient) Res.string.status_connected else Res.string.status_running)
        InstanceStatus.INSTANCE_STATUS_STARTING ->
            stringResource(if (isClient) Res.string.status_connecting else Res.string.status_starting)
        InstanceStatus.INSTANCE_STATUS_FAILED -> stringResource(Res.string.status_failed)
        InstanceStatus.INSTANCE_STATUS_STOPPED -> stringResource(Res.string.status_stopped)
        InstanceStatus.INSTANCE_STATUS_UNSPECIFIED -> stringResource(Res.string.status_unknown)
    }

val InstanceInfo.isClient: Boolean get() = type == ru.theairblow.turnable.proto.InstanceType.INSTANCE_TYPE_CLIENT

val InstanceInfo.displayName: String get() = name.ifBlank { id }

/** One-line service summary under its name in lists and the detail bar. */
@Composable
fun ServiceConfig.displaySubtitle(): String = when (type) {
    ServiceType.LOCAL -> stringResource(
        Res.string.service_subtitle_local,
        if (customCorePath.isNotBlank()) stringResource(Res.string.service_subtitle_custom_core)
        else version.ifBlank { stringResource(Res.string.service_subtitle_latest) },
    )
    ServiceType.REMOTE -> "$host:$port"
}

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
            Icons.Default.Hub,
            if (advanced) stringResource(Res.string.gate_connecting_to, serviceName ?: stringResource(Res.string.gate_service_word))
            else stringResource(Res.string.gate_connecting),
            if (c.attempt > 1) stringResource(Res.string.gate_attempt, c.attempt) else null,
        ) { CircularProgressIndicator(Modifier.size(28.dp)) }
        is ConnectionState.Failed -> EmptyState(
            Icons.Default.CloudOff,
            if (advanced) stringResource(Res.string.gate_cannot_reach, serviceName ?: stringResource(Res.string.gate_the_service_word))
            else stringResource(Res.string.gate_service_not_running),
            c.message + (c.retryInSec?.let { stringResource(Res.string.gate_retrying_in, it) } ?: ""),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (advanced) vm.reconnect() else vm.selectService(DEFAULT_SERVICE_ID) }) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.action_retry_now))
                }
                FilledTonalButton(onClick = openServices) {
                    Text(stringResource(if (advanced) Res.string.gate_services else Res.string.gate_settings))
                }
            }
        }
        ConnectionState.Idle -> if (starting.isNotEmpty()) EmptyState(
            Icons.Default.Hub,
            if (download != null) stringResource(Res.string.gate_setting_up) else stringResource(Res.string.gate_starting_turnable),
            if (download != null) stringResource(Res.string.gate_downloading_once) else null,
        ) {
            val d = download
            if (d != null) DownloadBar(d, Modifier.widthIn(max = 320.dp)) else CircularProgressIndicator(Modifier.size(28.dp))
        }
        else EmptyState(
            Icons.Default.Hub,
            if (advanced) stringResource(Res.string.gate_no_service) else stringResource(Res.string.gate_stopped),
            if (advanced) stringResource(Res.string.gate_pick_service) else null,
        ) {
            Button(onClick = { if (advanced) openServices() else vm.selectService(DEFAULT_SERVICE_ID) }) {
                Text(stringResource(if (advanced) Res.string.gate_open_services else Res.string.gate_start))
            }
        }
    }
}
