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
}
