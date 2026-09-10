package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoMainFrameResponseRulesTest {
    @Test
    fun `valid HTTP response is accepted`() {
        assertEquals(
            GeckoMainFrameResponse("https://example.com/missing", 404, 3),
            GeckoMainFrameResponseRules.resolve("https://example.com/missing", 404, 3),
        )
    }

    @Test
    fun `non-web URL is rejected`() {
        assertNull(GeckoMainFrameResponseRules.resolve("file:///secret", 404, 0))
    }

    @Test
    fun `invalid HTTP status is rejected`() {
        assertNull(GeckoMainFrameResponseRules.resolve("https://example.com", 700, 0))
    }

    @Test
    fun `invalid navigation generation is rejected`() {
        assertNull(GeckoMainFrameResponseRules.resolve("https://example.com", 404, -1))
    }
}
