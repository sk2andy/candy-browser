package dev.sk2andy.materialbrowser.browser.systemwebview

import android.webkit.WebView
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

internal object WebViewHitTestResolver {
    fun supports(hitType: Int): Boolean = hitType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
        hitType == WebView.HitTestResult.IMAGE_TYPE ||
        hitType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

    fun resolve(
        hitType: Int,
        extra: String?,
        focusedLinkUrl: String? = null,
        focusedImageUrl: String? = null,
    ): WebContentTarget? {
        val directUrl = safeHttpUrl(extra)
        val focusedLink = safeHttpUrl(focusedLinkUrl)
        val focusedImage = safeHttpUrl(focusedImageUrl)
        val target = when (hitType) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> WebContentTarget(
                linkUrl = focusedLink ?: directUrl,
            )
            WebView.HitTestResult.IMAGE_TYPE -> WebContentTarget(
                imageUrl = focusedImage ?: directUrl,
            )
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> WebContentTarget(
                linkUrl = focusedLink,
                imageUrl = focusedImage ?: directUrl,
            )
            else -> null
        }
        return target?.takeIf { it.linkUrl != null || it.imageUrl != null }
    }

    private fun safeHttpUrl(value: String?): String? = BrowserUriPolicy.normalizeHttpUrl(value)
}
