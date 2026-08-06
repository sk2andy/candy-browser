package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressEditorLayoutRulesTest {
    @Test
    fun `suggestions keep a fixed gap above measured bottom bar`() {
        assertEquals(
            108f,
            AddressEditorLayoutRules.suggestionBottomPaddingDp(
                rootHeightPx = 2_400f,
                bottomBarTopPx = 2_124f,
                density = 3f,
            ),
            0.001f,
        )
    }

    @Test
    fun `invalid measurements use stable fallback`() {
        assertEquals(
            AddressEditorLayoutRules.FALLBACK_BOTTOM_PADDING_DP,
            AddressEditorLayoutRules.suggestionBottomPaddingDp(
                rootHeightPx = 0f,
                bottomBarTopPx = Float.NaN,
                density = 3f,
            ),
            0.001f,
        )
    }
}
