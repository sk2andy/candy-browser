package dev.sk2andy.materialbrowser.browser.systemwebview.commands

import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

/** Site-scoped deletion in this view's storage context. Never falls back to global deletion. */
internal object WebViewSiteData {
    fun isSupported(requiresSeparateProfile: Boolean): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA) &&
            (!requiresSeparateProfile ||
                WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE))

    fun clear(
        webView: WebView,
        url: String,
        requiresSeparateProfile: Boolean,
        onComplete: (Boolean) -> Unit,
    ) {
        if (!isSupported(requiresSeparateProfile) || BrowserUriPolicy.normalizeHttpUrl(url) == null) {
            onComplete(false)
            return
        }
        runCatching {
            val storage = if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                WebViewCompat.getProfile(webView).webStorage
            } else {
                WebStorage.getInstance()
            }
            WebStorageCompat.deleteBrowsingDataForSite(storage, url) { onComplete(true) }
        }.onFailure { onComplete(false) }
    }
}
