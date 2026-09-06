package dev.sk2andy.materialbrowser.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapsuleIconCropRulesTest {
    @Test
    fun `layout center crops wide image to square viewport`() {
        val layout = requireNotNull(
            CapsuleIconCropRules.layout(
                imageWidth = 1_600f,
                imageHeight = 900f,
                viewportSize = 1_000f,
                crop = CapsuleIconCrop(),
            ),
        )

        assertEquals(-388.8889f, layout.left, 0.001f)
        assertEquals(0f, layout.top, 0.001f)
        assertEquals(1_777.7778f, layout.width, 0.001f)
        assertEquals(1_000f, layout.height, 0.001f)
    }

    @Test
    fun `transform keeps focal content stable and clamps pan`() {
        val transformed = CapsuleIconCropRules.transformed(
            crop = CapsuleIconCrop(),
            zoomChange = 2f,
            panX = 10_000f,
            panY = -10_000f,
            centroidX = 250f,
            centroidY = 500f,
            imageWidth = 1_000f,
            imageHeight = 1_000f,
            viewportSize = 1_000f,
        )

        assertEquals(2f, transformed.zoom, 0f)
        assertEquals(1f, transformed.normalizedPanX, 0f)
        assertEquals(-1f, transformed.normalizedPanY, 0f)
    }

    @Test
    fun `sanitize replaces non finite values and bounds crop`() {
        assertEquals(
            CapsuleIconCrop(zoom = 1f, normalizedPanX = 1f, normalizedPanY = -1f),
            CapsuleIconCropRules.sanitize(
                CapsuleIconCrop(
                    zoom = Float.NaN,
                    normalizedPanX = 8f,
                    normalizedPanY = -8f,
                ),
            ),
        )
    }

    @Test
    fun `layout rejects invalid dimensions`() {
        assertNull(
            CapsuleIconCropRules.layout(
                imageWidth = 0f,
                imageHeight = 100f,
                viewportSize = 100f,
                crop = CapsuleIconCrop(),
            ),
        )
    }
}
