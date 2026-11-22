package ru.theairblow.turnable.ui.util

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onProgress: (Float) -> Unit, onBack: () -> Unit) = PredictiveBackHandler(enabled) { events ->
    try {
        events.collect { onProgress(if (it.swipeEdge == BackEventCompat.EDGE_RIGHT) -it.progress else it.progress) }
    } catch (e: CancellationException) {
        onProgress(0f)
        throw e
    }
    onBack()
}
