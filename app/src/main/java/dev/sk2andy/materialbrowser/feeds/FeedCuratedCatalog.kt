package dev.sk2andy.materialbrowser.feeds

import java.net.URI
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

/** Bundled metadata only: searching never opens a network connection or changes subscriptions. */
internal class FeedCuratedCatalog(private val readAsset: () -> String) {
    private val entries by lazy {
        val array = JSONObject(readAsset()).getJSONArray("sources")
        (0 until array.length())
            .mapNotNull { index ->
                val item = array.getJSONObject(index)
                val url =
                    FeedCatalogRules.safeSourceUrl(item.getString("feedUrl"))
                        ?: return@mapNotNull null
                val title = FeedParser.plain(item.getString("title")).take(500)
                if (title.isBlank()) return@mapNotNull null
                Entry(
                    source =
                        FeedCatalogSource(
                            slug = item.getString("slug"),
                            title = title,
                            description =
                                FeedParser.plain(item.optString("description")).take(1000),
                            siteUrl =
                                FeedCatalogRules.safeSourceUrl(item.optString("siteUrl")).orEmpty(),
                            feedUrl = url,
                            language = "",
                            kind = null,
                        ),
                    topics =
                        item.getJSONArray("topics").let { values ->
                            (0 until values.length()).map(values::getString).toSet()
                        },
                    tags =
                        item.getJSONArray("tags").let { values ->
                            (0 until values.length()).joinToString(" ") { values.getString(it) }
                        },
                )
            }
            .distinctBy { FeedRules.identity(it.source.feedUrl) }
    }

    fun search(query: FeedCatalogQuery): FeedCatalogPage {
        val checked = FeedCatalogRules.validate(query)
        val term = normalize(checked.text)
        val tokens = term.split(' ').filter(String::isNotEmpty)
        val ranked =
            entries
                .asSequence()
                .filter { checked.topic == null || checked.topic in it.topics }
                .mapNotNull { entry ->
                    val title = normalize(entry.source.title)
                    val domains =
                        listOf(entry.source.feedUrl, entry.source.siteUrl)
                            .filter(String::isNotBlank)
                            .map { normalize(URI(it).host.orEmpty().removePrefix("www.")) }
                    val haystack =
                        "$title ${domains.joinToString(" ")} ${normalize(entry.tags)} ${normalize(entry.source.description)}"
                    if (tokens.any { it !in haystack }) return@mapNotNull null
                    val score =
                        when {
                            term.isEmpty() -> 0
                            title == term ||
                                domains.any { it == term || it.substringBefore('.') == term } -> 100
                            title.startsWith(term) -> 80
                            term in title -> 60
                            domains.any { term in it } -> 40
                            else -> 10
                        }
                    entry.source to score
                }
                .sortedWith(
                    compareByDescending<Pair<FeedCatalogSource, Int>> { it.second }
                        .thenBy { normalize(it.first.title) }
                        .thenBy { it.first.feedUrl }
                )
                .map { it.first }
                .toList()
        val end = (checked.offset + PAGE_SIZE).coerceAtMost(ranked.size)
        return FeedCatalogPage(
            ranked.drop(checked.offset).take(PAGE_SIZE),
            end.takeIf { it < ranked.size },
        )
    }

    private data class Entry(
        val source: FeedCatalogSource,
        val topics: Set<String>,
        val tags: String,
    )

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace("ß", "ss")
            .trim()
            .replace(Regex("\\s+"), " ")

    private companion object {
        const val PAGE_SIZE = 40
    }
}
