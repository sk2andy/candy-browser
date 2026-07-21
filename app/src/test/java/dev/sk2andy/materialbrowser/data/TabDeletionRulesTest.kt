package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabDeletionRulesTest {
    @Test
    fun `regular tab can be deleted`() {
        assertTrue(TabDeletionRules.canDelete(tab(isPinned = false)))
    }

    @Test
    fun `pinned tab cannot be deleted`() {
        assertFalse(TabDeletionRules.canDelete(tab(isPinned = true)))
    }

    private fun tab(isPinned: Boolean) = BrowserTab(
        id = "tab",
        lastAccessedAt = 1L,
        isPinned = isPinned,
    )
}
