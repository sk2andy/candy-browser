package dev.sk2andy.materialbrowser.browser

import android.util.Log
import android.view.MotionEvent
import android.view.View

internal object BrowserInputDiagnostics {
    private const val TAG = "CandyTouch"
    private val systemEnabled = Log.isLoggable(TAG, Log.VERBOSE)

    @Volatile
    private var sessionEnabled = false

    val isSessionEnabled: Boolean
        get() = sessionEnabled

    private val enabled: Boolean
        get() = systemEnabled || sessionEnabled

    fun setSessionEnabled(enabled: Boolean) {
        sessionEnabled = enabled
    }

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

    fun engineCreated(tabId: String, engine: String) {
        if (!enabled) return
        Log.v(
            TAG,
            "stage=engine-created tab=$tabId engine=$engine",
        )
    }

    fun engineDispatch(
        tabId: String,
        view: View,
        event: MotionEvent,
        handled: Boolean,
    ) {
        if (!enabled) return
        traceEvent(
            stage = "engine",
            event = event,
            handled = handled,
            hasWindowFocus = view.hasWindowFocus(),
            detail = "tab=$tabId viewFocus=${view.hasFocus()} " +
                "attached=${view.isAttachedToWindow} shown=${view.isShown} " +
                "scrollY=${view.scrollY}",
        )
    }

    fun engineWindowFocus(tabId: String, view: View, hasWindowFocus: Boolean) {
        if (!enabled) return
        Log.v(
            TAG,
            "stage=engine-window-focus tab=$tabId hasWindowFocus=$hasWindowFocus " +
                "viewFocus=${view.hasFocus()} attached=${view.isAttachedToWindow} " +
                "shown=${view.isShown} scrollY=${view.scrollY}",
        )
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
