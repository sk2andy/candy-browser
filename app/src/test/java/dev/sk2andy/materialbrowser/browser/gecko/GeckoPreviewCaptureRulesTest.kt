package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoPreviewCaptureRulesTest {
    @Test
    fun `captured surface is cropped to visible Candy content and scaled`() {
        assertEquals(
            GeckoPreviewBitmapLayout(
                sourceHeightPx = 1_800,
                targetWidthPx = 480,
                targetHeightPx = 900,
            ),
            GeckoPreviewCaptureRules.resolveBitmapLayout(
                viewHeightPx = 2_400,
                visibleViewHeightPx = 1_800,
                capturedWidthPx = 960,
                capturedHeightPx = 2_400,
                targetWidthPx = 480,
                maximumTargetHeightPx = 1_440,
            ),
        )
    }

    @Test
    fun `capture height never exceeds captured surface or preview limit`() {
        assertEquals(
            GeckoPreviewBitmapLayout(
                sourceHeightPx = 2_400,
                targetWidthPx = 480,
                targetHeightPx = 1_440,
            ),
            GeckoPreviewCaptureRules.resolveBitmapLayout(
                viewHeightPx = 2_400,
                visibleViewHeightPx = 3_000,
                capturedWidthPx = 600,
                capturedHeightPx = 2_400,
                targetWidthPx = 480,
                maximumTargetHeightPx = 1_440,
            ),
        )
    }

    @Test
    fun `invalid compositor dimensions cannot allocate a preview`() {
        assertNull(
            GeckoPreviewCaptureRules.resolveBitmapLayout(
                viewHeightPx = 0,
                visibleViewHeightPx = 1_000,
                capturedWidthPx = 480,
                capturedHeightPx = 1_000,
                targetWidthPx = 480,
                maximumTargetHeightPx = 1_440,
            ),
        )
    }
}
