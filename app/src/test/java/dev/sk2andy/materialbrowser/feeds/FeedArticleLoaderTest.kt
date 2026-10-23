package dev.sk2andy.materialbrowser.feeds

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedArticleLoaderTest {
    @Test
    fun readerExtractionKeepsArticleTextAndDiscardsNavigationAndScripts() {
        val article = article()
        val body =
            "An article paragraph with enough useful text to satisfy Reader extraction and remain available for offline reading."
        val document =
            FeedArticleLoader.extract(
                article,
                "<html><nav>Menu</nav><article><script>alert('secret')</script><h2>Details</h2><p>$body</p></article></html>",
            )!!

        assertEquals(article.url, document.sourceUrl)
        assertTrue(document.speechText.contains(body))
        assertFalse(document.speechText.contains("secret"))
        assertFalse(document.speechText.contains("Menu"))
    }

    @Test
    fun missingArticleContainerReturnsFallbackSignal() {
        assertNull(FeedArticleLoader.extract(article(), "<html><body>Sign in</body></html>"))
        assertNull(FeedArticleLoader.extract(article(), "<article><p>Short</p></article>"))
    }

    @Test
    fun `nested and malformed publisher HTML keeps the entire article once`() {
        val first =
            "The first paragraph gives the reader enough context to follow the actual article in detail."
        val last =
            "This final paragraph was previously lost by the regular expression at the first nested closing tag."
        val document =
            FeedArticleLoader.extract(
                article(),
                "<main><article><p>$first<div><article><p>Nested callout.</article></div><p>$last</article></main>",
            )!!
        assertTrue(document.speechText.contains(first))
        assertTrue(document.speechText.contains(last))
        assertEquals(1, document.blocks.count { it.text == last })
    }

    @Test
    fun `older div based publisher layout keeps text and safe links but drops hidden promotions`() {
        val body =
            "An older publisher places its entire article in ordinary div elements without semantic main or article tags."
        val document =
            FeedArticleLoader.extract(
                article(),
                "<html><body><nav><p>Navigation links</p></nav><div><p>$body <a href='/details'>Details</a></p>" +
                    "<div hidden><p>Hidden promotion</p></div><p style='display:none'>Hidden subscription</p>" +
                    "<div class='related-stories'><p>Related recommendation</p></div></div></body></html>",
            )!!
        assertTrue(document.speechText.contains(body))
        assertFalse(document.speechText.contains("Hidden"))
        assertFalse(document.speechText.contains("recommendation"))
        assertEquals("https://example.org/details", document.blocks.single().links.single().url)
    }

    @Test
    fun `short genuine articles are accepted without an arbitrary word limit`() {
        val body =
            "A brief complete article can contain fewer than one hundred characters and still be useful."
        val document = FeedArticleLoader.extract(article(), "<article><p>$body</p></article>")!!
        assertEquals(body, document.blocks.single().text)
    }

    @Test
    fun `descriptions copied to content do not become a full article by being long`() {
        val preview = "A description preview. ".repeat(100)
        assertFalse(
            FeedArticleContentRules.hasFeedBody(
                article().copy(summary = preview, content = preview)
            )
        )
        assertFalse(FeedArticleContentRules.hasFeedBody(article()))
        assertTrue(
            FeedArticleContentRules.hasFeedBody(
                article().copy(content = "A complete short entry.", contentIsSummary = false)
            )
        )
    }

    @Test
    fun `long RSS descriptions remain previews even when the card summary is truncated`() {
        val description = "A feed description with no separate content body. ".repeat(200)
        val parsed =
            FeedParser.parse(
                    "<rss><channel><title>Feed</title><item><title>Article</title><link>https://example.org/story</link><description>$description</description></item></channel></rss>",
                    "https://example.org/feed",
                )
                .articles
                .single()
        assertEquals(4000, parsed.summary.length)
        assertTrue(parsed.content.length > parsed.summary.length)
        assertFalse(FeedArticleContentRules.hasFeedBody(parsed))
        assertTrue(FeedArticleContentRules.isLegacyPreview(parsed, FeedRules.document(parsed)))
    }

    @Test
    fun `legacy teaser repair leaves genuine saved websites and freshly extracted short articles alone`() {
        val preview =
            article().copy(summary = "Short complete text", content = "Short complete text")
        val document = FeedRules.document(preview)
        assertTrue(FeedArticleContentRules.isLegacyPreview(preview, document))
        assertFalse(
            FeedArticleContentRules.isLegacyPreview(
                preview.copy(origin = FeedArticleOrigin.Website),
                document,
            )
        )
        assertFalse(
            FeedArticleContentRules.isLegacyPreview(
                preview.copy(offlineContentVersion = 1),
                document,
            )
        )
    }

    @Test
    fun `publisher teaser with explicit truncation is not reported as a full article`() {
        val body =
            "This article preview has enough characters to look readable but stops before the important remaining details"
        val teaser = article().copy(summary = "$body…", content = "$body…", contentIsSummary = true)
        assertNull(FeedArticleLoader.extract(teaser, "<article><p>$body…</p></article>"))
        val shortComplete = teaser.copy(summary = body, content = body)
        assertTrue(
            FeedArticleLoader.extract(shortComplete, "<article><p>$body</p></article>") != null
        )
    }

    @Test
    fun `publisher restricted article declaration keeps preview out of speech`() {
        val body =
            "A publisher teaser with enough readable characters that previously passed the simple extraction threshold."
        assertNull(
            FeedArticleLoader.extract(
                article(),
                "<script type='application/ld+json'>{\"@type\":\"NewsArticle\",\"isAccessibleForFree\":false}</script><article><p>$body</p></article>",
            )
        )
    }

    @Test
    fun quietHoursCrossMidnightAndEqualEndpointsDisableQuietPeriod() {
        assertTrue(FeedNotificationRules.quiet(23, 22, 8))
        assertTrue(FeedNotificationRules.quiet(7, 22, 8))
        assertFalse(FeedNotificationRules.quiet(8, 22, 8))
        assertFalse(FeedNotificationRules.quiet(12, 22, 8))
        assertFalse(FeedNotificationRules.quiet(22, 22, 22))
        assertTrue(FeedNotificationRules.quiet(12, 10, 14))
    }

    private fun article() = FeedArticle("id", emptySet(), "https://example.org/article", "Article")

    @Test
    fun digestDelayedPastMidnightKeepsOriginalTargetDay() {
        val activated =
            ZonedDateTime.parse("2026-10-20T12:00:00+02:00[Europe/Berlin]")
                .toInstant()
                .toEpochMilli()
        val morning = ZonedDateTime.parse("2026-10-22T08:00:00+02:00[Europe/Berlin]")
        assertEquals(
            "2026-10-21",
            FeedNotificationRules.digestDay(morning, 22, activated, "2026-10-20"),
        )
        assertNull(FeedNotificationRules.digestDay(morning, 22, activated, "2026-10-21"))
        assertNull(
            FeedNotificationRules.digestDay(morning, 22, morning.toInstant().toEpochMilli(), null)
        )
    }
}
