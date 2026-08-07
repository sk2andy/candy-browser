package dev.sk2andy.materialbrowser.ui

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal object PagePullRefreshRules {
    const val MAX_START_SCROLL_DP = 96f
    const val TRIGGER_DISTANCE_DP = 72f
    const val OPPOSITE_FLICK_HANDOFF_TIMEOUT_MS = 2_000L

    fun isEligible(startScrollY: Float, maxStartScroll: Float): Boolean =
        startScrollY in 0f..maxStartScroll

    fun direction(dragX: Float, dragY: Float, touchSlop: Float): PullGestureDirection = when {
        maxOf(dragX.absoluteValue, dragY.absoluteValue) < touchSlop ->
            PullGestureDirection.Undecided
        dragY > 0f && dragY > dragX.absoluteValue -> PullGestureDirection.Down
        else -> PullGestureDirection.Rejected
    }

    fun progress(
        startScrollY: Float,
        dragX: Float,
        dragY: Float,
        triggerDistance: Float,
    ): Float {
        if (dragY <= 0f || dragY <= dragX.absoluteValue) return 0f
        val pullPastTop = (dragY - startScrollY).coerceAtLeast(0f)
        return (pullPastTop / triggerDistance).coerceIn(0f, 1f)
    }

    fun shouldReinforceOppositeFlick(
        previousVelocityY: Float,
        currentVelocityX: Float,
        currentVelocityY: Float,
        elapsedSincePreviousFlickMs: Long,
        minimumFlingVelocity: Float,
    ): Boolean =
        elapsedSincePreviousFlickMs in 0..OPPOSITE_FLICK_HANDOFF_TIMEOUT_MS &&
            previousVelocityY.absoluteValue >= minimumFlingVelocity &&
            currentVelocityY.absoluteValue >= minimumFlingVelocity &&
            currentVelocityY.absoluteValue > currentVelocityX.absoluteValue &&
            previousVelocityY * currentVelocityY < 0f
}

internal enum class PullGestureDirection {
    Undecided,
    Down,
    Rejected,
}

internal class PagePullRefreshTouchListener(
    private val maxStartScroll: Float,
    private val triggerDistance: Float,
    private val touchSlop: Float,
    private val isEnabled: () -> Boolean,
    private val onProgress: (Float) -> Unit,
    private val onRefresh: () -> Unit,
) : View.OnTouchListener {
    private var tracking = false
    private var downX = 0f
    private var downY = 0f
    private var startScrollY = 0f
    private var direction = PullGestureDirection.Undecided
    private var velocityTracker: VelocityTracker? = null
    private var previousFlickVelocityY = 0f
    private var previousFlickAt: Long? = null
    private var interruptedFlickVelocityY = 0f
    private var interruptedFlickAt: Long? = null
    private var gestureGeneration = 0

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val webView = view as? WebView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureGeneration++
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                interruptedFlickVelocityY = previousFlickVelocityY
                interruptedFlickAt = previousFlickAt
                // Chromium maps a zero-velocity fling to a fling cancel. Send it before the
                // DOWN reaches WebView so the same pointer can take over active momentum.
                webView.flingScroll(0, 0)
                startScrollY = webView.scrollY.toFloat().coerceAtLeast(0f)
                tracking = isEnabled() &&
                    PagePullRefreshRules.isEligible(startScrollY, maxStartScroll)
                downX = event.x
                downY = event.y
                direction = PullGestureDirection.Undecided
                onProgress(0f)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                stopVelocityTracking(clearPreviousFlick = true)
                cancel()
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (tracking && event.pointerCount == 1) {
                    if (direction == PullGestureDirection.Undecided) {
                        direction = PagePullRefreshRules.direction(
                            dragX = event.x - downX,
                            dragY = event.y - downY,
                            touchSlop = touchSlop,
                        )
                    }
                    when (direction) {
                        PullGestureDirection.Down -> onProgress(progress(event))
                        PullGestureDirection.Rejected -> cancel()
                        PullGestureDirection.Undecided -> Unit
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                completeFlick(webView, event.eventTime)
                val shouldRefresh = tracking &&
                    direction == PullGestureDirection.Down &&
                    isEnabled() &&
                    progress(event) >= 1f
                reset()
                if (shouldRefresh) onRefresh()
            }

            MotionEvent.ACTION_CANCEL -> {
                stopVelocityTracking(clearPreviousFlick = true)
                reset()
            }
        }
        return false
    }

    private fun progress(event: MotionEvent): Float = PagePullRefreshRules.progress(
        startScrollY = startScrollY,
        dragX = event.x - downX,
        dragY = event.y - downY,
        triggerDistance = triggerDistance,
    )

    private fun completeFlick(webView: WebView, eventTime: Long) {
        val tracker = velocityTracker ?: return
        val configuration = ViewConfiguration.get(webView.context)
        tracker.computeCurrentVelocity(
            1_000,
            configuration.scaledMaximumFlingVelocity.toFloat(),
        )
        val velocityX = tracker.xVelocity
        val velocityY = tracker.yVelocity
        val shouldReinforce = PagePullRefreshRules.shouldReinforceOppositeFlick(
            previousVelocityY = interruptedFlickVelocityY,
            currentVelocityX = velocityX,
            currentVelocityY = velocityY,
            elapsedSincePreviousFlickMs = interruptedFlickAt
                ?.let { eventTime - it }
                ?: Long.MAX_VALUE,
            minimumFlingVelocity = configuration.scaledMinimumFlingVelocity.toFloat(),
        )
        stopVelocityTracking(clearPreviousFlick = false)

        val isVerticalFlick = velocityY.absoluteValue >=
            configuration.scaledMinimumFlingVelocity &&
            velocityY.absoluteValue > velocityX.absoluteValue
        if (isVerticalFlick) {
            previousFlickVelocityY = velocityY
            previousFlickAt = eventTime
        } else {
            previousFlickVelocityY = 0f
            previousFlickAt = null
        }

        if (shouldReinforce) {
            val generation = gestureGeneration
            val contentVelocityY = -velocityY.roundToInt()
            webView.post {
                if (generation == gestureGeneration && webView.isAttachedToWindow) {
                    webView.flingScroll(0, contentVelocityY)
                }
            }
        }
    }

    private fun stopVelocityTracking(clearPreviousFlick: Boolean) {
        velocityTracker?.recycle()
        velocityTracker = null
        interruptedFlickVelocityY = 0f
        interruptedFlickAt = null
        if (clearPreviousFlick) {
            previousFlickVelocityY = 0f
            previousFlickAt = null
        }
    }

    private fun cancel() {
        tracking = false
        direction = PullGestureDirection.Rejected
        onProgress(0f)
    }

    private fun reset() {
        cancel()
        downX = 0f
        downY = 0f
        startScrollY = 0f
        direction = PullGestureDirection.Undecided
    }
}
