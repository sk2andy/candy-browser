package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressBarInsetRulesTest {
    @Test
    fun `full-height root applies complete IME inset`() {
        assertEquals(
            900,
            AddressBarInsetRules.bottomPaddingPx(
                fullWindowHeightPx = 2_400,
                rootBottomInWindowPx = 2_400,
                imeBottomPx = 900,
                navigationBottomPx = 72,
            ),
        )
    }

    @Test
    fun `IME-resized root does not apply IME inset twice`() {
        assertEquals(
            0,
            AddressBarInsetRules.bottomPaddingPx(
                fullWindowHeightPx = 2_400,
                rootBottomInWindowPx = 1_500,
                imeBottomPx = 900,
                navigationBottomPx = 72,
            ),
        )
    }

    @Test
    fun `partially resized root applies only uncovered IME inset`() {
        assertEquals(
            300,
            AddressBarInsetRules.bottomPaddingPx(
                fullWindowHeightPx = 2_400,
                rootBottomInWindowPx = 1_800,
                imeBottomPx = 900,
                navigationBottomPx = 72,
            ),
        )
    }

    @Test
    fun `hidden IME keeps navigation bar inset`() {
        assertEquals(
            72,
            AddressBarInsetRules.bottomPaddingPx(
                fullWindowHeightPx = 2_400,
                rootBottomInWindowPx = 2_400,
                imeBottomPx = 0,
                navigationBottomPx = 72,
            ),
        )
    }

    @Test
    fun `invalid window measurements fall back to raw inset`() {
        assertEquals(
            900,
            AddressBarInsetRules.bottomPaddingPx(
                fullWindowHeightPx = 0,
                rootBottomInWindowPx = 0,
                imeBottomPx = 900,
                navigationBottomPx = 72,
            ),
        )
    }
}
