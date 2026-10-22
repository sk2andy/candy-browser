package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.reader.rss.RssArticle
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssFeedRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class RssFeedStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    fun feeds(): List<RssFeed> = synchronized(LOCK) {
        runCatching {
            val root = JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            val items = root.optJSONArray("feeds") ?: JSONArray()
            buildList {
                for (index in 0 until minOf(items.length(), RssFeedRules.MAX_FEEDS)) {
                    items.optJSONObject(index)?.let(::decodeFeed)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(feed: RssFeed): Boolean = synchronized(LOCK) {
        val current = feeds()
        if (current.any { it.feedUrl == feed.feedUrl }) return false
        write((current + feed).takeLast(RssFeedRules.MAX_FEEDS))
    }

    fun remove(feedId: String): Boolean = synchronized(LOCK) {
        write(feeds().filterNot { it.id == feedId })
    }

    fun setNotifications(feedId: String, enabled: Boolean): Boolean = synchronized(LOCK) {
        val current = feeds()
        write(current.map { feed -> if (feed.id == feedId) feed.copy(notify = enabled) else feed })
    }

    /** Returns articles that were not present before this refresh. */
    fun update(feedId: String, title: String, articles: List<RssArticle>): List<RssArticle> =
        synchronized(LOCK) {
            val current = feeds()
            val feed = current.firstOrNull { it.id == feedId } ?: return emptyList()
            val previousIds = feed.articles.mapTo(mutableSetOf(), RssArticle::id)
            val merged = (articles + feed.articles)
                .distinctBy(RssArticle::id)
                .take(RssFeedRules.MAX_ARTICLES_PER_FEED)
            if (write(
                    current.map {
                        if (it.id == feedId) {
                            it.copy(
                                title = title.ifBlank { it.title }.take(RssFeedRules.MAX_TITLE_CHARS),
                                articles = merged,
                                lastUpdatedAt = System.currentTimeMillis(),
                            )
                        } else {
                            it
                        }
                    },
                )
            ) {
                articles.filterNot { it.id in previousIds }
            } else {
                emptyList()
            }
        }

    private fun write(feeds: List<RssFeed>): Boolean = runCatching {
        val root = JSONObject().put(
            "feeds",
            JSONArray().apply { feeds.forEach { put(encodeFeed(it)) } },
        )
        val stream = file.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
            generation.value++
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
        true
    }.getOrDefault(false)

    private fun encodeFeed(feed: RssFeed): JSONObject = JSONObject()
        .put("id", feed.id)
        .put("title", feed.title)
        .put("feedUrl", feed.feedUrl)
        .put("siteUrl", feed.siteUrl)
        .put("notify", feed.notify)
        .put("lastUpdatedAt", feed.lastUpdatedAt)
        .put("articles", JSONArray().apply { feed.articles.forEach { put(encodeArticle(it)) } })

    private fun encodeArticle(article: RssArticle): JSONObject = JSONObject()
        .put("id", article.id)
        .put("title", article.title)
        .put("url", article.url)
        .put("summary", article.summary)
        .put("publishedAt", article.publishedAt)

    private fun decodeFeed(json: JSONObject): RssFeed? {
        val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
        val feedUrl = RssFeedRules.safeHttpUrl(json.optString("feedUrl")) ?: return null
        val articlesJson = json.optJSONArray("articles") ?: JSONArray()
        val articles = buildList {
            for (index in 0 until minOf(articlesJson.length(), RssFeedRules.MAX_ARTICLES_PER_FEED)) {
                articlesJson.optJSONObject(index)?.let(::decodeArticle)?.let(::add)
            }
        }
        return RssFeed(
            id = id,
            title = json.optString("title").take(RssFeedRules.MAX_TITLE_CHARS),
            feedUrl = feedUrl,
            siteUrl = RssFeedRules.safeHttpUrl(json.optString("siteUrl")).orEmpty(),
            notify = json.optBoolean("notify"),
            articles = articles,
            lastUpdatedAt = json.optLong("lastUpdatedAt"),
        )
    }

    private fun decodeArticle(json: JSONObject): RssArticle? {
        val id = json.optString("id").takeIf(String::isNotBlank) ?: return null
        val url = RssFeedRules.safeHttpUrl(json.optString("url")) ?: return null
        val title = json.optString("title").take(RssFeedRules.MAX_TITLE_CHARS)
            .takeIf(String::isNotBlank) ?: return null
        return RssArticle(
            id = id,
            title = title,
            url = url,
            summary = json.optString("summary").take(RssFeedRules.MAX_SUMMARY_CHARS),
            publishedAt = json.optLong("publishedAt"),
        )
    }

    companion object {
        private const val FILE_NAME = "rss-feeds.json"
        private val LOCK = Any()
        private val generation = MutableStateFlow(0L)
        val changes = generation.asStateFlow()
    }
}
