package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyTrailRulesTest {
    @Test
    fun `restore merge keeps persisted branches and all runtime navigation`() {
        val restored = CandyTrail(
            tabId = "tab",
            nodes = listOf(
                node("n0", null, "https://root.example", 1L),
                node("n1", "n0", "https://old.example", 2L),
            ),
            currentNodeId = "n0",
            nextOrdinal = 2L,
        )
        val runtime = CandyTrail(
            tabId = "tab",
            nodes = listOf(
                node("n0", null, "https://root.example", 3L),
                node("n1", "n0", "https://redirect.example", 4L),
                node("n2", "n1", "https://live.example", 5L),
            ),
            currentNodeId = "n2",
            nextOrdinal = 3L,
        )

        val mergeResult = CandyTrailRules.mergeRestoredWithRuntime(restored, runtime)
        val merged = mergeResult.trail

        assertEquals(4, merged.nodes.size)
        assertTrue(merged.nodes.any { it.url == "https://old.example" })
        val redirect = merged.nodes.single { it.url == "https://redirect.example" }
        val live = merged.nodes.single { it.url == "https://live.example" }
        assertEquals(restored.currentNodeId, redirect.parentId)
        assertEquals(redirect.id, live.parentId)
        assertEquals(live.id, merged.currentNodeId)
        assertEquals(restored.currentNodeId, mergeResult.runtimeNodeIds["n0"])
        assertEquals(live.id, mergeResult.runtimeNodeIds["n2"])
    }

    @Test
    fun `back to earlier node then navigation preserves both branches`() {
        val root = record(null, "https://a.example", 1L)
        val firstBranch = record(root, "https://b.example", 2L)
        val backAtRoot = CandyTrailRules.selectNode(firstBranch, "n0", 3L)!!
        val secondBranch = record(backAtRoot, "https://c.example", 4L)

        assertEquals("n2", secondBranch.currentNodeId)
        assertEquals(
            setOf("https://b.example", "https://c.example"),
            secondBranch.nodes.filter { it.parentId == "n0" }.mapTo(mutableSetOf()) { it.url },
        )
    }

    @Test
    fun `same url can represent distinct states on different branches`() {
        val root = record(null, "https://a.example", 1L)
        val first = record(root, "https://same.example", 2L)
        val back = CandyTrailRules.selectNode(first, "n0", 3L)!!
        val second = record(back, "https://same.example", 4L)

        assertEquals(3, second.nodes.size)
        assertEquals(2, second.nodes.count { it.url == "https://same.example" })
    }

    @Test
    fun `reload updates current metadata without adding a node`() {
        val trail = record(null, "https://a.example", 1L)
        val updated = CandyTrailRules.recordNavigation(
            current = trail,
            tabId = TAB_ID,
            url = "https://a.example",
            title = "Updated",
            visitedAt = 9L,
        )

        assertEquals(1, updated.nodes.size)
        assertEquals("Updated", updated.nodes.single().title)
        assertEquals(9L, updated.nodes.single().visitedAt)
    }

    @Test
    fun `retention removes oldest unprotected branch and protects active ancestry`() {
        var trail = record(null, "https://root.example", 1L)
        trail = record(trail, "https://old.example", 2L)
        trail = CandyTrailRules.selectNode(trail, "n0", 3L)!!
        trail = record(trail, "https://active.example", 4L)
        trail = record(trail, "https://leaf.example", 5L)

        val retained = CandyTrailRules.retain(trail, maxNodes = 3)

        assertEquals(listOf("n0", "n2", "n3"), retained.nodes.map { it.id })
        assertEquals("n3", retained.currentNodeId)
    }

    @Test
    fun `overlong active path is rerooted deterministically`() {
        var trail: CandyTrail? = null
        repeat(5) { index -> trail = record(trail, "https://$index.example", index.toLong()) }

        val retained = CandyTrailRules.retain(trail!!, maxNodes = 2)

        assertEquals(listOf("n3", "n4"), retained.nodes.map { it.id })
        assertNull(retained.nodes.first().parentId)
        assertEquals("n3", retained.nodes.last().parentId)
    }

    @Test
    fun `limits strings and rejects non web urls`() {
        val rejected = record(null, "about:blank", 1L)
        assertTrue(rejected.nodes.isEmpty())

        val accepted = CandyTrailRules.recordNavigation(
            current = null,
            tabId = TAB_ID,
            url = "https://example.com/" + "u".repeat(3_000),
            title = "t".repeat(300),
            visitedAt = 2L,
        )
        assertEquals(CandyTrailRules.MAX_URL_LENGTH, accepted.nodes.single().url.length)
        assertEquals(CandyTrailRules.MAX_TITLE_LENGTH, accepted.nodes.single().title.length)
    }

    @Test
    fun `missing selection leaves graph unchanged`() {
        val trail = record(null, "https://a.example", 1L)
        assertNull(CandyTrailRules.selectNode(trail, "missing", 2L))
        assertFalse(CandyTrailRules.parentNodeId(trail) != null)
    }

    @Test
    fun `normalization repairs cycles and missing parents`() {
        val normalized = CandyTrailRules.normalized(
            CandyTrail(
                tabId = TAB_ID,
                nodes = listOf(
                    CandyTrailNode("a", "b", "https://a.example", "A", 1L),
                    CandyTrailNode("b", "a", "https://b.example", "B", 2L),
                    CandyTrailNode("c", "missing", "https://c.example", "C", 3L),
                ),
                currentNodeId = "b",
            ),
        )

        assertTrue(normalized.nodes.all { it.parentId == null })
        assertEquals("b", normalized.currentNodeId)
    }

    private fun record(current: CandyTrail?, url: String, at: Long) =
        CandyTrailRules.recordNavigation(current, TAB_ID, url, url, at)

    private fun node(id: String, parentId: String?, url: String, at: Long) =
        CandyTrailNode(id, parentId, url, url, at)

    private companion object {
        const val TAB_ID = "00000000-0000-0000-0000-000000000001"
    }
}
