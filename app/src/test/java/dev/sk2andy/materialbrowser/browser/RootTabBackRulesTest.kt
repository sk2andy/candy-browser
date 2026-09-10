package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class RootTabBackRulesTest {
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
                        openerTabId = "sibling",
                        isPinned = true,
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
    ) = BrowserTab(
        id = id,
        lastAccessedAt = 1L,
        openerTabId = openerTabId,
        isPinned = isPinned,
    )
}
