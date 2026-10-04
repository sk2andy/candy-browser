package dev.sk2andy.materialbrowser.browser.systemwebview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemWebViewBlobDownloadTest {
    @Test
    fun `popup blob download requires gesture and opener origin`() {
        val blobUrl = "blob:https://gemini.google.com/93a7a1f2-7405-4f27-a3bc-4335cc9a3bfd"

        assertTrue(SystemWebViewBlobDownloadRules.isPopupBlobDownload(
            blobUrl,
            "https://gemini.google.com/app/123",
            hasUserGesture = true,
        ))
        assertFalse(SystemWebViewBlobDownloadRules.isPopupBlobDownload(
            blobUrl,
            "https://gemini.google.com/app/123",
            hasUserGesture = false,
        ))
        assertFalse(SystemWebViewBlobDownloadRules.isPopupBlobDownload(
            blobUrl,
            "https://chatgpt.com/c/123",
            hasUserGesture = true,
        ))
    }

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
    fun `accepts opaque PNG and JPEG downloads from network pages`() {
        val blobUrl = "blob:null/93a7a1f2-7405-4f27-a3bc-4335cc9a3bfd"

        listOf("image/png", "IMAGE/PNG; charset=binary", " image/jpeg ").forEach { mimeType ->
            assertTrue(
                mimeType,
                SystemWebViewBlobDownloadRules.isOpaqueImageBlob(
                    blobUrl = blobUrl,
                    pageUrl = "https://gemini.google.com/app/123",
                    mimeType = mimeType,
                ),
            )
            assertTrue(
                mimeType,
                SystemWebViewBlobDownloadRules.isSupportedBlob(
                    blobUrl = blobUrl,
                    pageUrl = "https://gemini.google.com/app/123",
                    mimeType = mimeType,
                ),
            )
        }
        assertTrue(
            SystemWebViewBlobDownloadRules.isOpaqueImageBlob(
                blobUrl = blobUrl,
                pageUrl = "http://example.com:80/gallery",
                mimeType = "image/png",
            ),
        )
        assertFalse(
            SystemWebViewBlobDownloadRules.isSameOriginBlob(
                blobUrl = blobUrl,
                pageUrl = "https://gemini.google.com/app/123",
            ),
        )
    }

    @Test
    fun `rejects opaque blobs with active unknown or unsupported MIME types`() {
        listOf(
            null,
            "",
            "image/svg+xml",
            "text/html",
            "application/xhtml+xml",
            "application/octet-stream",
            "image/webp",
            "image/gif",
            "image/ png",
        ).forEach { mimeType ->
            assertFalse(
                mimeType.orEmpty(),
                SystemWebViewBlobDownloadRules.isSupportedBlob(
                    blobUrl = "blob:null/93a7a1f2-7405-4f27-a3bc-4335cc9a3bfd",
                    pageUrl = "https://gemini.google.com/app/123",
                    mimeType = mimeType,
                ),
            )
        }
    }

    @Test
    fun `rejects opaque blob URLs without an exact UUID path`() {
        val uuid = "93a7a1f2-7405-4f27-a3bc-4335cc9a3bfd"

        listOf(
            "blob:null",
            "blob:null/",
            "blob:null/not-a-uuid",
            "blob:null/1-1-1-1-1",
            "blob:null/93a7a1f2-7405-4f27-a3bc-4335cc9a3bfz",
            "blob:null/$uuid/extra",
            "blob:null/$uuid?download=1",
            "blob:null/$uuid#image",
            "blob:null//$uuid",
            "blob:NULL/$uuid",
            "blob:null/93A7A1F2-7405-4F27-A3BC-4335CC9A3BFD",
            "blob:null/$uuid ",
        ).forEach { blobUrl ->
            assertFalse(
                blobUrl,
                SystemWebViewBlobDownloadRules.isOpaqueImageBlob(
                    blobUrl = blobUrl,
                    pageUrl = "https://gemini.google.com/app/123",
                    mimeType = "image/png",
                ),
            )
        }
    }

    @Test
    fun `rejects opaque image downloads without a valid network source page`() {
        listOf(
            "",
            "not a URL",
            "https:///gallery",
            "https://user:secret@gemini.google.com/app/123",
            "about:blank",
            "data:text/html,download",
            "file:///private/gallery",
            "blob:https://gemini.google.com/gallery",
        ).forEach { pageUrl ->
            assertFalse(
                pageUrl,
                SystemWebViewBlobDownloadRules.isSupportedBlob(
                    blobUrl = "blob:null/93a7a1f2-7405-4f27-a3bc-4335cc9a3bfd",
                    pageUrl = pageUrl,
                    mimeType = "image/png",
                ),
            )
        }
    }

    @Test
    fun `supported blobs preserve normal origin rules without the opaque MIME restriction`() {
        assertTrue(
            SystemWebViewBlobDownloadRules.isSupportedBlob(
                blobUrl = "blob:https://gemini.google.com/93a7a1f2-7405-4f27-a3bc-4335cc9a3bfd",
                pageUrl = "https://gemini.google.com/app/123",
                mimeType = "text/html",
            ),
        )
        assertTrue(
            SystemWebViewBlobDownloadRules.isSupportedBlob(
                blobUrl = "blob:http://example.com:80/image",
                pageUrl = "http://example.com/gallery",
                mimeType = null,
            ),
        )
        listOf(
            "blob:https://files.example/93a7a1f2-7405-4f27-a3bc-4335cc9a3bfd",
            "blob:file:///private/image",
            "data:image/png;base64,AA==",
        ).forEach { blobUrl ->
            assertFalse(
                blobUrl,
                SystemWebViewBlobDownloadRules.isSupportedBlob(
                    blobUrl = blobUrl,
                    pageUrl = "https://gemini.google.com/app/123",
                    mimeType = "image/png",
                ),
            )
        }
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
