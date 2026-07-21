package dev.sk2andy.materialbrowser.ui

import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import kotlin.math.absoluteValue

internal object PagePullRefreshRules {
    const val MAX_START_SCROLL_DP = 96f
    const val TRIGGER_DISTANCE_DP = 72f

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

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val webView = view as? WebView ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startScrollY = webView.scrollY.toFloat().coerceAtLeast(0f)
                tracking = isEnabled() &&
                    PagePullRefreshRules.isEligible(startScrollY, maxStartScroll)
                downX = event.x
                downY = event.y
                direction = PullGestureDirection.Undecided
                onProgress(0f)
            }

            MotionEvent.ACTION_POINTER_DOWN -> cancel()

            MotionEvent.ACTION_MOVE -> {
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
                val shouldRefresh = tracking &&
                    direction == PullGestureDirection.Down &&
                    isEnabled() &&
                    progress(event) >= 1f
                reset()
                if (shouldRefresh) onRefresh()
            }

            MotionEvent.ACTION_CANCEL -> reset()
        }
        return false
    }

    private fun progress(event: MotionEvent): Float = PagePullRefreshRules.progress(
        startScrollY = startScrollY,
        dragX = event.x - downX,
        dragY = event.y - downY,
        triggerDistance = triggerDistance,
    )

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
