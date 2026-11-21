package ru.theairblow.turnable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.platform.CoreUpdate
import ru.theairblow.turnable.platform.appSelfUpdate
import ru.theairblow.turnable.service.AppUpdate
import ru.theairblow.turnable.service.DownloadProgress
import turnable.composeapp.generated.resources.*

/** A round glyph for an update; filled with the accent color when something is waiting to be installed. */
@Composable
fun UpdateGlyph(icon: ImageVector, accent: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(40.dp).clip(CircleShape).background(if (accent) scheme.primary else scheme.surfaceContainerHighest),
        Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (accent) scheme.onPrimary else scheme.onSurfaceVariant)
    }
}

/** Prominent card offering an update; [busy] replaces [actions] while the update is being installed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateBanner(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    busy: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                UpdateGlyph(icon, accent = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = tint)
                }
            }
            if (busy != null) busy() else FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { actions() }
        }
    }
}

/** The Update button for an app release: installs it where the app can, otherwise opens its release page. */
@Composable
fun AppUpdateActions(update: AppUpdate, onInstall: () -> Unit, onLater: (() -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    if (onLater != null) TextButton(onClick = onLater) { Text(stringResource(Res.string.action_later)) }
    Button(onClick = { if (update.hasInstaller && appSelfUpdate) onInstall() else uriHandler.openUri(update.url) }) {
        Text(stringResource(Res.string.action_update))
    }
}

@Composable
fun AppUpdateBanner(
    update: AppUpdate,
    installing: Boolean,
    progress: DownloadProgress?,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) = UpdateBanner(
    Icons.Default.PhoneAndroid,
    stringResource(Res.string.update_app_title),
    stringResource(Res.string.update_app_body, update.version),
    modifier,
    busy = if (installing) ({ DownloadBar(progress, stringResource(Res.string.update_downloading_installer)) }) else null,
) { AppUpdateActions(update, onInstall, onLater) }

/** Offers a newer core release; [appOutdated] tells the user to update the app first. */
@Composable
fun CoreUpdateBanner(
    update: CoreUpdate,
    updating: Boolean,
    download: DownloadProgress?,
    appOutdated: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) = UpdateBanner(
    Icons.Default.Hub,
    stringResource(Res.string.update_core_title),
    if (appOutdated) stringResource(Res.string.update_app_first)
    else stringResource(Res.string.update_core_body, update.version),
    modifier,
    busy = if (updating) ({
        DownloadBar(
            download,
            if (download != null) stringResource(Res.string.download_version, download.version) else stringResource(Res.string.update_installing),
        )
    }) else null,
) {
    TextButton(onClick = onLater) { Text(stringResource(Res.string.action_later)) }
    Button(onClick = onUpdate) { Text(stringResource(Res.string.action_update)) }
}
