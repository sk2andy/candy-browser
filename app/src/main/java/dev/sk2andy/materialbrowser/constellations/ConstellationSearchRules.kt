package dev.sk2andy.materialbrowser.constellations

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

internal data class ConstellationSearchEntry(
    val profileId: String,
    val url: String,
    val normalizedContent: String,
)

/** Literal, local keyword matching over the current profile's visible graph nodes. */
internal object ConstellationSearchRules {
    const val MAX_QUERY_CHARS = 160
    private val combiningMarks = Regex("\\p{M}+")
    private val whitespace = Regex("[\\s\\p{Z}]+")

    fun boundQuery(query: String): String = query.take(MAX_QUERY_CHARS)

    fun isSearching(query: String): Boolean = normalized(boundQuery(query)).isNotEmpty()

    fun index(profileId: String, nodes: List<ConstellationGraphNode>): List<ConstellationSearchEntry> {
        if (profileId.isBlank()) return emptyList()
        return nodes.asSequence()
            .map { it.document }
            .filter { it.profileId == profileId && it.url.isNotBlank() }
            .distinctBy { it.url }
            .sortedBy { it.url }
            .take(ConstellationGraphRules.MAX_NODES)
            .map { document ->
                ConstellationSearchEntry(
                    profileId = profileId,
                    url = document.url,
                    normalizedContent = normalized(
                        "${document.title.take(512)}\n${document.url.take(8_192)}\n${document.excerpt.take(640)}",
                    ),
                )
            }.toList()
    }

    fun matchingUrls(
        profileId: String,
        index: List<ConstellationSearchEntry>,
        query: String,
    ): List<String> {
        if (profileId.isBlank()) return emptyList()
        val terms = normalized(boundQuery(query)).split(' ').filter(String::isNotEmpty)
        return index.asSequence()
            .filter { entry ->
                entry.profileId == profileId && terms.all { it in entry.normalizedContent }
            }
            .map { it.url }
            .distinct()
            .sorted()
            .toList()
    }

    fun step(matches: List<String>, currentUrl: String?, direction: Int): String? {
        if (matches.isEmpty()) return null
        val current = matches.indexOf(currentUrl)
        if (current < 0) return if (direction < 0) matches.last() else matches.first()
        val delta = if (direction < 0) -1 else if (direction > 0) 1 else 0
        return matches[(current + delta + matches.size) % matches.size]
    }

    fun focusViewport(
        node: ConstellationGraphNode,
        viewport: ConstellationGraphViewport,
        width: Float,
        height: Float,
    ): ConstellationGraphViewport {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f ||
            !node.x.isFinite() || !node.y.isFinite()
        ) return ConstellationGraphViewport()
        val bounded = ConstellationGraphRules.transformViewport(viewport, 1f, 0f, 0f)
        val scale = bounded.scale.coerceAtLeast(2f)
        val radius = min(width, height) * 0.42f * scale
        return ConstellationGraphRules.transformViewport(
            ConstellationGraphViewport(scale = scale),
            zoom = 1f,
            panX = -node.x.coerceIn(-1f, 1f) * radius,
            panY = -node.y.coerceIn(-1f, 1f) * radius,
        )
    }

    private fun normalized(value: String): String = whitespace.replace(
        combiningMarks.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
            .lowercase(Locale.ROOT),
        " ",
    ).trim()
}
