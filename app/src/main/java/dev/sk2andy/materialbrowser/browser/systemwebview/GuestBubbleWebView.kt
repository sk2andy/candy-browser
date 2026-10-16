package dev.sk2andy.materialbrowser.browser.systemwebview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.sk2andy.materialbrowser.browser.SearchSettings
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleRules
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules

/** This view is created only in the dedicated, disposable guest process and data directory. */
internal class GuestBubbleWebView(
    context: Context,
    private val searchSettings: SearchSettings,
    private val onNavigation: (String, Boolean) -> Unit,
    private val onLoading: (Boolean) -> Unit,
    private val onUnavailableAction: () -> Unit,
) {
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private val webView = WebView(context).apply {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        importantForContentCapture = View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS
        isSaveEnabled = false
        filterTouchesWhenObscured = true
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.saveFormData = false
        settings.mediaPlaybackRequiresUserGesture = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                BrowserUriPolicy.normalizeHttpUrl(request.url.toString()) == null

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                onLoading(true)
                onNavigation(BrowserUriPolicy.normalizeHttpUrl(url).orEmpty(), view.canGoBack())
            }

            override fun onPageFinished(view: WebView, url: String?) {
                onLoading(false)
                onNavigation(BrowserUriPolicy.normalizeHttpUrl(url).orEmpty(), view.canGoBack())
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                onNavigation(BrowserUriPolicy.normalizeHttpUrl(url).orEmpty(), view.canGoBack())
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback,
            ) = callback.invoke(origin, false, false)

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                filePathCallback.onReceiveValue(null)
                onUnavailableAction()
                return true
            }
        }
        setDownloadListener { _, _, _, _, _ -> onUnavailableAction() }
    }

    fun navigate(input: String): Boolean {
        val url = BrowserUrlRules.resolve(input, searchSettings).url?.value
            ?.let(BrowserUriPolicy::normalizeHttpUrl) ?: return false
        webView.loadUrl(url)
        return true
    }

    val view: View get() = webView

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun onResume() = webView.onResume()

    fun onPause() = webView.onPause()

    companion object {
        fun initializeProcessDirectory() {
            WebView.setDataDirectorySuffix(GuestBubbleRules.WEBVIEW_DATA_SUFFIX)
        }
    }

    fun close() {
        webView.stopLoading()
        webView.webChromeClient = null
        webView.setDownloadListener(null)
        webView.destroy()
    }
}
