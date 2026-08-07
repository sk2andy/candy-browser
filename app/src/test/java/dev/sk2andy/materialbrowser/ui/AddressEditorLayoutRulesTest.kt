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

    @Test
    fun `suggestions can use all space above the bottom bar`() {
        assertEquals(
            636f,
            AddressEditorLayoutRules.suggestionMaxHeightDp(
                bottomBarTopPx = 2_124f,
                topInsetPx = 132f,
                density = 3f,
            ),
            0.001f,
        )
    }

    @Test
    fun `invalid maximum height measurements use stable fallback`() {
        assertEquals(
            AddressEditorLayoutRules.FALLBACK_MAX_HEIGHT_DP,
            AddressEditorLayoutRules.suggestionMaxHeightDp(
                bottomBarTopPx = Float.NaN,
                topInsetPx = 132f,
                density = 3f,
            ),
            0.001f,
        )
    }
}
