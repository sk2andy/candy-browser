package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadRequestFactoryTest {
    @Test
    fun `rejects unsafe schemes and embedded credentials`() {
        assertNull(BrowserDownloadRequestFactory.create("javascript:alert(1)"))
        assertNull(BrowserDownloadRequestFactory.create("file:///data/private"))
        assertNull(BrowserDownloadRequestFactory.create("https://user:password@example.com/file"))
    }

    @Test
    fun `sanitizes traversal and control characters from server file name`() {
        val request = BrowserDownloadRequestFactory.create(
            url = "https://example.com/fallback",
            contentDisposition = "attachment; filename=\"../../bad\r\nname\"",
            mimeType = "image/png; charset=utf-8",
        )

        assertEquals("_.._bad__name.png", request?.fileName)
        assertEquals("image/png", request?.mimeType)
    }

    @Test
    fun `uses UTF-8 content disposition and keeps literal plus`() {
        val request = BrowserDownloadRequestFactory.create(
            url = "https://example.com/fallback",
            contentDisposition = "attachment; filename*=UTF-8''Gr%C3%BCn+Logo.png",
            mimeType = "image/png",
        )

        assertEquals("Grün+Logo.png", request?.fileName)
    }

    @Test
    fun `derives decoded file name and extension from URL and MIME`() {
        val request = BrowserDownloadRequestFactory.create(
            url = "https://example.com/My%20Image?size=large",
            mimeType = "image/webp",
        )

        assertEquals("My Image.webp", request?.fileName)
    }

    @Test
    fun `drops injected headers and bounds file name`() {
        val request = BrowserDownloadRequestFactory.create(
            url = "https://example.com/${"a".repeat(200)}.png",
            mimeType = "image/png",
            userAgent = "Browser\r\nX-Evil: yes",
            cookies = "session=safe",
        )

        assertNull(request?.userAgent)
        assertEquals("session=safe", request?.cookies)
        assertTrue(requireNotNull(request).fileName.length <= 120)
        assertTrue(request.fileName.endsWith(".png"))
    }

    @Test
    fun `keeps safe referrer without fragment and rejects unsafe referrer`() {
        val request = BrowserDownloadRequestFactory.create(
            url = "https://example.com/file.pdf",
            mimeType = "application/pdf",
            referrer = "https://example.com/account?tab=files#private-section",
        )

        assertEquals("https://example.com/account?tab=files", request?.referrer)
        assertNull(
            BrowserDownloadRequestFactory.create(
                url = "https://example.com/file.pdf",
                referrer = "javascript:alert(1)",
            )?.referrer,
        )
    }

    @Test
    fun `limits cross-origin referrer and omits downgrade referrer`() {
        val crossOrigin = BrowserDownloadRequestFactory.create(
            url = "https://downloads.example.net/file.pdf",
            referrer = "https://example.com/account?token=secret#section",
        )
        val downgrade = BrowserDownloadRequestFactory.create(
            url = "http://example.com/file.pdf",
            referrer = "https://example.com/account?token=secret",
        )

        assertEquals("https://example.com", crossOrigin?.referrer)
        assertNull(downgrade?.referrer)
    }
}
