package dev.sk2andy.materialbrowser.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceSettingsTest {
    @Test
    fun `defaults preserve system appearance and Material You`() {
        val settings = AppearanceSettings()

        assertEquals(BrowserAppearanceMode.System, settings.appearanceMode)
        assertFalse(settings.forceDarkWebsites)
        assertEquals(BrowserColorPalette.Dynamic, settings.colorPalette)
        assertEquals(BrowserSurfaceStyle.Clear, settings.surfaceStyle)
        assertEquals(BrowserShapeStyle.Rounded, settings.shapeStyle)
        assertEquals(40, settings.frostedTransparencyPercent)
        assertEquals(40, settings.frostedAddressBarTransparencyPercent)
        assertEquals(60, settings.frostedBlurPercent)
    }

    @Test
    fun `stable ids round trip and unknown values use safe defaults`() {
        BrowserAppearanceMode.entries.forEach { mode ->
            assertEquals(mode, BrowserAppearanceMode.fromStableId(mode.stableId))
        }
        BrowserColorPalette.entries.forEach { palette ->
            assertEquals(palette, BrowserColorPalette.fromStableId(palette.stableId))
        }
        BrowserSurfaceStyle.entries.forEach { style ->
            assertEquals(style, BrowserSurfaceStyle.fromStableId(style.stableId))
        }
        BrowserShapeStyle.entries.forEach { style ->
            assertEquals(style, BrowserShapeStyle.fromStableId(style.stableId))
        }

        assertEquals(BrowserAppearanceMode.System, BrowserAppearanceMode.fromStableId("unknown"))
        assertEquals(BrowserColorPalette.Dynamic, BrowserColorPalette.fromStableId("unknown"))
        assertEquals(BrowserSurfaceStyle.Clear, BrowserSurfaceStyle.fromStableId("unknown"))
        assertEquals(BrowserShapeStyle.Rounded, BrowserShapeStyle.fromStableId("unknown"))
    }

    @Test
    fun `appearance mode resolves darkness independently from palette`() {
        assertFalse(AppearanceSettings().usesDarkColors(systemDark = false))
        assertTrue(AppearanceSettings().usesDarkColors(systemDark = true))
        assertFalse(
            AppearanceSettings(appearanceMode = BrowserAppearanceMode.Light)
                .usesDarkColors(systemDark = true),
        )
        assertTrue(
            AppearanceSettings(appearanceMode = BrowserAppearanceMode.Dark)
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
