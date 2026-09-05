package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.view.View

internal data class GeckoBrowserSessionState(
    val url: String? = null,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

internal fun interface GeckoBrowserSessionStateListener {
    fun onStateChanged(state: GeckoBrowserSessionState)
}

/**
 * UI-facing session port. The implementation owns GeckoSession and only exposes an Android View
 * for Compose hosting, so callers cannot reach or replace the process GeckoRuntime.
 */
internal interface GeckoBrowserSession {
    val profileId: String
    val isPrivate: Boolean

    fun setStateListener(listener: GeckoBrowserSessionStateListener?)

    /** Creates and binds the one View currently rendering this session. */
    fun createView(context: Context): View

    /** Releases a View returned by [createView] without closing the navigable session. */
    fun releaseView(view: View)

    /** Loads a validated HTTP(S) URL. Returns false when validation rejects the input. */
    fun loadUrl(url: String): Boolean

    fun goBack()

    fun goForward()

    fun reload()

    fun stop()

    /** Permanently releases this session. Private session state must never be persisted. */
    fun close()
}
