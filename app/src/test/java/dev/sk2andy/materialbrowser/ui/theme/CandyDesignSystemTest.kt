package dev.sk2andy.materialbrowser.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CandyDesignSystemTest {
    @Test
    fun `design languages select distinct motion schemes`() {
        val material = CandyMotionSchemes.forDesignLanguage(
            CandyDesignLanguage.MaterialExpressive,
        )
        val liquidGlass = CandyMotionSchemes.forDesignLanguage(
            CandyDesignLanguage.LiquidGlass,
        )

        assertEquals(CandyMotionSchemes.MaterialExpressive, material)
        assertEquals(CandyMotionSchemes.LiquidGlass, liquidGlass)
        assertNotEquals(material, liquidGlass)
    }
}
