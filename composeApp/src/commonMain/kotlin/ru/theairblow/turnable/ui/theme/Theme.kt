package ru.theairblow.turnable.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4353D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF0B1470),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF006A62),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF9EF2E6),
    onTertiaryContainer = Color(0xFF00201D),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF1A1B23),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF1A1B23),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F3FB),
    surfaceContainer = Color(0xFFECEDF6),
    surfaceContainerHigh = Color(0xFFE6E7F0),
    surfaceContainerHighest = Color(0xFFE1E2EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    onPrimary = Color(0xFF16218F),
    primaryContainer = Color(0xFF2E3CBE),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFF82D5CA),
    onTertiary = Color(0xFF003732),
    tertiaryContainer = Color(0xFF005049),
    onTertiaryContainer = Color(0xFF9EF2E6),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1218),
    onBackground = Color(0xFFE3E2EC),
    surface = Color(0xFF0F1218),
    onSurface = Color(0xFFE3E2EC),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF171A21),
    surfaceContainer = Color(0xFF1B1E26),
    surfaceContainerHigh = Color(0xFF252830),
    surfaceContainerHighest = Color(0xFF30333B),
)

/** Semantic colors Material doesn't provide. */
@Immutable
data class StatusColors(
    val online: Color,
    val onlineContainer: Color,
    val pending: Color,
    val pendingContainer: Color,
    val warning: Color,
    val logBackground: Color,
    val logText: Color,
    val logMuted: Color,
    val logDebug: Color,
    val logInfo: Color,
    val logWarn: Color,
    val logError: Color,
)

private val LightStatus = StatusColors(
    online = Color(0xFF1B8A4B), onlineContainer = Color(0xFFD3F5DF),
    pending = Color(0xFFB26A00), pendingContainer = Color(0xFFFFE8C7),
    warning = Color(0xFF9A6700),
    logBackground = Color(0xFF14161D), logText = Color(0xFFE1E3EC), logMuted = Color(0xFF7D8191),
    logDebug = Color(0xFF8E93A6), logInfo = Color(0xFF6FD3A3), logWarn = Color(0xFFF5C26B), logError = Color(0xFFFF8A80),
)

private val DarkStatus = LightStatus.copy(
    online = Color(0xFF6FD3A3), onlineContainer = Color(0xFF0E3B25),
    pending = Color(0xFFF5C26B), pendingContainer = Color(0xFF3F2C07), warning = Color(0xFFF5C26B),
    logBackground = Color(0xFF0A0C11),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun appTypography(): Typography {
    val base = Typography()
    return base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

val MonoFamily = FontFamily.Monospace

@Composable
fun TurnableTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, shapes = AppShapes, typography = appTypography(), content = content)
    }
}

object TurnableTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
