package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import dev.sk2andy.materialbrowser.browser.BrowserEngineAndroidPermissionRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineAuthPromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineContentPermissionRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineDownloadResponse
import dev.sk2andy.materialbrowser.browser.BrowserEngineFilePromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineMediaPermissionRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineNavigationTarget
import dev.sk2andy.materialbrowser.browser.BrowserEngineWebPromptRequest
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollListener
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollMetrics
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import org.mozilla.geckoview.GeckoSession

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

internal data class GeckoBrowserHistoryState(
    val urls: List<String>,
    val currentIndex: Int,
    val currentTitle: String?,
)

internal fun interface GeckoBrowserHistoryStateListener {
    fun onHistoryStateChanged(state: GeckoBrowserHistoryState)
}

internal data class GeckoMainFrameNavigationRequest(
    val url: String,
    val isRedirect: Boolean,
    val hasUserGesture: Boolean,
    val isDirectNavigation: Boolean,
    val target: BrowserEngineNavigationTarget = BrowserEngineNavigationTarget.Current,
)

internal enum class GeckoNavigationRequestDecision {
    Allow,
    Deny,
}

internal fun interface GeckoNavigationRequestListener {
    fun onNavigationRequest(request: GeckoMainFrameNavigationRequest): GeckoNavigationRequestDecision
}

internal data class GeckoNewSessionRequest(
    val url: String,
    /** Unopened Gecko session that Candy must adopt before accepting the new window. */
    val session: GeckoSession,
)

internal fun interface GeckoNewSessionListener {
    fun onNewSession(request: GeckoNewSessionRequest): Boolean
}

internal class GeckoExternalDownloadResponse(
    val metadata: BrowserEngineDownloadResponse,
    private val startTransfer: (GeckoDownloadTransferListener) -> GeckoDownloadCancellation?,
    private val discard: () -> Unit,
) : AutoCloseable {
    private var consumed = false

    fun start(listener: GeckoDownloadTransferListener): GeckoDownloadCancellation? {
        if (consumed) return null
        consumed = true
        return startTransfer(listener)
    }

    override fun close() {
        if (consumed) return
        consumed = true
        discard()
    }
}

internal fun interface GeckoDownloadResponseListener {
    /** The receiver must start or close the one-shot response. */
    fun onDownloadResponse(response: GeckoExternalDownloadResponse)
}

internal data class GeckoContextDownloadRequest(
    val url: String,
    val suggestedFileName: String? = null,
    val referrer: String? = null,
)

internal fun interface GeckoFilePromptListener {
    fun onFilePrompt(request: BrowserEngineFilePromptRequest)
}

internal fun interface GeckoAndroidPermissionRequestListener {
    fun onAndroidPermissionRequest(request: BrowserEngineAndroidPermissionRequest)
}

internal fun interface GeckoContentPermissionRequestListener {
    fun onContentPermissionRequest(request: BrowserEngineContentPermissionRequest)
}

internal fun interface GeckoMediaPermissionRequestListener {
    fun onMediaPermissionRequest(request: BrowserEngineMediaPermissionRequest)
}

internal fun interface GeckoAuthPromptListener {
    fun onAuthPrompt(request: BrowserEngineAuthPromptRequest)
}

internal fun interface GeckoWebPromptListener {
    fun onWebPrompt(request: BrowserEngineWebPromptRequest)
}

internal fun interface BrowserEnginePreviewCapture {
    fun cancel()
}

internal data class GeckoFindResult(
    val activeMatchOrdinal: Int,
    val matchCount: Int,
    val isDoneCounting: Boolean,
)

internal data class GeckoMediaSessionState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val isFullscreen: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val currentPositionMillis: Long = 0,
    val durationMillis: Long? = null,
    val playbackRate: Float = 1f,
    val sourceUrl: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val audioTrackCount: Int = 0,
    val videoTrackCount: Int = 0,
)

internal fun interface GeckoMediaSessionStateListener {
    fun onStateChanged(state: GeckoMediaSessionState)
}

internal enum class GeckoMediaCommand {
    Play,
    Pause,
    Stop,
}

/**
 * UI-facing session port. The implementation owns GeckoSession and only exposes an Android View
 * for Compose hosting, so callers cannot reach or replace the process GeckoRuntime.
 */
internal interface GeckoBrowserSession {
    val profileId: String
    val isPrivate: Boolean

    fun setStateListener(listener: GeckoBrowserSessionStateListener?)

