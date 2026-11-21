package ru.theairblow.turnable.ui.util

import androidx.compose.runtime.Composable

/** Intercepts the system back gesture where the platform has one. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
