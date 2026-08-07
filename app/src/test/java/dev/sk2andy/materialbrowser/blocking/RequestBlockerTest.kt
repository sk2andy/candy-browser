package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBlockerTest {
    private val blocker = RequestBlocker(sequenceOf("doubleclick.net", "tracker.example"))

    @Test
    fun `blocks exact host and subdomains`() {
        assertTrue(blocker.shouldBlock("https://doubleclick.net/ad.js", "https://news.example"))
        assertTrue(blocker.shouldBlock("https://stats.tracker.example/pixel", "https://news.example"))
    }

    @Test
    fun `native host hot path avoids url parsing and keeps same-site escape`() {
        assertTrue(blocker.shouldBlockHosts("stats.tracker.example", "news.example"))
        assertFalse(blocker.shouldBlockHosts("stats.tracker.example", "tracker.example"))
        assertFalse(blocker.shouldBlockHosts("not a host/path", "news.example"))
    }

    @Test
    fun `sorted asset index matches exact ascii hosts without materializing rules`() {
        val index = SortedHostIndex.from(
            "# generated\r\nads.example\r\nalpha.example\nbeta.example\n".toByteArray(),
        )

        assertTrue("ads.example" in index)
        assertTrue("alpha.example" in index)
        assertFalse("sub.ads.example" in index)
        assertFalse("other.example" in index)
        assertTrue(index.size == 3)
    }

    @Test
    fun `request blocker checks suffixes against sorted asset index`() {
        val indexed = RequestBlocker(
            hostRules = emptySequence(),
            indexedHostRules = SortedHostIndex.from("ads.example\ntracker.example\n".toByteArray()),
        )

        assertTrue(indexed.shouldBlockHosts("cdn.ads.example", "news.example"))
        assertFalse(indexed.shouldBlockHosts("notads.example", "news.example"))
    }

    @Test
    fun `does not suffix-match unrelated host`() {
        assertFalse(blocker.shouldBlock("https://notdoubleclick.net/app.js", "https://news.example"))
    }

    @Test
    fun `allows same-site resources`() {
        assertFalse(blocker.shouldBlock("https://tracker.example/app.js", "https://www.tracker.example/article"))
    }

    @Test
    fun `allows malformed and non-web urls`() {
        assertFalse(blocker.shouldBlock("not a url", "https://news.example"))
        assertFalse(blocker.shouldBlock("data:text/plain,hello", "https://news.example"))
    }

    @Test
    fun `normalizes adblock host anchors and ignores unsupported rules`() {
        val anchored = RequestBlocker(
            sequenceOf("||Ads.Example.^", "||bad.example/path^", "*.wildcard.example"),
        )

        assertTrue(anchored.shouldBlock("https://cdn.ads.example/file.js", "https://news.example"))
        assertFalse(anchored.shouldBlock("https://bad.example/path", "https://news.example"))
        assertFalse(anchored.shouldBlock("https://x.wildcard.example/file.js", "https://news.example"))
    }

    @Test
    fun `accepts case insensitive web schemes`() {
        assertTrue(blocker.shouldBlock("HTTPS://doubleclick.net/ad.js", "https://news.example"))
    }

    @Test
    fun `allows upstream exception only on matching page host`() {
        val exceptionAware = RequestBlocker(
            hostRules = sequenceOf("adsninja.ca"),
            allowedHostPairs = sequenceOf("adsninja.ca\thowtogeek.com"),
        )

        assertFalse(
            exceptionAware.shouldBlock(
                "https://cdn.adsninja.ca/adsninja_client.js",
                "https://www.howtogeek.com/article",
            ),
        )
        assertTrue(
            exceptionAware.shouldBlock(
                "https://cdn.adsninja.ca/adsninja_client.js",
                "https://unrelated.example/article",
            ),
        )
    }

    @Test
    fun `does not treat parent of allowed page as matching subdomain`() {
        val exceptionAware = RequestBlocker(
            hostRules = sequenceOf("tracker.example"),
            allowedHostPairs = sequenceOf("tracker.example\tlocal.news.example"),
        )

        assertTrue(
            exceptionAware.shouldBlock(
                "https://tracker.example/pixel",
                "https://news.example/article",
            ),
        )
    }

    @Test
    fun `blocks a host pair only on the matching first party`() {
        val pairBlocker = RequestBlocker(
            hostRules = emptySequence(),
            blockedHostPairs = sequenceOf("ads.example\tnews.example"),
        )

        assertTrue(
            pairBlocker.shouldBlock(
                "https://cdn.ads.example/banner.js",
                "https://www.news.example/article",
            ),
        )
        assertFalse(
            pairBlocker.shouldBlock(
                "https://cdn.ads.example/banner.js",
                "https://notnews.example/article",
            ),
        )
    }

    @Test
    fun `allow pair wins over pair and global blocks`() {
        val exceptionAware = RequestBlocker(
            hostRules = sequenceOf("ads.example"),
            blockedHostPairs = sequenceOf("ads.example\tnews.example"),
            allowedHostPairs = sequenceOf("ads.example\tnews.example"),
        )

        assertFalse(
            exceptionAware.shouldBlock(
                "https://ads.example/banner.js",
                "https://news.example/article",
            ),
        )
        assertTrue(
            exceptionAware.shouldBlock(
                "https://ads.example/banner.js",
                "https://other.example/article",
            ),
        )
    }
}
