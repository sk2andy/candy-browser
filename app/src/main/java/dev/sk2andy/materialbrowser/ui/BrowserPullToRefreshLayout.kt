package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.math.absoluteValue

/** Native gesture bridge for browser engine views hosted through Compose AndroidView. */
internal class BrowserPullToRefreshLayout(
    context: Context,
    internal val contentView: View,
) : SwipeRefreshLayout(context) {
    private var canContentScrollUp: () -> Boolean = { true }
    private var onRefresh: () -> Boolean = { false }
    private var gestureOnRefresh: (() -> Boolean)? = null
    private var indicatorColor: Int? = null
    private var indicatorContainerColor: Int? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var gestureDirection = BrowserPullGestureDirection.Undecided

    init {
        addView(
            contentView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        setOnChildScrollUpCallback { _, _ -> canContentScrollUp() }
        setOnRefreshListener {
            val accepted = isEnabled && (gestureOnRefresh ?: onRefresh)()
            gestureOnRefresh = null
            if (!accepted) isRefreshing = false
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                gestureDirection = BrowserPullGestureDirection.Undecided
                gestureOnRefresh = onRefresh
            }

            MotionEvent.ACTION_MOVE -> {
                gestureDirection = BrowserPullGestureRules.resolve(
                    current = gestureDirection,
                    deltaX = event.x - downX,
                    deltaY = event.y - downY,
                    touchSlop = touchSlop,
                )
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                gestureDirection = BrowserPullGestureDirection.PassThrough
            }
        }
        val terminal =
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        val intercepted =
            if (gestureDirection != BrowserPullGestureDirection.PassThrough || terminal) {
                super.onInterceptTouchEvent(event)
            } else {
                false
            }
        if (terminal) {
            if (
                event.actionMasked == MotionEvent.ACTION_CANCEL ||
                gestureDirection == BrowserPullGestureDirection.PassThrough
            ) {
                gestureOnRefresh = null
            }
            gestureDirection = BrowserPullGestureDirection.Undecided
        }
        return intercepted
    }

    fun update(
        enabled: Boolean,
        refreshing: Boolean,
        indicatorColor: Int,
        indicatorContainerColor: Int,
        canChildScrollUp: () -> Boolean,
        onRefresh: () -> Boolean,
    ) {
        canContentScrollUp = canChildScrollUp
        this.onRefresh = onRefresh
        isEnabled = enabled
        val nextRefreshing = enabled && refreshing
        if (isRefreshing != nextRefreshing) isRefreshing = nextRefreshing
        if (this.indicatorColor != indicatorColor) {
            this.indicatorColor = indicatorColor
            setColorSchemeColors(indicatorColor)
        }
        if (this.indicatorContainerColor != indicatorContainerColor) {
            this.indicatorContainerColor = indicatorContainerColor
            setProgressBackgroundColorSchemeColor(indicatorContainerColor)
        }
    }
}

internal enum class BrowserPullGestureDirection {
    Undecided,
    Pull,
    PassThrough,
}

internal object BrowserPullGestureRules {
    fun resolve(
        current: BrowserPullGestureDirection,
        deltaX: Float,
        deltaY: Float,
        touchSlop: Float,
    ): BrowserPullGestureDirection {
        if (current != BrowserPullGestureDirection.Undecided) return current
        if (!deltaX.isFinite() || !deltaY.isFinite() || !touchSlop.isFinite() || touchSlop < 0f) {
            return BrowserPullGestureDirection.PassThrough
        }
        if (deltaX.absoluteValue <= touchSlop && deltaY.absoluteValue <= touchSlop) {
            return BrowserPullGestureDirection.Undecided
        }
        return if (deltaY > 0f && deltaY.absoluteValue > deltaX.absoluteValue) {
            BrowserPullGestureDirection.Pull
        } else {
            BrowserPullGestureDirection.PassThrough
        }
    }
}
