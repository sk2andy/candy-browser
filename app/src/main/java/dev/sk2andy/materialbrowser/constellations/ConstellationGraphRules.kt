package dev.sk2andy.materialbrowser.constellations

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Bounded semantic relationships and deterministic, content-anchored graph layout. */
internal object ConstellationGraphRules {
    const val MAX_NODES = 180
    const val MAX_DEGREE = 3
    const val MIN_SIMILARITY = 0.62f
    private const val MAX_DIMENSIONS = 2_048
    private const val LAYOUT_ITERATIONS = 72

    fun build(profileId: String, documents: List<ConstellationDocument>): ConstellationGraph {
        if (profileId.isBlank()) return ConstellationGraph()
        val candidates = documents.asSequence()
            .filter { it.profileId == profileId && it.url.isNotBlank() && validEmbedding(it.embedding) }
            .sortedWith(compareByDescending<ConstellationDocument> { it.visitedAt }.thenBy { it.url })
            .distinctBy { it.url }
            .take(MAX_NODES)
            .toList()
        val dimensions = candidates.groupingBy { it.embedding.size }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key ?: return ConstellationGraph()
        val pages = candidates.filter { it.embedding.size == dimensions }.sortedBy { it.url }
        val vectors = pages.map { normalized(it.embedding) }
        val edges = strongestEdges(pages, vectors)
        return ConstellationGraph(nodes = layout(pages, vectors, edges), edges = edges)
    }

    fun transformViewport(
        viewport: ConstellationGraphViewport,
        zoom: Float,
        panX: Float,
        panY: Float,
        focusX: Float = 0f,
        focusY: Float = 0f,
    ): ConstellationGraphViewport {
        val oldScale = viewport.scale.takeIf { it.isFinite() }?.coerceIn(0.6f, 5f) ?: 1f
        val scale = (oldScale * zoom.takeIf { it.isFinite() && it > 0f }.orDefault(1f))
            .coerceIn(0.6f, 5f)
        val ratio = scale / oldScale
        fun pan(previous: Float, delta: Float, focus: Float): Float = (
            previous.takeIf(Float::isFinite).orDefault(0f) * ratio +
                focus.takeIf(Float::isFinite).orDefault(0f) * (1f - ratio) +
                delta.takeIf(Float::isFinite).orDefault(0f)
            ).coerceIn(-4_000f, 4_000f)
        return ConstellationGraphViewport(
            scale = scale,
            panX = pan(viewport.panX, panX, focusX),
            panY = pan(viewport.panY, panY, focusY),
        )
    }

    private fun Float?.orDefault(fallback: Float): Float = this ?: fallback

    private fun validEmbedding(values: List<Float>): Boolean =
        values.size in 2..MAX_DIMENSIONS && values.all(Float::isFinite) &&
            values.any { it != 0f }

    private fun normalized(values: List<Float>): List<Double> {
        val magnitude = sqrt(values.sumOf { it.toDouble() * it })
        return values.map { it / magnitude }
    }

    private fun strongestEdges(
        pages: List<ConstellationDocument>,
        vectors: List<List<Double>>,
    ): List<ConstellationGraphEdge> {
        val candidates = buildList {
            for (left in pages.indices) {
                for (right in left + 1 until pages.size) {
                    val similarity = vectors[left].indices.sumOf { vectors[left][it] * vectors[right][it] }
                        .toFloat().coerceIn(-1f, 1f)
                    if (similarity >= MIN_SIMILARITY) {
                        add(ConstellationGraphEdge(pages[left].url, pages[right].url, similarity))
                    }
                }
            }
        }.sortedWith(
            compareByDescending<ConstellationGraphEdge> { it.similarity }
                .thenBy { it.sourceUrl }.thenBy { it.targetUrl },
        )
        val degrees = mutableMapOf<String, Int>()
        return candidates.filter { edge ->
            val leftDegree = degrees[edge.sourceUrl] ?: 0
            val rightDegree = degrees[edge.targetUrl] ?: 0
            if (leftDegree >= MAX_DEGREE || rightDegree >= MAX_DEGREE) {
                false
            } else {
                degrees[edge.sourceUrl] = leftDegree + 1
                degrees[edge.targetUrl] = rightDegree + 1
                true
            }
        }
    }

    private fun layout(
        pages: List<ConstellationDocument>,
        vectors: List<List<Double>>,
        edges: List<ConstellationGraphEdge>,
    ): List<ConstellationGraphNode> {
        val anchors = pages.mapIndexed { index, page ->
            val angle = (page.url.hashCode().toLong() and 0xffffL) / 65_536.0 * Math.PI * 2
            val projectedX = vectors[index].mapIndexed { dimension, value ->
                value * if (mixedBits(dimension, 17) and 1 == 0) 1 else -1
            }.sum()
            val projectedY = vectors[index].mapIndexed { dimension, value ->
                value * if (mixedBits(dimension, 71) and 1 == 0) 1 else -1
            }.sum()
            doubleArrayOf(tanh(projectedX) * 0.65 + cos(angle) * 0.12, tanh(projectedY) * 0.65 + sin(angle) * 0.12)
        }
        val positions = anchors.map(DoubleArray::clone)
        val indices = pages.mapIndexed { index, page -> page.url to index }.toMap()
        repeat(LAYOUT_ITERATIONS) {
            val forces = Array(pages.size) { DoubleArray(2) }
            for (left in pages.indices) {
                for (right in left + 1 until pages.size) {
                    var dx = positions[left][0] - positions[right][0]
                    var dy = positions[left][1] - positions[right][1]
                    if (dx * dx + dy * dy < 0.00001) {
                        dx = 0.002 * (left + 1)
                        dy = -0.002 * (right + 1)
                    }
                    val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01)
                    val strength = (0.0012 / (distance * distance)).coerceAtMost(0.06)
                    forces[left][0] += dx / distance * strength
                    forces[left][1] += dy / distance * strength
                    forces[right][0] -= dx / distance * strength
                    forces[right][1] -= dy / distance * strength
                }
            }
            edges.forEach { edge ->
                val left = indices.getValue(edge.sourceUrl)
                val right = indices.getValue(edge.targetUrl)
                val dx = positions[right][0] - positions[left][0]
                val dy = positions[right][1] - positions[left][1]
                val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01)
                val strength = (distance - (0.18 + (1 - edge.similarity) * 0.4)) * 0.12
                forces[left][0] += dx / distance * strength
                forces[left][1] += dy / distance * strength
                forces[right][0] -= dx / distance * strength
                forces[right][1] -= dy / distance * strength
            }
            pages.indices.forEach { index ->
                repeat(2) { axis ->
                    val anchorForce = (anchors[index][axis] - positions[index][axis]) * 0.04
                    positions[index][axis] = (positions[index][axis] +
                        (forces[index][axis] + anchorForce).coerceIn(-0.06, 0.06)).coerceIn(-1.0, 1.0)
                }
            }
        }
        return pages.mapIndexed { index, page ->
            ConstellationGraphNode(page, positions[index][0].toFloat(), positions[index][1].toFloat())
        }
    }

    private fun mixedBits(dimension: Int, seed: Int): Int {
        var value = dimension * 0x45d9f3b + seed
        value = (value xor (value ushr 16)) * 0x45d9f3b
        return value xor (value ushr 16)
    }
}
