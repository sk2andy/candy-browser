package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal data class BlankTabColors(
    val regularBackground: Color,
    val incognitoBackground: Color,
    val regularHero: Color,
    val incognitoHero: Color,
    val incognitoContent: Color,
)

internal object BlankTabColorRules {
    fun resolve(colors: ColorScheme): BlankTabColors = if (colors.surface.luminance() < 0.5f) {
        // Dynamic accent and inverse roles can be light even in a dark color scheme.
        BlankTabColors(
            regularBackground = colors.surfaceContainerHigh,
            incognitoBackground = colors.surfaceContainerHighest,
            regularHero = colors.surfaceContainerHighest,
            incognitoHero = colors.surfaceContainerLow,
            incognitoContent = colors.onSurface,
        )
    } else {
        BlankTabColors(
            regularBackground = colors.primaryContainer,
            incognitoBackground = colors.inverseSurface,
            regularHero = colors.primary,
            incognitoHero = colors.inverseSurface,
            incognitoContent = colors.inverseOnSurface,
        )
    }
}
