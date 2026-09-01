package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FindInPageRulesTest {
    @Test
    fun `new query resets matches and starts counting`() {
        val state = FindInPageState(
            tabId = "tab",
            query = "old",
            activeMatchOrdinal = 2,
            matchCount = 4,
            isDoneCounting = true,
        )

        val updated = FindInPageRules.withQuery(state, "new")

        assertEquals("new", updated.query)
        assertNull(updated.activeMatchOrdinal)
        assertEquals(0, updated.matchCount)
        assertFalse(updated.isDoneCounting)
    }

    @Test
    fun `empty query clears matches without counting`() {
        val updated = FindInPageRules.withQuery(
            state = FindInPageState(
                tabId = "tab",
                query = "old",
                activeMatchOrdinal = 1,
                matchCount = 2,
                isDoneCounting = false,
            ),
            query = "",
        )

        assertNull(updated.activeMatchOrdinal)
        assertEquals(0, updated.matchCount)
        assertTrue(updated.isDoneCounting)
        assertFalse(FindInPageRules.canNavigate(updated))
    }

    @Test
    fun `unchanged query preserves current result`() {
        val state = FindInPageState(
            tabId = "tab",
            query = "candy",
            activeMatchOrdinal = 1,
            matchCount = 3,
        )

        assertSame(state, FindInPageRules.withQuery(state, "candy"))
    }

    @Test
    fun `result normalizes count and ordinal`() {
        val state = FindInPageState(tabId = "tab", query = "candy")

        val upper = FindInPageRules.withResult(
            state = state,
            activeMatchOrdinal = 20,
            matchCount = 3,
            isDoneCounting = false,
        )
        val empty = FindInPageRules.withResult(
            state = state,
            activeMatchOrdinal = -4,
            matchCount = -2,
            isDoneCounting = true,
        )
        val lower = FindInPageRules.withResult(
            state = state,
            activeMatchOrdinal = -4,
            matchCount = 3,
            isDoneCounting = true,
        )

        assertEquals(2, upper.activeMatchOrdinal)
        assertEquals(3, upper.matchCount)
        assertFalse(upper.isDoneCounting)
        assertNull(empty.activeMatchOrdinal)
        assertEquals(0, empty.matchCount)
        assertTrue(empty.isDoneCounting)
        assertEquals(0, lower.activeMatchOrdinal)
    }

    @Test
    fun `empty query rejects stale result`() {
        val state = FindInPageState(tabId = "tab")

        assertSame(
            state,
            FindInPageRules.withResult(
                state = state,
                activeMatchOrdinal = 1,
                matchCount = 4,
                isDoneCounting = true,
            ),
        )
    }

    @Test
    fun `display position is one based only when match exists`() {
        assertEquals(
            FindInPageMatchPosition(activeMatchNumber = 2, matchCount = 4),
            FindInPageRules.displayPosition(
                FindInPageState(
                    tabId = "tab",
                    query = "candy",
                    activeMatchOrdinal = 1,
                    matchCount = 4,
                ),
            ),
        )
        assertEquals(
            FindInPageMatchPosition(activeMatchNumber = 0, matchCount = 0),
            FindInPageRules.displayPosition(FindInPageState(tabId = "tab")),
        )
        assertEquals(
            FindInPageMatchPosition(activeMatchNumber = 3, matchCount = 3),
            FindInPageRules.displayPosition(
                FindInPageState(
                    tabId = "tab",
                    query = "candy",
                    activeMatchOrdinal = 20,
                    matchCount = 3,
                ),
            ),
        )
    }

    @Test
    fun `navigation requires nonempty query and at least one match`() {
        assertFalse(
            FindInPageRules.canNavigate(
                FindInPageState(tabId = "tab", query = "", matchCount = 2),
            ),
        )
        assertFalse(
            FindInPageRules.canNavigate(
                FindInPageState(tabId = "tab", query = "candy", matchCount = 0),
            ),
        )
        assertTrue(
            FindInPageRules.canNavigate(
                FindInPageState(tabId = "tab", query = "candy", matchCount = 1),
            ),
        )
    }
}
