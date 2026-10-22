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
