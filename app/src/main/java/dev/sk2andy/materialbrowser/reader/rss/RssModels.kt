package dev.sk2andy.materialbrowser.reader.rss

import android.util.Xml
import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class RssArticle(
    val id: String,
    val title: String,
    val url: String,
    val summary: String,
    val publishedAt: Long,
)

internal data class RssFeed(
    val id: String,
    val title: String,
    val feedUrl: String,
    val siteUrl: String,
    val notify: Boolean = false,
    val articles: List<RssArticle> = emptyList(),
    val lastUpdatedAt: Long = 0L,
)

internal data class ParsedRssFeed(
    val title: String,
    val articles: List<RssArticle>,
)

internal object RssFeedRules {
    const val MAX_FEEDS = 50
    const val MAX_ARTICLES_PER_FEED = 40
    const val MAX_TITLE_CHARS = 300
    const val MAX_SUMMARY_CHARS = 4_000
    const val MAX_FEED_BYTES = 2 * 1024 * 1024

    fun safeHttpUrl(value: String?): String? {
        val candidate = value?.trim()?.takeIf { it.length in 1..2_048 } ?: return null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return uri.normalize().toASCIIString()
    }

    fun articleId(guid: String?, url: String?, title: String): String =
        (guid?.takeIf(String::isNotBlank) ?: url?.takeIf(String::isNotBlank) ?: title)
            .take(2_048)

    fun cleanText(value: String?, limit: Int): String = HtmlCompat
        .fromHtml(value.orEmpty(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(limit)
}

internal object RssFeedParser {
    fun parse(xml: String, baseUrl: String): ParsedRssFeed? = runCatching {
        if (xml.length > RssFeedRules.MAX_FEED_BYTES) return null
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }
        parser.next()
        var feedTitle = ""
        val articles = mutableListOf<RssArticle>()
        var depth = 0
        var currentTag = ""
        var itemDepth = -1
        var title = ""
        var link = ""
        var guid = ""
        var summary = ""
        var published = 0L
        var currentText = StringBuilder()
        while (true) {
            val event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    currentTag = parser.name.lowercase()
                    currentText = StringBuilder()
                    if (currentTag == "item" || currentTag == "entry") {
                        itemDepth = depth
                        title = ""
                        link = ""
                        guid = ""
                        summary = ""
                        published = 0L
                    } else if (itemDepth > 0 && currentTag == "link") {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        val rel = parser.getAttributeValue(null, "rel").orEmpty()
                        if (href.isNotBlank() && (rel == "alternate" || link.isBlank())) link = href
                    } else if (itemDepth > 0 && currentTag in setOf("description", "summary", "content", "encoded")) {
                        currentText = StringBuilder()
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    if (depth > 0) currentText.append(parser.text.orEmpty())
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    val value = currentText.toString()
                    if (itemDepth > 0) {
                        when {
                            depth == itemDepth + 1 && tag == "title" -> title = value
                            depth == itemDepth + 1 && tag == "link" -> link = value.ifBlank { link }
                            depth == itemDepth + 1 && tag in setOf("guid", "id") -> guid = value
                            depth == itemDepth + 1 && tag in setOf("description", "summary", "content", "encoded") -> {
                                if (summary.isBlank()) summary = value
                            }
                            depth == itemDepth + 1 && tag in setOf("pubdate", "published", "updated") ->
                                published = parsePublishedAt(value)
                        }
                        if (depth == itemDepth && tag in setOf("item", "entry")) {
                            val safeLink = RssFeedRules.safeHttpUrl(resolveUrl(baseUrl, link))
                            val safeTitle = RssFeedRules.cleanText(title, RssFeedRules.MAX_TITLE_CHARS)
                            if (safeLink != null && safeTitle.isNotBlank()) {
                                articles += RssArticle(
                                    id = RssFeedRules.articleId(guid, safeLink, safeTitle),
                                    title = safeTitle,
                                    url = safeLink,
                                    summary = RssFeedRules.cleanText(summary, RssFeedRules.MAX_SUMMARY_CHARS),
                                    publishedAt = published,
                                )
                            }
                            itemDepth = -1
                        }
                    } else if (tag == "title" && feedTitle.isBlank()) {
                        feedTitle = RssFeedRules.cleanText(value, RssFeedRules.MAX_TITLE_CHARS)
                    }
                    depth--
                    currentText = StringBuilder()
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        ParsedRssFeed(
            title = feedTitle.ifBlank { URI(baseUrl).host.orEmpty() },
            articles = articles.distinctBy(RssArticle::id).take(RssFeedRules.MAX_ARTICLES_PER_FEED),
        )
    }.getOrNull()

    private fun resolveUrl(baseUrl: String, value: String): String =
        runCatching { URI(baseUrl).resolve(value.trim()).toString() }.getOrDefault(value)

    private fun parsePublishedAt(value: String): Long {
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).parse(value.trim())?.time
            }.getOrNull()
        } ?: Date().time
    }
}
