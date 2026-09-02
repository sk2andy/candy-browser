package dev.sk2andy.materialbrowser.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncOutboxRulesTest {
    @Test
    fun `latest navigation replaces older pending navigation for same tab`() {
        val first = navigate("first", "tab-1", "https://example.com/first")
        val second = navigate("second", "tab-1", "https://example.com/second")

        assertEquals(listOf(second), SyncOutboxRules.enqueue(listOf(first), second))
    }

    @Test
    fun `close discards obsolete navigation and pin but preserves open ordering`() {
        val open = SyncPendingMutation.Open(
            "open",
            "target",
            SyncTab("tab-1", 0, 0, null, true, false, "Tab", "https://example.com/"),
        )
        val pending = listOf(
            open,
            navigate("navigate", "tab-1", "https://example.com/next"),
            SyncPendingMutation.SetPinned("pin", "target", "tab-1", true),
        )
        val close = SyncPendingMutation.Close("close", "target", "tab-1")

        assertEquals(listOf(open, close), SyncOutboxRules.enqueue(pending, close))
    }

    @Test
    fun `mutations for different tabs and targets stay independent`() {
        val first = navigate("first", "tab-1", "https://example.com/first")
        val otherTab = navigate("other-tab", "tab-2", "https://example.com/other")
        val otherTarget = first.copy(mutationId = "other-target", targetDeviceId = "other")

        assertEquals(
            listOf(first, otherTab, otherTarget),
            SyncOutboxRules.enqueue(listOf(first, otherTab), otherTarget),
        )
    }

    private fun navigate(id: String, tabId: String, url: String) = SyncPendingMutation.Navigate(
        mutationId = id,
        targetDeviceId = "target",
        candyId = tabId,
        title = id,
        url = url,
    )
}
