package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

internal object TabOverviewContrastRules {
    fun titleContentColor(
        primaryContainer: Color,
        tertiaryContainer: Color,
    ): Color = highestContrastColor(
        background = lerp(primaryContainer, tertiaryContainer, TITLE_BAND_BLEND_FRACTION),
    )

    fun highestContrastColor(background: Color): Color =
        if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
            Color.Black
        } else {
            Color.White
        }

    fun contrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        return (max(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05f)
    }

    private const val TITLE_BAND_BLEND_FRACTION = 0.5f
}
