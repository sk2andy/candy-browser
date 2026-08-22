package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsErrorRulesTest {
    @Test
    fun `matching main frame TLS error is surfaced`() {
        assertTrue(
            TlsErrorRules.isForMainFrame(
                errorUrl = "https://news.example/article?edition=de",
                currentMainFrameUrls = listOf("https://news.example/article?edition=de"),
            ),
        )
    }

    @Test
    fun `default port empty path and fragment normalize`() {
        assertTrue(
            TlsErrorRules.isForMainFrame(
                errorUrl = "https://NEWS.example:443/#details",
                currentMainFrameUrls = listOf("https://news.example"),
            ),
        )
    }

    @Test
    fun `cross origin subresource TLS error stays local`() {
        assertFalse(
            TlsErrorRules.isForMainFrame(
                errorUrl = "https://tracker.example/pixel.gif",
                currentMainFrameUrls = listOf("https://publisher.example/article"),
            ),
        )
    }

    @Test
    fun `same origin subresource TLS error stays local`() {
        assertFalse(
            TlsErrorRules.isForMainFrame(
                errorUrl = "https://publisher.example/assets/banner.png",
                currentMainFrameUrls = listOf("https://publisher.example/article"),
            ),
        )
    }

    @Test
    fun `redirect target may match either current main frame source`() {
        assertTrue(
            TlsErrorRules.isForMainFrame(
                errorUrl = "https://login.example/continue",
                currentMainFrameUrls = listOf(
                    "https://publisher.example/login",
                    "https://login.example/continue",
                ),
            ),
        )
    }

    @Test
    fun `unicode and punycode hosts normalize`() {
        assertTrue(
            TlsErrorRules.isForMainFrame(
                errorUrl = "https://xn--bcher-kva.example/article",
                currentMainFrameUrls = listOf("https://bücher.example/article"),
            ),
        )
    }

    @Test
    fun `ipv6 main frame targets normalize`() {
        assertTrue(
            TlsErrorRules.isForMainFrame(
                errorUrl = "https://[2001:db8::1]:443/article",
                currentMainFrameUrls = listOf("https://[2001:db8::1]/article"),
            ),
        )
    }

    @Test
    fun `malformed and non web error URLs stay local`() {
        assertFalse(TlsErrorRules.isForMainFrame("not a url", listOf("https://news.example/")))
        assertFalse(TlsErrorRules.isForMainFrame("data:text/plain,error", listOf("data:text/plain,error")))
    }
}
