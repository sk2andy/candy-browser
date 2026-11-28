package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TabOverviewGridRulesTest {
    @Test
    fun `automatic keeps the previous portrait and landscape layouts`() {
        assertEquals(2, TabOverviewGridRules.layout(360f, 640f).columnCount)
        assertEquals(2, TabOverviewGridRules.layout(800f, 1067f).columnCount)
        assertEquals(3, TabOverviewGridRules.layout(1000f, 700f).columnCount)
    }

    @Test
    fun `explicit columns resize cards and fall back to a width that fits`() {
        val large = TabOverviewGridRules.layout(360f, 640f, preferredColumns = 1)
        val dense = TabOverviewGridRules.layout(360f, 640f, preferredColumns = 5)
        val tablet = TabOverviewGridRules.layout(800f, 1067f, preferredColumns = 5)
        assertEquals(1, large.columnCount)
        assertEquals(328f, large.cardWidth)
        assertEquals(3, dense.columnCount)
        assertEquals(304f / 3f, dense.cardWidth)
        assertEquals(5, tablet.columnCount)
        assertEquals(144f, tablet.cardWidth)
    }

    @Test
    fun `invalid preferences preserve automatic and tiny widths stay valid`() {
        assertEquals(2, TabOverviewGridRules.layout(360f, 640f, preferredColumns = -2).columnCount)
        assertEquals(2, TabOverviewGridRules.layout(360f, 640f, preferredColumns = 6).columnCount)
        assertEquals(1, TabOverviewGridRules.layout(Float.NaN, 400f, preferredColumns = 5).columnCount)
    }

    @Test
    fun `bottom start pads incomplete first row`() {
        assertEquals(1, TabOverviewGridRules.leadingEmptyCellCount(3, 2, true))
        assertEquals(2, TabOverviewGridRules.leadingEmptyCellCount(4, 3, true))
    }

    @Test
    fun `top start and complete rows need no leading cells`() {
        assertEquals(0, TabOverviewGridRules.leadingEmptyCellCount(3, 2, false))
        assertEquals(0, TabOverviewGridRules.leadingEmptyCellCount(4, 2, true))
    }

    @Test
    fun `empty or invalid grids need no leading cells`() {
        assertEquals(0, TabOverviewGridRules.leadingEmptyCellCount(0, 2, true))
        assertEquals(0, TabOverviewGridRules.leadingEmptyCellCount(3, 0, true))
    }
}
