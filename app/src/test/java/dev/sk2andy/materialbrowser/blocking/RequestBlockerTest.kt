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
}
