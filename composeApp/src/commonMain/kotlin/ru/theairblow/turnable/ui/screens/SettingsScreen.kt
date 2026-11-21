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
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
import turnable.composeapp.generated.resources.*

/** Backup format; older backups were a bare list of services, which is still accepted. */
@Serializable
private data class Backup(val services: List<ServiceConfig> = emptyList(), val subscriptions: List<Subscription> = emptyList())

private val backupJson = Json { ignoreUnknownKeys = true }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var logs by remember { mutableStateOf(false) }
    if (logs) LogsPane(vm, title = stringResource(Res.string.services_service_logs), onBack = { logs = false })
    else SettingsContent(vm, openLogs = { logs = true })
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
    val backupLabel = stringResource(Res.string.settings_backup)

    Scaffold(topBar = { PaneTopBar(stringResource(Res.string.settings_title)) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val section = Modifier.widthIn(max = 640.dp)
            SectionCard(section, title = stringResource(Res.string.settings_appearance), icon = Icons.Default.DarkMode) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to Res.string.settings_theme_system,
                        ThemeMode.LIGHT to Res.string.settings_theme_light,
                        ThemeMode.DARK to Res.string.settings_theme_dark,
                    )
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(prefs.themeMode == mode, { vm.updateThemeMode(mode) }, SegmentedButtonDefaults.itemShape(i, modes.size)) {
                            Text(stringResource(label))
                        }
                    }
                }
            }

            if (!advanced) SectionCard(section, title = stringResource(Res.string.settings_captcha), icon = Icons.Default.Security) {
                Text(
                    stringResource(Res.string.settings_captcha_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (local != null) SwitchRow(
                    stringResource(Res.string.settings_force_manual), local.forceManualCaptcha,
                    { vm.updateForceManualCaptcha(local.id, it) },
                    stringResource(Res.string.settings_force_manual_hint),
                )
            }

            if (!advanced && local != null) LocalServiceCard(vm, local, prefs.autoUpdate, vm::updateAutoUpdate, openLogs, section)

            SectionCard(section, title = stringResource(Res.string.settings_advanced), icon = Icons.Default.Tune) {
                SwitchRow(
                    stringResource(Res.string.settings_advanced_mode), advanced, vm::updateAdvancedMode,
                    stringResource(Res.string.settings_advanced_mode_hint),
                )
                if (advanced) SwitchRow(
                    stringResource(Res.string.settings_auto_update_services), prefs.autoUpdate, vm::updateAutoUpdate,
                    stringResource(Res.string.settings_auto_update_hint),
                )
                SwitchRow(
                    stringResource(Res.string.settings_restore_running), prefs.restoreRunning, vm::updateRestoreRunning,
                    stringResource(Res.string.settings_restore_running_hint),
                )
                if (advanced) SwitchRow(
                    stringResource(Res.string.settings_auto_connect), prefs.autoConnect, vm::updateAutoConnect,
                    stringResource(Res.string.settings_auto_connect_hint),
                )
            }

            SectionCard(section, title = stringResource(Res.string.settings_backup), icon = Icons.Default.Backup) {
                Text(
                    stringResource(Res.string.settings_backup_hint),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        copy(
                            backupJson.encodeToString(Backup.serializer(), Backup(prefs.services, prefs.subscriptions)),
                            backupLabel,
                        )
                    }, enabled = prefs.services.isNotEmpty()) {
                        Icon(Icons.Default.Backup, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.settings_copy_backup))
                    }
                    OutlinedButton(onClick = {
                        val text = readClipboard().trim()
                        val backup = runCatching { backupJson.decodeFromString(Backup.serializer(), text) }
                            .recoverCatching { Backup(backupJson.decodeFromString<List<ServiceConfig>>(text)) }
                            .getOrNull()
                        if (backup == null) scope.launch { notify(getString(Res.string.settings_no_backup)) }
                        else scope.launch {
                            val added = vm.importServices(backup.services, backup.subscriptions)
                            notify(
                                if (added == 0) getString(Res.string.settings_nothing_new)
                                else getPluralString(Res.plurals.settings_imported, added, added),
                            )
                        }
                    }) {
                        Icon(Icons.Default.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.settings_restore_clipboard))
                    }
                }
            }

            SectionCard(section, title = stringResource(Res.string.settings_about), icon = Icons.Default.Info) {
                Text("Turnable", style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.settings_app_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                InfoRow(stringResource(Res.string.settings_version), APP_VERSION)
                InfoRow(stringResource(Res.string.settings_service_protocol), "v$SERVICE_PROTOCOL_VERSION")
                InfoRow(stringResource(Res.string.settings_user_agent), USER_AGENT, mono = true)
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
    SectionCard(
        modifier, title = stringResource(Res.string.settings_service_section), icon = Icons.Default.Hub,
        action = { StatusBadge(status.kind, status.label) },
    ) {
        InfoRow(
            stringResource(Res.string.settings_release),
            if (svc.customCorePath.isNotBlank()) stringResource(Res.string.service_custom_core)
            else svc.version.ifBlank { stringResource(Res.string.service_newest_installed) },
        )
        if (svc.customCorePath.isNotBlank()) InfoRow(
            stringResource(Res.string.settings_core_file), svc.customCorePath.substringAfterLast('/'), mono = true,
        )
        Text(
            if (svc.customCorePath.isBlank()) stringResource(Res.string.settings_needs_min, MIN_CORE_VERSION)
            else stringResource(Res.string.settings_version_checks_off),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(enabled = !checking && svc.customCorePath.isBlank(), onClick = {
                scope.launch {
                    checking = true
                    vm.checkForUpdates(force = true)
                        .onSuccess {
                            notify(
                                if (it == null) getString(Res.string.settings_up_to_date)
                                else getString(Res.string.settings_update_available, it.version),
                            )
                        }
                        .onFailure { notify(getString(Res.string.settings_check_failed, it.message.orEmpty())) }
                    checking = false
                }
            }) {
                Text(stringResource(if (checking) Res.string.settings_checking else Res.string.settings_check_updates))
            }
            OutlinedButton(onClick = { vm.restartLocalService(svc.id) }) {
                Text(stringResource(Res.string.action_restart))
            }
            OutlinedButton(onClick = openLogs, enabled = connection is ConnectionState.Connected) {
                Text(stringResource(Res.string.settings_logs))
            }
            CustomCoreButtons(svc.id, svc.customCorePath) { path -> scope.launch { vm.saveService(svc.copy(customCorePath = path)) } }
        }
        SwitchRow(
            stringResource(Res.string.settings_update_automatically), autoUpdate, onAutoUpdate,
            stringResource(Res.string.settings_auto_update_hint),
        )
        download?.let { DownloadBar(it) }
        (connection as? ConnectionState.Failed)?.let { ErrorBanner(it.message) }
    }
}
