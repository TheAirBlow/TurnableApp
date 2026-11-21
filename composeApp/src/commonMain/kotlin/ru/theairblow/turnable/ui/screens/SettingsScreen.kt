package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.data.AppPreferencesData
import ru.theairblow.turnable.data.DEFAULT_SERVICE_ID
import ru.theairblow.turnable.data.ServiceConfig
import ru.theairblow.turnable.data.ServiceType
import ru.theairblow.turnable.data.ThemeMode
import ru.theairblow.turnable.service.APP_VERSION
import ru.theairblow.turnable.service.MIN_CORE_VERSION
import ru.theairblow.turnable.service.SERVICE_PROTOCOL_VERSION
import ru.theairblow.turnable.service.USER_AGENT
import ru.theairblow.turnable.subscription.Subscription
import ru.theairblow.turnable.ui.components.AppUpdateActions
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
import ru.theairblow.turnable.ui.util.asTag
import ru.theairblow.turnable.ui.util.formatAgo
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.ConnectionState
import turnable.composeapp.generated.resources.*

/** Backup format; older backups were a bare list of services, which is still accepted. */
@Serializable
private data class Backup(val services: List<ServiceConfig> = emptyList(), val subscriptions: List<Subscription> = emptyList())

private val backupJson = Json { ignoreUnknownKeys = true }

/** Width from which settings split into two columns. */
private val TWO_COLUMNS_FROM = 760.dp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var logs by remember { mutableStateOf(false) }
    if (logs) LogsPane(vm, title = stringResource(Res.string.services_service_logs), onBack = { logs = false })
    else SettingsContent(vm, openLogs = { logs = true })
}

