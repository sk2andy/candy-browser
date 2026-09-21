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
        assertEquals(100, settings.webContentFontSizePercent)
        assertEquals(BrowserColorPalette.Dynamic, settings.colorPalette)
        assertEquals(BrowserSurfaceStyle.Clear, settings.surfaceStyle)
        assertEquals(BrowserShapeStyle.Rounded, settings.shapeStyle)
        assertEquals(BrowserAddressBarStyle.Classic, settings.addressBarStyle)
        assertEquals(BrowserAddressBarColorPreset.Theme, settings.addressBarColorPreset)
        assertEquals("", settings.addressBarCustomColorHex)
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
        BrowserAddressBarStyle.entries.forEach { style ->
            assertEquals(style, BrowserAddressBarStyle.fromStableId(style.stableId))
        }
        BrowserAddressBarColorPreset.entries.forEach { preset ->
            assertEquals(preset, BrowserAddressBarColorPreset.fromStableId(preset.stableId))
        }

        assertEquals(BrowserAppearanceMode.System, BrowserAppearanceMode.fromStableId("unknown"))
        assertEquals(BrowserColorPalette.Dynamic, BrowserColorPalette.fromStableId("unknown"))
        assertEquals(BrowserSurfaceStyle.Clear, BrowserSurfaceStyle.fromStableId("unknown"))
        assertEquals(BrowserShapeStyle.Rounded, BrowserShapeStyle.fromStableId("unknown"))
        assertEquals(
            BrowserAddressBarStyle.Classic,
            BrowserAddressBarStyle.fromStableId("unknown"),
        )
        assertEquals(
            BrowserAddressBarColorPreset.Theme,
            BrowserAddressBarColorPreset.fromStableId("unknown"),
        )
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

    @Test
    fun `website font size normalizes to supported five percent steps`() {
        assertEquals(
            50,
            AppearanceSettings(webContentFontSizePercent = -1)
                .normalized()
                .webContentFontSizePercent,
        )
        assertEquals(
            125,
            AppearanceSettings(webContentFontSizePercent = 123)
                .normalized()
                .webContentFontSizePercent,
        )
        assertEquals(
            200,
            AppearanceSettings(webContentFontSizePercent = 201)
                .normalized()
                .webContentFontSizePercent,
        )
    }

    @Test
    fun `custom address bar colors normalize supported hex forms`() {
        assertEquals("#AABBCC", AddressBarColorRules.normalizeHex(" #abc "))
        assertEquals("#12ABEF", AddressBarColorRules.normalizeHex("12abef"))
        assertEquals(0xFF12ABEFL, AddressBarColorRules.colorArgb("#12abef"))
    }

    @Test
    fun `invalid custom address bar color falls back to theme`() {
        val normalized = AppearanceSettings(
            addressBarColorPreset = BrowserAddressBarColorPreset.Custom,
            addressBarCustomColorHex = "not-a-color",
        ).normalized()

        assertEquals(BrowserAddressBarColorPreset.Theme, normalized.addressBarColorPreset)
        assertEquals("", normalized.addressBarCustomColorHex)
    }
}
