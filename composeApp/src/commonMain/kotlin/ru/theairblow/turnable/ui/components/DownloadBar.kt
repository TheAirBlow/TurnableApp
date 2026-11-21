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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.theairblow.turnable.service.DownloadProgress
import turnable.composeapp.generated.resources.*

/** Progress bar with the release name and transferred size. */
@Composable
fun DownloadBar(progress: DownloadProgress, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val fraction = progress.fraction
        if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.download_version, progress.version), style = MaterialTheme.typography.bodySmall)
            Text(
                if (progress.total > 0) stringResource(Res.string.download_of, formatMb(progress.bytes), formatMb(progress.total))
                else formatMb(progress.bytes),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun formatMb(bytes: Long): String {
    val tenths = bytes * 10 / (1024 * 1024)
    return stringResource(Res.string.unit_mb, "${tenths / 10}.${tenths % 10}")
}
