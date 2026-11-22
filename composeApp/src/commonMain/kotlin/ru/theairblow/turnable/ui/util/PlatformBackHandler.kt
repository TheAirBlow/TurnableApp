package ru.theairblow.turnable.ui.util

import androidx.compose.runtime.Composable

/**
 * Intercepts the system back gesture where the platform has one.
 * [onProgress] follows a swipe in progress: 0..1 from the left edge, 0..-1 from the right, 0 when it is cancelled.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onProgress: (Float) -> Unit = {}, onBack: () -> Unit)
