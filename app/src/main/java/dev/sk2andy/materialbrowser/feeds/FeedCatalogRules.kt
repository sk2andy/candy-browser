package dev.sk2andy.materialbrowser.feeds

import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object FeedCatalogRules {
    const val SEARCH_PAGE_SIZE = 12
    const val TOPIC_PAGE_SIZE = 16
    const val MAX_OFFSET = 10_000
    const val MAX_QUERY_LENGTH = 200
    private val slugPattern = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}_.-]{0,239}")
    private val topicPattern = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    private val languagePattern = Regex("[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8})*")

    fun validate(query: FeedCatalogQuery): FeedCatalogQuery {
        require(query.text.length <= MAX_QUERY_LENGTH) { "Catalog query too long" }
        require(query.offset in 0..MAX_OFFSET) { "Invalid catalog offset" }
        val text = query.text.trim()
        require(text.none(Char::isISOControl)) { "Invalid catalog query" }
        val topic = query.topic?.trim()?.takeIf(String::isNotEmpty)
        require(topic == null || topic.length <= 120 && topic.matches(topicPattern)) {
            "Invalid catalog topic"
        }
        require(topic == null || text.isEmpty()) { "Choose a query or a topic" }
        return query.copy(text = text, topic = topic)
    }

    fun safeSlug(value: String): String? = value.takeIf { it.matches(slugPattern) }

    fun safeSourceUrl(value: String): String? {
        val checked = value.takeIf { it.length <= 2048 }?.let(FeedRules::safeUrl) ?: return null
        val url = checked.toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        // OkHttp canonicalizes international domain names without rejecting valid Unicode hosts.
        return FeedRules.safeUrl(url.toString())
    }

    fun language(value: String): String {
        if (value.length > 35) return ""
        val normalized = value.replace('_', '-')
        if (!normalized.matches(languagePattern)) return ""
        return Locale.forLanguageTag(normalized)
            .toLanguageTag()
            .takeUnless { it == "und" }
            .orEmpty()
    }

    fun requireNormalProfile(isPrivate: Boolean) {
        check(!isPrivate) { "Catalog search is unavailable in private browsing" }
    }

    fun unique(sources: List<FeedCatalogSource>): List<FeedCatalogSource> =
        sources.distinctBy { FeedRules.identity(it.feedUrl) }
}
