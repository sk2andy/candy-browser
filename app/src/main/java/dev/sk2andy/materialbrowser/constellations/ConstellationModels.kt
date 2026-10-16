package dev.sk2andy.materialbrowser.constellations

internal data class ConstellationDocument(
    val profileId: String,
    val url: String,
    val title: String,
    val excerpt: String,
    val visitedAt: Long,
    val embedding: List<Float>,
)

internal data class ConstellationSnapshot(
    val profileId: String,
    val enabled: Boolean = false,
    val documents: List<ConstellationDocument> = emptyList(),
    val modelReady: Boolean = false,
    val busy: Boolean = false,
    val downloadProgress: Float? = null,
    val error: Boolean = false,
)

internal data class ConstellationGraph(
    val nodes: List<ConstellationGraphNode> = emptyList(),
    val edges: List<ConstellationGraphEdge> = emptyList(),
)

internal data class ConstellationGraphNode(
    val document: ConstellationDocument,
    val x: Float,
    val y: Float,
)

internal data class ConstellationGraphEdge(
    val sourceUrl: String,
    val targetUrl: String,
    val similarity: Float,
)

internal data class ConstellationGraphViewport(
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)
