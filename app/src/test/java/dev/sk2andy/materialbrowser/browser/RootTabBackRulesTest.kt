package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class RootTabBackRulesTest {
    @Test
    fun `single website returns to home before system back`() {
        assertEquals(
            RootTabBackDecision.ReturnToHome,
            RootTabBackRules.decide(
                tabs = listOf(tab(id = "selected", url = "https://example.com/first")),
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `pinned website returns to home without closing sibling tabs`() {
        assertEquals(
            RootTabBackDecision.ReturnToHome,
            RootTabBackRules.decide(
                tabs = listOf(
                    tab(id = "sibling"),
                    tab(
                        id = "selected",
                        isPinned = true,
                        url = "https://example.com/first",
                    ),
                ),
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `single active tab delegates back to system`() {
        assertEquals(
            RootTabBackDecision.DelegateToSystem,
            RootTabBackRules.decide(
                tabs = listOf(tab(id = "selected")),
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `pinned tab delegates back to system without hiding sibling tabs`() {
        assertEquals(
            RootTabBackDecision.DelegateToSystem,
            RootTabBackRules.decide(
                tabs = listOf(
                    tab(id = "sibling"),
                    tab(
                        id = "selected",
                        isPinned = true,
                    ),
                ),
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `pinned tab returns to active opener before home without closing`() {
        assertEquals(
            RootTabBackDecision.ReturnToOpener,
            RootTabBackRules.decide(
                tabs = listOf(
                    tab(id = "opener", url = "https://example.com/source"),
                    tab(
                        id = "selected",
                        openerTabId = "opener",
                        isPinned = true,
                        url = "https://example.com/child",
                    ),
                ),
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `pinned tab with closed opener returns home`() {
        assertEquals(
            RootTabBackDecision.ReturnToHome,
            RootTabBackRules.decide(
                tabs = listOf(
                    tab(
                        id = "selected",
                        openerTabId = "closed",
                        isPinned = true,
                        url = "https://example.com/child",
                    ),
                ),
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `tab with active opener closes and returns to opener`() {
        assertEquals(
            RootTabBackDecision.CloseAndReturnToOpener,
            RootTabBackRules.decide(
                tabs = listOf(
                    tab(id = "opener"),
                    tab(id = "selected", openerTabId = "opener"),
                ),
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `tab without active opener closes into overview when sibling exists`() {
        assertEquals(
            RootTabBackDecision.CloseAndShowTabOverview,
            RootTabBackRules.decide(
                tabs = listOf(
                    tab(id = "sibling"),
                    tab(id = "selected", openerTabId = "missing"),
                ),
                selectedTabId = "selected",
            ),
        )
    }

    @Test
    fun `missing selection delegates back to system`() {
        assertEquals(
            RootTabBackDecision.DelegateToSystem,
            RootTabBackRules.decide(
                tabs = listOf(tab(id = "sibling")),
                selectedTabId = "missing",
            ),
        )
    }

    private fun tab(
        id: String,
        openerTabId: String? = null,
        isPinned: Boolean = false,
        url: String = BLANK_URL,
    ) = BrowserTab(
        id = id,
        lastAccessedAt = 1L,
        openerTabId = openerTabId,
        isPinned = isPinned,
        url = url,
    )
}
