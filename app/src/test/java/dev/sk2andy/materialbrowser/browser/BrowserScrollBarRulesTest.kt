package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserScrollBarRulesTest {
    @Test
    fun `gecko metrics produce a proportional thumb and offset`() {
        val geometry = BrowserScrollBarRules.geometry(
            metrics = BrowserEngineScrollMetrics(offsetPx = 500, extentPx = 500, rangePx = 2_000),
            trackLengthPx = 1_000f,
            minimumThumbLengthPx = 24f,
        )

        requireNotNull(geometry)
        assertEquals(250f, geometry.thumbLengthPx, 0.01f)
        assertEquals(250f, geometry.thumbOffsetPx, 0.01f)
    }

    @Test
    fun `thumb drag maps to document offset and clamps`() {
        val geometry = requireNotNull(
            BrowserScrollBarRules.geometry(
                metrics = BrowserEngineScrollMetrics(offsetPx = 0, extentPx = 500, rangePx = 2_000),
                trackLengthPx = 1_000f,
                minimumThumbLengthPx = 24f,
            ),
        )

        assertEquals(500, BrowserScrollBarRules.scrollOffsetAfterDrag(0, 250f, geometry))
        assertEquals(1_500, BrowserScrollBarRules.scrollOffsetAfterDrag(1_500, 1_000f, geometry))
        assertEquals(0, BrowserScrollBarRules.scrollOffsetAfterDrag(500, -1_000f, geometry))
    }

    @Test
    fun `short or non scrollable pages have no indicator`() {
        assertNull(
            BrowserScrollBarRules.geometry(
                metrics = BrowserEngineScrollMetrics(offsetPx = 0, extentPx = 500, rangePx = 500),
                trackLengthPx = 1_000f,
                minimumThumbLengthPx = 24f,
            ),
        )
    }

    @Test
    fun `very long pages retain a touch sized thumb`() {
        val geometry = BrowserScrollBarRules.geometry(
            metrics = BrowserEngineScrollMetrics(
                offsetPx = 0,
                extentPx = 500,
                rangePx = 100_000,
            ),
            trackLengthPx = 1_000f,
            minimumThumbLengthPx = 48f,
        )

        requireNotNull(geometry)
        assertEquals(48f, geometry.thumbLengthPx, 0.01f)
        assertEquals(952f, geometry.thumbTravelPx, 0.01f)
    }

    @Test
    fun `invalid renderer and track metrics fail closed`() {
        assertNull(
            BrowserScrollBarRules.geometry(
                metrics = BrowserEngineScrollMetrics(offsetPx = 0, extentPx = -1, rangePx = 2_000),
                trackLengthPx = 1_000f,
                minimumThumbLengthPx = 48f,
            ),
        )
        assertNull(
            BrowserScrollBarRules.geometry(
                metrics = BrowserEngineScrollMetrics(offsetPx = 0, extentPx = 500, rangePx = 2_000),
                trackLengthPx = 0f,
                minimumThumbLengthPx = 48f,
            ),
        )
    }

    @Test
    fun `renderer offsets outside range map to track edges`() {
        val above = requireNotNull(
            BrowserScrollBarRules.geometry(
                metrics = BrowserEngineScrollMetrics(offsetPx = -200, extentPx = 500, rangePx = 2_000),
                trackLengthPx = 1_000f,
                minimumThumbLengthPx = 48f,
            ),
        )
        val below = requireNotNull(
            BrowserScrollBarRules.geometry(
                metrics = BrowserEngineScrollMetrics(offsetPx = 9_000, extentPx = 500, rangePx = 2_000),
                trackLengthPx = 1_000f,
                minimumThumbLengthPx = 48f,
            ),
        )

        assertEquals(0f, above.thumbOffsetPx, 0.01f)
        assertEquals(750f, below.thumbOffsetPx, 0.01f)
    }
}
