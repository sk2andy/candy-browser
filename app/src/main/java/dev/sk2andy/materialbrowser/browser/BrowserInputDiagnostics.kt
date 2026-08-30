package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.webkit.WebView
import android.widget.OverScroller
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

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

    fun momentumRecovery(stage: String, tabId: String, detail: String) {
        if (!enabled) return
        Log.v(TAG, "stage=momentum-$stage tab=$tabId $detail")
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
    private val viewConfiguration = ViewConfiguration.get(context)
    private val touchSlop = viewConfiguration.scaledTouchSlop
    private val recoveryScroller = OverScroller(context)
    private val recoveryVelocityScroller = OverScroller(context)
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var gestureCanFling = false
    private var gestureVelocityTracker: VelocityTracker? = null
    private var gestureGeneration = 0L
    private var touchActive = false
    private var expectedFlingDirection = 0
    private var expectedFlingAtMs = Long.MIN_VALUE
    private var confirmedFlingDirection = 0
    private var confirmedFlingAtMs = Long.MIN_VALUE
    private var momentumInterruption: BrowserMomentumInterruption? = null
    private var recoveryGeneration = Long.MIN_VALUE
    private val recoveryFrame = object : Runnable {
        override fun run() {
            if (
                recoveryGeneration != gestureGeneration ||
                touchActive ||
                !isAttachedToWindow ||
                !recoveryScroller.computeScrollOffset()
            ) return
            scrollTo(scrollX, recoveryScroller.currY)
            postOnAnimation(this)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureGeneration++
                momentumInterruption = momentumInterruptionFor(event)
                BrowserInputDiagnostics.momentumRecovery(
                    stage = "down",
                    tabId = tabId,
                    detail = "candidate=${momentumInterruption != null} " +
                        "confirmedDirection=$confirmedFlingDirection " +
                        "confirmedAgeMs=${SystemClock.uptimeMillis() - confirmedFlingAtMs}",
                )
                stopRecoveryFling()
                clearConfirmedFling()
                beginVelocityTracking(event)
                gestureDownX = event.x
                gestureDownY = event.y
                gestureCanFling = isSingleTouchscreenPointer(event)
                touchActive = true
                pointerSessions.begin()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                gestureGeneration++
                momentumInterruption = null
                gestureCanFling = false
                stopRecoveryFling()
                recycleVelocityTracker()
                clearConfirmedFling()
                pointerSessions.end()
            }

            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP,
            -> gestureVelocityTracker?.addMovement(event)
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) interruptMomentumScroll(event)
        BrowserInputDiagnostics.webViewDispatch(tabId, this, event, handled)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val scrollVelocityY = trackedScrollVelocityY()
            rememberPotentialFling(event)
            recoverInterruptedFling(scrollVelocityY)
        }
        if (
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            gestureCanFling = false
            touchActive = false
            momentumInterruption = null
            recycleVelocityTracker()
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                gestureGeneration++
                stopRecoveryFling()
                clearConfirmedFling()
            }
            pointerSessions.end()
        }
        return handled
    }

    private fun momentumInterruptionFor(event: MotionEvent): BrowserMomentumInterruption? {
        if (
            event.pointerCount != 1 ||
            event.source and InputDevice.SOURCE_TOUCHSCREEN != InputDevice.SOURCE_TOUCHSCREEN ||
            confirmedFlingDirection == 0 ||
            event.eventTime - confirmedFlingAtMs > FLING_INTERRUPTION_WINDOW_MS
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
            interruption.manualCorrectionApplied = true
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
            expectedFlingAtMs = SystemClock.uptimeMillis()
        } else {
            expectedFlingDirection = 0
            expectedFlingAtMs = Long.MIN_VALUE
        }
        confirmedFlingDirection = 0
        confirmedFlingAtMs = Long.MIN_VALUE
    }

    private fun beginVelocityTracking(event: MotionEvent) {
        recycleVelocityTracker()
        gestureVelocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
    }

    private fun trackedScrollVelocityY(): Float {
        val tracker = gestureVelocityTracker ?: return 0f
        tracker.computeCurrentVelocity(
            VELOCITY_UNITS_PER_SECOND,
            viewConfiguration.scaledMaximumFlingVelocity.toFloat(),
        )
        return -tracker.yVelocity
    }

    private fun recoverInterruptedFling(scrollVelocityY: Float) {
        val interruption = momentumInterruption
        if (interruption == null) {
            BrowserInputDiagnostics.momentumRecovery(
                stage = "skip",
                tabId = tabId,
                detail = "reason=no-candidate velocityY=$scrollVelocityY",
            )
            return
        }
        val direction = expectedFlingDirection
        if (
            direction == 0 ||
            direction * interruption.momentumDirection >= 0 ||
            scrollVelocityY.absoluteValue < minimumRecoveryVelocity ||
            scrollVelocityY * direction <= 0f
        ) {
            BrowserInputDiagnostics.momentumRecovery(
                stage = "skip",
                tabId = tabId,
                detail = "reason=unqualified direction=$direction " +
                    "priorDirection=${interruption.momentumDirection} " +
                    "manualCorrection=${interruption.manualCorrectionApplied} " +
                    "velocityY=$scrollVelocityY",
            )
            return
        }

        val capturedGeneration = gestureGeneration
        val scrollYAtUp = scrollY
        val maximumScrollY = (
            computeVerticalScrollRange() - computeVerticalScrollExtent()
        ).coerceAtLeast(scrollY)
        recoveryVelocityScroller.fling(
            0,
            scrollY,
            0,
            scrollVelocityY.roundToInt(),
            0,
            0,
            0,
            maximumScrollY,
        )
        BrowserInputDiagnostics.momentumRecovery(
            stage = "scheduled",
            tabId = tabId,
            detail = "direction=$direction velocityY=$scrollVelocityY scrollY=$scrollY",
        )
        val replacementFling = Runnable {
            if (
                gestureGeneration != capturedGeneration ||
                touchActive ||
                !isAttachedToWindow
            ) return@Runnable

            // Old Chromium builds can accept every reverse touch event but keep the previous
            // compositor fling after UP. Preserve the velocity decay that elapsed while the
            // watchdog observed the native fling so recovery does not visibly restart at full speed.
            recoveryVelocityScroller.computeScrollOffset()
            val recoveryVelocityY = (
                recoveryVelocityScroller.currVelocity * direction
            ).roundToInt()
            if (recoveryVelocityY.absoluteValue < viewConfiguration.scaledMinimumFlingVelocity) {
                return@Runnable
            }
            BrowserInputDiagnostics.momentumRecovery(
                stage = "fling",
                tabId = tabId,
                detail = "direction=$direction velocityY=$recoveryVelocityY scrollY=$scrollY",
            )
            flingScroll(0, 0)
            recoveryGeneration = capturedGeneration
            recoveryScroller.fling(
                0,
                scrollY,
                0,
                recoveryVelocityY,
                0,
                0,
                0,
                maximumScrollY.coerceAtLeast(scrollY),
            )
            postOnAnimation(recoveryFrame)
        }
        val nativeFlingWatchdog = object : Runnable {
            var previousScrollY = scrollYAtUp
            var stalledFrames = 0

            override fun run() {
                if (
                    gestureGeneration != capturedGeneration ||
                    touchActive ||
                    !isAttachedToWindow
                ) return
                val shadowRunning = recoveryVelocityScroller.computeScrollOffset()
                val shadowVelocity = recoveryVelocityScroller.currVelocity
                val observation = BrowserMomentumRecoveryRules.observe(
                    previousScrollY = previousScrollY,
                    currentScrollY = scrollY,
                    direction = direction,
                    stalledFrames = stalledFrames,
                    shadowRunning = shadowRunning,
                    shadowVelocity = shadowVelocity,
                    minimumRecoveryVelocity = minimumRecoveryVelocity,
                    requiredStalledFrames = NATIVE_FLING_STALL_FRAMES,
                )
                stalledFrames = observation.stalledFrames
                previousScrollY = scrollY
                when (observation.decision) {
                    BrowserMomentumWatchdogDecision.Continue -> {
                        postOnAnimation(this)
                        return
                    }

                    BrowserMomentumWatchdogDecision.Stop -> {
                        recoveryVelocityScroller.abortAnimation()
                        return
                    }

                    BrowserMomentumWatchdogDecision.Recover -> Unit
                }
                BrowserInputDiagnostics.momentumRecovery(
                    stage = "stalled",
                    tabId = tabId,
                    detail = "direction=$direction shadowVelocity=$shadowVelocity " +
                        "stalledFrames=$stalledFrames scrollY=$scrollY",
                )
                replacementFling.run()
            }
        }
        postOnAnimation(nativeFlingWatchdog)
    }

    private val minimumRecoveryVelocity: Float
        get() = viewConfiguration.scaledMinimumFlingVelocity *
            MIN_RECOVERY_VELOCITY_MULTIPLIER.toFloat()

    private fun stopRecoveryFling() {
        recoveryGeneration = Long.MIN_VALUE
        removeCallbacks(recoveryFrame)
        recoveryScroller.abortAnimation()
        recoveryVelocityScroller.abortAnimation()
    }

    private fun recycleVelocityTracker() {
        gestureVelocityTracker?.recycle()
        gestureVelocityTracker = null
    }

    private fun isSingleTouchscreenPointer(event: MotionEvent): Boolean =
        event.pointerCount == 1 &&
            event.source and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        if (touchActive || top == oldTop || expectedFlingDirection == 0) return
        val now = SystemClock.uptimeMillis()
        if (now - expectedFlingAtMs > FLING_CONFIRMATION_WINDOW_MS) {
            clearConfirmedFling()
            return
        }
        val direction = (top - oldTop).compareTo(0)
        if (direction == expectedFlingDirection) {
            expectedFlingAtMs = now
            confirmedFlingDirection = direction
            confirmedFlingAtMs = now
            BrowserInputDiagnostics.momentumRecovery(
                stage = "confirmed",
                tabId = tabId,
                detail = "direction=$direction scrollY=$top delta=${top - oldTop}",
            )
        }
    }

    private fun clearConfirmedFling() {
        expectedFlingDirection = 0
        expectedFlingAtMs = Long.MIN_VALUE
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
        gestureGeneration++
        momentumInterruption = null
        stopRecoveryFling()
        clearConfirmedFling()
        flingScroll(0, 0)
        val metrics = scrollMetricsSnapshot()
        scrollTo(
            scrollX,
            offsetPx.coerceIn(0, (metrics.rangePx - metrics.extentPx).coerceAtLeast(0)),
        )
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus) {
            gestureGeneration++
            gestureCanFling = false
            touchActive = false
            momentumInterruption = null
            stopRecoveryFling()
            recycleVelocityTracker()
            clearConfirmedFling()
            pointerSessions.end()
        }
        super.onWindowFocusChanged(hasWindowFocus)
        BrowserInputDiagnostics.webViewWindowFocus(tabId, this, hasWindowFocus)
    }

    override fun onDetachedFromWindow() {
        gestureGeneration++
        gestureCanFling = false
        touchActive = false
        momentumInterruption = null
        stopRecoveryFling()
        recycleVelocityTracker()
        clearConfirmedFling()
        pointerSessions.end()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val MAX_FLING_GESTURE_DURATION_MS = 250L
        const val MIN_FLING_TOUCH_SLOP_MULTIPLIER = 4
        const val MOMENTUM_RESPONSE_TOUCH_SLOP_MULTIPLIER = 2
        const val VELOCITY_UNITS_PER_SECOND = 1_000
        const val FLING_CONFIRMATION_WINDOW_MS = 500L
        const val FLING_INTERRUPTION_WINDOW_MS = 120L
        const val MIN_RECOVERY_VELOCITY_MULTIPLIER = 4
        const val NATIVE_FLING_STALL_FRAMES = 3
    }
}

private data class BrowserMomentumInterruption(
    val downX: Float,
    val downY: Float,
    var momentumEdgeScrollY: Int,
    val momentumDirection: Int,
    var manualCorrectionApplied: Boolean = false,
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
