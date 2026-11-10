package dev.sk2andy.materialbrowser.browser.systemwebview.credentials

import android.view.View
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Enables system Autofill for a browser WebView without injecting JavaScript or
 * handling credential material in the app.
 *
 * Controller hooks:
 * 1. Call [configure] once from `createWebView`, before the first `loadUrl`.
 * 2. Call [onAttached] after moving the WebView into its visible container.
 *
 * No detach hook is needed. WebView owns its virtual Autofill nodes and the system Autofill
 * service owns each fill/save session. Cancelling that session while switching tabs would also
 * cancel legitimate password-manager UI.
 */
object SystemWebViewCredentials {
    /** Configures Android Autofill while WebAuthn is unavailable to Candy's package. */
    @MainThread
    fun configure(webView: WebView) {
        enableSystemAutofill(webView)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                WebSettingsCompat.setWebAuthenticationSupport(
                    webView.settings,
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE,
                )
            }
        } catch (_: RuntimeException) {
            // An older or changing provider keeps its default WebAuthn mode.
        }
    }

    /** Reasserts Autofill eligibility after a retained WebView moves between tab containers. */
    @MainThread
    fun onAttached(webView: WebView) {
        enableSystemAutofill(webView)
    }

    private fun enableSystemAutofill(webView: WebView) {
        // WebView exposes HTML controls as virtual Autofill children. Do not set hints on the
        // WebView itself: sites' autocomplete attributes describe username/password fields.
        webView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
    }
}
