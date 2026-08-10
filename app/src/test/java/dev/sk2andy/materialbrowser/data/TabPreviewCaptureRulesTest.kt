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
    fun `uniform black bitmap is recognized as failed capture`() {
        assertEquals(
            true,
            TabPreviewCaptureRules.isLikelyFailedCapture(
                TabPreviewQuality(visualRange = 0, nearBlackFraction = 1f),
            ),
        )
    }

    @Test
    fun `dark page with visible content is not treated as failed capture`() {
        assertEquals(
            false,
            TabPreviewCaptureRules.isLikelyFailedCapture(
                TabPreviewQuality(visualRange = 120, nearBlackFraction = 0.98f),
            ),
        )
    }

    @Test
    fun `black failed capture is never stored`() {
        assertEquals(
            false,
            TabPreviewCaptureRules.shouldStorePixelCopy(
                candidate = TabPreviewQuality(visualRange = 0, nearBlackFraction = 1f),
            ),
        )
    }

    @Test
    fun `uniform light page remains a valid pixel copy`() {
        assertEquals(
            true,
            TabPreviewCaptureRules.shouldStorePixelCopy(
                candidate = TabPreviewQuality(visualRange = 0, nearBlackFraction = 0f),
            ),
        )
    }
}
