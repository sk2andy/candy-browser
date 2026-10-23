package dev.sk2andy.materialbrowser.constellations

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal data class ConstellationTopicGroup(
    val id: String,
    val title: String,
    val urls: Set<String>,
)

/** Presentation decisions use only the currently authorized, local graph. */
internal object ConstellationPresentationRules {
    private val titleWord = Regex("[\\p{L}\\p{N}]{3,}")
    private val stopWords = setOf(
        "the", "and", "for", "with", "from", "this", "that", "your", "how", "what", "why", "www", "com",
        "der", "die", "das", "und", "für", "mit", "von", "ein", "eine", "auf", "den", "des", "ist", "wie",
    )

    /** Never retain a deleted, changed, foreign, or disabled document while recomputing positions. */
    fun retainedGraph(snapshot: ConstellationSnapshot, previous: ConstellationGraph): ConstellationGraph {
        if (!snapshot.enabled || !snapshot.modelReady) return ConstellationGraph()
        val current = snapshot.documents.filter { it.profileId == snapshot.profileId }.associateBy { it.url }
        val nodes = previous.nodes.filter { it.document == current[it.document.url] }
        val urls = nodes.mapTo(mutableSetOf()) { it.document.url }
        return ConstellationGraph(nodes, previous.edges.filter { it.sourceUrl in urls && it.targetUrl in urls })
    }

    fun neighbors(graph: ConstellationGraph, url: String?): Set<String> = graph.edges.mapNotNullTo(mutableSetOf()) {
        when (url) {
            it.sourceUrl -> it.targetUrl
            it.targetUrl -> it.sourceUrl
            else -> null
        }
    }

    /** Components express existing similarity edges; names quote recurring words from saved titles. */
    fun groups(graph: ConstellationGraph): List<ConstellationTopicGroup> {
        val nodes = graph.nodes.associateBy { it.document.url }
        val unseen = nodes.keys.toMutableSet()
        val adjacency = nodes.keys.associateWith { mutableSetOf<String>() }
        graph.edges.forEach { edge ->
            if (edge.sourceUrl in nodes && edge.targetUrl in nodes) {
                adjacency.getValue(edge.sourceUrl) += edge.targetUrl
                adjacency.getValue(edge.targetUrl) += edge.sourceUrl
            }
        }
        val groups = mutableListOf<ConstellationTopicGroup>()
        while (unseen.isNotEmpty()) {
            val first = unseen.minOrNull() ?: break
            val pending = ArrayDeque<String>()
            val urls = sortedSetOf<String>()
            pending.add(first)
            unseen.remove(first)
            while (pending.isNotEmpty()) {
                val url = pending.removeFirst()
                urls += url
                adjacency.getValue(url).sorted().forEach { neighbor ->
                    if (unseen.remove(neighbor)) pending.add(neighbor)
                }
            }
            if (urls.size < 2) continue
            val documents = urls.map { nodes.getValue(it).document }
            val words = documents.flatMap { document ->
                titleWord.findAll(document.title.take(512)).map { it.value.lowercase(Locale.ROOT) }
                    .filter { it !in stopWords }.distinct().toList()
            }.groupingBy { it }.eachCount()
            val recurring = words.entries.filter { it.value >= 2 }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(2).map { it.key }
            val title = if (recurring.isNotEmpty()) {
                recurring.joinToString(" · ")
            } else {
                documents.maxWithOrNull(compareBy<ConstellationDocument> { adjacency.getValue(it.url).size }
                    .thenByDescending { it.url })?.let { it.title.ifBlank { it.url } }.orEmpty().take(48)
            }
            groups += ConstellationTopicGroup(first, title, urls)
        }
        return groups.sortedWith(compareByDescending<ConstellationTopicGroup> { it.urls.size }.thenBy { it.id })
    }

    /** A larger viewport/zoom earns more labels; selected pages and neighbors always win. */
    fun labelPriorities(
        graph: ConstellationGraph,
        selectedUrl: String?,
        matchedUrls: Set<String>,
        scale: Float,
        widthDp: Float,
        heightDp: Float,
        visibleUrls: Set<String> = graph.nodes.mapTo(mutableSetOf()) { it.document.url },
    ): Map<String, Int> {
        if (!scale.isFinite() || !widthDp.isFinite() || !heightDp.isFinite() || widthDp <= 0 || heightDp <= 0) {
            return emptyMap()
        }
        val neighbors = neighbors(graph, selectedUrl)
        val degree = graph.edges.flatMap { listOf(it.sourceUrl, it.targetUrl) }.groupingBy { it }.eachCount()
        val budget = (widthDp * heightDp / 12_000f * scale.coerceIn(0.6f, 5f)).toInt().coerceIn(3, 180)
        val ranked = graph.nodes.filter { it.document.url in visibleUrls }.sortedWith(
            compareByDescending<ConstellationGraphNode> { it.document.url == selectedUrl }
                .thenByDescending { it.document.url in matchedUrls }
                .thenByDescending { it.document.url in neighbors }
                .thenByDescending { degree[it.document.url] ?: 0 }
                .thenByDescending { it.document.visitedAt }
                .thenBy { it.document.url },
        )
        return ranked.take(max(budget, ranked.count {
            it.document.url == selectedUrl || it.document.url in neighbors || it.document.url in matchedUrls
        })).mapIndexed { index, node -> node.document.url to (ranked.size - index) }.toMap()
    }

    fun focusViewport(
        graph: ConstellationGraph,
        urls: Set<String>,
        width: Float,
        height: Float,
    ): ConstellationGraphViewport {
        val nodes = graph.nodes.filter { it.document.url in urls && it.x.isFinite() && it.y.isFinite() }
        if (nodes.isEmpty() || !width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
            return ConstellationGraphViewport()
        }
        val left = nodes.minOf { it.x }
        val right = nodes.maxOf { it.x }
        val top = nodes.minOf { it.y }
        val bottom = nodes.maxOf { it.y }
        val radius = min(width, height) * 0.42f
        // Product choice: reserve room for labels around the focused neighborhood.
        val scale = min(width * 0.65f / ((right - left).coerceAtLeast(0.35f) * radius),
            height * 0.65f / ((bottom - top).coerceAtLeast(0.35f) * radius)).coerceIn(0.6f, 2.5f)
        return ConstellationGraphRules.transformViewport(
            ConstellationGraphViewport(scale), 1f,
            -(left + right) / 2f * radius * scale,
            -(top + bottom) / 2f * radius * scale,
        )
    }
}
