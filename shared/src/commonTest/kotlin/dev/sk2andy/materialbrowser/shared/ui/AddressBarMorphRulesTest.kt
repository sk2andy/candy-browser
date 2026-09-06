package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp

class AddressBarMorphRulesTest {
    @Test
    fun `default platform styles preserve Android chrome geometry`() {
        assertEquals(64.dp, CandyBrowserChromeMetrics().addressMinHeight)
        assertEquals(48.dp, CandyBrowserChromeMetrics().actionSize)
        assertEquals(64.dp, BrowserMainMenuStyle().toolbarMinHeight)
        assertEquals(2.dp, BrowserMainMenuStyle().groupItemSpacing)
        assertTrue(BrowserMainMenuStyle().showHeader)
    }

    @Test
    fun `resisted progress is bounded and rejects non finite input`() {
        assertEquals(0f, AddressBarMorphRules.resistedProgress(-1f))
        assertEquals(0f, AddressBarMorphRules.resistedProgress(Float.NaN))
        assertEquals(0.425f, AddressBarMorphRules.resistedProgress(0.5f), 0.001f)
        assertEquals(1f, AddressBarMorphRules.resistedProgress(2f))
    }

    @Test
    fun `container scale falls back for invalid geometry`() {
        listOf(
            AddressBarMorphRules.containerScale(0.5f, 0f, 56f),
            AddressBarMorphRules.containerScale(0.5f, Float.NaN, 56f),
            AddressBarMorphRules.containerScale(0.5f, 280f, Float.POSITIVE_INFINITY),
        ).forEach { scale -> assertEquals(1f, scale) }
    }

    @Test
    fun `corner morph keeps displayed radii circular under non uniform scale`() {
        val radii = AddressBarMorphRules.cornerRadii(
            progress = 0.5f,
            sourceWidth = 280f,
            sourceHeight = 48f,
            targetSize = 56f,
        )
        val displayedHorizontal = radii.horizontal * AddressBarMorphRules.containerScale(
            progress = 0.5f,
            sourceSize = 280f,
            targetSize = 56f,
        )
        val displayedVertical = radii.vertical * AddressBarMorphRules.containerScale(
            progress = 0.5f,
            sourceSize = 48f,
            targetSize = 56f,
        )

        assertEquals(displayedHorizontal, displayedVertical, 0.001f)
        assertTrue(radii.horizontal in 0f..140f)
        assertTrue(radii.vertical in 0f..24f)
    }

    @Test
    fun `corner morph returns zero radii for invalid geometry`() {
        assertEquals(
            AddressBarMorphCornerRadii(horizontal = 0f, vertical = 0f),
            AddressBarMorphRules.cornerRadii(
                progress = 0.5f,
                sourceWidth = Float.NaN,
                sourceHeight = 48f,
                targetSize = 56f,
            ),
        )
    }

    @Test
    fun `menu surface expands from square address anchor`() {
        assertEquals(
            0.12f,
            BrowserMainMenuMotion.surfaceScale(
                expansionProgress = 0f,
                surfaceSize = 400f,
                anchorSize = 48f,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            BrowserMainMenuMotion.surfaceScale(
                expansionProgress = 1f,
                surfaceSize = 400f,
                anchorSize = 48f,
            ),
            0.001f,
        )

        val collapsedRadii = BrowserMainMenuMotion.surfaceCornerRadii(
            expansionProgress = 0f,
            surfaceWidth = 400f,
            surfaceHeight = 600f,
            anchorSize = 48f,
            surfaceCornerRadius = 28f,
        )
        assertEquals(200f, collapsedRadii.horizontal, 0.001f)
        assertEquals(300f, collapsedRadii.vertical, 0.001f)
    }

    @Test
    fun `native glass morph preserves visual effect opacity`() {
        assertEquals(
            1f,
            BrowserMainMenuMotion.surfaceAlpha(
                expansionProgress = 0.35f,
                preservesVisualEffect = true,
            ),
        )
        assertEquals(
            0.35f,
            BrowserMainMenuMotion.surfaceAlpha(
                expansionProgress = 0.35f,
                preservesVisualEffect = false,
            ),
        )
    }
}
