package dev.sk2andy.materialbrowser.constellations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationPresentationRulesTest {
    @Test
    fun `retained graph immediately removes deleted changed and foreign documents`() {
        val first = node("a", "Local AI")
        val second = node("b", "Local models")
        val foreign = node("c", "Secret").let { it.copy(document = it.document.copy(profileId = "work")) }
        val previous = ConstellationGraph(listOf(first, second, foreign), listOf(edge("a", "b")))
        val snapshot = ConstellationSnapshot("personal", enabled = true, modelReady = true,
            documents = listOf(first.document.copy(title = "Changed"), foreign.document))

        assertTrue(ConstellationPresentationRules.retainedGraph(snapshot, previous).nodes.isEmpty())
        assertTrue(ConstellationPresentationRules.retainedGraph(snapshot, previous).edges.isEmpty())
        val retained = ConstellationPresentationRules.retainedGraph(snapshot.copy(documents = listOf(first.document)), previous)
        assertEquals(listOf(first), retained.nodes)
        assertTrue(retained.edges.isEmpty())
        assertTrue(ConstellationPresentationRules.retainedGraph(snapshot.copy(enabled = false), previous).nodes.isEmpty())
    }

    @Test
    fun `topics use recurring saved title words and are independent of traversal order`() {
        val nodes = listOf(node("a", "Kotlin coroutines guide"), node("b", "Kotlin coroutines explained"),
            node("c", "Alpine hiking"), node("d", "Hiking routes"), node("e", "Unconnected"))
        val graph = ConstellationGraph(nodes, listOf(edge("a", "b"), edge("c", "d")))

        val groups = ConstellationPresentationRules.groups(graph)

        assertEquals(listOf("coroutines · kotlin", "hiking"), groups.map { it.title })
        assertEquals(setOf("a", "b"), groups.first().urls)
        assertFalse(groups.any { "e" in it.urls })
        assertEquals(groups, ConstellationPresentationRules.groups(graph.copy(nodes = nodes.reversed(), edges = graph.edges.reversed())))
    }

    @Test
    fun `labels prioritize visible selection and neighborhood as zoom reveals more`() {
        val nodes = (1..60).map { node("$it", "Page $it") }
        val graph = ConstellationGraph(nodes, listOf(edge("59", "60")))
        val visible = nodes.drop(30).mapTo(mutableSetOf()) { it.document.url }
        val near = ConstellationPresentationRules.labelPriorities(graph, "60", emptySet(), 3f, 360f, 320f, visible)
        val far = ConstellationPresentationRules.labelPriorities(graph, "60", emptySet(), 0.6f, 360f, 320f, visible)

        assertEquals("60", far.maxBy { it.value }.key)
        assertTrue("59" in far)
        assertTrue(near.size > far.size)
        assertTrue(far.keys.all { it in visible })
        assertTrue(ConstellationPresentationRules.labelPriorities(graph, null, emptySet(), Float.NaN, 360f, 320f).isEmpty())
    }

    @Test
    fun `neighborhood focus includes every target within central viewport`() {
        val graph = ConstellationGraph(listOf(node("a", "A").copy(x = -0.7f, y = 0.5f),
            node("b", "B").copy(x = 0.6f, y = -0.8f)))
        val viewport = ConstellationPresentationRules.focusViewport(graph, setOf("a", "b"), 360f, 300f)
        val radius = 300f * 0.42f * viewport.scale
        graph.nodes.forEach {
            val x = 180f + it.x * radius + viewport.panX
            val y = 150f + it.y * radius + viewport.panY
            assertTrue(x in 0f..360f && y in 0f..300f)
        }
        assertEquals(ConstellationGraphViewport(), ConstellationPresentationRules.focusViewport(graph, emptySet(), 360f, 300f))
        assertEquals(ConstellationGraphViewport(), ConstellationPresentationRules.focusViewport(graph, setOf("a"), 0f, 300f))
    }

    private fun node(url: String, title: String) = ConstellationGraphNode(
        ConstellationDocument("personal", url, title, "", 1L, listOf(1f, 0f)), 0f, 0f,
    )

    private fun edge(source: String, target: String) = ConstellationGraphEdge(source, target, 0.9f)
}
