package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoBrowsingDataReloadRulesTest {
    @Test
    fun `completed clearing reloads unchanged engine session`() {
        assertTrue(
            GeckoBrowsingDataReloadRules.canReload(
                capturedUrl = "https://example.com/page",
                currentUrl = "https://example.com/page",
                capturedNavigationGeneration = 4,
                currentNavigationGeneration = 4,
                sameSession = true,
            ),
        )
    }

    @Test
    fun `completed clearing rejects stale session navigation and address`() {
        assertFalse(canReload(currentUrl = "https://example.com/other"))
        assertFalse(canReload(currentNavigationGeneration = 5))
        assertFalse(canReload(sameSession = false))
        assertFalse(canReload(currentUrl = null))
        assertFalse(
            GeckoBrowsingDataReloadRules.canReload(
                capturedUrl = "https://example.com/page",
                currentUrl = "https://example.com/page",
                capturedNavigationGeneration = null,
                currentNavigationGeneration = null,
                sameSession = true,
            ),
        )
    }

    private fun canReload(
        currentUrl: String? = "https://example.com/page",
        currentNavigationGeneration: Int? = 4,
        sameSession: Boolean = true,
    ): Boolean = GeckoBrowsingDataReloadRules.canReload(
        capturedUrl = "https://example.com/page",
        currentUrl = currentUrl,
        capturedNavigationGeneration = 4,
        currentNavigationGeneration = currentNavigationGeneration,
        sameSession = sameSession,
    )
}
