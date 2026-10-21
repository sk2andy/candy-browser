package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

enum class SettingsHeaderArtwork {
    Appearance, Tabs, Player, SearchEngines, Translations, Downloads, Profiles, Gestures, LinkPreview,
    TabLifecycle, PersonalLinkRules, ExternalApps,
}

/** Source windows refer to the original, unmodified imagegen PNGs, in image pixels. */
internal object SettingsHeaderArtworkRules {
    fun sourceWindow(artwork: SettingsHeaderArtwork): IntRect = when (artwork) {
        SettingsHeaderArtwork.Appearance -> IntRect(160, 80, 860, 610)
        SettingsHeaderArtwork.Tabs -> IntRect(160, 615, 860, 1060)
        SettingsHeaderArtwork.Player -> IntRect(160, 245, 1376, 780)
        SettingsHeaderArtwork.SearchEngines -> IntRect(160, 250, 1376, 750)
        SettingsHeaderArtwork.Translations -> IntRect(220, 250, 1320, 770)
        SettingsHeaderArtwork.Downloads -> IntRect(260, 140, 1300, 880)
        SettingsHeaderArtwork.Profiles -> IntRect(50, 270, 1486, 700)
        SettingsHeaderArtwork.Gestures -> IntRect(160, 245, 1376, 780)
        // The approved hand extends almost to the original's bottom edge.
        SettingsHeaderArtwork.LinkPreview -> IntRect(0, 0, 1536, 1024)
        SettingsHeaderArtwork.TabLifecycle -> IntRect(340, 115, 1210, 910)
        SettingsHeaderArtwork.PersonalLinkRules -> IntRect(140, 225, 1400, 795)
        SettingsHeaderArtwork.ExternalApps -> IntRect(160, 205, 1385, 815)
    }

    fun destinationWindow(source: IntSize, available: IntSize): IntRect {
        if (source.width <= 0 || source.height <= 0 ||
            available.width <= 0 || available.height <= 0
        ) {
            return IntRect.Zero
        }
        val scale = min(
            available.width.toFloat() / source.width,
            available.height.toFloat() / source.height,
        )
        val size = IntSize(
            (source.width * scale).roundToInt().coerceAtMost(available.width),
            (source.height * scale).roundToInt().coerceAtMost(available.height),
        )
        return IntRect(
            offset = IntOffset(
                (available.width - size.width) / 2,
                (available.height - size.height) / 2,
            ),
            size = size,
        )
    }

    /**
     * The originals have an opaque, slightly textured offwhite background. Normalize its
     * luminance to white before tinting, with a clamp between the two drawing operations.
     * The source-window borders measure at least 244/255; lower tones retain their detail.
     */
    fun backgroundNormalization(): ColorMatrix {
        val scale = 255f / 244f
        val red = 0.2126f * scale
        val green = 0.7152f * scale
        val blue = 0.0722f * scale
        return ColorMatrix(
            floatArrayOf(
                red, green, blue, 0f, 0f,
                red, green, blue, 0f, 0f,
                red, green, blue, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    /** Black maps to the theme ink, white to the actual page, with source alpha preserved. */
    fun themeColors(ink: Color, page: Color): ColorMatrix = ColorMatrix(
        floatArrayOf(
            page.red - ink.red, 0f, 0f, 0f, ink.red * 255f,
            0f, page.green - ink.green, 0f, 0f, ink.green * 255f,
            0f, 0f, page.blue - ink.blue, 0f, ink.blue * 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}
