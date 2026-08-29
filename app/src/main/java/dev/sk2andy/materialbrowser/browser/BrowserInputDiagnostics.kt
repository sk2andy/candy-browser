package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.absoluteValue

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
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var gestureCanFling = false
    private var touchActive = false
    private var expectedFlingDirection = 0
    private var confirmedFlingDirection = 0
    private var confirmedFlingAtMs = Long.MIN_VALUE
    private var momentumInterruption: BrowserMomentumInterruption? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                momentumInterruption = momentumInterruptionFor(event)
                clearConfirmedFling()
                gestureDownX = event.x
                gestureDownY = event.y
                gestureCanFling = isSingleTouchscreenPointer(event)
                touchActive = true
                pointerSessions.begin()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                momentumInterruption = null
                gestureCanFling = false
                clearConfirmedFling()
                pointerSessions.end()
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) interruptMomentumScroll(event)
        BrowserInputDiagnostics.webViewDispatch(tabId, this, event, handled)
        if (event.actionMasked == MotionEvent.ACTION_UP) rememberPotentialFling(event)
        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            gestureCanFling = false
            touchActive = false
            momentumInterruption = null
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) clearConfirmedFling()
            pointerSessions.end()
        }
        return handled
    }

    private fun momentumInterruptionFor(event: MotionEvent): BrowserMomentumInterruption? {
        if (
            event.pointerCount != 1 ||
            event.source and InputDevice.SOURCE_TOUCHSCREEN != InputDevice.SOURCE_TOUCHSCREEN ||
            confirmedFlingDirection == 0 ||
            SystemClock.uptimeMillis() - confirmedFlingAtMs > FLING_INTERRUPTION_WINDOW_MS
        ) return null
        return BrowserMomentumInterruption(
            downX = event.x,
            downY = event.y,
            momentumEdgeScrollY = scrollY,
            momentumDirection = confirmedFlingDirection,
        )
    }

    private fun interruptMomentumScroll(event: MotionEvent) {
        val interruption = momentumInterruption ?: return
        if (event.pointerCount != 1) {
            momentumInterruption = null
            return
        }
        val fingerTravelY = event.y - interruption.downY
        val fingerTravelX = event.x - interruption.downX
        if (
            fingerTravelY.absoluteValue <= touchSlop ||
            fingerTravelY.absoluteValue <= fingerTravelX.absoluteValue
        ) return
        val desiredScrollDelta = -fingerTravelY
        if (desiredScrollDelta * interruption.momentumDirection >= 0f) return
        val responseDistancePx = touchSlop * MOMENTUM_RESPONSE_TOUCH_SLOP_MULTIPLIER
        val targetScrollY = if (interruption.momentumDirection > 0) {
            interruption.momentumEdgeScrollY = maxOf(
                interruption.momentumEdgeScrollY,
                scrollY,
            )
            interruption.momentumEdgeScrollY - responseDistancePx
        } else {
            interruption.momentumEdgeScrollY = minOf(
                interruption.momentumEdgeScrollY,
                scrollY,
            )
            interruption.momentumEdgeScrollY + responseDistancePx
        }
        val nativeScrollInterrupted = if (interruption.momentumDirection > 0) {
            scrollY <= targetScrollY
        } else {
            scrollY >= targetScrollY
        }
        // Chromium may keep writing the old compositor-fling position after accepting the new
        // touch stream. Preserve only a visible reverse edge; the remaining drag stays native.
        if (!nativeScrollInterrupted) {
            val maximumScrollY = (
                computeVerticalScrollRange() - computeVerticalScrollExtent()
            ).coerceAtLeast(scrollY)
            scrollTo(scrollX, targetScrollY.coerceIn(0, maximumScrollY))
        }
    }

    private fun rememberPotentialFling(event: MotionEvent) {
        val fingerTravelY = event.y - gestureDownY
        val fingerTravelX = event.x - gestureDownX
        val durationMs = event.eventTime - event.downTime
        if (
            gestureCanFling &&
            durationMs <= MAX_FLING_GESTURE_DURATION_MS &&
            fingerTravelY.absoluteValue >= touchSlop * MIN_FLING_TOUCH_SLOP_MULTIPLIER &&
            fingerTravelY.absoluteValue > fingerTravelX.absoluteValue
        ) {
            expectedFlingDirection = (-fingerTravelY).compareTo(0f)
        } else {
            expectedFlingDirection = 0
        }
        confirmedFlingDirection = 0
        confirmedFlingAtMs = Long.MIN_VALUE
    }

    private fun isSingleTouchscreenPointer(event: MotionEvent): Boolean =
        event.pointerCount == 1 &&
            event.source and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        if (touchActive || top == oldTop || expectedFlingDirection == 0) return
        val direction = (top - oldTop).compareTo(0)
        if (direction == expectedFlingDirection) {
            confirmedFlingDirection = direction
            confirmedFlingAtMs = SystemClock.uptimeMillis()
        } else {
            clearConfirmedFling()
        }
    }

    private fun clearConfirmedFling() {
        expectedFlingDirection = 0
        confirmedFlingDirection = 0
        confirmedFlingAtMs = Long.MIN_VALUE
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
        if (!hasWindowFocus) {
            gestureCanFling = false
            touchActive = false
            momentumInterruption = null
            clearConfirmedFling()
            pointerSessions.end()
        }
        super.onWindowFocusChanged(hasWindowFocus)
        BrowserInputDiagnostics.webViewWindowFocus(tabId, this, hasWindowFocus)
    }

    override fun onDetachedFromWindow() {
        gestureCanFling = false
        touchActive = false
        momentumInterruption = null
        clearConfirmedFling()
        pointerSessions.end()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_FLING_GESTURE_DURATION_MS = 250L
        const val MIN_FLING_TOUCH_SLOP_MULTIPLIER = 4
        const val MOMENTUM_RESPONSE_TOUCH_SLOP_MULTIPLIER = 2
        // Complex pages can delay Java dispatch for seconds. The candidate is consumed at the
        // next DOWN, so this tolerates queue latency without affecting later gestures.
        const val FLING_INTERRUPTION_WINDOW_MS = 10_000L
    }
}

private data class BrowserMomentumInterruption(
    val downX: Float,
    val downY: Float,
    var momentumEdgeScrollY: Int,
    val momentumDirection: Int,
)

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
