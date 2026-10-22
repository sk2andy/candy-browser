package dev.sk2andy.materialbrowser.reader.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssInboxRulesTest {
    @Test
    fun `article identity separates ambiguous and unicode feed identifiers`() {
        val first = RssInboxRules.articleKey("ab", "c")
        val second = RssInboxRules.articleKey("a", "bc")
        val unicode = RssInboxRules.articleKey("日本語", "über 📰")

        assertNotEquals(first, second)
        assertEquals(unicode, RssInboxRules.articleKey("日本語", "über 📰"))
        assertEquals(64, unicode.length)
        assertTrue(unicode.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `retained read keys discard removed feeds and enforce feed and article bounds`() {
        val currentFeeds = listOf(feed("current", article("kept")))
        val removedFeedKey = RssInboxRules.articleKey("removed", "old")
        val keptKey = RssInboxRules.articleKey("current", "kept")

        assertEquals(
            setOf(keptKey),
            RssInboxRules.retainedReadKeys(currentFeeds, setOf(removedFeedKey, keptKey)),
        )

        val oversizedFeeds = (0..RssFeedRules.MAX_FEEDS).map { feedIndex ->
            RssFeed(
                id = "feed-$feedIndex",
                title = "Feed $feedIndex",
                feedUrl = "https://feed-$feedIndex.example/rss",
                siteUrl = "https://feed-$feedIndex.example",
                articles = (0..RssFeedRules.MAX_ARTICLES_PER_FEED).map { articleIndex ->
                    article("article-$articleIndex")
                },
            )
        }
        val oversizedKeys = oversizedFeeds.flatMap { feed ->
            feed.articles.map { article -> RssInboxRules.articleKey(feed.id, article.id) }
        }.toSet()

        val retained = RssInboxRules.retainedReadKeys(oversizedFeeds, oversizedKeys)

        assertEquals(RssInboxRules.MAX_READ_ARTICLES, retained.size)
        assertTrue(retained.all { it in oversizedKeys })
    }

    private fun feed(id: String, article: RssArticle) = RssFeed(
        id = id,
        title = id,
        feedUrl = "https://$id.example/rss",
        siteUrl = "https://$id.example",
        articles = listOf(article),
    )

    private fun article(id: String) = RssArticle(
        id = id,
        title = id,
        url = "https://example.com/$id",
        summary = "Summary for $id",
        publishedAt = 1L,
    )
}
