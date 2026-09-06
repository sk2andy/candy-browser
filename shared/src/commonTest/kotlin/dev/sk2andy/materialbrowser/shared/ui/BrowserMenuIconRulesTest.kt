package dev.sk2andy.materialbrowser.shared.ui

import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserMenuIconRulesTest {
    @Test
    fun `pin uses outline until selected`() {
        assertFalse(
            BrowserMenuIconRules.useFilledVariant(
                action = BrowserFeatureMenuAction.TogglePinned,
                selected = false,
            ),
        )
        assertTrue(
            BrowserMenuIconRules.useFilledVariant(
                action = BrowserFeatureMenuAction.TogglePinned,
                selected = true,
            ),
        )
    }

    @Test
    fun `favorite preserves selection and static actions remain filled`() {
        assertFalse(
            BrowserMenuIconRules.useFilledVariant(
                action = BrowserFeatureMenuAction.ToggleFavorite,
                selected = false,
            ),
        )
        assertTrue(
            BrowserMenuIconRules.useFilledVariant(
                action = BrowserFeatureMenuAction.ToggleFavorite,
                selected = true,
            ),
        )
        assertTrue(
            BrowserMenuIconRules.useFilledVariant(
                action = BrowserFeatureMenuAction.Share,
                selected = false,
            ),
        )
    }
}
