package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TabPreviewCaptureRulesTest {
    @Test
    fun `compact viewport captures its native width for sharp restoration`() {
        assertEquals(
            1_080,
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
        assertEquals(3_840, TabPreviewCaptureRules.maximumTargetHeightPx(1_280))
        assertEquals(
            1_280,
            TabPreviewCaptureRules.targetWidthPx(
                sourceWidthPx = 1_600,
                viewportWidthPx = 1_600,
                viewportHeightPx = 2_560,
                density = 2f,
            ),
        )
    }

    @Test
    fun `Pixel portrait preview keeps native pixels without exceeding three megapixels`() {
        assertEquals(
            TabPreviewBitmapDimensions(1_080, 2_410),
            TabPreviewCaptureRules.resolveBitmapDimensions(1_080, 2_410, 1_080, 3_240),
        )
    }

    @Test
    fun `tall preview scales both axes together within pixel and dimension bounds`() {
        val dimensions = requireNotNull(TabPreviewCaptureRules.resolveBitmapDimensions(
            sourceWidthPx = 1_280,
            sourceHeightPx = 4_000,
            targetWidthPx = 1_280,
            maximumTargetHeightPx = 3_840,
        ))

        assertEquals(TabPreviewBitmapDimensions(979, 3_061), dimensions)
        assertEquals(true, dimensions.widthPx.toLong() * dimensions.heightPx <= 3_000_000)
        assertEquals(true, kotlin.math.abs(dimensions.heightPx / dimensions.widthPx.toDouble() - 3.125) < 0.005)
        assertEquals(4_096, TabPreviewCaptureRules.maximumTargetHeightPx(Int.MAX_VALUE))
    }

    @Test
    fun `thumbnail sampling keeps enough pixels for a sharp 480 pixel overview`() {
        assertEquals(2, TabPreviewCaptureRules.decodeSampleSize(1_080, 480))
        assertEquals(1, TabPreviewCaptureRules.decodeSampleSize(480, 1_280))
        assertEquals(1, TabPreviewCaptureRules.decodeSampleSize(0, 480))
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
