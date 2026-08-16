package com.opencover.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = CivilCyan,
    onPrimary = Anthracite,
    secondary = InfiltrateRed,
    onSecondary = TextPrimary,
    tertiary = MrWhite,
    onTertiary = Anthracite,
    background = Anthracite,
    onBackground = TextPrimary,
    surface = AnthraciteSurface,
    onSurface = TextPrimary,
    surfaceVariant = AnthraciteSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = InfiltrateRed,
    onError = TextPrimary,
    outline = TextSecondary
)

/**
 * Thème OpenCover : Material Design 3, mode sombre strict.
 * Aucune variante claire : on force toujours le schéma sombre (cahier des charges §6).
 */
@Composable
fun OpenCoverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
