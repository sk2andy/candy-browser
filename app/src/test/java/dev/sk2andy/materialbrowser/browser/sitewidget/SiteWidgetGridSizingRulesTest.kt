package dev.sk2andy.materialbrowser.browser.sitewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteWidgetGridSizingRulesTest {
    @Test
    fun `portrait calibration pairs minimum width with maximum height`() {
        val ratio = requireNotNull(
            SiteWidgetGridSizingRules.cellAspectRatio(
                minWidthDp = 320,
                minHeightDp = 180,
                maxWidthDp = 480,
                maxHeightDp = 360,
                isLandscape = false,
                format = SiteWidgetFormat.FourByThree,
            ),
        )

        assertEquals(2f / 3f, ratio, 0.0001f)
        val nextCrop = requireNotNull(
            SiteWidgetCropRules.initialCrop(
                format = SiteWidgetFormat(3, 2),
                viewportWidth = 400,
                viewportHeight = 800,
                cellAspectRatio = ratio,
            ),
        )
        // The measured 80 x 120 dp cells make the next 3 x 2 widget square.
        assertEquals(1f, nextCrop.width, 0.0001f)
        assertEquals(0.5f, nextCrop.height, 0.0001f)
        assertEquals(0.25f, nextCrop.top, 0.0001f)
    }

    @Test
    fun `landscape calibration pairs maximum width with minimum height`() {
        assertEquals(
            2f,
            requireNotNull(
                SiteWidgetGridSizingRules.cellAspectRatio(
                    minWidthDp = 320,
                    minHeightDp = 180,
                    maxWidthDp = 480,
                    maxHeightDp = 360,
                    isLandscape = true,
                    format = SiteWidgetFormat.FourByThree,
                ),
            ),
            0.0001f,
        )
    }

    @Test
    fun `launchers reporting only minimum bounds retain a usable estimate`() {
        listOf(false, true).forEach { isLandscape ->
            assertEquals(
                1f,
                requireNotNull(
                    SiteWidgetGridSizingRules.cellAspectRatio(
                        minWidthDp = 320,
                        minHeightDp = 240,
                        maxWidthDp = 0,
                        maxHeightDp = 0,
                        isLandscape = isLandscape,
                        format = SiteWidgetFormat.FourByThree,
                    ),
                ),
                0.0001f,
            )
        }
    }

    @Test
    fun `invalid measurements do not replace learned sizing`() {
        assertNull(SiteWidgetGridSizingRules.cellAspectRatio(0, 240, SiteWidgetFormat.FourByThree))
        assertNull(SiteWidgetGridSizingRules.cellAspectRatio(320, -1, SiteWidgetFormat.FourByThree))
        assertNull(SiteWidgetGridSizingRules.cellAspectRatio(320, 240, SiteWidgetFormat(0, 3)))
        assertNull(SiteWidgetGridSizingRules.cellAspectRatio(Int.MAX_VALUE, 1, SiteWidgetFormat.FourByThree))
    }
}
