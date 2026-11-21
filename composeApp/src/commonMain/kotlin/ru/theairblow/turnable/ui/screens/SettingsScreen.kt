package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.theairblow.turnable.data.ServiceConfig
import ru.theairblow.turnable.data.ThemeMode
import ru.theairblow.turnable.data.DEFAULT_SERVICE_ID
import ru.theairblow.turnable.service.APP_VERSION
import ru.theairblow.turnable.service.MIN_CORE_VERSION
import ru.theairblow.turnable.service.SERVICE_PROTOCOL_VERSION
import ru.theairblow.turnable.service.USER_AGENT
import ru.theairblow.turnable.subscription.Subscription
import ru.theairblow.turnable.ui.components.CustomCoreButtons
import ru.theairblow.turnable.ui.components.DownloadBar
import ru.theairblow.turnable.ui.components.ErrorBanner
import ru.theairblow.turnable.ui.components.InfoRow
import ru.theairblow.turnable.ui.components.LocalNotifier
import ru.theairblow.turnable.ui.components.PaneTopBar
import ru.theairblow.turnable.ui.components.SectionCard
import ru.theairblow.turnable.ui.components.StatusBadge
import ru.theairblow.turnable.ui.components.SwitchRow
import ru.theairblow.turnable.ui.components.rememberClipboardReader
import ru.theairblow.turnable.ui.components.rememberCopier
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.ConnectionState

/** Backup format; older backups were a bare list of services, which is still accepted. */
@Serializable
private data class Backup(val services: List<ServiceConfig> = emptyList(), val subscriptions: List<Subscription> = emptyList())

private val backupJson = Json { ignoreUnknownKeys = true }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var logs by remember { mutableStateOf(false) }
    if (logs) LogsPane(vm, title = "Service logs", onBack = { logs = false }) else SettingsContent(vm, openLogs = { logs = true })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(vm: AppViewModel, openLogs: () -> Unit) {
    val prefs by vm.prefsData.collectAsStateWithLifecycle()
    val copy = rememberCopier()
    val readClipboard = rememberClipboardReader()
    val notify = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val local = prefs.services.firstOrNull { it.id == DEFAULT_SERVICE_ID }
    val advanced = prefs.advancedMode

    Scaffold(topBar = { PaneTopBar("Settings") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val section = Modifier.widthIn(max = 640.dp)
            SectionCard(section, title = "Appearance", icon = Icons.Default.DarkMode) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(prefs.themeMode == mode, { vm.updateThemeMode(mode) }, SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(label) }
                    }
                }
            }

            if (!advanced) SectionCard(section, title = "Captcha", icon = Icons.Default.Security) {
                Text(
                    "Turnable tries to solve the platform's captcha on its own. When that fails it notifies you, and you solve it " +
                        "in a page that is filled in for you.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (local != null) SwitchRow(
                    "Always solve captchas manually", local.forceManualCaptcha,
                    { vm.updateForceManualCaptcha(local.id, it) },
                    "Skips the automatic attempt. Restarts the service when changed",
                )
            }

            if (!advanced && local != null) LocalServiceCard(vm, local, prefs.autoUpdate, vm::updateAutoUpdate, openLogs, section)

            SectionCard(section, title = "Advanced", icon = Icons.Default.Tune) {
                SwitchRow(
                    "Advanced mode", advanced, vm::updateAdvancedMode,
                    "Adds tabs to manage services, servers and providers. Leave it off to just use Turnable as a client",
                )
                if (advanced) SwitchRow(
                    "Update local services automatically", prefs.autoUpdate, vm::updateAutoUpdate,
                    "Checks for new releases every few hours and asks before installing one",
                )
                SwitchRow(
                    "Reconnect clients on launch", prefs.restoreRunning, vm::updateRestoreRunning,
                    "Starts the clients that were running when the app was closed",
                )
                if (advanced) SwitchRow(
                    "Connect on launch", prefs.autoConnect, vm::updateAutoConnect,
                    "Reconnect to the last used service (and start it, if local) when the app opens",
                )
            }

            SectionCard(section, title = "Backup", icon = Icons.Default.Backup) {
                Text(
                    "Copies your saved services and subscriptions, including keys, to the clipboard as JSON. Keep it private.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        copy(backupJson.encodeToString(Backup.serializer(), Backup(prefs.services, prefs.subscriptions)), "Backup")
                    }, enabled = prefs.services.isNotEmpty()) {
                        Icon(Icons.Default.Backup, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Copy backup")
                    }
                    OutlinedButton(onClick = {
                        val text = readClipboard().trim()
                        val backup = runCatching { backupJson.decodeFromString(Backup.serializer(), text) }
                            .recoverCatching { Backup(backupJson.decodeFromString<List<ServiceConfig>>(text)) }
                            .getOrNull()
                        if (backup == null) notify("The clipboard doesn't contain a Turnable backup")
                        else scope.launch {
                            val added = vm.importServices(backup.services, backup.subscriptions)
                            notify(if (added == 0) "Nothing new to import" else "Imported $added service(s)")
                        }
                    }) {
                        Icon(Icons.Default.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Restore from clipboard")
                    }
                }
            }

            SectionCard(section, title = "About", icon = Icons.Default.Info) {
                Text("Turnable", style = MaterialTheme.typography.titleMedium)
                Text("Tunnels traffic through platform call infrastructure.", style = MaterialTheme.typography.bodyMedium)
                InfoRow("Version", APP_VERSION)
                InfoRow("Service protocol", "v$SERVICE_PROTOCOL_VERSION")
                InfoRow("User agent", USER_AGENT, mono = true)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The on-device service in simple mode: state, version and the controls that would otherwise live in Services. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalServiceCard(vm: AppViewModel, svc: ServiceConfig, autoUpdate: Boolean, onAutoUpdate: (Boolean) -> Unit, openLogs: () -> Unit, modifier: Modifier) {
    val status = rememberServiceStatus(vm, svc)
    val connection by vm.connection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val notify = LocalNotifier.current
    val download by vm.coreDownload.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }
    SectionCard(modifier, title = "Service", icon = Icons.Default.Hub, action = { StatusBadge(status.kind, status.label) }) {
        InfoRow("Release", if (svc.customCorePath.isNotBlank()) "Custom core" else svc.version.ifBlank { "Newest installed" })
        if (svc.customCorePath.isNotBlank()) InfoRow("Core file", svc.customCorePath.substringAfterLast('/'), mono = true)
        Text(
            if (svc.customCorePath.isBlank()) "Needs $MIN_CORE_VERSION or newer." else "Version checks are off.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled = !checking && svc.customCorePath.isBlank(), onClick = {
                scope.launch {
                    checking = true
                    vm.checkForUpdates(force = true)
                        .onSuccess { notify(if (it == null) "Turnable is up to date" else "Turnable ${it.version} is available") }
                        .onFailure { notify("Couldn't check for updates: ${it.message}") }
                    checking = false
                }
            }) { Text(if (checking) "Checking…" else "Check for updates") }
            OutlinedButton(onClick = { vm.restartLocalService(svc.id) }) { Text("Restart") }
            OutlinedButton(onClick = openLogs, enabled = connection is ConnectionState.Connected) { Text("Logs") }
            CustomCoreButtons(svc.id, svc.customCorePath) { path -> scope.launch { vm.saveService(svc.copy(customCorePath = path)) } }
        }
        SwitchRow(
            "Update automatically", autoUpdate, onAutoUpdate,
            "Checks for new releases every few hours and asks before installing one",
        )
        download?.let { DownloadBar(it) }
        (connection as? ConnectionState.Failed)?.let { ErrorBanner(it.message) }
    }
}
