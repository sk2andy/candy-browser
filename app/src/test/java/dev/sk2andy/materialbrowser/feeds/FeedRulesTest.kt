package dev.sk2andy.materialbrowser.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedRulesTest {
    private val source = FeedSource("source", "https://example.org/feed", "News")
    private val article =
        FeedArticle(
            "article",
            setOf("source"),
            "https://example.org/article",
            "New batteries",
            content = "Original article",
        )

    @Test
    fun `refresh preserves user state and retains one revision`() {
        val old =
            article.copy(
                isRead = true,
                readingProgress = 0.4f,
                isStarred = true,
                tags = setOf("energy"),
            )
        val before = FeedState(sources = listOf(source), articles = listOf(old))
        val after =
            FeedRules.merge(before, source, listOf(article.copy(content = "Updated article")), 42)
        val updated = after.articles.single()
        assertTrue(updated.isRead)
        assertEquals(0.4f, updated.readingProgress)
        assertTrue(updated.isStarred)
        assertEquals(setOf("energy"), updated.tags)
        assertEquals("Original article", updated.previousContent)
        assertEquals(42, updated.updatedAtMillis)
    }

    @Test
    fun `matching urls merge sources without losing bookmark`() {
        val before = FeedState(articles = listOf(article.copy(isStarred = true)))
        val second = source.copy(id = "second")
        val after =
            FeedRules.merge(before, second, listOf(article.copy(sourceIds = setOf("second"))), 1)
        assertEquals(setOf("source", "second"), after.articles.single().sourceIds)
        assertTrue(after.articles.single().isStarred)
    }

    @Test
    fun `hidden rule results remain recoverable`() {
        val rule = FeedRule("rule", "Hide batteries", "batteries", FeedRuleAction.Hide)
        val before = FeedState(sources = listOf(source), rules = listOf(rule))
        val after = FeedRules.merge(before, source, listOf(article), 1)
        assertEquals(0, after.unreadCount)
        assertTrue(FeedRules.filter(after, FeedInboxFilter.All).isEmpty())
        assertEquals(
            listOf(after.articles.single()),
            FeedRules.filter(after, FeedInboxFilter.Hidden),
        )
    }

    @Test
    fun `refreshing existing article does not reapply rule after manual unhide`() {
        val before =
            FeedState(
                sources = listOf(source),
                articles = listOf(article),
                rules = listOf(FeedRule("rule", "Hide", "batteries", FeedRuleAction.Hide)),
            )
        assertFalse(FeedRules.merge(before, source, listOf(article), 1).articles.single().isHidden)
    }

    @Test
    fun `codec preserves offline article and settings`() {
        val id = FeedRules.identity(article.url)
        val saved =
            article.copy(
                id = id,
                offlineStatus = FeedOfflineStatus.Ready,
                offlineDocument = FeedRules.document(article),
            )
        val state =
            FeedState(
                articles = listOf(saved),
                settings = FeedSettings(digestEnabled = true),
                queue = listOf(id),
            )
        val decoded = FeedCodec.decode(FeedCodec.encode(state))
        assertEquals(saved, decoded.articles.single())
        assertEquals(state.settings, decoded.settings)
        assertEquals(state.queue, decoded.queue)
    }

    @Test
    fun `subscribed article preserves website metadata and gains feed provenance`() {
        val saved =
            article.copy(
                origin = FeedArticleOrigin.Website,
                sourceIds = emptySet(),
                readingProgress = 0.6f,
            )
        val before = FeedState(articles = listOf(saved))
        assertEquals(0, before.unreadCount)
        assertTrue(FeedRules.filter(before, FeedInboxFilter.All).isEmpty())
        val after = FeedRules.merge(before, source, listOf(article), 1)
        assertEquals(FeedArticleOrigin.Website, after.articles.single().origin)
        assertEquals(0.6f, after.articles.single().readingProgress)
        assertEquals(1, after.unreadCount)
        assertEquals(listOf(saved.id), FeedRules.filter(after, FeedInboxFilter.All).map { it.id })
    }

    @Test
    fun `codec clamps malformed reading progress and recovers interrupted download`() {
        val json =
            """{"version":1,"articles":[{"url":"https://example.org/1","readingProgress":"NaN","offlineStatus":"Downloading"}]}"""
        val decoded = FeedCodec.decode(json).articles.single()
        assertEquals(0f, decoded.readingProgress)
        assertEquals(FeedOfflineStatus.Waiting, decoded.offlineStatus)
    }
}
