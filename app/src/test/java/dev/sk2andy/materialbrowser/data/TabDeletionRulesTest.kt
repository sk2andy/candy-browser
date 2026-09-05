package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
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

    @Test
    fun `bulk deletion keeps pinned tabs`() {
        assertEquals(
            setOf("regular", "private"),
            TabDeletionRules.deletableTabIds(
                listOf(
                    tab(id = "pinned", isPinned = true),
                    tab(id = "regular", isPinned = false),
                    tab(id = "private", isPinned = false, isIncognito = true),
                ),
            ),
        )
    }

    private fun tab(
        id: String = "tab",
        isPinned: Boolean,
        isIncognito: Boolean = false,
    ) = BrowserTab(
        id = id,
        lastAccessedAt = 1L,
        isPinned = isPinned,
        isIncognito = isIncognito,
    )
}