    fun setHistoryStateListener(listener: GeckoBrowserHistoryStateListener?)

    fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?)

    fun setNewSessionListener(listener: GeckoNewSessionListener?) = Unit

    fun setDownloadResponseListener(listener: GeckoDownloadResponseListener?) = Unit

    /** Fetches through Gecko so authenticated/profile/private request state stays engine-owned. */
    fun startContextDownload(
        request: GeckoContextDownloadRequest,
        listener: GeckoDownloadTransferListener,
    ): GeckoDownloadCancellation? = null

    fun setFilePromptListener(listener: GeckoFilePromptListener?) = Unit

    fun setAndroidPermissionRequestListener(
        listener: GeckoAndroidPermissionRequestListener?,
    ) = Unit

    fun setContentPermissionRequestListener(
        listener: GeckoContentPermissionRequestListener?,
    ) = Unit

    fun setMediaPermissionRequestListener(listener: GeckoMediaPermissionRequestListener?) = Unit

    fun setAuthPromptListener(listener: GeckoAuthPromptListener?) = Unit

    fun setWebPromptListener(listener: GeckoWebPromptListener?) = Unit

    fun setContentTargetListener(listener: BrowserContentTargetListener?)

    fun dispatchContentTargetForTesting(target: WebContentTarget) = Unit

    fun setMediaStateListener(listener: GeckoMediaSessionStateListener?)

    fun setScrollListener(listener: BrowserEngineScrollListener?)

    /** Native renderer metrics used by Candy's engine-neutral scrollbar overlay. */
    fun scrollMetrics(): BrowserEngineScrollMetrics? = null

    /** Scrolls the Gecko document without synthesizing touch events. */
    fun scrollToVerticalOffset(offsetPx: Int) = Unit

    fun setVideoAutoplayBlocked(blocked: Boolean)

    /** Mutes current and future Gecko media sessions without changing tab activity. */
    fun setAudioMuted(muted: Boolean)

    fun executeMediaCommand(command: GeckoMediaCommand)

    fun seekMedia(positionMillis: Long)

    /** Keeps Gecko's compositor surface in sync with Android picture-in-picture mode. */
    fun notifyPictureInPictureModeChanged(inPictureInPicture: Boolean)

    /** Keeps page media aligned with the user's PiP play or pause intent. */
    fun setPictureInPicturePlaybackExpected(expected: Boolean)

    /** Reports whether this is Candy's selected tab so WebExtensions receive active-tab events. */
    fun setActive(active: Boolean)

    /** Binds Gecko's session delegates to Candy's stable tab identity. */
    fun bindExtensionTab(tabId: String, generation: Long) = Unit

    /** Creates and binds the one View currently rendering this session. */
    fun createView(context: Context): View

    /**
     * Runs after Gecko has both started its compositor and painted valid page content.
     * A newer request replaces an older pending request for this session.
     */
    fun awaitContentPresented(listener: () -> Unit)

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

    /** Extracts bounded article JSON through Candy's internal Gecko content-script bridge. */
    fun extractPageForReader(onComplete: (String?) -> Unit)

    /** Opens Gecko's Android print flow for the current document. */
    fun printPage(): Boolean

    /** Applies Gecko's native desktop/mobile UA and viewport policy for the next reload. */
    fun setDesktopMode(enabled: Boolean)

    /** Returns Gecko's own opaque session payload for a regular tab, if Gecko has produced one. */
    fun sessionStateSnapshot(): String? = null

    /** Restores an opaque payload only after the owning regular profile/session has been validated. */
    fun restoreSessionState(encodedState: String): Boolean = false

    /** Loads a validated HTTP(S) URL. Returns false when validation rejects the input. */
    fun loadUrl(url: String): Boolean

    /** Loads an already host-validated moz-extension options URL through startup gates. */
    fun loadExtensionUrl(url: String): Boolean = false

    fun updatePrivacyPolicy(
        policy: GeckoPrivacyPolicy,
        reloadOnCookiePermissionChange: Boolean = false,
        onReady: () -> Unit = {},
    ) = onReady()

    fun goBack()

    fun goForward()

    fun goToHistoryIndex(index: Int)

    /** Returns a known history target relative to the current entry without navigating. */
    fun historyUrlAtOffset(offset: Int): String?

    fun reload()

    fun stop()

    /** Permanently releases this session. Private session state must never be persisted. */
    fun close()
}
