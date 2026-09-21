package dev.sk2andy.materialbrowser.browser

import kotlin.math.pow

internal data class WebContentStatusBarAppearance(
    val colorArgb: Int,
    val useDarkIcons: Boolean,
)

internal data class WebContentTopBarState(
    val statusBarAppearance: WebContentStatusBarAppearance?,
)

internal object WebContentStatusBarAppearanceRules {
    fun fromReportedColor(reportedColor: String?): WebContentStatusBarAppearance? {
        val color = reportedColor
            ?.takeIf { value -> value.length == NORMALIZED_HEX_COLOR_LENGTH }
            ?.takeIf { value -> HEX_COLOR.matches(value) }
            ?.removePrefix("#")
            ?.toLongOrNull(radix = 16)
            ?: return null
        val colorArgb = (OPAQUE_ALPHA or color).toInt()
        return WebContentStatusBarAppearance(
            colorArgb = colorArgb,
            useDarkIcons = relativeLuminance(colorArgb) > LIGHT_BACKGROUND_LUMINANCE,
        )
    }

    private fun relativeLuminance(colorArgb: Int): Double {
        fun linearChannel(shift: Int): Double {
            val channel = ((colorArgb ushr shift) and 0xFF) / 255.0
            return if (channel <= 0.04045) {
                channel / 12.92
            } else {
                ((channel + 0.055) / 1.055).pow(2.4)
            }
        }

        return 0.2126 * linearChannel(16) +
            0.7152 * linearChannel(8) +
            0.0722 * linearChannel(0)
    }

    private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")
    private const val OPAQUE_ALPHA = 0xFF000000L
    private const val LIGHT_BACKGROUND_LUMINANCE = 0.5
    private const val NORMALIZED_HEX_COLOR_LENGTH = 7
}
