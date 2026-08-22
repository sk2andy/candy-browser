package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WebViewScrollBarRulesTest {
    @Test
    fun `non-scrollable content has no thumb`() {
        assertNull(
            WebViewScrollBarRules.geometry(
                scrollY = 0,
                viewportHeightPx = 1_000,
                contentHeightPx = 1_000,
                trackHeightPx = 1_000f,
                minimumThumbHeightPx = 48f,
            ),
        )
    }

    @Test
    fun `thumb position follows clamped scroll progress`() {
        val top = requireNotNull(
            WebViewScrollBarRules.geometry(
                scrollY = -10,
                viewportHeightPx = 1_000,
                contentHeightPx = 4_000,
                trackHeightPx = 800f,
                minimumThumbHeightPx = 48f,
            ),
        )
        val bottom = requireNotNull(
            WebViewScrollBarRules.geometry(
                scrollY = 9_000,
                viewportHeightPx = 1_000,
                contentHeightPx = 4_000,
                trackHeightPx = 800f,
                minimumThumbHeightPx = 48f,
            ),
        )

        assertEquals(200f, top.thumbHeightPx)
        assertEquals(0f, top.thumbTopPx)
        assertEquals(600f, bottom.thumbTopPx)
        assertEquals(3_000, bottom.scrollRangePx)
    }

    @Test
    fun `very long pages keep minimum draggable thumb size`() {
        val geometry = WebViewScrollBarRules.geometry(
            scrollY = 0,
            viewportHeightPx = 1_000,
            contentHeightPx = 100_000,
            trackHeightPx = 800f,
            minimumThumbHeightPx = 48f,
        )

        assertNotNull(geometry)
        assertEquals(48f, geometry?.thumbHeightPx)
    }

    @Test
    fun `thumb drag maps to page distance and clamps at both ends`() {
        val geometry = requireNotNull(
            WebViewScrollBarRules.geometry(
                scrollY = 1_500,
                viewportHeightPx = 1_000,
                contentHeightPx = 4_000,
                trackHeightPx = 800f,
                minimumThumbHeightPx = 48f,
            ),
        )

        assertEquals(
            2_000,
            WebViewScrollBarRules.scrollYAfterDrag(1_500, 100f, geometry),
        )
        assertEquals(
            0,
            WebViewScrollBarRules.scrollYAfterDrag(0, -1_000f, geometry),
        )
        assertEquals(
            3_000,
            WebViewScrollBarRules.scrollYAfterDrag(3_000, 1_000f, geometry),
        )

        val firstTarget = WebViewScrollBarRules.scrollYAfterDrag(1_500, 60f, geometry)
        val secondTarget = WebViewScrollBarRules.scrollYAfterDrag(firstTarget, 40f, geometry)
        assertEquals(2_000, secondTarget)
    }
}
