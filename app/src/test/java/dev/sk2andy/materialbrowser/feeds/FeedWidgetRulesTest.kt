package dev.sk2andy.materialbrowser.feeds

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedWidgetRulesTest {
    @Test
    fun `widget uses latest visible subscribed articles and retains read entries`() {
        val state = FeedState(
            sources = listOf(FeedSource("source", "https://example.com/feed", "Example")),
            articles = listOf(
                article("read", 2).copy(isRead = true),
                article("unread", 1),
                article("hidden", 5).copy(isHidden = true),
                article("removed", 4).copy(sourceIds = setOf("removed-source")),
                article("website", 3).copy(sourceIds = emptySet(), origin = FeedArticleOrigin.Website),
            ),
        )

        val entries = FeedWidgetRules.articles(state)

        assertEquals(listOf("read", "unread"), entries.map { it.id })
        assertEquals(listOf("Example", "Example"), entries.map { it.sourceTitle })
    }

    @Test
    fun `shared articles occur once and retain subscribed source after another is removed`() {
        val state = FeedState(
            sources = listOf(FeedSource("source", "https://example.com/feed", "Example")),
            articles = listOf(article("shared", 1).copy(sourceIds = setOf("removed", "source"))),
        )

        assertEquals(
            listOf(FeedWidgetArticle("shared", "Example", "shared")),
            FeedWidgetRules.articles(state),
        )
    }

    @Test
    fun `remote collection includes at most fifty newest entries`() {
        val state = FeedState(
            sources = listOf(FeedSource("source", "https://example.com/feed", "Example")),
            articles = (1..80).map { article("article-$it", it.toLong()) },
        )

        val entries = FeedWidgetRules.articles(state)

        assertEquals(50, entries.size)
        assertEquals("article-80", entries.first().id)
        assertEquals("article-31", entries.last().id)
    }

    private fun article(id: String, publishedAt: Long) = FeedArticle(
        id = id,
        sourceIds = setOf("source"),
        url = "https://example.com/$id",
        title = id,
        publishedAtMillis = publishedAt,
    )
}
