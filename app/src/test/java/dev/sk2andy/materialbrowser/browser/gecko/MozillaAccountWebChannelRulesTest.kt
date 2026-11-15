package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MozillaAccountWebChannelRulesTest {
    @Test
    fun `only the Mozilla HTTPS origin may send account messages`() {
        assertTrue(accepts("https://accounts.firefox.com/authorization?state=test"))
        assertTrue(accepts("https://accounts.firefox.com:443/"))
        listOf(
            "http://accounts.firefox.com/",
            "https://accounts.firefox.com.evil.example/",
            "https://accounts.firefox.com@evil.example/",
            "https://user@accounts.firefox.com/",
            "https://accounts.firefox.com:8443/",
            "https://accounts.firefox.com./",
            "https://accounts.stage.mozaws.net/",
            "about:blank",
            "not a URI",
        ).forEach { assertFalse(it, accepts(it)) }
    }

    @Test
    fun `private pages and subframes cannot access account messages`() {
        val url = "https://accounts.firefox.com/"
        assertFalse(MozillaAccountWebChannelRules.acceptsSender(url, topLevel = false, privateMode = false))
        assertFalse(MozillaAccountWebChannelRules.acceptsSender(url, topLevel = true, privateMode = true))
    }

    private fun accepts(url: String) =
        MozillaAccountWebChannelRules.acceptsSender(url, topLevel = true, privateMode = false)
}
