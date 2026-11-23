package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedTabQuickAccessRulesTest {
    @Test
    fun `returns existing pinned IDs in active order without mixing profile or mode`() {
        val tabs = listOf(
            tab("regular-second"),
            tab("other-profile", profileId = "other"),
            tab("unpinned", isPinned = false),
            tab("private", isIncognito = true),
            tab("regular-first"),
        )

        assertEquals(
            listOf("regular-second", "regular-first"),
            PinnedTabQuickAccessRules.eligibleTabs(tabs, "active", isIncognito = false).map(BrowserTab::id),
        )
        assertEquals(
            listOf("private"),
            PinnedTabQuickAccessRules.eligibleTabs(tabs, "active", isIncognito = true).map(BrowserTab::id),
        )
        assertEquals(
            emptyList<BrowserTab>(),
            PinnedTabQuickAccessRules.eligibleTabs(tabs, "missing", isIncognito = false),
        )
    }

    private fun tab(
        id: String,
        profileId: String = "active",
        isIncognito: Boolean = false,
        isPinned: Boolean = true,
    ): BrowserTab = BrowserTab(
        id = id,
        lastAccessedAt = 0,
        profileId = profileId,
        isIncognito = isIncognito,
        isPinned = isPinned,
    )
}
