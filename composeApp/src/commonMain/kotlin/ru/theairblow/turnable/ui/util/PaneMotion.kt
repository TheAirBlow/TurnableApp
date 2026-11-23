package ru.theairblow.turnable.ui.util

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

const val PANE_OPEN_MS = 220
const val PANE_CLOSE_MS = 180

/** Start delay that lets the first, heavy composition of a new pane finish off-screen so the slide does not skip frames. */
private const val PANE_OPEN_DELAY_MS = 70

/** Closing speeds up as the pane leaves, so it carries on from a back swipe instead of starting slow. */
private val CloseEasing: Easing = FastOutLinearInEasing

/** What a pane covers moves this fraction of the width while it is covered. */
const val PANE_PARALLAX = 0.25f

/** The one open/close motion for stacked panes: a pane slides over from the right and slides back out when closed. */
fun paneTransition(hadPane: Boolean, hasPane: Boolean): ContentTransform {
    val (enter, exit) = when {
        !hadPane && hasPane -> slideInHorizontally(tween(PANE_OPEN_MS, PANE_OPEN_DELAY_MS, FastOutSlowInEasing)) { it } to ExitTransition.None
        hadPane && !hasPane -> EnterTransition.None to slideOutHorizontally(tween(PANE_CLOSE_MS, easing = CloseEasing)) { it }
        else -> fadeIn(tween(150)) to fadeOut(tween(150))
    }
    // No size animation: it would clip a closing pane down to the empty target's size
    return ContentTransform(enter, exit, sizeTransform = null)
}

/**
 * Shifts a layer left while another pane [covered] it. [backProgress] (0..1) is the progress of a back swipe on the pane
 * above, which brings this layer back to rest as the swipe goes on.
 */
@Composable
fun Modifier.paneParallax(covered: Boolean, backProgress: () -> Float): Modifier {
    val shift by animateFloatAsState(
        if (covered) 1f else 0f, if (covered) tween(PANE_OPEN_MS, PANE_OPEN_DELAY_MS, FastOutSlowInEasing) else tween(PANE_CLOSE_MS, easing = CloseEasing), label = "paneParallax",
    )
    return graphicsLayer { translationX = -size.width * PANE_PARALLAX * shift * (1f - backProgress()) }
}
