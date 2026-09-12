package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TabOverviewGridRulesTest {
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
