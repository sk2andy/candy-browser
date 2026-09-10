package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuCapabilities
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuState
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserMainMenuIconRulesTest {
    @Test
    fun `website toggles use purpose-specific icons`() {
        val items = BrowserFeatureMenuRules.items(
            state = BrowserFeatureMenuState(
                hasPage = true,
                canToggleCookieBannerRemoval = true,
                canToggleForceVerticalScrolling = true,
                canToggleForcePageZooming = true,
                canToggleForceSafeArea = true,
                canToggleAlwaysBlockPopups = true,
                canToggleDesktopView = true,
            ),
            capabilities = BrowserFeatureMenuCapabilities(),
        )
        val expectedIcons = mapOf(
            BrowserFeatureMenuAction.ToggleCookieBannerRemoval to R.drawable.ic_symbol_cookie,
            BrowserFeatureMenuAction.ToggleForceVerticalScrolling to
                R.drawable.ic_symbol_vertical_scroll,
            BrowserFeatureMenuAction.ToggleForcePageZooming to R.drawable.ic_symbol_zoom_in,
            BrowserFeatureMenuAction.ToggleForceSafeArea to R.drawable.ic_symbol_fit_screen,
            BrowserFeatureMenuAction.ToggleAlwaysBlockPopups to R.drawable.ic_symbol_block,
            BrowserFeatureMenuAction.ToggleDesktopView to R.drawable.ic_symbol_desktop,
        )

        val actualIcons = items
            .filter { it.action in expectedIcons }
            .associate { it.action to it.androidDrawableResource() }

        assertEquals(expectedIcons, actualIcons)
    }
}
