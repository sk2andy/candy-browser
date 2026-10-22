package dev.sk2andy.materialbrowser.reader.rss

import java.security.MessageDigest

internal data class RssInboxState(
    val feeds: List<RssFeed> = emptyList(),
    val readArticleKeys: Set<String> = emptySet(),
)

internal object RssInboxRules {
    const val MAX_READ_ARTICLES = RssFeedRules.MAX_FEEDS * RssFeedRules.MAX_ARTICLES_PER_FEED

    fun articleKey(feedId: String, articleId: String): String {
        val identity = "${feedId.length}:$feedId$articleId"
        val bytes = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0xf])
            }
        }
    }

    fun retainedReadKeys(feeds: List<RssFeed>, readKeys: Set<String>): Set<String> =
        feeds.asSequence()
            .take(RssFeedRules.MAX_FEEDS)
            .flatMap { feed ->
                feed.articles.asSequence().take(RssFeedRules.MAX_ARTICLES_PER_FEED)
                    .map { article -> articleKey(feed.id, article.id) }
            }
            .filter { it in readKeys }
            .take(MAX_READ_ARTICLES)
            .toSet()

    private const val HEX = "0123456789abcdef"
}