@Composable
private fun SettingsContent(vm: AppViewModel, openLogs: () -> Unit) {
    val prefs by vm.prefsData.collectAsStateWithLifecycle()
    val local = prefs.services.firstOrNull { it.id == DEFAULT_SERVICE_ID }
    val advanced = prefs.advancedMode

    val primary: @Composable ColumnScope.() -> Unit = {
        UpdatesCard(vm, prefs)
        AppearanceCard(prefs.themeMode, vm::updateThemeMode)
        GeneralCard(vm, prefs)
    }
    val secondary: @Composable ColumnScope.() -> Unit = {
        if (!advanced && local != null) {
            CaptchaCard(local.forceManualCaptcha) { vm.updateForceManualCaptcha(local.id, it) }
            LocalServiceCard(vm, local, openLogs)
        }
        BackupCard(vm, prefs)
        AboutCard()
    }

    Scaffold(topBar = { PaneTopBar(stringResource(Res.string.settings_title)) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val columns = if (maxWidth >= TWO_COLUMNS_FROM) 2 else 1
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (columns == 2) Row(Modifier.widthIn(max = 1040.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = secondary)
                } else Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    primary()
                    secondary()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AppearanceCard(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SectionCard(title = stringResource(Res.string.settings_appearance), icon = Icons.Default.DarkMode) {
        val modes = listOf(
            Triple(ThemeMode.SYSTEM, Res.string.settings_theme_system, Icons.Default.BrightnessAuto),
            Triple(ThemeMode.LIGHT, Res.string.settings_theme_light, Icons.Default.LightMode),
            Triple(ThemeMode.DARK, Res.string.settings_theme_dark, Icons.Default.DarkMode),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (value, label, icon) ->
                SegmentedButton(
                    mode == value, { onSelect(value) }, SegmentedButtonDefaults.itemShape(i, modes.size),
                    icon = { Icon(icon, null, Modifier.size(SegmentedButtonDefaults.IconSize)) },
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
    }
}

@Composable
private fun GeneralCard(vm: AppViewModel, prefs: AppPreferencesData) {
    SectionCard(title = stringResource(Res.string.settings_general), icon = Icons.Default.Tune) {
        Column {
            SwitchRow(
                stringResource(Res.string.settings_restore_running), prefs.restoreRunning, vm::updateRestoreRunning,
                stringResource(Res.string.settings_restore_running_hint),
            )
            if (prefs.advancedMode) SwitchRow(
                stringResource(Res.string.settings_auto_connect), prefs.autoConnect, vm::updateAutoConnect,
                stringResource(Res.string.settings_auto_connect_hint),
            )
            SwitchRow(
                stringResource(Res.string.settings_advanced_mode), prefs.advancedMode, vm::updateAdvancedMode,
                stringResource(Res.string.settings_advanced_mode_hint),
            )
        }
    }
}

@Composable
private fun CaptchaCard(forceManual: Boolean, onForceManual: (Boolean) -> Unit) {
    SectionCard(title = stringResource(Res.string.settings_captcha), icon = Icons.Default.Security) {
        Text(
            stringResource(Res.string.settings_captcha_hint),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            stringResource(Res.string.settings_force_manual), forceManual, onForceManual,
            stringResource(Res.string.settings_force_manual_hint),
        )
    }
}

/** The on-device service in simple mode: state, release and the controls that would otherwise live in Services. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalServiceCard(vm: AppViewModel, svc: ServiceConfig, openLogs: () -> Unit) {
    val status = rememberServiceStatus(vm, svc)
    val connection by vm.connection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val download by vm.coreDownload.collectAsStateWithLifecycle()
    val customCore = svc.customCorePath.isNotBlank()
    val coreVersion = rememberCoreVersion(vm, svc)
    SectionCard(
        title = stringResource(Res.string.settings_service_section), icon = Icons.Default.Hub,
        action = { StatusBadge(status.kind, status.label) },
    ) {
        InfoRow(stringResource(Res.string.settings_release), if (customCore) stringResource(Res.string.service_custom_core) else coreVersion)
        if (customCore) InfoRow(stringResource(Res.string.settings_core_file), svc.customCorePath.substringAfterLast('/'), mono = true)
        Text(
            if (customCore) stringResource(Res.string.settings_version_checks_off) else stringResource(Res.string.settings_needs_min, MIN_CORE_VERSION),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        download?.let { DownloadBar(it, stringResource(Res.string.download_version, it.version)) }
        (connection as? ConnectionState.Failed)?.let { ErrorBanner(it.message) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { vm.restartLocalService(svc.id) }) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.action_restart))
            }
            OutlinedButton(onClick = openLogs, enabled = connection is ConnectionState.Connected) {
                Text(stringResource(Res.string.settings_logs))
            }
            CustomCoreButtons(svc.id, svc.customCorePath) { path -> scope.launch { vm.saveService(svc.copy(customCorePath = path)) } }
        }
    }
}

/** App and core updates in one place: what is current, what is waiting, and how often to look. */
@Composable
private fun UpdatesCard(vm: AppViewModel, prefs: AppPreferencesData) {
    val appUpdate by vm.appUpdate.collectAsStateWithLifecycle()
    val coreUpdate by vm.update.collectAsStateWithLifecycle()
    val appInstalling by vm.installingApp.collectAsStateWithLifecycle()
    val appDownload by vm.appDownload.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val coreService = prefs.services.firstOrNull { it.id == coreUpdate?.serviceId }
        ?: prefs.services.firstOrNull { it.type == ServiceType.LOCAL }
    val scheme = MaterialTheme.colorScheme

    SectionCard(title = stringResource(Res.string.update_title), icon = Icons.Default.SystemUpdate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        when {
                            failure != null -> Res.string.update_status_failed
                            appUpdate != null || coreUpdate != null -> Res.string.update_status_available
                            else -> Res.string.update_status_current
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    failure ?: stringResource(Res.string.update_last_checked, formatAgo(prefs.lastUpdateCheckSec)),
                    style = MaterialTheme.typography.bodySmall, color = if (failure != null) scheme.error else scheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(enabled = !checking, onClick = {
                scope.launch {
                    checking = true
                    failure = null
                    vm.checkForUpdates(manual = true).onFailure { failure = it.message ?: getString(Res.string.vm_request_failed) }
                    checking = false
                }
            }) {
                if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (checking) Res.string.update_checking else Res.string.update_check_now))
            }
        }

        Column {
            UpdateRow(
                Icons.Default.PhoneAndroid, stringResource(Res.string.update_app_name), APP_VERSION.asTag(), appUpdate?.version,
                busy = if (appInstalling) ({ DownloadBar(appDownload, stringResource(Res.string.update_downloading_installer)) }) else null,
            ) { appUpdate?.let { AppUpdateActions(it, vm::installAppUpdate) } }

            if (coreService != null) {
                val coreVersion = rememberCoreVersion(vm, coreService)
                val updating by vm.updating.collectAsStateWithLifecycle()
                val download by vm.coreDownload.collectAsStateWithLifecycle()
                UpdateRow(
                    Icons.Default.Hub, stringResource(Res.string.update_core_name), coreVersion, coreUpdate?.version,
                    subtitle = if (coreService.customCorePath.isNotBlank()) stringResource(Res.string.service_custom_core) else null,
                    hint = if (appUpdate != null && coreUpdate != null) stringResource(Res.string.update_app_first) else null,
                    busy = if (updating) ({
                        DownloadBar(
                            download,
                            download?.let { stringResource(Res.string.download_version, it.version) } ?: stringResource(Res.string.update_installing),
                        )
                    }) else null,
                ) { Button(onClick = vm::applyUpdate) { Text(stringResource(Res.string.action_update)) } }
            }
        }

        SwitchRow(
            stringResource(Res.string.update_auto), prefs.autoUpdate || prefs.autoUpdateApp, vm::updateAutoCheck,
            stringResource(Res.string.update_auto_hint),
        )
    }
}

/** One updatable component on a single line; with [newVersion] it shows the jump and the [actions] to take it. */
@Composable
private fun UpdateRow(
    icon: ImageVector,
    title: String,
    version: String,
    newVersion: String?,
    subtitle: String? = null,
    hint: String? = null,
    busy: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (newVersion != null) scheme.primary else scheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.padding(end = 16.dp).size(24.dp), tint = accent)
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        newVersion != null -> stringResource(Res.string.update_version_change, version, newVersion)
                        subtitle != null -> subtitle
                        else -> stringResource(Res.string.update_version_current, version)
                    },
                    style = MaterialTheme.typography.bodySmall, color = accent,
                )
                if (newVersion != null && hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            if (newVersion != null && busy == null) actions()
        }
        if (newVersion != null) busy?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackupCard(vm: AppViewModel, prefs: AppPreferencesData) {
    val copy = rememberCopier()
    val readClipboard = rememberClipboardReader()
    val notify = LocalNotifier.current
    val scope = rememberCoroutineScope()
    val backupLabel = stringResource(Res.string.settings_backup)
    SectionCard(title = stringResource(Res.string.settings_backup), icon = Icons.Default.Backup) {
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
}

@Composable
private fun AboutCard() {
    SectionCard(title = "Turnable", icon = Icons.Default.Info) {
        Text(
            stringResource(Res.string.settings_app_description),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoRow(stringResource(Res.string.settings_version), APP_VERSION.asTag())
        InfoRow(stringResource(Res.string.settings_service_protocol), "v$SERVICE_PROTOCOL_VERSION")
        InfoRow(stringResource(Res.string.settings_user_agent), USER_AGENT, mono = true)
    }
}
