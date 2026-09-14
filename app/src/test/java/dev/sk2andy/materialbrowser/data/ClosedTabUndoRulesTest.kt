package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.BrowserTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ClosedTabUndoRulesTest {
    @Test
    fun `undo keeps pins first after remaining tab is pinned`() {
        val result = requireNotNull(restore(
            listOf(tab("last").copy(isPinned = true)), "last", token(),
        ))

        assertEquals(listOf("last", "closed"), result.tabs.map(BrowserTab::id))
    }

    @Test
    fun `undo restores original position and selected tab`() {
        val token = token(originalIndex = 1)
        val result = requireNotNull(restore(listOf(tab("first"), tab("last")), "last", token))

        assertEquals(listOf("first", "closed", "last"), result.tabs.map(BrowserTab::id))
        assertEquals("closed", result.selectedTabId)
    }

    @Test
    fun `undo removes only untouched replacement with matching privacy`() {
        val token = token(replacementTabId = "replacement").copy(
            selectedTabIdAfterClose = "replacement",
        )
        val result = requireNotNull(restore(listOf(tab("replacement")), "replacement", token))

        assertEquals(listOf("closed"), result.tabs.map(BrowserTab::id))
        assertEquals("replacement", result.removedReplacementTabId)
        assertEquals("closed", result.selectedTabId)
        val changedReplacement = tab("replacement").copy(url = "https://example.com/new")
        assertNull(requireNotNull(restore(listOf(changedReplacement), "replacement", token))
            .removedReplacementTabId)
        assertNull(requireNotNull(restore(
            listOf(tab("replacement").copy(isIncognito = true)), "replacement", token,
        )).removedReplacementTabId)
        assertNull(requireNotNull(restore(
            listOf(tab("replacement").copy(isPinned = true)), "replacement", token,
        )).removedReplacementTabId)
    }

    @Test
    fun `undo preserves selection changed after closing`() {
        val result = requireNotNull(restore(listOf(tab("last"), tab("new")), "new", token()))

        assertEquals("new", result.selectedTabId)
    }

    @Test
    fun `closing background tab does not change selection on undo`() {
        val result = requireNotNull(restore(listOf(tab("last")), "last", token().copy(
            wasSelected = false,
        )))

        assertEquals("last", result.selectedTabId)
    }

    @Test
    fun `undo expires at exactly three seconds and rejects backwards clock`() {
        assertNull(restore(listOf(tab("last")), "last", token(), nowMillis = 4_000))
        assertNull(restore(listOf(tab("last")), "last", token(), nowMillis = 999))
        assertEquals("closed", requireNotNull(restore(
            listOf(tab("last")), "last", token(), nowMillis = 3_999,
        )).selectedTabId)
    }

    @Test
    fun `undo rejects duplicate id and another active profile`() {
        assertNull(restore(listOf(tab("closed")), "closed", token()))
        assertNull(ClosedTabUndoRules.restore(
            tabs = listOf(tab("last")),
            selectedTabId = "last",
            activeProfileId = "other",
            token = token(),
            nowMillis = 1_001,
        ))
    }

    @Test
    fun `private tab keeps identity and privacy but resets renderer state`() {
        val original = tab("closed").copy(
            isIncognito = true,
            url = "https://example.com/private",
            title = "Private page",
            progress = 80,
            isLoading = true,
            canGoBack = true,
            canGoForward = true,
            blockedCount = 7,
            error = "old error",
            httpStatusCode = 500,
        )
        val result = requireNotNull(restore(listOf(tab("last")), "last", token().copy(
            tab = original,
        )))

        assertEquals(original.copy(
            progress = 0,
            isLoading = false,
            canGoBack = false,
            canGoForward = false,
            blockedCount = 0,
            error = null,
            httpStatusCode = null,
        ), result.tabs.first())
        assertFalse(result.tabs.first().isLoading)
    }

    private fun tab(id: String) = BrowserTab(id = id, lastAccessedAt = 100)

    private fun token(originalIndex: Int = 0, replacementTabId: String? = null) =
        ClosedTabUndoToken(
            tab = tab("closed"),
            originalIndex = originalIndex,
            wasSelected = true,
            selectedTabIdAfterClose = "last",
            replacementTabId = replacementTabId,
            closedAtMillis = 1_000,
        )

    private fun restore(
        tabs: List<BrowserTab>,
        selectedTabId: String,
        token: ClosedTabUndoToken,
        nowMillis: Long = 1_001,
    ) = ClosedTabUndoRules.restore(
        tabs = tabs,
        selectedTabId = selectedTabId,
        activeProfileId = token.tab.profileId,
        token = token,
        nowMillis = nowMillis,
    )
}
