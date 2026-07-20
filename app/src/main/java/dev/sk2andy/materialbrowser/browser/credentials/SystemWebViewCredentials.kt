package dev.sk2andy.materialbrowser.browser.credentials

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Enables system-owned credentials for a browser WebView without injecting JavaScript or
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
    /**
     * Configures Android Autofill/password managers and WebAuthn/passkeys.
     *
     * Passkey support depends on the installed WebView provider, not only the OS version. An old
     * provider therefore leaves WebAuthn at its safe default instead of throwing during startup.
     */
    @MainThread
    fun configure(webView: WebView): WebViewCredentialSupport {
        enableSystemAutofill(webView)

        val webAuthentication = when (CredentialFeaturePolicy.webAuthenticationMode(
            featureSupported = isWebAuthenticationSupported(),
        )) {
            WebAuthenticationMode.NONE -> WebAuthenticationSupport.UNAVAILABLE
            WebAuthenticationMode.BROWSER -> enableBrowserWebAuthentication(webView)
        }

        return WebViewCredentialSupport(
            systemAutofillReady = true,
            webAuthentication = webAuthentication,
        )
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

    @SuppressLint("WrongConstant")
    private fun isWebAuthenticationSupported(): Boolean = try {
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)
    } catch (_: RuntimeException) {
        false
    }

    private fun enableBrowserWebAuthentication(webView: WebView): WebAuthenticationSupport = try {
        WebSettingsCompat.setWebAuthenticationSupport(
            webView.settings,
            WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER,
        )
        WebAuthenticationSupport.ENABLED_FOR_BROWSER
    } catch (_: RuntimeException) {
        // Provider could change between feature detection and settings access. Keep default mode.
        WebAuthenticationSupport.CONFIGURATION_FAILED
    }
}

data class WebViewCredentialSupport(
    /** Platform is ready; actual fill/save availability still depends on user's Autofill service. */
    val systemAutofillReady: Boolean,
    val webAuthentication: WebAuthenticationSupport,
)

enum class WebAuthenticationSupport {
    ENABLED_FOR_BROWSER,
    UNAVAILABLE,
    CONFIGURATION_FAILED,
}

internal enum class WebAuthenticationMode {
    NONE,
    BROWSER,
}

internal object CredentialFeaturePolicy {
    fun webAuthenticationMode(featureSupported: Boolean): WebAuthenticationMode =
        if (featureSupported) WebAuthenticationMode.BROWSER else WebAuthenticationMode.NONE
}
