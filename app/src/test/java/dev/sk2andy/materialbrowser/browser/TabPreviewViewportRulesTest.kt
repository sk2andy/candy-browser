package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabPreviewViewportRulesTest {
    @Test
    fun `equal aspect ratios with different dimensions do not invalidate previews`() {
        assertFalse(
            TabPreviewViewportRules.hasAspectRatioChanged(
                previousWidthDp = 400,
                previousHeightDp = 800,
                currentWidthDp = 600,
                currentHeightDp = 1_200,
            ),
        )
        assertFalse(
            TabPreviewViewportRules.hasAspectRatioChanged(
                previousWidthDp = 400,
                previousHeightDp = 800,
                currentWidthDp = 399,
                currentHeightDp = 800,
            ),
        )
    }

    @Test
    fun `orientation and fold size changes invalidate previews when aspect ratio changes`() {
        assertTrue(
            TabPreviewViewportRules.hasAspectRatioChanged(
                previousWidthDp = 400,
                previousHeightDp = 800,
                currentWidthDp = 800,
                currentHeightDp = 400,
            ),
        )
        assertTrue(
            TabPreviewViewportRules.hasAspectRatioChanged(
                previousWidthDp = 800,
                previousHeightDp = 800,
                currentWidthDp = 1_200,
                currentHeightDp = 800,
            ),
        )
    }

    @Test
    fun `invalid viewport sizes do not invalidate previews`() {
        assertFalse(
            TabPreviewViewportRules.hasAspectRatioChanged(
                previousWidthDp = 0,
                previousHeightDp = 800,
                currentWidthDp = 800,
                currentHeightDp = 400,
            ),
        )
    }
}
