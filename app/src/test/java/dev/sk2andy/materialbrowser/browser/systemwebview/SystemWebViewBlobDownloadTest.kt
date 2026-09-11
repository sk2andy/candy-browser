package dev.sk2andy.materialbrowser.browser.systemwebview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWebViewBlobDownloadTest {
    @Test
    fun `accepts HTTP blob owned by current page origin`() {
        assertTrue(
            SystemWebViewBlobDownloadRules.isSameOriginBlob(
                blobUrl = "blob:https://chatgpt.com/93a7a1f2-7405-4f27-a3bc-4335cc9a3bfd",
                pageUrl = "https://chatgpt.com/c/123",
            ),
        )
        assertTrue(
            SystemWebViewBlobDownloadRules.isSameOriginBlob(
                blobUrl = "blob:http://example.com:80/image",
                pageUrl = "http://example.com/gallery",
            ),
        )
    }

    @Test
    fun `rejects cross-origin and non-network blobs`() {
        assertFalse(
            SystemWebViewBlobDownloadRules.isSameOriginBlob(
                blobUrl = "blob:https://files.example/image",
                pageUrl = "https://example.com/gallery",
            ),
        )
        assertFalse(
            SystemWebViewBlobDownloadRules.isSameOriginBlob(
                blobUrl = "data:image/jpeg;base64,AA==",
                pageUrl = "https://example.com/gallery",
            ),
        )
        assertFalse(
            SystemWebViewBlobDownloadRules.isSameOriginBlob(
                blobUrl = "blob:file:///private/image",
                pageUrl = "https://example.com/gallery",
            ),
        )
        assertFalse(
            SystemWebViewBlobDownloadRules.isSameOriginBlob(
                blobUrl = "blob:https://attacker@chatgpt.com/image",
                pageUrl = "https://chatgpt.com/gallery",
            ),
        )
    }

    @Test
    fun `parses bounded transfer messages with matching token`() {
        val start = SystemWebViewBlobDownloadMessage.parse(
            """
                {"v":1,"token":"safe","id":7,"sequence":0,"type":"start",
                 "mime":"image/jpeg","total":3}
            """.trimIndent(),
            expectedToken = "safe",
        )
        val chunk = SystemWebViewBlobDownloadMessage.parse(
            """{"v":1,"token":"safe","id":7,"sequence":1,"type":"chunk","data":"AQID"}""",
            expectedToken = "safe",
        )

        assertEquals(
            SystemWebViewBlobDownloadMessage.Start(7, 0, "image/jpeg", 3),
            start,
        )
        assertEquals(SystemWebViewBlobDownloadMessage.Chunk(7, 1, "AQID"), chunk)
    }

    @Test
    fun `rejects unknown malformed and unauthorized messages`() {
        assertNull(
            SystemWebViewBlobDownloadMessage.parse(
                """{"v":1,"token":"wrong","id":7,"sequence":0,"type":"finish"}""",
                expectedToken = "safe",
            ),
        )
        assertNull(
            SystemWebViewBlobDownloadMessage.parse(
                """{"v":1,"token":"safe","id":0,"sequence":0,"type":"finish"}""",
                expectedToken = "safe",
            ),
        )
        assertNull(SystemWebViewBlobDownloadMessage.parse("not-json", expectedToken = "safe"))
    }
}
