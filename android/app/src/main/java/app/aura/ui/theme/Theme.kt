package app.aura.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// AURA Color System
val AuraCyan = Color(0xFF00D4FF)
val AuraViolet = Color(0xFF7C3AED)
val AuraVioletLight = Color(0xFF9D5FF0)
val AuraError = Color(0xFFFF4444)
val AuraErrorDark = Color(0xFFB3261E)
val AuraSuccess = Color(0xFF22C55E)
val AuraWarning = Color(0xFFF59E0B)

/** Brand color bundle every screen reads instead of hardcoded constants. */
data class AuraPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
)

private val DarkPalette = AuraPalette(
    background = Color(0xFF0D0D0F),
    surface = Color(0xFF111114),
    surfaceVariant = Color(0xFF1A1A1F),
    onBackground = Color(0xFFE8E8EC),
    onSurface = Color(0xFFC4C4CC),
    onSurfaceMuted = Color(0xFF8A8A96),
)

private val LightPalette = AuraPalette(
    background = Color(0xFFF8F9FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF0F4),
    onBackground = Color(0xFF1A1C20),
    onSurface = Color(0xFF2A2D33),
    onSurfaceMuted = Color(0xFF5C6068),
)

val LocalAuraPalette = staticCompositionLocalOf { DarkPalette }

private val DarkColorScheme = darkColorScheme(
    primary = AuraCyan,
    onPrimary = Color(0xFF001F2A),
    primaryContainer = Color(0xFF003544),
    onPrimaryContainer = AuraCyan,
    secondary = AuraViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2D1B69),
    onSecondaryContainer = AuraVioletLight,
    tertiary = AuraVioletLight,
    background = DarkPalette.background,
    onBackground = DarkPalette.onBackground,
    surface = DarkPalette.surface,
    onSurface = DarkPalette.onSurface,
    surfaceVariant = DarkPalette.surfaceVariant,
    onSurfaceVariant = DarkPalette.onSurfaceMuted,
    error = AuraError,
    onError = Color(0xFF410002),
    outline = Color(0xFF3A3A44),
    outlineVariant = Color(0xFF2A2A32),
)

// Light variant reusing the brand colors on bright surfaces.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0092C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2F1FF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = AuraViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDCFF),
    onSecondaryContainer = Color(0xFF2D14A8),
    tertiary = Color(0xFF6150E0),
    background = LightPalette.background,
    onBackground = LightPalette.onBackground,
    surface = LightPalette.surface,
    onSurface = LightPalette.onSurface,
    surfaceVariant = LightPalette.surfaceVariant,
    onSurfaceVariant = LightPalette.onSurfaceMuted,
    error = AuraErrorDark,
    onError = Color.White,
    outline = Color(0xFFC3C7CE),
    outlineVariant = Color(0xFFE0E3E9),
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalAuraPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AuraTypography,
            content = content
        )
    }
}
