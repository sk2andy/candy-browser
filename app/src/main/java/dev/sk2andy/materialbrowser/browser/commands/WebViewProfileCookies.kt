package dev.sk2andy.materialbrowser.browser.commands

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

internal object WebViewProfileCookies {
    fun managerFor(webView: WebView): CookieManager? =
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            runCatching { WebViewCompat.getProfile(webView).cookieManager }.getOrNull()
        } else {
            CookieManager.getInstance()
        }
}
