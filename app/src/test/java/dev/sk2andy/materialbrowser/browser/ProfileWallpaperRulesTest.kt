package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileWallpaperRulesTest {
    @Test
    fun `sanitize replaces invalid values and bounds transform`() {
        assertEquals(
            ProfileWallpaper(zoom = 1f, normalizedPanX = 1f, normalizedPanY = -1f),
            ProfileWallpaperRules.sanitize(
                ProfileWallpaper(
                    zoom = Float.NaN,
                    normalizedPanX = 8f,
                    normalizedPanY = -8f,
                ),
            ),
        )
    }

    @Test
    fun `layout center crops image to fill viewport`() {
        val layout = requireNotNull(
            ProfileWallpaperRules.layout(
                imageWidth = 1_600f,
                imageHeight = 900f,
                viewportWidth = 1_000f,
                viewportHeight = 1_000f,
                wallpaper = ProfileWallpaper(),
            ),
        )

        assertEquals(-388.8889f, layout.left, 0.001f)
        assertEquals(0f, layout.top, 0.001f)
        assertEquals(1_777.7778f, layout.width, 0.001f)
        assertEquals(1_000f, layout.height, 0.001f)
    }

    @Test
    fun `transform preserves pixel pan while zooming and clamps drag`() {
        val transformed = ProfileWallpaperRules.transformed(
            wallpaper = ProfileWallpaper(normalizedPanX = 0.5f),
            zoomChange = 2f,
            panX = 10_000f,
            panY = -10_000f,
            centroidX = 500f,
            centroidY = 500f,
            imageWidth = 1_000f,
            imageHeight = 1_000f,
            viewportWidth = 1_000f,
            viewportHeight = 1_000f,
        )

        assertEquals(2f, transformed.zoom, 0f)
        assertEquals(1f, transformed.normalizedPanX, 0f)
        assertEquals(-1f, transformed.normalizedPanY, 0f)
    }

    @Test
    fun `zoom keeps content below gesture centroid stable`() {
        val transformed = ProfileWallpaperRules.transformed(
            wallpaper = ProfileWallpaper(),
            zoomChange = 2f,
            panX = 0f,
            panY = 0f,
            centroidX = 250f,
            centroidY = 500f,
            imageWidth = 1_000f,
            imageHeight = 1_000f,
            viewportWidth = 1_000f,
            viewportHeight = 1_000f,
        )

        assertEquals(2f, transformed.zoom, 0f)
        assertEquals(0.5f, transformed.normalizedPanX, 0.001f)
        assertEquals(0f, transformed.normalizedPanY, 0f)
    }

    @Test
    fun `layout rejects invalid dimensions`() {
        assertNull(
            ProfileWallpaperRules.layout(
                imageWidth = 0f,
                imageHeight = 100f,
                viewportWidth = 100f,
                viewportHeight = 100f,
                wallpaper = ProfileWallpaper(),
            ),
        )
    }
}
