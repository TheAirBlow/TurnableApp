package ru.theairblow.turnable.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.service.DownloadProgress
import ru.theairblow.turnable.ui.util.formatBytes
import turnable.composeapp.generated.resources.*

/** Progress bar with a [label] and the transferred size; indeterminate while [progress] is null or has no total. */
@Composable
fun DownloadBar(progress: DownloadProgress?, label: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val fraction = progress?.fraction
        if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (progress != null && progress.bytes > 0) Text(
                if (progress.total > 0) stringResource(Res.string.download_of, formatBytes(progress.bytes), formatBytes(progress.total))
                else formatBytes(progress.bytes),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
