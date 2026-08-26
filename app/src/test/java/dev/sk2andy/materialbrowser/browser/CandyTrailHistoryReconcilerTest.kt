package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class CandyTrailHistoryReconcilerTest {
    @Test
    fun `restore merge remaps runtime binding so back reactivates existing node`() {
        val restored = CandyTrail(
            tabId = TAB_ID,
            nodes = listOf(node("n0", null, "https://a.example", 1L)),
            currentNodeId = "n0",
            nextOrdinal = 1L,
        )
        val runtime = CandyTrail(
            tabId = TAB_ID,
            nodes = listOf(
                node("n0", null, "https://a.example", 2L),
                node("n1", "n0", "https://b.example", 3L),
            ),
            currentNodeId = "n1",
            nextOrdinal = 2L,
        )
        val runtimeBinding = CandyTrailHistoryBinding(
            entries = listOf(
                CandyTrailHistoryEntry("https://a.example", "n0"),
                CandyTrailHistoryEntry("https://b.example", "n1"),
            ),
            currentIndex = 1,
        )
        val merge = CandyTrailRules.mergeRestoredWithRuntime(restored, runtime)
        val remapped = CandyTrailHistoryReconciler.remapNodeIds(
            runtimeBinding,
            merge.runtimeNodeIds,
        )

        val result = CandyTrailHistoryReconciler.reconcile(
            trail = merge.trail,
            tabId = TAB_ID,
            previous = remapped,
            snapshot = CandyTrailHistorySnapshot(
                urls = listOf("https://a.example", "https://b.example"),
                currentIndex = 0,
            ),
            title = "A",
            visitedAt = 4L,
        )

        assertEquals(2, result.trail.nodes.size)
        assertEquals(merge.runtimeNodeIds["n0"], result.trail.currentNodeId)
    }
    @Test
    fun `web history back and replacement creates sibling branch`() {
        val first = reconcile(null, CandyTrailHistoryBinding(), listOf(A), 0, 1L)
        val second = reconcile(first.trail, first.binding, listOf(A, B), 1, 2L)
        val back = reconcile(second.trail, second.binding, listOf(A, B), 0, 3L)
        val branched = reconcile(back.trail, back.binding, listOf(A, C), 1, 4L)

        assertEquals(3, branched.trail.nodes.size)
        val rootId = branched.trail.nodes.first { it.url == A }.id
        assertEquals(setOf(B, C), branched.trail.nodes.filter { it.parentId == rootId }.map { it.url }.toSet())
    }

    @Test
    fun `forward traversal reactivates bound node`() {
        val first = reconcile(null, CandyTrailHistoryBinding(), listOf(A), 0, 1L)
        val second = reconcile(first.trail, first.binding, listOf(A, B), 1, 2L)
        val back = reconcile(second.trail, second.binding, listOf(A, B), 0, 3L)
        val forward = CandyTrailHistoryReconciler.reconcile(
            trail = back.trail,
            tabId = TAB_ID,
            previous = back.binding,
            snapshot = CandyTrailHistorySnapshot(listOf(A, B), 1),
            title = B,
            visitedAt = 4L,
            pendingTargetNodeId = second.trail.currentNodeId,
        )

        assertEquals(2, forward.trail.nodes.size)
        assertEquals(second.trail.currentNodeId, forward.trail.currentNodeId)
    }

    @Test
    fun `new navigation to same forward url creates a distinct branch`() {
        val first = reconcile(null, CandyTrailHistoryBinding(), listOf(A), 0, 1L)
        val second = reconcile(first.trail, first.binding, listOf(A, B), 1, 2L)
        val back = reconcile(second.trail, second.binding, listOf(A, B), 0, 3L)
        val replacement = reconcile(back.trail, back.binding, listOf(A, B), 1, 4L)

        assertEquals(3, replacement.trail.nodes.size)
        assertEquals(2, replacement.trail.nodes.count { it.url == B })
    }

    @Test
    fun `same url push state creates a distinct page state`() {
        val first = reconcile(null, CandyTrailHistoryBinding(), listOf(A), 0, 1L)
        val pushed = reconcile(first.trail, first.binding, listOf(A, A), 1, 2L)

        assertEquals(2, pushed.trail.nodes.size)
        assertEquals(A, pushed.trail.nodes.last().url)
        assertEquals(pushed.trail.nodes.first().id, pushed.trail.nodes.last().parentId)
    }

    @Test
    fun `restored current url binds without phantom node`() {
        val restored = CandyTrailRules.recordNavigation(null, TAB_ID, A, "A", 1L)
        val result = reconcile(restored, CandyTrailHistoryBinding(), listOf(A), 0, 2L)

        assertEquals(1, result.trail.nodes.size)
        assertEquals(restored.currentNodeId, result.binding.entries.single().nodeId)
    }

    @Test
    fun `pending graph target binds duplicate web entry to existing node`() {
        val first = reconcile(null, CandyTrailHistoryBinding(), listOf(A), 0, 1L)
        val second = reconcile(first.trail, first.binding, listOf(A, B), 1, 2L)
        val targetId = first.trail.currentNodeId!!
        val jumped = CandyTrailHistoryReconciler.reconcile(
            trail = second.trail,
            tabId = TAB_ID,
            previous = second.binding,
            snapshot = CandyTrailHistorySnapshot(listOf(A, B, A), 2),
            title = "A",
            visitedAt = 3L,
            pendingTargetNodeId = targetId,
        )

        assertEquals(2, jumped.trail.nodes.size)
        assertEquals(targetId, jumped.trail.currentNodeId)
        assertEquals(targetId, jumped.binding.entries.last().nodeId)
    }

    @Test
    fun `removed trail nodes are detached from web history binding`() {
        val binding = CandyTrailHistoryBinding(
            entries = listOf(
                CandyTrailHistoryEntry(A, "n0"),
                CandyTrailHistoryEntry(B, "n1"),
            ),
            currentIndex = 1,
        )

        assertEquals(
            listOf("n0", null),
            CandyTrailHistoryReconciler.retainNodeIds(binding, setOf("n0"))
                .entries
                .map(CandyTrailHistoryEntry::nodeId),
        )
    }

    private fun reconcile(
        trail: CandyTrail?,
        binding: CandyTrailHistoryBinding,
        urls: List<String>,
        index: Int,
        at: Long,
    ) = CandyTrailHistoryReconciler.reconcile(
        trail = trail,
        tabId = TAB_ID,
        previous = binding,
        snapshot = CandyTrailHistorySnapshot(urls, index),
        title = urls[index],
        visitedAt = at,
    )

    private fun node(id: String, parentId: String?, url: String, at: Long) =
        CandyTrailNode(id, parentId, url, url, at)

    private companion object {
        const val TAB_ID = "00000000-0000-0000-0000-000000000001"
        const val A = "https://a.example"
        const val B = "https://b.example"
        const val C = "https://c.example"
    }
}
