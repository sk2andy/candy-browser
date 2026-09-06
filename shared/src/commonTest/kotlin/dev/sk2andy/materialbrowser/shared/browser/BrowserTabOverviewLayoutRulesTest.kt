package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserTabOverviewLayoutRulesTest {
    @Test
    fun `portrait hero keeps Candy card geometry`() {
        val layout = BrowserTabOverviewLayoutRules.heroCard(
            viewportWidth = 400f,
            viewportHeight = 800f,
        )

        assertEquals(296f, layout.width)
        assertEquals(0.45f, layout.aspectRatio)
    }

    @Test
    fun `landscape hero keeps neighbors and respects height`() {
        val tablet = BrowserTabOverviewLayoutRules.heroCard(
            viewportWidth = 1_067f,
            viewportHeight = 667f,
        )
        val short = BrowserTabOverviewLayoutRules.heroCard(
            viewportWidth = 800f,
            viewportHeight = 360f,
        )

        assertEquals(704.352f, tablet.width, 0.001f)
        assertEquals(1.6f, tablet.aspectRatio)
        assertTrue(tablet.width < 1_067f)
        assertEquals(380.16f, short.width, 0.001f)
    }

    @Test
    fun `hero rejects invalid dimensions deterministically`() {
        val layout = BrowserTabOverviewLayoutRules.heroCard(
            viewportWidth = Float.NaN,
            viewportHeight = Float.POSITIVE_INFINITY,
        )

        assertEquals(0f, layout.width)
        assertEquals(0.45f, layout.aspectRatio)
    }

    @Test
    fun `preview crop moves continuously into card`() {
        val target = BrowserTabOverviewLayoutRules.previewCrop(
            rootWidth = 2_400f,
            rootHeight = 1_080f,
            targetWidth = 1_536f,
            targetHeight = 960f,
            cropTopFraction = 0.25f,
        )

        assertEquals(-105f, target.sourceTop)
        assertEquals(1_500f, target.sourceHeight)
        assertEquals(
            BrowserTabPreviewCropLayout(sourceTop = -16.5f, sourceHeight = 1_750f),
            BrowserTabOverviewLayoutRules.interpolatePreviewCrop(
                startTop = 72f,
                startHeight = 2_000f,
                target = target,
                progress = 0.5f,
            ),
        )
    }

    @Test
    fun `portrait grid uses exact Candy spacing`() {
        val layout = BrowserTabOverviewLayoutRules.grid(
            viewportWidth = 400f,
            viewportHeight = 800f,
        )

        assertEquals(2, layout.columnCount)
        assertEquals(0.72f, layout.previewAspectRatio)
        assertEquals(178f, layout.cardWidth)
        assertEquals(190f, layout.columnPitch)
        assertEquals(259.22223f, layout.rowPitch, 0.001f)
        assertEquals(16f, layout.contentPadding)
        assertEquals(12f, layout.itemSpacing)
    }

    @Test
    fun `tablet landscape grid uses three wide columns`() {
        val layout = BrowserTabOverviewLayoutRules.grid(
            viewportWidth = 1_067f,
            viewportHeight = 667f,
        )

        assertEquals(3, layout.columnCount)
        assertEquals(1.6f, layout.previewAspectRatio)
        assertEquals(337f, layout.cardWidth)
        assertEquals(222.625f, layout.rowPitch)
    }

    @Test
    fun `list keeps exact Candy row geometry`() {
        val layout = BrowserTabOverviewLayoutRules.list()

        assertEquals(64f, layout.rowHeight)
        assertEquals(16f, layout.horizontalPadding)
        assertEquals(8f, layout.itemSpacing)
        assertEquals(18f, layout.cornerRadius)
    }
}
