package dev.sk2andy.materialbrowser.ui.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedEntryStateRulesTest {
    @Test
    fun `normal collection entry restores only into its exact profile and tab`() {
        val entry =
            FeedsEntry(
                libraryOnly = true,
                discoveryUrl = "https://publisher.example/feed",
                queueRequestId = 42,
            )
        val saved = requireNotNull(FeedEntryStateRules.save(entry, "profile-a", "tab-a", false))
        assertEquals(entry, FeedEntryStateRules.restore(saved, "profile-a", "tab-a", false))
        assertNull(FeedEntryStateRules.restore(saved, "profile-b", "tab-a", false))
        assertNull(FeedEntryStateRules.restore(saved, "profile-a", "tab-b", false))
        assertNull(FeedEntryStateRules.restore(saved, "profile-a", "tab-a", true))
    }

    @Test
    fun `widget article restores its collection without replaying the article launch`() {
        val entry = FeedsEntry(articleId = "a".repeat(64))
        val saved = requireNotNull(FeedEntryStateRules.save(entry, "profile", "tab", false))

        assertEquals(
            entry.copy(articleId = null),
            FeedEntryStateRules.restore(saved, "profile", "tab", false),
        )
    }

    @Test
    fun `catalog entry restores and old entries keep their inbox destination`() {
        val entry = FeedsEntry(openCatalog = true)
        val saved = requireNotNull(FeedEntryStateRules.save(entry, "profile", "tab", false))
        assertEquals(entry, FeedEntryStateRules.restore(saved, "profile", "tab", false))
        val previous = listOf("2", "profile", "tab", "false", "", "0", entry.invocationToken)
        assertEquals(
            entry.copy(openCatalog = false),
            FeedEntryStateRules.restore(previous, "profile", "tab", false),
        )
        assertNull(
            FeedEntryStateRules.restore(saved.dropLast(1) + "invalid", "profile", "tab", false)
        )
    }

    @Test
    fun `new invocation differs while copies and restored entries retain launch identity`() {
        val entry = FeedsEntry(discoveryUrl = "https://publisher.example/feed")
        assertNotEquals(
            entry.invocationToken,
            FeedsEntry(discoveryUrl = entry.discoveryUrl).invocationToken,
        )
        assertEquals(entry.invocationToken, entry.copy(discoveryUrl = null).invocationToken)
        val saved = requireNotNull(FeedEntryStateRules.save(entry, "profile", "tab", false))
        assertEquals(
            entry.invocationToken,
            FeedEntryStateRules.restore(saved, "profile", "tab", false)?.invocationToken,
        )
    }

    @Test
    fun `private navigation and discovery URL never enter saved state`() {
        val entry = FeedsEntry(discoveryUrl = "https://private.example/secret")
        assertNull(FeedEntryStateRules.save(entry, "profile", "tab", true))
        assertNull(FeedEntryStateRules.save(null, "profile", "tab", false))
        assertNull(FeedEntryStateRules.save(entry, "", "tab", false))
    }

    @Test
    fun `malformed state cannot fabricate a collection entry`() {
        assertNull(FeedEntryStateRules.restore(emptyList(), "profile", "tab", false))
        assertNull(
            FeedEntryStateRules.restore(
                listOf("3", "profile", "tab", "false", "", "0", "launch"),
                "profile",
                "tab",
                false,
            )
        )
        assertNull(
            FeedEntryStateRules.restore(
                listOf("2", "profile", "tab", "bad", "", "0", "launch"),
                "profile",
                "tab",
                false,
            )
        )
        assertNull(
            FeedEntryStateRules.restore(
                listOf("2", "profile", "tab", "false", "", "-1", "launch"),
                "profile",
                "tab",
                false,
            )
        )
        assertNull(
            FeedEntryStateRules.restore(
                listOf("2", "profile", "tab", "false", "", "999999999999", "launch"),
                "profile",
                "tab",
                false,
            )
        )
    }

    @Test
    fun `unsafe discovery URL is dropped while normal navigation remains restorable`() {
        val entry = FeedsEntry(discoveryUrl = "javascript:alert(1)")
        val saved = requireNotNull(FeedEntryStateRules.save(entry, "profile", "tab", false))
        assertEquals("", saved[4])
        assertEquals(
            entry.copy(discoveryUrl = null),
            FeedEntryStateRules.restore(saved, "profile", "tab", false),
        )
    }
}
