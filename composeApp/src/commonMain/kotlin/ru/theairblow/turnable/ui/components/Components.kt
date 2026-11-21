package ru.theairblow.turnable.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.theairblow.turnable.ui.theme.MonoFamily
import ru.theairblow.turnable.ui.theme.TurnableTheme
import ru.theairblow.turnable.ui.util.PlatformBackHandler

/** Shows a transient message (snackbar). Provided by the app root. */
val LocalNotifier = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Returns a function that copies text and confirms with a snackbar. */
@Suppress("DEPRECATION")
@Composable
fun rememberCopier(): (text: String, what: String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val notify = LocalNotifier.current
    return remember(clipboard, notify) {
        { text, what -> clipboard.setText(AnnotatedString(text)); notify("$what copied") }
    }
}

@Suppress("DEPRECATION")
@Composable
fun rememberClipboardReader(): () -> String {
    val clipboard = LocalClipboardManager.current
    return remember(clipboard) { { clipboard.getText()?.text.orEmpty() } }
}

// ─── Status ───────────────────────────────────────────────────────────────────

enum class StatusKind { Online, Pending, Failed, Offline }

@Composable
fun StatusKind.color(): Color = when (this) {
    StatusKind.Online -> TurnableTheme.status.online
    StatusKind.Pending -> TurnableTheme.status.pending
    StatusKind.Failed -> MaterialTheme.colorScheme.error
    StatusKind.Offline -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StatusKind.containerColor(): Color = when (this) {
    StatusKind.Online -> TurnableTheme.status.onlineContainer
    StatusKind.Pending -> TurnableTheme.status.pendingContainer
    StatusKind.Failed -> MaterialTheme.colorScheme.errorContainer
    StatusKind.Offline -> MaterialTheme.colorScheme.surfaceContainerHighest
}

/** A colored dot that pulses while [kind] is pending. */
@Composable
fun StatusDot(kind: StatusKind, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val alpha = if (kind == StatusKind.Pending) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 1f, targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulseAlpha",
        ).value
    } else 1f
    Box(modifier.size(size).alpha(alpha).clip(CircleShape).background(kind.color()))
}

@Composable
fun StatusBadge(kind: StatusKind, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(kind.containerColor()).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(kind, size = 8.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = kind.color(), fontWeight = FontWeight.SemiBold)
    }
}

// ─── Layout ───────────────────────────────────────────────────────────────────

/** Top app bar used by every pane, with an optional back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaneTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    busy: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(
                        subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = 0.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/**
 * List/detail layout: side by side when wide, a single animated pane with back handling when narrow.
 * [detail] is shown when [detailKey] is non-null; [placeholder] fills the empty detail pane on wide screens.
 */
@Composable
fun <K : Any> AdaptiveListDetail(
    detailKey: K?,
    onCloseDetail: () -> Unit,
    list: @Composable () -> Unit,
    placeholder: @Composable () -> Unit,
    detail: @Composable (K) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.widthIn(max = 420.dp).weight(0.42f).fillMaxHeight()) { list() }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    AnimatedContent(detailKey, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "detail") { key ->
                        if (key == null) placeholder() else detail(key)
                    }
                }
            }
        } else {
            PlatformBackHandler(enabled = detailKey != null, onBack = onCloseDetail)
            // The list stays composed under the detail so it keeps its scroll position
            Box(Modifier.fillMaxSize()) {
                list()
                AnimatedContent(
                    detailKey,
                    transitionSpec = {
                        val opening = targetState != null
                        val transition = if (opening) {
                            (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith fadeOut(tween(200))
                        } else {
                            fadeIn(tween(1)) togetherWith (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(220)))
                        }
                        // The incoming detail draws above the list; the outgoing one stays above while it slides away
                        transition.apply { targetContentZIndex = if (opening) 1f else 0f }
                    },
                    label = "listDetail",
                ) { key ->
                    if (key != null) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { detail(key) }
                }
            }
        }
    }
}

/** A rounded, tonal container with an optional header row. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    action: @Composable RowScope.() -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    action()
                }
            }
            content()
        }
    }
}

/** A tappable row card with leading visual, title/subtitle and trailing content. */
@Composable
fun ListCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
    }
}

/** A tinted rounded square holding an icon; used as the leading visual of list cards. */
@Composable
fun IconTile(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, container: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
    Box(Modifier.size(42.dp).clip(MaterialTheme.shapes.medium).background(container), Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), Alignment.Center) {
                Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (body != null) Text(
                body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.size(4.dp))
                action()
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            if (onDismiss != null) IconButton(onClick = onDismiss, Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Dismiss", Modifier.size(18.dp)) }
        }
    }
}

/** Label/value row; long-press-free copy button when [copyable]. */
@Composable
fun InfoRow(label: String, value: String, mono: Boolean = false, copyable: Boolean = false) {
    val copy = rememberCopier()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.38f))
        Text(
            value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (mono) MonoFamily else null,
            modifier = Modifier.weight(0.62f), maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (copyable) IconButton(onClick = { copy(value, label) }, Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, "Copy $label", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A compact metric tile: big value, small label. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = { onDismiss(); onConfirm() },
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError,
                ) else ButtonDefaults.buttonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── Form fields ──────────────────────────────────────────────────────────────

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supporting: String? = null,
    isError: Boolean = false,
    numeric: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        textStyle = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily) else MaterialTheme.typography.bodyLarge,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        trailingIcon = trailing,
        shape = MaterialTheme.shapes.medium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    value: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    describe: (T) -> String = { it.toString() },
    hint: (T) -> String? = { null },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = describe(value), onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            supportingText = hint(value)?.let { { Text(it) } },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(describe(opt)) },
                    onClick = { onSelect(opt); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Small uppercase heading inside forms. */
@Composable
fun FormSection(title: String) {
    Text(
        title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}
