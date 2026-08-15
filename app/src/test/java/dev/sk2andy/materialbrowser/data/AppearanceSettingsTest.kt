package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun `defaults preserve system appearance and Material You`() {
        val settings = AppearanceSettings()

        assertTrue(settings.appearanceMode == BrowserAppearanceMode.System)
        assertTrue(settings.colorPalette == BrowserColorPalette.Dynamic)
        assertTrue(settings.surfaceStyle == BrowserSurfaceStyle.Clear)
        assertTrue(settings.shapeStyle == BrowserShapeStyle.Rounded)
        assertEquals(40, settings.frostedTransparencyPercent)
        assertEquals(40, settings.frostedAddressBarTransparencyPercent)
        assertEquals(60, settings.frostedBlurPercent)
    }

    @Test
    fun `stable ids round trip for every appearance option`() {
        BrowserAppearanceMode.entries.forEach { mode ->
            assertTrue(BrowserAppearanceMode.fromStableId(mode.stableId) == mode)
        }
        BrowserColorPalette.entries.forEach { palette ->
            assertTrue(BrowserColorPalette.fromStableId(palette.stableId) == palette)
        }
        BrowserSurfaceStyle.entries.forEach { style ->
            assertTrue(BrowserSurfaceStyle.fromStableId(style.stableId) == style)
        }
        BrowserShapeStyle.entries.forEach { style ->
            assertTrue(BrowserShapeStyle.fromStableId(style.stableId) == style)
        }
    }

    @Test
    fun `surface choices contain only clear and frosted`() {
        assertEquals(
            listOf(BrowserSurfaceStyle.Clear, BrowserSurfaceStyle.Frosted),
            BrowserSurfaceStyle.entries,
        )
    }

    @Test
    fun `unknown and removed stable ids use safe defaults`() {
        assertTrue(BrowserAppearanceMode.fromStableId("unknown") == BrowserAppearanceMode.System)
        assertTrue(BrowserColorPalette.fromStableId("unknown") == BrowserColorPalette.Dynamic)
        assertTrue(BrowserSurfaceStyle.fromStableId("unknown") == BrowserSurfaceStyle.Clear)
        assertTrue(BrowserSurfaceStyle.fromStableId("soft") == BrowserSurfaceStyle.Clear)
        assertTrue(BrowserShapeStyle.fromStableId("unknown") == BrowserShapeStyle.Rounded)
    }

    @Test
    fun `appearance mode resolves darkness independently from palette`() {
        assertFalse(
            AppearanceSettings(appearanceMode = BrowserAppearanceMode.System)
                .usesDarkColors(systemDark = false),
        )
        assertTrue(
            AppearanceSettings(appearanceMode = BrowserAppearanceMode.System)
                .usesDarkColors(systemDark = true),
        )
        assertFalse(
            AppearanceSettings(appearanceMode = BrowserAppearanceMode.Light)
                .usesDarkColors(systemDark = true),
        )
        assertTrue(
            AppearanceSettings(appearanceMode = BrowserAppearanceMode.Dark)
                .usesDarkColors(systemDark = false),
        )
        assertTrue(
            AppearanceSettings(appearanceMode = BrowserAppearanceMode.Amoled)
                .usesDarkColors(systemDark = false),
        )
    }

    @Test
    fun `frosted controls normalize to supported ranges`() {
        assertEquals(
            AppearanceSettings(
                frostedTransparencyPercent = 80,
                frostedAddressBarTransparencyPercent = 0,
                frostedBlurPercent = 0,
            ),
            AppearanceSettings(
                frostedTransparencyPercent = 200,
                frostedAddressBarTransparencyPercent = -1,
                frostedBlurPercent = -1,
            ).normalized(),
        )
    }
}
