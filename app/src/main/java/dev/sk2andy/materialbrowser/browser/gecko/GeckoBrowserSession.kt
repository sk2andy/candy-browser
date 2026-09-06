package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.graphics.Bitmap
import android.view.View

internal data class GeckoBrowserSessionState(
    val url: String? = null,
    val title: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val lastNavigationSucceeded: Boolean? = null,
    val crashed: Boolean = false,
    val failureDescription: String? = null,
)

internal fun interface GeckoBrowserSessionStateListener {
    fun onStateChanged(state: GeckoBrowserSessionState)
}

internal fun interface BrowserEnginePreviewCapture {
    fun cancel()
}

internal data class GeckoFindResult(
    val activeMatchOrdinal: Int,
    val matchCount: Int,
    val isDoneCounting: Boolean,
)

/**
 * UI-facing session port. The implementation owns GeckoSession and only exposes an Android View
 * for Compose hosting, so callers cannot reach or replace the process GeckoRuntime.
 */
internal interface GeckoBrowserSession {
    val profileId: String
    val isPrivate: Boolean

    fun setStateListener(listener: GeckoBrowserSessionStateListener?)

    /** Reports whether this is Candy's selected tab so WebExtensions receive active-tab events. */
    fun setActive(active: Boolean)

    /** Creates and binds the one View currently rendering this session. */
    fun createView(context: Context): View

    /** Releases a View returned by [createView] without closing the navigable session. */
    fun releaseView(view: View)

    /** Captures the currently rendered viewport and returns a bounded tab-preview bitmap. */
    fun capturePreview(
        targetWidthPx: Int,
        visibleViewHeightPx: Int,
        maximumTargetHeightPx: Int,
        onComplete: (Bitmap?) -> Unit,
    ): BrowserEnginePreviewCapture?

    fun findInPage(
        query: String,
        forward: Boolean,
        onComplete: (GeckoFindResult?) -> Unit,
    )

    fun clearFindInPage()

    /** Opens Gecko's Android print flow for the current document. */
    fun printPage(): Boolean

    /** Applies Gecko's native desktop/mobile UA and viewport policy for the next reload. */
    fun setDesktopMode(enabled: Boolean)

    /** Loads a validated HTTP(S) URL. Returns false when validation rejects the input. */
    fun loadUrl(url: String): Boolean

    fun updatePrivacyPolicy(
        policy: GeckoPrivacyPolicy,
        onReady: () -> Unit = {},
    ) = onReady()

    fun goBack()

    fun goForward()

    fun reload()

    fun stop()

    /** Permanently releases this session. Private session state must never be persisted. */
    fun close()
}
