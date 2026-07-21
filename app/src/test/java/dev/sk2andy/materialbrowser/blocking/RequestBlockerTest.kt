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
}
