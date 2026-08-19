package com.lemytho.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = CitizenCyan,
    onPrimary = Anthracite,
    secondary = ImpostorRed,
    onSecondary = TextPrimary,
    tertiary = UnknownWhite,
    onTertiary = Anthracite,
    background = Anthracite,
    onBackground = TextPrimary,
    surface = AnthraciteSurface,
    onSurface = TextPrimary,
    surfaceVariant = AnthraciteSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ImpostorRed,
    onError = TextPrimary,
    outline = TextSecondary
)

/**
 * Thème Le Mytho : Material Design 3, mode sombre strict.
 * Aucune variante claire : on force toujours le schéma sombre (cahier des charges §6).
 */
@Composable
fun LeMythoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
