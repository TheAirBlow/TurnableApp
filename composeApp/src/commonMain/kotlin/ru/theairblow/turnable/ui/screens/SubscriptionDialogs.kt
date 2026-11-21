package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.theairblow.turnable.proto.InstanceInfo
import ru.theairblow.turnable.subscription.Subscription
import ru.theairblow.turnable.ui.components.EmptyState
import ru.theairblow.turnable.ui.components.ErrorBanner
import ru.theairblow.turnable.ui.components.IconTile
import ru.theairblow.turnable.ui.components.PaneTopBar
import ru.theairblow.turnable.ui.components.SectionCard
import ru.theairblow.turnable.ui.components.StatusDot
import ru.theairblow.turnable.ui.components.StatusKind
import ru.theairblow.turnable.ui.components.SwitchRow
import ru.theairblow.turnable.ui.components.TextInput
import ru.theairblow.turnable.ui.components.rememberClipboardReader
import ru.theairblow.turnable.ui.util.formatAgo
import ru.theairblow.turnable.ui.util.formatBytes
import ru.theairblow.turnable.ui.util.formatDuration
import ru.theairblow.turnable.ui.util.nowUnixSeconds
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.ConnectionState


/** Remove confirmation for a subscription, with the choice to stop its clients as well. */
@Composable
internal fun RemoveSubscriptionDialog(sub: Subscription, onDismiss: () -> Unit, onRemove: (removeClients: Boolean) -> Unit) {
    var removeClients by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove ${sub.displayName}?") },
        text = { SwitchRow("Also remove its ${sub.entries.size} client(s)", removeClients, { removeClients = it }) },
        confirmButton = { Button(onClick = { onDismiss(); onRemove(removeClients) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun SubscriptionDialog(
    existing: Subscription?,
    initialUrl: String = "",
    onDismiss: () -> Unit,
    /** Returns an error to show in the dialog, or null once the dialog can close. */
    onSave: suspend (name: String, url: String, updateHours: Int) -> String?,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val readClipboard = rememberClipboardReader()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url ?: initialUrl.ifBlank { readClipboard().trim().takeIf { it.startsWith("http") }.orEmpty() }) }
    var hours by remember { mutableStateOf(existing?.updateHours?.toString() ?: "") }
    val urlOk = url.startsWith("http://") || url.startsWith("https://")
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (existing == null) focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add subscription" else "Edit subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { ErrorBanner(it) }
                TextInput(url, { url = it.trim(); error = null }, "Subscription URL", Modifier.focusRequester(focus), mono = true, isError = url.isNotEmpty() && !urlOk)
                TextInput(name, { name = it }, "Name (optional)", supporting = "Defaults to the title the panel sends")
                if (existing != null) TextInput(hours, { hours = it.filter(Char::isDigit).take(3) }, "Auto-update every (hours)",
                    numeric = true, supporting = "0 turns automatic updates off")
            }
        },
        confirmButton = {
            Button(enabled = urlOk && !busy, onClick = {
                scope.launch {
                    busy = true
                    error = onSave(name, url, hours.toIntOrNull() ?: 0)
                    busy = false
                }
            }) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (existing == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
