package ru.theairblow.turnable.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.theairblow.turnable.ui.util.PlatformBackHandler
import ru.theairblow.turnable.ui.components.EmptyState
import ru.theairblow.turnable.ui.components.PaneTopBar
import ru.theairblow.turnable.ui.components.rememberCopier
import ru.theairblow.turnable.ui.theme.MonoFamily
import ru.theairblow.turnable.ui.theme.TurnableTheme
import ru.theairblow.turnable.ui.util.formatClock
import ru.theairblow.turnable.viewmodel.AppViewModel
import ru.theairblow.turnable.viewmodel.LogEntry

/** slog levels: debug -4, info 0, warn 4, error 8. */
private enum class LogLevel(val label: String, val min: Int) {
    DEBUG("Debug", Int.MIN_VALUE), INFO("Info", 0), WARN("Warn", 4), ERROR("Error", 8);

    companion object {
        fun of(level: Int) = entries.last { level >= it.min }
    }
}

private fun LogEntry.toPlainLine(): String =
    "${formatClock(timeNs)} ${LogLevel.of(level).label.uppercase()} $message" +
        attrs.joinToString("") { " ${it.key}=${it.value}" }

/**
 * Live service logs. With [instanceId] only lines tagged with that instance (server_id / client_id) are shown.
 */
@Composable
fun LogsPane(vm: AppViewModel, title: String, onBack: () -> Unit, instanceId: String? = null) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var minLevel by remember { mutableStateOf(LogLevel.DEBUG) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val copy = rememberCopier()
    PlatformBackHandler(onBack = onBack)

    val shown = remember(logs, query, minLevel, instanceId) {
        logs.filter { e ->
            e.level >= minLevel.min &&
                (instanceId == null || e.attrs.any { (it.key == "server_id" || it.key == "client_id") && it.value == instanceId }) &&
                (query.isBlank() || e.message.contains(query, true) || e.attrs.any { it.value.contains(query, true) })
        }
    }
    // Follow new lines only while the user is already at the bottom
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty() && atBottom) listState.scrollToItem(shown.lastIndex)
    }

    Scaffold(
        topBar = {
            PaneTopBar(title, "${shown.size} of ${logs.size} lines", onBack = onBack) {
                IconButton(onClick = { copy(shown.joinToString("\n") { it.toPlainLine() }, "Logs") }, enabled = shown.isNotEmpty()) {
                    Icon(Icons.Default.ContentCopy, "Copy shown lines")
                }
                IconButton(onClick = vm::clearLogs, enabled = logs.isNotEmpty()) { Icon(Icons.Default.DeleteSweep, "Clear") }
            }
        },
        floatingActionButton = {
            if (!atBottom && shown.isNotEmpty()) SmallFloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(shown.lastIndex) } }) {
                Icon(Icons.Default.KeyboardDoubleArrowDown, "Jump to latest")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search messages and attributes") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear search") } },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = minLevel == level, onClick = { minLevel = level },
                        label = { Text(if (level == LogLevel.DEBUG) "All" else "${level.label}+") },
                    )
                }
            }
            val colors = TurnableTheme.status
            Box(
                Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
                    .background(colors.logBackground, MaterialTheme.shapes.large),
            ) {
                if (shown.isEmpty()) {
                    EmptyState(Icons.AutoMirrored.Filled.Article, if (logs.isEmpty()) "Waiting for logs" else "Nothing matches")
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
    val text = remember(entry) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = c.logMuted)) { append(formatClock(entry.timeNs)); append("  ") }
            withStyle(SpanStyle(color = levelColor)) { append(level.label.uppercase().padEnd(5)); append(" ") }
            withStyle(SpanStyle(color = c.logText)) { append(entry.message) }
            entry.attrs.forEach { a ->
                withStyle(SpanStyle(color = c.logMuted)) { append("  ${a.key}=") }
                withStyle(SpanStyle(color = c.logText.copy(alpha = 0.8f))) { append(a.value) }
            }
        }
    }
    Text(text, fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
}
