package dev.sk2andy.materialbrowser.feeds

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Read-only provider access. No cookies, accounts, local search history or background requests. */
internal class FeedCatalogClient(
    private val fetch: suspend (String) -> String = FeedCatalogNetwork::fetch,
    private val requireAccess: suspend () -> Unit = {},
) {
    suspend fun search(query: FeedCatalogQuery): FeedCatalogPage =
        withContext(Dispatchers.IO) {
            requireAccess()
            val checked = FeedCatalogRules.validate(query)
            if (checked.topic == null && checked.text.isEmpty())
                return@withContext FeedCatalogPage(emptyList(), null)
            // The provider's text-search endpoint ignores offset; never offer a false next page.
            require(checked.topic != null || checked.offset == 0) { "Search has no next page" }
            val page = if (checked.topic != null) topic(checked) else searchTerms(checked)
            val semaphore = Semaphore(4)
            val hydrated = coroutineScope {
                page.entries
                    .map { entry ->
                        async {
                            semaphore.withPermit {
                                val slug =
                                    FeedCatalogRules.safeSlug(entry.string("slug", 241))
                                        ?: return@withPermit Hydrated(null, false)
                                val fallback = source(entry, slug)
                                try {
                                    val details = request(endpoint("feeds", slug))
                                    Hydrated(source(details, slug), false)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: IOException) {
                                    Hydrated(fallback, true)
                                }
                            }
                        }
                    }
                    .awaitAll()
            }
            ensureActive()
            requireAccess()
            val sources = hydrated.mapNotNull { it.source }
            if (sources.isEmpty() && page.entries.isNotEmpty() && hydrated.any { it.failed })
                throw IOException("Catalog previews unavailable")
            FeedCatalogPage(
                sources =
                    FeedCatalogRules.unique(sources).filter {
                        checked.kind == null || it.kind == checked.kind
                    },
                nextOffset = page.nextOffset,
            )
        }

    private suspend fun topic(query: FeedCatalogQuery): Entries = coroutineScope {
        val kinds = query.kind?.let(::listOf) ?: FeedCatalogKind.entries
        val pages =
            kinds
                .map { kind ->
                    async {
                        val url =
                            endpoint("topics", requireNotNull(query.topic))
                                .toHttpUrl()
                                .newBuilder()
                                .addQueryParameter(
                                    "group",
                                    if (kind == FeedCatalogKind.Blog) "blogs" else "news",
                                )
                                .addQueryParameter(
                                    "limit",
                                    FeedCatalogRules.TOPIC_PAGE_SIZE.toString(),
                                )
                                .addQueryParameter("offset", query.offset.toString())
                                .build()
                                .toString()
                        val response =
                            try {
                                request(url)
                            } catch (error: FeedCatalogHttpException) {
                                // The provider uses 404 for a topic without sources in this group.
                                if (error.statusCode == 404) return@async Entries(emptyList(), null)
                                throw error
                            }
                        val array =
                            response.optJSONArray("feeds")
                                ?: throw IOException("Invalid catalog page")
                        val rawCount = array.length()
                        val next = query.offset + FeedCatalogRules.TOPIC_PAGE_SIZE
                        Entries(
                            array.objects(FeedCatalogRules.TOPIC_PAGE_SIZE).filter(::supportedKind),
                            next.takeIf {
                                rawCount >= FeedCatalogRules.TOPIC_PAGE_SIZE &&
                                    it <= FeedCatalogRules.MAX_OFFSET &&
                                    (response.optInt("total", -1).let { total ->
                                        total < 0 || it < total
                                    })
                            },
                        )
                    }
                }
                .awaitAll()
        Entries(
            pages.flatMap { it.entries }.distinctBy { it.string("slug", 241) },
            pages.mapNotNull { it.nextOffset }.maxOrNull(),
        )
    }

    private suspend fun searchTerms(query: FeedCatalogQuery): Entries = coroutineScope {
        val kinds = query.kind?.let(::listOf) ?: FeedCatalogKind.entries
        val pages =
            kinds
                .map { kind ->
                    async {
                        val url =
                            endpoint("search")
                                .toHttpUrl()
                                .newBuilder()
                                .addQueryParameter("q", query.text)
                                .addQueryParameter(
                                    "kind",
                                    if (kind == FeedCatalogKind.Blog) "blogs" else "news",
                                )
                                .addQueryParameter(
                                    "limit",
                                    FeedCatalogRules.SEARCH_PAGE_SIZE.toString(),
                                )
                                .build()
                                .toString()
                        val response = request(url)
                        // Despite its name this array contains news sources too. `posts` are not
                        // sources.
                        val array =
                            response.optJSONArray("blogs")
                                ?: throw IOException("Invalid catalog search")
                        array.objects(FeedCatalogRules.SEARCH_PAGE_SIZE).filter(::supportedKind)
                    }
                }
                .awaitAll()
        Entries(pages.flatten().distinctBy { it.string("slug", 240) }, null)
    }

    private suspend fun request(url: String): JSONObject {
        requireAccess()
        val text = fetch(url)
        requireAccess()
        if (text.length > FeedRules.MAX_FEED_BYTES) throw IOException("Catalog response too large")
        return try {
            JSONObject(text)
        } catch (error: JSONException) {
            throw IOException("Invalid catalog response", error)
        }
    }

    private fun source(value: JSONObject, expectedSlug: String): FeedCatalogSource? {
        val slug = FeedCatalogRules.safeSlug(value.string("slug", 241)) ?: return null
        if (slug != expectedSlug) return null
        val feedUrl = FeedCatalogRules.safeSourceUrl(value.string("feedUrl", 2049)) ?: return null
        val kind = kind(value) ?: return null
        val title = FeedParser.plain(value.string("title", 2000)).take(500)
        if (title.isBlank()) return null
        return FeedCatalogSource(
            slug = slug,
            title = title,
            description = FeedParser.plain(value.string("description", 8000)).take(1000),
            siteUrl = FeedCatalogRules.safeSourceUrl(value.string("siteUrl", 2049)).orEmpty(),
            feedUrl = feedUrl,
            language = FeedCatalogRules.language(value.string("language", 36)),
            kind = kind,
        )
    }

    private fun kind(value: JSONObject): FeedCatalogKind? =
        when (value.string("kind", 20).ifBlank { value.string("category", 20) }) {
            "blog" -> FeedCatalogKind.Blog
            "news" -> FeedCatalogKind.News
            else -> null
        }

    private fun supportedKind(value: JSONObject): Boolean = kind(value) != null

    private fun endpoint(vararg segments: String): String =
        "https://rssamplifier.com"
            .toHttpUrl()
            .newBuilder()
            .apply {
                addPathSegment("api")
                segments.forEach(::addPathSegment)
            }
            .build()
            .toString()

    private fun JSONObject.string(key: String, limit: Int): String =
        if (isNull(key)) "" else optString(key, "").take(limit)

    private fun JSONArray.objects(limit: Int): List<JSONObject> =
        (0 until minOf(length(), limit)).mapNotNull(::optJSONObject)

    private data class Entries(val entries: List<JSONObject>, val nextOffset: Int?)

    private data class Hydrated(val source: FeedCatalogSource?, val failed: Boolean)
}
