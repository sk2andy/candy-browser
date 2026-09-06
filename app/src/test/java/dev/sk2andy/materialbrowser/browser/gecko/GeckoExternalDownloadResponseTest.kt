package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.BrowserEngineDownloadResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeckoExternalDownloadResponseTest {
    @Test
    fun `response body can be claimed exactly once`() {
        var starts = 0
        var discards = 0
        val response = response(
            start = {
                starts += 1
                GeckoDownloadCancellation {}
            },
            discard = { discards += 1 },
        )

        assertNotNull(response.start(listener()))
        assertNull(response.start(listener()))
        response.close()

        assertEquals(1, starts)
        assertEquals(0, discards)
    }

    @Test
    fun `closing an unclaimed response discards it exactly once`() {
        var starts = 0
        var discards = 0
        val response = response(
            start = {
                starts += 1
                GeckoDownloadCancellation {}
            },
            discard = { discards += 1 },
        )

        response.close()
        response.close()

        assertNull(response.start(listener()))
        assertEquals(0, starts)
        assertEquals(1, discards)
    }

    private fun response(
        start: (GeckoDownloadTransferListener) -> GeckoDownloadCancellation?,
        discard: () -> Unit,
    ) = GeckoExternalDownloadResponse(
        metadata = BrowserEngineDownloadResponse(
            url = "https://example.com/file.bin",
            contentDisposition = "attachment; filename=file.bin",
            mimeType = "application/octet-stream",
        ),
        startTransfer = start,
        discard = discard,
    )

    private fun listener() = object : GeckoDownloadTransferListener {
        override fun onStarted(start: GeckoDownloadTransferStart) = Unit
    }
}
