package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabPersistenceRulesTest {
    @Test
    fun `persistent tabs exclude incognito tabs without reordering`() {
        val tabs = listOf(
            tab("normal-old", 1L, isPinned = true),
            tab("private", 2L, isIncognito = true),
            tab("normal-new", 3L),
        )

        assertEquals(
            listOf("normal-old", "normal-new"),
            TabPersistenceRules.persistentTabs(tabs).map(BrowserTab::id),
        )
        assertTrue(TabPersistenceRules.persistentTabs(tabs).first().isPinned)
    }

    @Test
    fun `persistent selection never points at incognito tab`() {
        val tabs = listOf(
            tab("normal-old", 1L),
            tab("private", 20L, isIncognito = true),
            tab("normal-new", 10L),
        )

        assertEquals("normal-new", TabPersistenceRules.persistentSelection(tabs, "private"))
        assertNull(TabPersistenceRules.persistentSelection(listOf(tabs[1]), "private"))
    }

    private fun tab(
        id: String,
        lastAccessedAt: Long,
        isIncognito: Boolean = false,
        isPinned: Boolean = false,
    ) = BrowserTab(
        id = id,
        lastAccessedAt = lastAccessedAt,
        isIncognito = isIncognito,
        isPinned = isPinned,
    )
}
