package dev.sk2andy.materialbrowser.browser.sitewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteWidgetCropRulesTest {
    @Test
    fun `grid formats round trip and preserve legacy names`() {
        assertEquals(25, SiteWidgetFormat.entries.size)
        assertEquals(SiteWidgetFormat(4, 3), SiteWidgetFormat.valueOf("FourByThree"))
        assertEquals(SiteWidgetFormat(4, 4), SiteWidgetFormat.valueOf("FourByFour"))
        assertEquals(SiteWidgetFormat(2, 6), SiteWidgetFormat.valueOf("Grid2x6"))
        assertEquals("Grid6x2", SiteWidgetFormat(6, 2).name)
        SiteWidgetFormat.entries.forEach { format ->
            assertEquals(format, SiteWidgetFormat.valueOf(format.name))
        }
        listOf("Grid1x3", "Grid4x7", "Grid04x3", "", "FourByTwo").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { SiteWidgetFormat.valueOf(name) }
        }
    }

    @Test
    fun `invalid grid dimensions reject geometry`() {
        val crop = SiteWidgetCrop(0f, 0f, 1f, 1f)
        listOf(SiteWidgetFormat(1, 3), SiteWidgetFormat(4, 7), SiteWidgetFormat(0, 0)).forEach { format ->
            assertNull(SiteWidgetCropRules.initialCrop(format, 400, 800))
            assertNull(SiteWidgetCropRules.resizeCrop(crop, 1f, format, 400, 800))
        }
    }

    @Test
    fun `zoom magnifies selected content while preserving its center`() {
        val initial = SiteWidgetCropRules.initialCrop(SiteWidgetFormat.FourByThree, 400, 800)!!
        assertEquals(
            SiteWidgetCrop(left = 0.25f, top = 0.40625f, width = 0.5f, height = 0.1875f),
            SiteWidgetCropRules.zoomCrop(initial, 2f, SiteWidgetFormat.FourByThree, 400, 800),
        )
        val maximumZoom = SiteWidgetCropRules.zoomCrop(initial, 100f, SiteWidgetFormat.FourByThree, 400, 800)!!
        assertEquals(0.1f, maximumZoom.width, 0.0001f)
        assertEquals(0.0375f, maximumZoom.height, 0.0001f)
        assertEquals(initial, SiteWidgetCropRules.zoomCrop(initial, 0.5f, SiteWidgetFormat.FourByThree, 400, 800))
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { zoom ->
            assertNull(SiteWidgetCropRules.zoomCrop(initial, zoom, SiteWidgetFormat.FourByThree, 400, 800))
        }
    }

    @Test
    fun `changing grid preserves zoom and clamps crop at the edge`() {
        val crop = SiteWidgetCrop(left = 0.5f, top = 0.8125f, width = 0.5f, height = 0.1875f)
        val reformatted = SiteWidgetCropRules.reformatCrop(
            crop, SiteWidgetFormat.FourByThree, SiteWidgetFormat(2, 6), 400, 800,
        )!!
        assertEquals(1f / 3f, reformatted.width, 0.0001f)
        assertEquals(0.5f, reformatted.height, 0.0001f)
        assertEquals(0.5f, reformatted.top, 0.0001f)
        assertTrue(SiteWidgetCropRules.sanitize(reformatted) != null)
        assertNull(SiteWidgetCropRules.reformatCrop(crop, SiteWidgetFormat.FourByThree, SiteWidgetFormat(1, 6), 400, 800))
    }

    @Test
    fun `portrait viewport centers largest four by three crop`() {
        assertEquals(
            SiteWidgetCrop(left = 0f, top = 0.3125f, width = 1f, height = 0.375f),
            SiteWidgetCropRules.initialCrop(SiteWidgetFormat.FourByThree, 400, 800),
        )
    }

    @Test
    fun `landscape viewport centers largest square crop`() {
        assertEquals(
            SiteWidgetCrop(left = 0.25f, top = 0f, width = 0.5f, height = 1f),
            SiteWidgetCropRules.initialCrop(SiteWidgetFormat.FourByFour, 800, 400),
        )
    }

    @Test
    fun `matching viewport uses whole page`() {
        assertEquals(
            SiteWidgetCrop(left = 0f, top = 0f, width = 1f, height = 1f),
            SiteWidgetCropRules.initialCrop(SiteWidgetFormat.FourByThree, 800, 600),
        )
    }

    @Test
    fun `invalid viewport rejects crop`() {
        assertNull(SiteWidgetCropRules.initialCrop(SiteWidgetFormat.FourByFour, 0, 800))
        assertNull(SiteWidgetCropRules.initialCrop(SiteWidgetFormat.FourByThree, 400, -1))
    }

    @Test
    fun `movement clamps each edge while preserving size`() {
        val crop = SiteWidgetCrop(left = 0.25f, top = 0.25f, width = 0.5f, height = 0.5f)
        assertEquals(
            SiteWidgetCrop(left = 0f, top = 0.5f, width = 0.5f, height = 0.5f),
            SiteWidgetCropRules.moveCrop(crop, deltaX = -2f, deltaY = 2f),
        )
        assertEquals(
            SiteWidgetCrop(left = 0.5f, top = 0f, width = 0.5f, height = 0.5f),
            SiteWidgetCropRules.moveCrop(crop, deltaX = Float.MAX_VALUE, deltaY = -Float.MAX_VALUE),
        )
    }

    @Test
    fun `nonfinite movement and malformed crop reject`() {
        val crop = SiteWidgetCrop(left = 0f, top = 0f, width = 1f, height = 1f)
        assertNull(SiteWidgetCropRules.moveCrop(crop, deltaX = Float.NaN, deltaY = 0f))
        assertNull(SiteWidgetCropRules.moveCrop(crop, deltaX = 0f, deltaY = Float.POSITIVE_INFINITY))
        listOf(
            crop.copy(left = -0.01f),
            crop.copy(top = Float.NaN),
            crop.copy(width = Float.POSITIVE_INFINITY),
            crop.copy(height = 0f),
            crop.copy(left = 0.1f),
            crop.copy(top = 0.1f),
        ).forEach { invalid ->
            assertNull(SiteWidgetCropRules.sanitize(invalid))
            assertNull(SiteWidgetCropRules.moveCrop(invalid, deltaX = 0f, deltaY = 0f))
            assertNull(SiteWidgetCropRules.pixelBounds(invalid, bitmapWidth = 800, bitmapHeight = 600))
        }
    }

    @Test
    fun `resize preserves center and requested physical aspect`() {
        assertEquals(
            SiteWidgetCrop(left = 0.25f, top = 0.40625f, width = 0.5f, height = 0.1875f),
            SiteWidgetCropRules.resizeCrop(
                SiteWidgetCrop(left = 0f, top = 0.3125f, width = 1f, height = 0.375f),
                scale = 0.5f,
                format = SiteWidgetFormat.FourByThree,
                viewportWidth = 400,
                viewportHeight = 800,
            ),
        )
    }

    @Test
    fun `resize expansion stays in viewport and rejects invalid scale`() {
        val crop = SiteWidgetCrop(left = 0.5f, top = 0.5f, width = 0.25f, height = 0.25f)
        assertEquals(
            SiteWidgetCrop(left = 0f, top = 0f, width = 1f, height = 1f),
            SiteWidgetCropRules.resizeCrop(crop, 10f, SiteWidgetFormat.FourByFour, 400, 400),
        )
        for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(SiteWidgetCropRules.resizeCrop(crop, scale, SiteWidgetFormat.FourByFour, 400, 400))
        }
    }

    @Test
    fun `resize keeps tiny gestures above minimum selectable size`() {
        assertEquals(
            SiteWidgetCrop(left = 0.45f, top = 0.45f, width = 0.1f, height = 0.1f),
            SiteWidgetCropRules.resizeCrop(
                SiteWidgetCrop(left = 0f, top = 0f, width = 1f, height = 1f),
                scale = Float.MIN_VALUE,
                format = SiteWidgetFormat.FourByFour,
                viewportWidth = 400,
                viewportHeight = 400,
            ),
        )
    }

    @Test
    fun `pixel bounds enclose fractional selection and stay within bitmap`() {
        assertEquals(
            SiteWidgetPixelBounds(left = 1, top = 1, width = 3, height = 2),
            SiteWidgetCropRules.pixelBounds(
                SiteWidgetCrop(left = 0.125f, top = 0.25f, width = 0.25f, height = 0.375f),
                bitmapWidth = 10,
                bitmapHeight = 4,
            ),
        )
        assertEquals(
            SiteWidgetPixelBounds(left = 0, top = 0, width = 1, height = 1),
            SiteWidgetCropRules.pixelBounds(
                SiteWidgetCrop(left = 0f, top = 0f, width = 1f, height = 1f),
                bitmapWidth = 1,
                bitmapHeight = 1,
            ),
        )
    }

    @Test
    fun `invalid bitmap dimensions reject crop`() {
        val crop = SiteWidgetCrop(left = 0f, top = 0f, width = 1f, height = 1f)
        assertNull(SiteWidgetCropRules.pixelBounds(crop, bitmapWidth = 0, bitmapHeight = 1))
        assertNull(SiteWidgetCropRules.pixelBounds(crop, bitmapWidth = 1, bitmapHeight = -1))
    }
}
