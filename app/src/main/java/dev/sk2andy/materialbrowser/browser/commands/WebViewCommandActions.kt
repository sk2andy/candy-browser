package dev.sk2andy.materialbrowser.browser.commands

import android.webkit.CookieManager
import android.webkit.WebView

internal object WebViewCommandActions {
    fun clearCacheAndReload(webView: WebView) {
        webView.clearCache(true)
        webView.reload()
    }

    fun clearCookiesAndReload(
        cookieManager: CookieManager,
        webView: WebView,
        shouldReload: () -> Boolean = { true },
        onComplete: () -> Unit = {},
    ) {
        cookieManager.removeAllCookies {
            cookieManager.flush()
            if (shouldReload()) webView.reload()
            onComplete()
        }
    }
}
