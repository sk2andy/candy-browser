package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabOverviewContrastRulesTest {
    @Test
    fun `dark title background chooses white`() {
        assertEquals(
            Color.White,
            TabOverviewContrastRules.titleContentColor(
                primaryContainer = Color(0xFF282828),
                tertiaryContainer = Color(0xFF484848),
            ),
        )
    }

    @Test
    fun `light title background chooses black`() {
        assertEquals(
            Color.Black,
            TabOverviewContrastRules.titleContentColor(
                primaryContainer = Color(0xFFD8D8D8),
                tertiaryContainer = Color(0xFFF8F8F8),
            ),
        )
    }

    @Test
    fun `chosen color has strongest contrast`() {
        val background = Color(0xFF616161)
        val chosen = TabOverviewContrastRules.highestContrastColor(background)
        val alternative = if (chosen == Color.Black) Color.White else Color.Black

        assertTrue(
            TabOverviewContrastRules.contrastRatio(chosen, background) >=
                TabOverviewContrastRules.contrastRatio(alternative, background),
        )
    }
}
