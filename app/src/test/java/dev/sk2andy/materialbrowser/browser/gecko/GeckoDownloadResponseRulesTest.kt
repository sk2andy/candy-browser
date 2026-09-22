package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoDownloadResponseRulesTest {
    @Test
    fun `blob response without HTTP status is accepted`() {
        assertFalse(
            GeckoDownloadResponseRules.shouldRejectStatus(
                uri = "blob:https://example.com/generated-image",
                statusCode = 0,
                allowHttpErrors = false,
            ),
        )
    }

    @Test
    fun `network response without successful HTTP status is rejected`() {
        assertTrue(
            GeckoDownloadResponseRules.shouldRejectStatus(
                uri = "https://example.com/download",
                statusCode = 0,
                allowHttpErrors = false,
            ),
        )
    }

    @Test
    fun `non blob response without successful status is rejected`() {
        assertTrue(
            GeckoDownloadResponseRules.shouldRejectStatus(
                uri = "data:application/octet-stream;base64,AA==",
                statusCode = 0,
                allowHttpErrors = false,
            ),
        )
    }

    @Test
    fun `blob HTTP error is rejected`() {
        assertTrue(
            GeckoDownloadResponseRules.shouldRejectStatus(
                uri = "blob:https://example.com/generated-image",
                statusCode = 404,
                allowHttpErrors = false,
            ),
        )
    }

    @Test
    fun `allowed HTTP errors bypass status validation`() {
        assertFalse(
            GeckoDownloadResponseRules.shouldRejectStatus(
                uri = "https://example.com/download",
                statusCode = 404,
                allowHttpErrors = true,
            ),
        )
    }
}
