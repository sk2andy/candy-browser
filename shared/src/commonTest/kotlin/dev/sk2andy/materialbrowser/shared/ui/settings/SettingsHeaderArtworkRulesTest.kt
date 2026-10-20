package dev.sk2andy.materialbrowser.shared.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsHeaderArtworkRulesTest {
    @Test
    fun addedWindowsPreserveCloudTrailProfileCardsAndCompleteHand() {
        // Visible bounds independently inspected in the approved 1536 x 1024 originals.
        val visibleBounds = mapOf(
            SettingsHeaderArtwork.SearchEngines to IntRect(200, 294, 1336, 708),
            SettingsHeaderArtwork.Translations to IntRect(260, 296, 1278, 724),
            SettingsHeaderArtwork.Downloads to IntRect(309, 184, 1245, 836),
            SettingsHeaderArtwork.Profiles to IntRect(94, 314, 1442, 646),
            SettingsHeaderArtwork.Gestures to IntRect(208, 292, 1329, 732),
            SettingsHeaderArtwork.LinkPreview to IntRect(83, 141, 1454, 1001),
            SettingsHeaderArtwork.TabLifecycle to IntRect(394, 162, 1166, 866),
            SettingsHeaderArtwork.PersonalLinkRules to IntRect(185, 272, 1352, 753),
        )
        visibleBounds.forEach { (artwork, visible) ->
            val window = SettingsHeaderArtworkRules.sourceWindow(artwork)
            assertTrue(window.left <= visible.left && window.top <= visible.top, artwork.name)
            assertTrue(window.right >= visible.right && window.bottom >= visible.bottom, artwork.name)
            assertTrue(window.left >= 0 && window.top >= 0, artwork.name)
            assertTrue(window.right <= 1536 && window.bottom <= 1024, artwork.name)
        }
        assertEquals(
            IntRect(0, 0, 1536, 1024),
            SettingsHeaderArtworkRules.sourceWindow(SettingsHeaderArtwork.LinkPreview),
        )
    }

    @Test
    fun originalWindowsExcludeNeighboringAndRejectedArtwork() {
        val appearance = SettingsHeaderArtworkRules.sourceWindow(SettingsHeaderArtwork.Appearance)
        val tabs = SettingsHeaderArtworkRules.sourceWindow(SettingsHeaderArtwork.Tabs)
        val player = SettingsHeaderArtworkRules.sourceWindow(SettingsHeaderArtwork.Player)

        // Independent landmarks from the approved originals: appearance ends above y=580,
        // tabs occupy y=647..1016, and the rejected player starts at y=1105.
        assertTrue(appearance.top < 116 && appearance.bottom in 580..646)
        assertTrue(tabs.top in 581..646 && tabs.bottom in 1017..1104)
        assertTrue(appearance.right <= 1024 && tabs.right <= 1024)
        assertTrue(player.left < 209 && player.top < 293)
        assertTrue(player.right > 1328 && player.bottom > 730)
        assertTrue(player.right <= 1536 && player.bottom <= 1024)
    }

    @Test
    fun sourceFitsWithoutStretchingOrClippingOnNarrowAndWideLayouts() {
        assertEquals(
            IntRect(0, 25, 300, 175),
            SettingsHeaderArtworkRules.destinationWindow(IntSize(600, 300), IntSize(300, 200)),
        )
        assertEquals(
            IntRect(200, 0, 300, 200),
            SettingsHeaderArtworkRules.destinationWindow(IntSize(300, 600), IntSize(500, 200)),
        )
        assertEquals(
            IntRect.Zero,
            SettingsHeaderArtworkRules.destinationWindow(IntSize(0, 600), IntSize(300, 200)),
        )
        assertEquals(
            IntRect.Zero,
            SettingsHeaderArtworkRules.destinationWindow(IntSize(600, 300), IntSize(300, 0)),
        )
    }

    @Test
    fun texturedPaperBecomesExactPageColorInLightDarkAndBlackThemes() {
        val ink = Color(0xFF7451B8)
        for (page in listOf(Color(0xFFF8F2FA), Color(0xFF201C24), Color.Black)) {
            for (paper in listOf(
                floatArrayOf(244f, 245f, 244f, 255f),
                floatArrayOf(246f, 246f, 245f, 255f),
                floatArrayOf(255f, 255f, 255f, 255f),
            )) {
                val result = tinted(paper, ink, page)
                assertEquals(page.red * 255f, result[0], 0.001f)
                assertEquals(page.green * 255f, result[1], 0.001f)
                assertEquals(page.blue * 255f, result[2], 0.001f)
                assertEquals(255f, result[3])
            }
        }
    }

    @Test
    fun darkMidAndLightDetailsRetainDistinctThemeTones() {
        val ink = Color(0xFF8040C0)
        val page = Color.Black
        val dark = tinted(floatArrayOf(0f, 0f, 0f, 255f), ink, page)
        val middle = tinted(floatArrayOf(122f, 122f, 122f, 255f), ink, page)
        val pale = tinted(floatArrayOf(220f, 220f, 220f, 255f), ink, page)

        assertEquals(128f, dark[0], 0.001f)
        assertEquals(64f, dark[1], 0.001f)
        assertEquals(192f, dark[2], 0.001f)
        assertEquals(64f, middle[0], 0.001f)
        assertEquals(32f, middle[1], 0.001f)
        assertEquals(96f, middle[2], 0.001f)
        assertTrue(pale[0] > 0f && pale[0] < middle[0])
    }

    @Test
    fun bothDrawingPassesPreserveTransparentAndPartialSourceAlpha() {
        for (alpha in listOf(0f, 64f, 128f, 255f)) {
            assertEquals(
                alpha,
                tinted(floatArrayOf(83f, 84f, 82f, alpha), Color.Red, Color.Black)[3],
            )
        }
    }

    @Test
    fun paletteChangeRecolorsTheSameTones() {
        val source = floatArrayOf(122f, 122f, 122f, 255f)
        val red = tinted(source, Color.Red, Color.Black)
        val blue = tinted(source, Color.Blue, Color.Black)
        assertEquals(127.5f, red[0], 0.001f)
        assertEquals(0f, red[2], 0.001f)
        assertEquals(0f, blue[0], 0.001f)
        assertEquals(127.5f, blue[2], 0.001f)
    }

    private fun tinted(source: FloatArray, ink: Color, page: Color): FloatArray =
        SettingsHeaderArtworkRules.themeColors(ink, page).applyTo(
            SettingsHeaderArtworkRules.backgroundNormalization().applyTo(source),
        )

    private fun ColorMatrix.applyTo(source: FloatArray): FloatArray = FloatArray(4) { row ->
        (source.indices.sumOf { column ->
            (this[row, column] * source[column]).toDouble()
        }.toFloat() + this[row, 4]).coerceIn(0f, 255f)
    }
}
