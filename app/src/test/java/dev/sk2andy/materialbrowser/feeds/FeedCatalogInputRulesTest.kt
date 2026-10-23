package dev.sk2andy.materialbrowser.feeds

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedCatalogInputRulesTest {
    @Test
    fun `feed and website URLs enter preview with canonical international hosts`() {
        assertEquals(
            FeedCatalogInput.Address("https://www.golem.de/rss.php?feed=RSS2.0"),
            FeedCatalogInputRules.classify("  https://www.golem.de/rss.php?feed=RSS2.0  "),
        )
        assertEquals(
            FeedCatalogInput.Address("https://www.golem.de/"),
            FeedCatalogInputRules.classify("www.golem.de"),
        )
        assertEquals(
            FeedCatalogInput.Address("https://xn--bcher-kva.de/feed.xml"),
            FeedCatalogInputRules.classify("bücher.de/feed.xml"),
        )
        assertEquals(
            FeedCatalogInput.Address("https://example.com:8443/feed"),
            FeedCatalogInputRules.classify("example.com:8443/feed"),
        )
    }

    @Test
    fun `ordinary international terms remain local searches`() {
        listOf("Golem", "Android 16", "日本語 科学", "économie", "news about example.com").forEach {
            assertEquals(FeedCatalogInput.Search(it), FeedCatalogInputRules.classify(it))
        }
        assertEquals(FeedCatalogInput.Empty, FeedCatalogInputRules.classify("  "))
    }

    @Test
    fun `malformed URLs and unsafe schemes never become preview requests`() {
        listOf(
                "javascript:alert(1)",
                "file:///sdcard/feed.xml",
                "content://provider/feed",
                "intent://example.com",
                "ftp://example.com/feed",
                "https://",
                "https://example.com/a b",
                "https://user:password@example.com/feed",
                "user@example.com/feed",
                "https://example.com/a\nb",
            )
            .forEach {
                assertEquals(it, FeedCatalogInput.Invalid, FeedCatalogInputRules.classify(it))
            }
    }

    @Test
    fun `bounds reject truncated addresses and oversized search terms`() {
        assertEquals(FeedCatalogInput.Invalid, FeedCatalogInputRules.classify("a".repeat(201)))
        assertEquals(
            FeedCatalogInput.Invalid,
            FeedCatalogInputRules.classify("https://example.com/" + "a".repeat(2048)),
        )
    }

    @Test
    fun `private input allows explicit address preview but blocks catalog search`() {
        assertEquals(
            FeedCatalogInput.Address("https://example.com/feed"),
            FeedCatalogInputRules.classify("example.com/feed", isPrivate = true),
        )
        assertEquals(
            FeedCatalogInput.PrivateSearch,
            FeedCatalogInputRules.classify("private subject", isPrivate = true),
        )
        assertEquals(
            FeedCatalogInput.Invalid,
            FeedCatalogInputRules.classify("javascript:alert(1)", isPrivate = true),
        )
    }
}
