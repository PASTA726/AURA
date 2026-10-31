package app.aura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AURA Color System
val AuraCyan = Color(0xFF00D4FF)
val AuraViolet = Color(0xFF7C3AED)
val AuraVioletLight = Color(0xFF9D5FF0)
val AuraBackground = Color(0xFF0D0D0F)
val AuraSurface = Color(0xFF111114)
val AuraSurfaceVariant = Color(0xFF1A1A1F)
val AuraSurfaceHigh = Color(0xFF222228)
val AuraOnBackground = Color(0xFFE8E8EC)
val AuraOnSurface = Color(0xFFC4C4CC)
val AuraOnSurfaceMuted = Color(0xFF8A8A96)
val AuraError = Color(0xFFFF4444)
val AuraSuccess = Color(0xFF22C55E)
val AuraWarning = Color(0xFFF59E0B)

private val DarkColorScheme = darkColorScheme(
    primary = AuraCyan,
    onPrimary = Color(0xFF001F2A),
    primaryContainer = Color(0xFF003544),
    onPrimaryContainer = AuraCyan,
    secondary = AuraViolet,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2D1B69),
    onSecondaryContainer = AuraVioletLight,
    tertiary = AuraVioletLight,
    background = AuraBackground,
    onBackground = AuraOnBackground,
    surface = AuraSurface,
    onSurface = AuraOnSurface,
    surfaceVariant = AuraSurfaceVariant,
    onSurfaceVariant = AuraOnSurfaceMuted,
    error = AuraError,
    onError = Color(0xFF410002),
    outline = Color(0xFF3A3A44),
    outlineVariant = Color(0xFF2A2A32),
)

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AuraTypography,
        content = content
    )
}
