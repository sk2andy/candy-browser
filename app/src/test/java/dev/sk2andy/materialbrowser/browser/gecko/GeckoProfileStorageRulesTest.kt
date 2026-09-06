package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoProfileStorageRulesTest {
    private val regular = BrowserTab(id = "regular", lastAccessedAt = 1L, profileId = "work")

    @Test
    fun `profile deletion closes only that profile including its private sessions`() {
        val private = regular.copy(id = "private", isIncognito = true)
        val other = regular.copy(id = "other", profileId = "personal")

        assertEquals(
            setOf("regular", "private"),
            GeckoProfileStorageRules.affectedTabIds(listOf(regular, private, other), "work"),
        )
    }

    @Test
    fun `profile and private-mode changes require a fresh session`() {
        assertTrue(GeckoProfileStorageRules.contextChanged(regular, regular.copy(profileId = "personal")))
        assertTrue(GeckoProfileStorageRules.contextChanged(regular, regular.copy(isIncognito = true)))
        assertFalse(GeckoProfileStorageRules.contextChanged(regular, regular.copy(title = "Updated")))
    }

    @Test
    fun `private privacy events never share the regular profile storage key`() {
        assertEquals("work", GeckoProfileStorageRules.privacyStorageKey(regular))
        assertNotEquals(
            GeckoProfileStorageRules.privacyStorageKey(regular),
            GeckoProfileStorageRules.privacyStorageKey(regular.copy(isIncognito = true)),
        )
    }

    @Test
    fun `regular profiles share default context while isolated and private profiles are named`() {
        assertEquals(null, GeckoProfileStorageRules.contextId("work", false, false))
        assertEquals("work", GeckoProfileStorageRules.contextId("work", true, false))
        assertEquals("private:work", GeckoProfileStorageRules.contextId("work", false, true))
        assertEquals("private:work", GeckoProfileStorageRules.contextId("work", true, true))
        assertFalse(GeckoProfileStorageRules.requiresContextDeletion(false))
        assertTrue(GeckoProfileStorageRules.requiresContextDeletion(true))
    }

    @Test
    fun `moving a profile preserves tab identity and unrelated profiles`() {
        val other = regular.copy(id = "other", profileId = "personal")
        assertEquals(
            listOf(regular.copy(profileId = "archive"), other),
            GeckoProfileStorageRules.movedTabs(listOf(regular, other), "work", "archive"),
        )
    }
}
