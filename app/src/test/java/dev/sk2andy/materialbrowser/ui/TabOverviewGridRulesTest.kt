package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.shared.ui.TabOverviewGridRules

import org.junit.Assert.assertEquals
import org.junit.Test

class TabOverviewGridRulesTest {
    @Test
    fun `portrait grid keeps two compact columns`() {
        val layout = TabOverviewGridRules.layout(
            viewportWidth = 400f,
            viewportHeight = 800f,
        )

        assertEquals(2, layout.columnCount)
        assertEquals(0.72f, layout.previewAspectRatio, 0f)
        assertEquals(178f, layout.cardWidth, 0f)
        assertEquals(190f, layout.columnPitch, 0f)
        assertEquals(259.22223f, layout.rowPitch, 0.001f)
    }

    @Test
    fun `tablet landscape grid uses three wide columns`() {
        val layout = TabOverviewGridRules.layout(
            viewportWidth = 1_067f,
            viewportHeight = 667f,
        )

        assertEquals(3, layout.columnCount)
        assertEquals(1.6f, layout.previewAspectRatio, 0f)
        assertEquals(337f, layout.cardWidth, 0f)
        assertEquals(349f, layout.columnPitch, 0f)
        assertEquals(222.625f, layout.rowPitch, 0.001f)
    }

    @Test
    fun `short landscape grid keeps two wide columns`() {
        val layout = TabOverviewGridRules.layout(
            viewportWidth = 800f,
            viewportHeight = 360f,
        )

        assertEquals(2, layout.columnCount)
        assertEquals(1.6f, layout.previewAspectRatio, 0f)
        assertEquals(378f, layout.cardWidth, 0f)
        assertEquals(248.25f, layout.rowPitch, 0.001f)
    }

    @Test
    fun `invalid grid dimensions collapse deterministically`() {
        val layout = TabOverviewGridRules.layout(
            viewportWidth = Float.NaN,
            viewportHeight = Float.POSITIVE_INFINITY,
        )

        assertEquals(2, layout.columnCount)
        assertEquals(0f, layout.cardWidth, 0f)
        assertEquals(0.72f, layout.previewAspectRatio, 0f)
    }

    @Test
    fun `bottom-start grid pads incomplete first row`() {
        assertEquals(1, TabOverviewGridRules.leadingEmptyCellCount(3, 2, true))
        assertEquals(2, TabOverviewGridRules.leadingEmptyCellCount(4, 3, true))
    }

    @Test
    fun `bottom-start grid keeps complete rows and disabled layouts unchanged`() {
        assertEquals(0, TabOverviewGridRules.leadingEmptyCellCount(4, 2, true))
        assertEquals(0, TabOverviewGridRules.leadingEmptyCellCount(3, 2, false))
        assertEquals(0, TabOverviewGridRules.leadingEmptyCellCount(0, 2, true))
        assertEquals(0, TabOverviewGridRules.leadingEmptyCellCount(3, 0, true))
    }
}
