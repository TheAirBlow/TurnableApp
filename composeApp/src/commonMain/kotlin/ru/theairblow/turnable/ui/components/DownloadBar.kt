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
import ru.theairblow.turnable.service.DownloadProgress

/** Progress bar with the release name and transferred size. */
@Composable
fun DownloadBar(progress: DownloadProgress, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val fraction = progress.fraction
        if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Downloading ${progress.version}", style = MaterialTheme.typography.bodySmall)
            Text(
                if (progress.total > 0) "${formatMb(progress.bytes)} of ${formatMb(progress.total)}" else formatMb(progress.bytes),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatMb(bytes: Long): String {
    val tenths = bytes * 10 / (1024 * 1024)
    return "${tenths / 10}.${tenths % 10} MB"
}
