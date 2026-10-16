package dev.sk2andy.materialbrowser.constellations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstellationGraphRulesTest {
    @Test
    fun `graph excludes every other profile even when embeddings match`() {
        val graph = ConstellationGraphRules.build(
            "personal",
            listOf(page("one"), page("secret", profileId = "work"), page("two")),
        )

        assertEquals(listOf("https://one.example", "https://two.example"), graph.nodes.map { it.document.url })
        assertEquals(1, graph.edges.size)
        assertFalse(graph.edges.any { "secret" in it.sourceUrl || "secret" in it.targetUrl })
        assertTrue(ConstellationGraphRules.build("", listOf(page("one"))).nodes.isEmpty())
    }

    @Test
    fun `similar content connects while orthogonal and opposite content stays unrelated`() {
        val graph = ConstellationGraphRules.build(
            "personal",
            listOf(
                page("one"),
                page("similar", vector = listOf(0.9f, 0.1f)),
                page("other", vector = listOf(0f, 1f)),
                page("opposite", vector = listOf(-1f, 0f)),
            ),
        )

        assertEquals(4, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        assertEquals("https://one.example", graph.edges.single().sourceUrl)
        assertEquals("https://similar.example", graph.edges.single().targetUrl)
        assertTrue(graph.edges.single().similarity > 0.99f)
    }

    @Test
    fun `weak relationships never get forced into graph`() {
        val graph = ConstellationGraphRules.build(
            "personal",
            listOf(page("one"), page("weak", vector = listOf(0.6f, 0.8f))),
        )

        assertEquals(2, graph.nodes.size)
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `strongest relationships win and every node has bounded degree`() {
        val graph = ConstellationGraphRules.build(
            "personal",
            List(14) { page("page$it", vector = listOf(1f, it / 100f)) },
        )
        val degrees = graph.edges.flatMap { listOf(it.sourceUrl, it.targetUrl) }.groupingBy { it }.eachCount()

        assertTrue(graph.edges.isNotEmpty())
        assertTrue(degrees.values.all { it <= 3 })
        assertTrue(graph.edges.all { it.similarity >= 0.62f })
        assertEquals(graph.edges.sortedByDescending { it.similarity }, graph.edges)
    }

    @Test
    fun `malformed vectors get excluded without poisoning valid neighbors`() {
        val graph = ConstellationGraphRules.build(
            "personal",
            listOf(
                page("valid"),
                page("valid-two"),
                page("nan", vector = listOf(Float.NaN, 1f)),
                page("infinity", vector = listOf(Float.POSITIVE_INFINITY, 1f)),
                page("empty", vector = emptyList()),
                page("zero", vector = listOf(0f, 0f)),
                page("wrong-size", vector = listOf(1f, 0f, 0f)),
                page("huge", vector = List(2_049) { 1f }),
            ),
        )

        assertEquals(setOf("valid", "valid-two"), graph.nodes.map { it.document.title }.toSet())
        assertEquals(1, graph.edges.size)
    }

    @Test
    fun `normalization tolerates finite extreme float values`() {
        val graph = ConstellationGraphRules.build(
            "personal",
            listOf(
                page("one", vector = listOf(Float.MAX_VALUE, Float.MAX_VALUE)),
                page("two", vector = listOf(Float.MIN_VALUE, Float.MIN_VALUE)),
            ),
        )

        assertEquals(1, graph.edges.size)
        assertEquals(1f, graph.edges.single().similarity, 0.0001f)
        assertTrue(graph.nodes.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun `same input regardless of order gives identical bounded finite layout`() {
        val pages = List(30) { page("page$it", vector = listOf(1f, it / 4f)) }
        val graph = ConstellationGraphRules.build("personal", pages)

        assertEquals(graph, ConstellationGraphRules.build("personal", pages.reversed()))
        assertTrue(graph.nodes.all { it.x in -1f..1f && it.y in -1f..1f })
        assertEquals(graph.nodes.size, graph.nodes.map { it.x to it.y }.distinct().size)
    }

    @Test
    fun `newest page version wins and retained page count stays bounded`() {
        val pages = List(185) { page("page$it", visitedAt = it.toLong()) } +
            page("page184", visitedAt = 999L).copy(title = "Updated title")
        val graph = ConstellationGraphRules.build("personal", pages)

        assertEquals(180, graph.nodes.size)
        assertFalse(graph.nodes.any { it.document.visitedAt < 5L })
        assertEquals("Updated title", graph.nodes.single { it.document.url == "https://page184.example" }.document.title)
    }

    @Test
    fun `viewport clamps malformed zoom and pan and retains zoom focal point`() {
        val zoomed = ConstellationGraphRules.transformViewport(
            ConstellationGraphViewport(), zoom = 2f, panX = 0f, panY = 0f, focusX = 50f, focusY = 30f,
        )
        assertEquals(ConstellationGraphViewport(scale = 2f, panX = -50f, panY = -30f), zoomed)

        val malformed = ConstellationGraphRules.transformViewport(
            ConstellationGraphViewport(Float.NaN, Float.NaN, Float.NEGATIVE_INFINITY),
            zoom = Float.NaN, panX = Float.POSITIVE_INFINITY, panY = Float.NaN,
        )
        assertEquals(ConstellationGraphViewport(), malformed)
        val bounded = ConstellationGraphRules.transformViewport(zoomed, 100f, 100_000f, -100_000f)
        assertEquals(ConstellationGraphViewport(5f, 4_000f, -4_000f), bounded)
    }

    private fun page(
        name: String,
        profileId: String = "personal",
        vector: List<Float> = listOf(1f, 0f),
        visitedAt: Long = 1L,
    ) = ConstellationDocument(profileId, "https://$name.example", name, "", visitedAt, vector)
}
