package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TabPreviewCaptureRulesTest {
    @Test
    fun `compact viewport keeps existing 480 pixel capture`() {
        assertEquals(
            480,
            TabPreviewCaptureRules.targetWidthPx(
                sourceWidthPx = 1_080,
                viewportWidthPx = 1_080,
                viewportHeightPx = 2_400,
                density = 3f,
            ),
        )
    }

    @Test
    fun `tablet hero capture follows display size within memory bounds`() {
        assertEquals(
            1_280,
            TabPreviewCaptureRules.targetWidthPx(
                sourceWidthPx = 2_560,
                viewportWidthPx = 2_560,
                viewportHeightPx = 1_600,
                density = 2f,
            ),
        )
        assertEquals(1_953, TabPreviewCaptureRules.maximumTargetHeightPx(1_280))
        assertEquals(
            720,
            TabPreviewCaptureRules.targetWidthPx(
                sourceWidthPx = 1_600,
                viewportWidthPx = 1_600,
                viewportHeightPx = 2_560,
                density = 2f,
            ),
        )
    }

    @Test
    fun `capture never upscales source and rejects missing geometry`() {
        assertEquals(
            420,
            TabPreviewCaptureRules.targetWidthPx(
                sourceWidthPx = 420,
                viewportWidthPx = 2_560,
                viewportHeightPx = 1_600,
                density = 2f,
            ),
        )
        assertEquals(
            0,
            TabPreviewCaptureRules.targetWidthPx(
                sourceWidthPx = 0,
                viewportWidthPx = 2_560,
                viewportHeightPx = 1_600,
                density = 2f,
            ),
        )
        assertEquals(
            0,
            TabPreviewCaptureRules.targetWidthPx(
                sourceWidthPx = 2_560,
                viewportWidthPx = 2_560,
                viewportHeightPx = 1_600,
                density = Float.NaN,
            ),
        )
    }

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
