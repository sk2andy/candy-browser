package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TabPreviewCaptureRulesTest {
    @Test
    fun `capture ends before compose bottom bar`() {
        assertEquals(
            2_080,
            TabPreviewCaptureRules.sourceBottomPx(
                viewTopPx = 72,
                viewHeightPx = 2_328,
                decorHeightPx = 2_400,
                contentBottomPx = 2_080,
            ),
        )
    }

    @Test
    fun `capture falls back to visible decor bounds`() {
        assertEquals(
            2_400,
            TabPreviewCaptureRules.sourceBottomPx(
                viewTopPx = 72,
                viewHeightPx = 2_500,
                decorHeightPx = 2_400,
                contentBottomPx = null,
            ),
        )
    }

    @Test
    fun `same page keeps informative preview when protected surface turns black`() {
        val previous = TabPreviewQuality(visualRange = 210, nearBlackFraction = 0.08f)
        val protectedSurface = TabPreviewQuality(
            visualRange = 240,
            nearBlackFraction = 0.72f,
        )

        assertEquals(
            false,
            TabPreviewCaptureRules.shouldStore(
                candidate = protectedSurface,
                previous = previous,
                isSamePage = true,
            ),
        )
    }

    @Test
    fun `dark page remains valid when it is not a same page quality regression`() {
        val restoredPreviewWithoutProvenance = TabPreviewQuality(
            visualRange = 210,
            nearBlackFraction = 0.08f,
        )
        val darkPage = TabPreviewQuality(visualRange = 180, nearBlackFraction = 0.72f)

        assertEquals(
            true,
            TabPreviewCaptureRules.shouldStore(
                candidate = darkPage,
                previous = restoredPreviewWithoutProvenance,
                isSamePage = false,
            ),
        )
    }
}
