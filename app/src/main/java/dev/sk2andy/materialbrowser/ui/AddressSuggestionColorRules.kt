package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal data class AddressSuggestionColors(
    val container: Color,
    val content: Color,
)

internal object AddressSuggestionColorRules {
    fun navigation(
        colors: ColorScheme,
        highlighted: Boolean,
        switchesToOpenTab: Boolean,
    ): AddressSuggestionColors {
        val dark = colors.surface.luminance() < 0.5f
        return when {
            highlighted && dark -> AddressSuggestionColors(
                container = colors.surfaceContainerHighest,
                content = colors.onSurface,
            )
            highlighted -> AddressSuggestionColors(
                container = colors.tertiaryContainer,
                content = colors.onTertiaryContainer,
            )
            switchesToOpenTab && dark -> AddressSuggestionColors(
                container = colors.surfaceContainerHigh,
                content = colors.onSurface,
            )
            switchesToOpenTab -> AddressSuggestionColors(
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
            )
            else -> AddressSuggestionColors(
                container = Color.Transparent,
                content = colors.onSurface,
            )
        }
    }
}
