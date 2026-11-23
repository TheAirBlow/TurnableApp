package ru.theairblow.turnable.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

private const val MIN_SCALE = 0.9f

/** Runs [onBack] on the back gesture; while the user swipes, [content] shrinks and slides toward the swipe like the system's own preview. */
@Composable
fun PredictiveBack(
    enabled: Boolean = true, onBack: () -> Unit, modifier: Modifier = Modifier,
    /** Swipe progress 0..1; it stays where the swipe ended when the back is committed. */
    onProgress: (Float) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var swiping by remember { mutableStateOf(false) }
    PlatformBackHandler(enabled, onProgress = { progress = it; swiping = it != 0f; onProgress(abs(it)) }, onBack = onBack)
    val shown by animateFloatAsState(progress, if (swiping) snap() else spring(stiffness = Spring.StiffnessMediumLow), label = "predictiveBack")
    Box(
        modifier.graphicsLayer {
            val p = abs(shown)
            scaleX = 1f - (1f - MIN_SCALE) * p
            scaleY = scaleX
            translationX = sign(shown) * p * 12.dp.toPx()
            if (p > 0f) {
                shape = RoundedCornerShape((32 * p).dp)
                clip = true
            }
        },
    ) { content() }
}
