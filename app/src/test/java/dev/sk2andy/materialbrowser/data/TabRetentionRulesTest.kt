package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabRetentionRulesTest {
    private val now = 3_000_000_000L

    @Test
    fun neverExpiresNothing() {
        assertTrue(expired(InactiveTabLifetime.Never, oldTab()).isEmpty())
    }

    @Test
    fun immediateClosesEveryTabOnBackground() {
        val selected = tab("selected", now)
        val private = oldTab("private").copy(isIncognito = true)
        val pinned = oldTab("pinned").copy(isPinned = true)

        assertEquals(
            setOf(selected.id, private.id, pinned.id),
            TabRetentionRules.tabIdsToCloseOnBackground(
                tabs = listOf(selected, private, pinned),
                lifetime = InactiveTabLifetime.Immediately,
            ),
        )
    }

    @Test
    fun timedLifetimeDoesNotCloseTabsMerelyForBackgrounding() {
        assertTrue(
            TabRetentionRules.tabIdsToCloseOnBackground(
                tabs = listOf(oldTab()),
                lifetime = InactiveTabLifetime.SixHours,
            ).isEmpty(),
        )
    }

    @Test
    fun exactBoundaryRemainsAndOneMillisecondOlderExpires() {
        val boundary = tab("boundary", now - InactiveTabLifetime.SixHours.maxAgeMillis!!)
        val expired = tab("expired", boundary.lastAccessedAt - 1)

        assertEquals(setOf("expired"), expired(InactiveTabLifetime.SixHours, boundary, expired))
    }

    @Test
    fun selectedTabNeverExpires() {
        val selected = oldTab("selected")
        val other = oldTab("other")

        assertEquals(
            setOf("other"),
            TabRetentionRules.expiredTabIds(
                tabs = listOf(selected, other),
                selectedTabId = selected.id,
                lifetime = InactiveTabLifetime.SixHours,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun invalidSelectionProtectsFreshestTab() {
        val older = oldTab("older")
        val fresher = tab("fresher", older.lastAccessedAt + 1)

        assertEquals(
            setOf("older"),
            TabRetentionRules.expiredTabIds(
                tabs = listOf(older, fresher),
                selectedTabId = "missing",
                lifetime = InactiveTabLifetime.SixHours,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun pinnedInactiveTabNeverExpires() {
        val pinned = oldTab("pinned").copy(isPinned = true)
        val regular = oldTab("regular")

        assertEquals(
            setOf("regular"),
            expired(InactiveTabLifetime.SixHours, pinned, regular),
        )
    }

    private fun expired(lifetime: InactiveTabLifetime, vararg tabs: BrowserTab): Set<String> =
        TabRetentionRules.expiredTabIds(
            tabs = listOf(tab("selected", now)) + tabs,
            selectedTabId = "selected",
            lifetime = lifetime,
            nowMillis = now,
        )

    private fun oldTab(id: String = "old") = tab(id, now - 100_000_000L)

    private fun tab(id: String, lastAccessedAt: Long) = BrowserTab(
        id = id,
        lastAccessedAt = lastAccessedAt,
    )
}
