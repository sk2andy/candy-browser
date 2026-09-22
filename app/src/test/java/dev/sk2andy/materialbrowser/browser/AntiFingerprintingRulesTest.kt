package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class AntiFingerprintingRulesTest {
    @Test
    fun `user agent hides Android device OS and browser build`() {
        val userAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Build/AP4A; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/138.0.7204.112 Mobile Safari/537.36"

        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
                "Chrome/138.0.0.0 Mobile Safari/537.36",
            AntiFingerprintingRules.reduceUserAgent(userAgent),
        )
    }

    @Test
    fun `reduced user agent remains stable`() {
        val reduced = "Mozilla/5.0 (Linux; Android 10; K; wv) " +
            "AppleWebKit/537.36 Chrome/138.0.0.0 Mobile Safari/537.36"

        assertEquals(reduced, AntiFingerprintingRules.reduceUserAgent(reduced))
    }
}
