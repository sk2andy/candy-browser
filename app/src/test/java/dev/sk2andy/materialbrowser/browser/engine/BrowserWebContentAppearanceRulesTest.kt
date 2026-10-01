package dev.sk2andy.materialbrowser.browser.engine

import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserWebContentAppearanceRulesTest {
    @Test
    fun `system appearance forwards concrete current website preference`() {
        val settings = AppearanceSettings(appearanceMode = BrowserAppearanceMode.System)

        assertEquals(
            BrowserWebContentColorScheme.Light,
            BrowserWebContentAppearanceRules.colorScheme(settings, systemDark = false),
        )
        assertEquals(
            BrowserWebContentColorScheme.Dark,
            BrowserWebContentAppearanceRules.colorScheme(settings, systemDark = true),
        )
    }

    @Test
    fun `explicit light dark and amoled choices override both system preferences`() {
        listOf(false, true).forEach { systemDark ->
            assertEquals(
                BrowserWebContentColorScheme.Light,
                BrowserWebContentAppearanceRules.colorScheme(
                    AppearanceSettings(appearanceMode = BrowserAppearanceMode.Light),
                    systemDark,
                ),
            )
            listOf(BrowserAppearanceMode.Dark, BrowserAppearanceMode.Amoled).forEach { mode ->
                assertEquals(
                    BrowserWebContentColorScheme.Dark,
                    BrowserWebContentAppearanceRules.colorScheme(
                        AppearanceSettings(appearanceMode = mode),
                        systemDark,
                    ),
                )
            }
        }
    }
}
