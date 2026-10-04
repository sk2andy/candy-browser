package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoPrivacyXRayRulesTest {
    @Test
    fun `request blocker action belongs to exact ublock browser action and current tab`() {
        val action = action(tabId = "selected")
        assertEquals(
            action.key,
            GeckoPrivacyXRayRules.requestBlockerAction(listOf(action), "selected"),
        )
        assertNull(GeckoPrivacyXRayRules.requestBlockerAction(listOf(action), "other"))
        assertNull(
            GeckoPrivacyXRayRules.requestBlockerAction(
                listOf(action.copy(key = action.key.copy(extensionId = "other@extension.test"))),
                "selected",
            ),
        )
        assertNull(
            GeckoPrivacyXRayRules.requestBlockerAction(
                listOf(action.copy(key = action.key.copy(kind = GeckoExtensionActionKind.Page))),
                "selected",
            ),
        )
    }

    @Test
    fun `default browser action works while disabled and absent actions stay unavailable`() {
        val action = action(tabId = null)
        assertEquals(
            action.key,
            GeckoPrivacyXRayRules.requestBlockerAction(listOf(action), "selected"),
        )
        assertNull(
            GeckoPrivacyXRayRules.requestBlockerAction(listOf(action.copy(enabled = false)), "selected"),
        )
        assertNull(GeckoPrivacyXRayRules.requestBlockerAction(emptyList(), "selected"))
    }

    private fun action(tabId: String?) = GeckoExtensionActionState(
        key = GeckoExtensionActionKey("uBlock0@raymondhill.net", tabId, GeckoExtensionActionKind.Browser),
        title = "uBlock Origin",
        enabled = true,
        badgeText = "7",
        badgeBackgroundColor = null,
        badgeTextColor = null,
    )
}
