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

    @Test
    fun `latest reorder follows intervening open and replays requested order`() {
        val a = SyncTab("a", 0, 0, null, true, false, "A", "https://example.com/a")
        val b = SyncTab("b", 0, 1, null, false, false, "B", "https://example.com/b")
        val c = SyncTab("c", 0, 2, null, false, false, "C", "https://example.com/c")
        val profile = SyncProfile(
            "target", "Desktop", SyncDeviceIconDescriptor("computer", 0), 1,
            listOf(a, b), "2026-11-14T00:00:00Z",
        )
        val first = SyncPendingMutation.Reorder("first", "target", listOf("b", "a"))
        val open = SyncPendingMutation.Open("open", "target", c)
        val latest = SyncPendingMutation.Reorder("latest", "target", listOf("c", "b", "a"))

        val queued = SyncOutboxRules.enqueue(listOf(first, open), latest)
        assertEquals(listOf(open, latest), queued)
        val replayed = queued.fold(profile) { current, mutation ->
            (SyncTabRules.apply(current, mutation) as SyncMutationResult.Applied).profile
        }
        assertEquals(listOf("c", "b", "a"), replayed.tabs.map { it.candyId })
    }

    @Test
    fun `latest reorder follows intervening close`() {
        val first = SyncPendingMutation.Reorder("first", "target", listOf("b", "a"))
        val close = SyncPendingMutation.Close("close", "target", "b")
        val latest = SyncPendingMutation.Reorder("latest", "target", listOf("a"))

        assertEquals(listOf(close, latest), SyncOutboxRules.enqueue(listOf(first, close), latest))
    }

    private fun navigate(id: String, tabId: String, url: String) = SyncPendingMutation.Navigate(
        mutationId = id,
        targetDeviceId = "target",
        candyId = tabId,
        title = id,
        url = url,
    )
}
