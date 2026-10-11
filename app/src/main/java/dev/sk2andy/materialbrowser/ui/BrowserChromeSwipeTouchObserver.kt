package dev.sk2andy.materialbrowser.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import dev.sk2andy.materialbrowser.browser.BrowserChromeSwipeRules
import dev.sk2andy.materialbrowser.browser.BrowserChromeSwipeState

/** Observes viewport drags without taking the touch stream away from the page or refresh layout. */
internal class BrowserChromeSwipeTouchObserver(context: Context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val density = context.resources.displayMetrics.density
    private var enabled = false
    private var tabId: String? = null
    private var engineViewRevision = 0
    private var onCompactChanged: (Boolean) -> Unit = {}
    private var state: BrowserChromeSwipeState? = null

    fun update(
        enabled: Boolean,
        tabId: String,
        engineViewRevision: Int,
        onCompactChanged: (Boolean) -> Unit,
    ) {
        if (
            this.enabled != enabled ||
            this.tabId != tabId ||
            this.engineViewRevision != engineViewRevision
        ) {
            reset()
        }
        this.enabled = enabled
        this.tabId = tabId
        this.engineViewRevision = engineViewRevision
        this.onCompactChanged = onCompactChanged
    }

    fun onTouchEvent(event: MotionEvent) {
        if (!enabled) return
        if (event.pointerCount != 1) {
            reset()
            return
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state = BrowserChromeSwipeState(initialX = event.x, initialY = event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                val current = state ?: return
                val update = BrowserChromeSwipeRules.update(
                    state = current,
                    touchX = event.x,
                    touchY = event.y,
                    touchSlop = touchSlop,
                    collapseThresholdPx = BrowserChromeSwipeRules.COLLAPSE_THRESHOLD_DP * density,
                    expandThresholdPx = BrowserChromeSwipeRules.EXPAND_THRESHOLD_DP * density,
                )
                state = update.state
                update.compact?.let(onCompactChanged)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_POINTER_UP,
            -> reset()
        }
    }

    fun reset() {
        state = null
    }
}
