package dev.sk2andy.materialbrowser.browser.actions

import android.webkit.WebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewHitTestResolverTest {
    @Test
    fun `anchor exposes background-tab action`() {
        val target = WebViewHitTestResolver.resolve(
            hitType = WebView.HitTestResult.SRC_ANCHOR_TYPE,
            extra = "https://example.com/article",
        )

        assertEquals("https://example.com/article", target?.linkUrl)
        assertTrue(requireNotNull(target).canOpenLinkInBackground)
        assertFalse(target.canDownloadImage)
        assertEquals(
            WebContentAction.OpenLinkInBackground("https://example.com/article"),
            target.openLinkInBackgroundAction(),
        )
    }

    @Test
    fun `image anchor exposes link and image actions from focused node`() {
        val target = WebViewHitTestResolver.resolve(
            hitType = WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
            extra = "https://cdn.example.com/preview.png",
            focusedLinkUrl = "https://example.com/article",
            focusedImageUrl = "https://cdn.example.com/full.png",
        )

        assertEquals("https://example.com/article", target?.linkUrl)
        assertEquals("https://cdn.example.com/full.png", target?.imageUrl)
        assertEquals("full.png", target?.downloadImageAction()?.request?.fileName)
    }

    @Test
    fun `unsafe hit URLs produce no action`() {
        assertNull(
            WebViewHitTestResolver.resolve(
                hitType = WebView.HitTestResult.IMAGE_TYPE,
                extra = "data:image/png;base64,abc",
            ),
        )
        assertNull(
            WebViewHitTestResolver.resolve(
                hitType = WebView.HitTestResult.SRC_ANCHOR_TYPE,
                extra = "javascript:alert(1)",
            ),
        )
        assertNull(
            WebViewHitTestResolver.resolve(
                hitType = WebView.HitTestResult.SRC_ANCHOR_TYPE,
                extra = "https://user:secret@example.com/private",
            ),
        )
        assertNull(
            WebViewHitTestResolver.resolve(
                hitType = WebView.HitTestResult.SRC_ANCHOR_TYPE,
                extra = "mailto:hello@example.com",
            ),
        )
    }

    @Test
    fun `supported link is trimmed through shared browser URI policy`() {
        val target = WebViewHitTestResolver.resolve(
            hitType = WebView.HitTestResult.SRC_ANCHOR_TYPE,
            extra = "  HTTPS://example.com/article  ",
        )

        assertEquals("HTTPS://example.com/article", target?.linkUrl)
    }

    @Test
    fun `text and unknown hits stay unconsumed for native selection`() {
        assertFalse(WebViewHitTestResolver.supports(WebView.HitTestResult.UNKNOWN_TYPE))
        assertFalse(WebViewHitTestResolver.supports(WebView.HitTestResult.EDIT_TEXT_TYPE))
        assertNull(
            WebViewHitTestResolver.resolve(
                hitType = WebView.HitTestResult.UNKNOWN_TYPE,
                extra = null,
                focusedLinkUrl = "https://example.com/should-not-open",
            ),
        )
    }

    @Test
    fun `known link and image hit types are actionable`() {
        assertTrue(WebViewHitTestResolver.supports(WebView.HitTestResult.SRC_ANCHOR_TYPE))
        assertTrue(WebViewHitTestResolver.supports(WebView.HitTestResult.IMAGE_TYPE))
        assertTrue(WebViewHitTestResolver.supports(WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE))
    }
}
