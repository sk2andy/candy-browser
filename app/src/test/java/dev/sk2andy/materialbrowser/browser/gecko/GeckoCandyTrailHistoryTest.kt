package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailHistoryBinding
import dev.sk2andy.materialbrowser.browser.CandyTrailHistoryReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoCandyTrailHistoryTest {
    @Test
    fun `Gecko history preserves back forward reload titles and branches`() {
        var trail: CandyTrail? = null
        var binding = CandyTrailHistoryBinding()
        var pendingTargetId: String? = null
        var visitedAt = 0L
        val tracker = GeckoCandyTrailHistoryTracker(TAB_ID) { event ->
            val result = CandyTrailHistoryReconciler.reconcile(
                trail = trail,
                tabId = event.tabId,
                previous = binding,
                snapshot = event.snapshot,
                title = event.title,
                visitedAt = ++visitedAt,
                pendingTargetNodeId = pendingTargetId,
            )
            pendingTargetId = null
            trail = result.trail
            binding = result.binding
        }

        tracker.onHistoryStateChanged(history(listOf(A), 0, "A"))
        tracker.onHistoryStateChanged(history(listOf(A, B), 1, "B"))
        val bNodeId = checkNotNull(trail).currentNodeId
        tracker.onHistoryStateChanged(history(listOf(A, B), 0, "A revisited"))
        pendingTargetId = bNodeId
        tracker.onHistoryStateChanged(history(listOf(A, B), 1, "B forward"))
        tracker.onHistoryStateChanged(history(listOf(A, B), 1, "B reloaded"))
        tracker.onHistoryStateChanged(history(listOf(A, B), 0, "A branch origin"))
        tracker.onHistoryStateChanged(history(listOf(A, C), 1, "C"))

        val result = checkNotNull(trail)
        assertEquals(3, result.nodes.size)
        assertEquals("B reloaded", result.nodes.single { node -> node.url == B }.title)
        val root = result.nodes.single { node -> node.url == A }
        assertEquals(setOf(B, C), result.nodes.filter { node -> node.parentId == root.id }
            .mapTo(mutableSetOf()) { node -> node.url })
        assertEquals(C, result.nodes.single { node -> node.id == result.currentNodeId }.url)
    }

    @Test
    fun `invalid Gecko history is ignored and equal history is a reload`() {
        val events = mutableListOf<GeckoCandyTrailHistoryEvent>()
        val tracker = GeckoCandyTrailHistoryTracker(TAB_ID, events::add)

        tracker.onHistoryStateChanged(history(listOf(A), -1, "invalid"))
        tracker.onHistoryStateChanged(history(listOf(A), 0, "A"))
        tracker.onHistoryStateChanged(history(listOf(A), 0, "A updated"))

        assertEquals(2, events.size)
        assertFalse(events.first().snapshot.isReload)
        assertTrue(events.last().snapshot.isReload)
    }

    private fun history(urls: List<String>, currentIndex: Int, title: String) =
        GeckoBrowserHistoryState(urls, currentIndex, title)

    private companion object {
        const val TAB_ID = "00000000-0000-0000-0000-000000000001"
        const val A = "https://a.example"
        const val B = "https://b.example"
        const val C = "https://c.example"
    }
}
