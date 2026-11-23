package ru.theairblow.turnable.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.data.ThemeMode
import ru.theairblow.turnable.ui.components.LocalNotifier
import ru.theairblow.turnable.ui.screens.CaptchaScreen
import ru.theairblow.turnable.ui.screens.ClientsScreen
import ru.theairblow.turnable.ui.screens.ServersScreen
import androidx.compose.material3.Surface
import ru.theairblow.turnable.ui.screens.LogsScreen
import ru.theairblow.turnable.ui.util.PredictiveBack
import ru.theairblow.turnable.ui.util.paneTransition
import ru.theairblow.turnable.ui.screens.ProvidersScreen
import ru.theairblow.turnable.ui.screens.ServicesScreen
import ru.theairblow.turnable.ui.screens.SettingsScreen
import ru.theairblow.turnable.ui.theme.TurnableTheme
import ru.theairblow.turnable.viewmodel.AppViewModel
import turnable.composeapp.generated.resources.*

enum class AppTab(
    val label: StringResource, val icon: ImageVector, val selectedIcon: ImageVector,
    val advancedOnly: Boolean = false, val plainOnly: Boolean = false,
) {
    CLIENTS(Res.string.tab_clients, Icons.Outlined.SettingsEthernet, Icons.Filled.SettingsEthernet),
    SERVICES(Res.string.tab_services, Icons.Outlined.Hub, Icons.Filled.Hub, advancedOnly = true),
    SERVERS(Res.string.tab_servers, Icons.Outlined.Dns, Icons.Filled.Dns, advancedOnly = true),
    PROVIDERS(Res.string.tab_providers, Icons.Outlined.Inventory2, Icons.Filled.Inventory2, advancedOnly = true),
    LOGS(Res.string.tab_logs, Icons.AutoMirrored.Outlined.Article, Icons.AutoMirrored.Filled.Article, plainOnly = true),
    SETTINGS(Res.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
fun App(viewModel: AppViewModel) {
    val prefs by viewModel.prefsData.collectAsStateWithLifecycle()
    val dark = when (prefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val tabs = AppTab.entries.filter { if (prefs.advancedMode) !it.plainOnly else !it.advancedOnly }
    var selected by rememberSaveable { mutableStateOf(AppTab.CLIENTS) }
    // Turning advanced mode off while on one of its tabs falls back to the client list
    val tab = selected.takeIf { it in tabs } ?: AppTab.CLIENTS
    val captchaId by viewModel.captchaToSolve.collectAsStateWithLifecycle()
    val captchas by viewModel.captchas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = remember(snackbar) { { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } } }

    LaunchedEffect(viewModel) { viewModel.messages.collect(notify) }

    TurnableTheme(dark) {
        CompositionLocalProvider(LocalNotifier provides notify) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 720.dp
                val content: @Composable (Modifier) -> Unit = { modifier ->
                    AnimatedContent(tab, modifier, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                        TabContent(t, viewModel) { selected = if (prefs.advancedMode) AppTab.SERVICES else AppTab.SETTINGS }
                    }
                }
                if (wide) {
                    // Lifted so snackbars don't cover the panes' floating action buttons
                    Scaffold(snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 88.dp)) }) { padding ->
                        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                            NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Spacer(Modifier.height(12.dp))
                                tabs.forEach { t ->
                                    NavigationRailItem(
                                        selected = tab == t, onClick = { selected = t },
                                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                                        label = { Text(stringResource(t.label)) },
                                    )
                                }
                            }
                            content(Modifier.weight(1f))
                        }
                    }
                } else {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbar) },
                        bottomBar = {
                            NavigationBar {
                                tabs.forEach { t ->
                                    NavigationBarItem(
                                        selected = tab == t, onClick = { selected = t },
                                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                                        label = { Text(stringResource(t.label), maxLines = 1) },
                                    )
                                }
                            }
                        },
                        contentWindowInsets = WindowInsets(0),
                    ) { padding ->
                        Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content(Modifier.fillMaxSize()) }
                    }
                }
                AnimatedContent(
                    captchas.firstOrNull { it.instanceId == captchaId },
                    Modifier.fillMaxSize(),
                    transitionSpec = { paneTransition(initialState != null, targetState != null) },
                    label = "captcha",
                ) { request ->
                    if (request != null) PredictiveBack(onBack = viewModel::closeCaptcha, modifier = Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                            CaptchaScreen(request, onSolved = { viewModel.captchaSolved(request.instanceId) }, onClose = viewModel::closeCaptcha)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabContent(tab: AppTab, vm: AppViewModel, openServices: () -> Unit) = when (tab) {
    AppTab.SERVICES -> ServicesScreen(vm)
    AppTab.SERVERS -> ServersScreen(vm, openServices)
    AppTab.CLIENTS -> ClientsScreen(vm, openServices)
    AppTab.PROVIDERS -> ProvidersScreen(vm, openServices)
    AppTab.LOGS -> LogsScreen(vm)
    AppTab.SETTINGS -> SettingsScreen(vm)
}
