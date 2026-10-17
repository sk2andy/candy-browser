package dev.sk2andy.materialbrowser.shared.ui.settings

import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceSettingsCapabilitiesTest {
    @Test
    fun `default form retains every Android control and appearance mode`() {
        val capabilities = AppearanceSettingsCapabilities()

        assertEquals(BrowserAppearanceMode.entries, capabilities.appearanceModes)
        assertTrue(capabilities.forceDarkWebsites)
        assertTrue(capabilities.webContentFontSize)
        assertTrue(capabilities.customChrome)
    }

    @Test
    fun `core form exposes functional appearance motion and size without Android chrome`() {
        val capabilities = AppearanceSettingsCapabilities.Core

        assertEquals(
            listOf(BrowserAppearanceMode.System, BrowserAppearanceMode.Light, BrowserAppearanceMode.Dark),
            capabilities.appearanceModes,
        )
        assertFalse(capabilities.forceDarkWebsites)
        assertTrue(capabilities.webContentFontSize)
        assertFalse(capabilities.customChrome)
    }
}
