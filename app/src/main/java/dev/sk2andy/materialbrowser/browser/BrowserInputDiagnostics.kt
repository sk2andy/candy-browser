package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

internal object BrowserInputDiagnostics {
    private const val TAG = "CandyTouch"
    private val enabled = Log.isLoggable(TAG, Log.VERBOSE)

    fun activityDispatch(
        event: MotionEvent,
        handled: Boolean,
        hasWindowFocus: Boolean,
        focusedView: View?,
    ) {
        if (!enabled) return
        traceEvent(
            stage = "activity",
            event = event,
            handled = handled,
            hasWindowFocus = hasWindowFocus,
            detail = "focus=${focusedView?.javaClass?.simpleName ?: "none"}",
        )
    }

    fun activityWindowFocus(hasWindowFocus: Boolean, focusedView: View?) {
        if (!enabled) return
        Log.v(
            TAG,
            "stage=activity-window-focus hasWindowFocus=$hasWindowFocus " +
                "focus=${focusedView?.javaClass?.simpleName ?: "none"}",
        )
    }

    fun popupState(expanded: Boolean, popupVisible: Boolean) {
        if (!enabled) return
        Log.v(TAG, "stage=browser-menu expanded=$expanded popupVisible=$popupVisible")
    }

    fun webViewCreated(tabId: String) {
        if (!enabled) return
        val provider = WebView.getCurrentWebViewPackage()
        Log.v(
            TAG,
            "stage=webview-created tab=$tabId " +
                "provider=${provider?.packageName ?: "unknown"} " +
                "version=${provider?.versionName ?: "unknown"}",
        )
    }

    fun webViewDispatch(
        tabId: String,
        webView: WebView,
        event: MotionEvent,
        handled: Boolean,
    ) {
        if (!enabled) return
        traceEvent(
            stage = "webview",
            event = event,
            handled = handled,
            hasWindowFocus = webView.hasWindowFocus(),
            detail = "tab=$tabId viewFocus=${webView.hasFocus()} " +
                "attached=${webView.isAttachedToWindow} shown=${webView.isShown} " +
                "scrollY=${webView.scrollY}",
        )
    }

    fun webViewWindowFocus(tabId: String, webView: WebView, hasWindowFocus: Boolean) {
        if (!enabled) return
        Log.v(
            TAG,
            "stage=webview-window-focus tab=$tabId hasWindowFocus=$hasWindowFocus " +
                "viewFocus=${webView.hasFocus()} attached=${webView.isAttachedToWindow} " +
                "shown=${webView.isShown} scrollY=${webView.scrollY}",
        )
    }

    fun fullscreenCustomView(stage: String, tabId: String, detail: String) {
        if (!enabled) return
        Log.v(TAG, "stage=fullscreen-$stage tab=$tabId $detail")
    }

    private fun traceEvent(
        stage: String,
        event: MotionEvent,
        handled: Boolean,
        hasWindowFocus: Boolean,
        detail: String,
    ) {
        if (!enabled) return
        Log.v(
            TAG,
            "stage=$stage action=${MotionEvent.actionToString(event.actionMasked)} " +
                "downTime=${event.downTime} eventTime=${event.eventTime} " +
                "x=${event.x.toInt()} y=${event.y.toInt()} pointers=${event.pointerCount} " +
                "windowFocus=$hasWindowFocus handled=$handled $detail",
        )
    }
}

internal class BrowserWebView(
    context: Context,
    private val tabId: String,
) : WebView(context) {
    private val pointerSessions = BrowserPointerSessionState()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pointerSessions.begin()
            MotionEvent.ACTION_POINTER_DOWN -> pointerSessions.end()
        }
        val handled = super.dispatchTouchEvent(event)
        BrowserInputDiagnostics.webViewDispatch(tabId, this, event, handled)
        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            pointerSessions.end()
        }
        return handled
    }

    fun pointerSessionSnapshot(): BrowserPointerSessionSnapshot = pointerSessions.snapshot()

    fun acceptsPointerSession(captured: BrowserPointerSessionSnapshot): Boolean =
        pointerSessions.accepts(captured)

    fun scrollMetricsSnapshot(): BrowserWebViewScrollMetrics = BrowserWebViewScrollMetrics(
        offsetPx = computeVerticalScrollOffset().coerceAtLeast(0),
        extentPx = computeVerticalScrollExtent().coerceAtLeast(0),
        rangePx = computeVerticalScrollRange().coerceAtLeast(0),
    )

    fun scrollToVerticalOffset(offsetPx: Int) {
        val metrics = scrollMetricsSnapshot()
        scrollTo(
            scrollX,
            offsetPx.coerceIn(0, (metrics.rangePx - metrics.extentPx).coerceAtLeast(0)),
        )
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus) pointerSessions.end()
        super.onWindowFocusChanged(hasWindowFocus)
        BrowserInputDiagnostics.webViewWindowFocus(tabId, this, hasWindowFocus)
    }

    override fun onDetachedFromWindow() {
        pointerSessions.end()
        super.onDetachedFromWindow()
    }
}

internal data class BrowserWebViewScrollMetrics(
    val offsetPx: Int,
    val extentPx: Int,
    val rangePx: Int,
)

internal class BrowserPointerSessionState {
    private var generation = 0L
    private var active = false

    fun begin() {
        generation++
        active = true
    }

    fun end() {
        generation++
        active = false
    }

    fun snapshot(): BrowserPointerSessionSnapshot = BrowserPointerSessionSnapshot(
        generation = generation,
        active = active,
    )

    fun accepts(captured: BrowserPointerSessionSnapshot): Boolean = snapshot() == captured
}

internal data class BrowserPointerSessionSnapshot(
    val generation: Long,
    val active: Boolean,
)
