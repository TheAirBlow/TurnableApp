package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.ui.util.PredictiveBack
import ru.theairblow.turnable.data.DEFAULT_SERVICE_ID
import ru.theairblow.turnable.ui.components.EmptyState
import ru.theairblow.turnable.ui.components.PaneTopBar
import ru.theairblow.turnable.ui.components.rememberCopier
import ru.theairblow.turnable.ui.theme.MonoFamily
import ru.theairblow.turnable.ui.theme.TurnableTheme
import ru.theairblow.turnable.ui.util.formatClock
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.LogEntry
import turnable.composeapp.generated.resources.*

/** slog levels: debug -4, info 0, warn 4, error 8. */
private enum class LogLevel(val min: Int) {
    DEBUG(Int.MIN_VALUE),
    INFO(0),
    WARN(4),
    ERROR(8);

    companion object {
        fun of(level: Int) = entries.last { level >= it.min }
    }
}

private fun LogEntry.toPlainLine(): String =
    "${formatClock(timeNs)} ${LogLevel.of(level)} $message" +
        attrs.joinToString("") { " ${it.key}=${it.value}" }

/**
 * Live service logs. With [instanceId] only lines tagged with that instance (server_id / client_id) are shown.
 * The DEBUG+ filter is offered once debug logging is on ([verbose]) or debug lines have come in.
 */
@Composable
fun LogsPane(
    vm: AppViewModel, title: String, onBack: (() -> Unit)? = null, instanceId: String? = null,
    verbose: Boolean? = null,
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(LogLevel.INFO) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val copy = rememberCopier()
    val hasDebug = verbose == true || logs.any { it.level < LogLevel.INFO.min }
    val levels = LogLevel.entries.filter { hasDebug || it != LogLevel.DEBUG }
    val minLevel = if (picked in levels) picked else LogLevel.INFO
    val copyLabel = stringResource(Res.string.logs_copy_label)

    val shown = remember(logs, query, minLevel, instanceId) {
        logs.filter { e ->
            e.level >= minLevel.min &&
                (instanceId == null || e.attrs.any { (it.key == "server_id" || it.key == "client_id") && it.value == instanceId }) &&
                (query.isBlank() || e.message.contains(query, true) || e.attrs.any { it.value.contains(query, true) })
        }
    }
    // Pinned to the bottom until the user scrolls away; new lines must not unpin it
    var atBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { !it }.collect { atBottom = !listState.canScrollForward }
    }
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty() && atBottom) listState.scrollToItem(shown.lastIndex)
    }

    PredictiveBack(enabled = onBack != null, onBack = { onBack?.invoke() }, modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            PaneTopBar(title, stringResource(Res.string.logs_lines, shown.size, logs.size), onBack = onBack) {
                IconButton(onClick = {
                    copy(shown.joinToString("\n") { it.toPlainLine() }, copyLabel)
                }, enabled = shown.isNotEmpty()) {
                    Icon(Icons.Default.ContentCopy, stringResource(Res.string.logs_copy_shown))
                }
                IconButton(onClick = vm::clearLogs, enabled = logs.isNotEmpty()) {
                    Icon(Icons.Default.DeleteSweep, stringResource(Res.string.logs_clear))
                }
            }
        },
        floatingActionButton = {
            if (!atBottom && shown.isNotEmpty()) SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(shown.lastIndex) } }) {
                Icon(Icons.Default.KeyboardDoubleArrowDown, stringResource(Res.string.logs_jump_latest))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxWidth().height(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text(
                                stringResource(Res.string.logs_search_hint), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            inner()
                        }
                        if (query.isNotEmpty()) Icon(
                            Icons.Default.Clear, stringResource(Res.string.logs_clear_search),
                            Modifier.size(18.dp).clickable { query = "" }, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                levels.forEach { level ->
                    FilterChip(
                        selected = minLevel == level, onClick = { picked = level },
                        label = { Text("$level+") },
                    )
                }
            }
            val colors = TurnableTheme.status
            Box(
                Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
                    .background(colors.logBackground, MaterialTheme.shapes.large),
            ) {
                if (shown.isEmpty()) {
                    EmptyState(
                        Icons.AutoMirrored.Filled.Article,
                        stringResource(if (logs.isEmpty()) Res.string.logs_waiting else Res.string.logs_nothing_matches),
                    )
                } else {
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(12.dp)) {
                            items(shown, key = { it.id }) { LogLine(it) }
                        }
                    }
                }
            }
        }
    }
    }
}

/** The on-device service's logs as a tab of their own, for simple mode. */
@Composable
fun LogsScreen(vm: AppViewModel) {
    val prefs by vm.prefsData.collectAsStateWithLifecycle()
    val local = prefs.services.firstOrNull { it.id == DEFAULT_SERVICE_ID }
    LogsPane(
        vm, title = stringResource(Res.string.services_service_logs),
        verbose = local?.verbose,
    )
}

@Composable
private fun LogLine(entry: LogEntry) {
    val c = TurnableTheme.status
    val level = LogLevel.of(entry.level)
    val levelColor = when (level) {
        LogLevel.DEBUG -> c.logDebug
        LogLevel.INFO -> c.logInfo
        LogLevel.WARN -> c.logWarn
        LogLevel.ERROR -> c.logError
    }
    val text = remember(entry, levelColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = c.logMuted)) { append(formatClock(entry.timeNs)); append("  ") }
            withStyle(SpanStyle(color = levelColor)) { append(level.name.padEnd(5)); append(" ") }
            withStyle(SpanStyle(color = c.logText)) { append(entry.message) }
            entry.attrs.forEach { a ->
                withStyle(SpanStyle(color = c.logMuted)) { append("  ${a.key}=") }
                withStyle(SpanStyle(color = c.logText.copy(alpha = 0.8f))) { append(a.value) }
            }
        }
    }
    Text(text, fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
}
