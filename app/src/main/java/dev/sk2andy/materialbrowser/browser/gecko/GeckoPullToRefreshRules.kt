package dev.sk2andy.materialbrowser.browser.gecko

import org.mozilla.geckoview.PanZoomController

/** The touched Gecko scroll chain, rather than only the document, admits a parent pull. */
internal object GeckoPullToRefreshRules {
    fun canCheckPullOnMove(
        handledResult: Int,
        scrollableDirections: Int,
        overscrollDirections: Int,
    ): Boolean = when (handledResult) {
        PanZoomController.INPUT_RESULT_UNHANDLED,
        PanZoomController.INPUT_RESULT_HANDLED,
        PanZoomController.INPUT_RESULT_HANDLED_CONTENT,
        -> allowsTopOverscroll(scrollableDirections, overscrollDirections)
        else -> false
    }

    fun canAllowParentIntercept(
        handledResult: Int,
        scrollableDirections: Int,
        overscrollDirections: Int,
    ): Boolean = when (handledResult) {
        PanZoomController.INPUT_RESULT_UNHANDLED,
        PanZoomController.INPUT_RESULT_HANDLED,
        -> allowsTopOverscroll(scrollableDirections, overscrollDirections)
        else -> false
    }

    private fun allowsTopOverscroll(scrollableDirections: Int, overscrollDirections: Int): Boolean =
        scrollableDirections and PanZoomController.SCROLLABLE_FLAG_TOP == 0 &&
            overscrollDirections and PanZoomController.OVERSCROLL_FLAG_VERTICAL != 0
}

/** Invalidates asynchronous hit-test answers on terminal, interrupted or replacement streams. */
internal class GeckoPullToRefreshTouchState {
    private var generation = 0L
    private var active = false
    private var downAllowsPull: Boolean? = null
    private var moveAllowsPull = false
    private var moveRequested = false

    fun start(): Long {
        generation++
        active = true
        downAllowsPull = null
        moveAllowsPull = false
        moveRequested = false
        return generation
    }

    fun finish() {
        active = false
    }

    fun canApply(requestGeneration: Long): Boolean = active && requestGeneration == generation

    fun requestMove(): Long? {
        if (!active || moveRequested || downAllowsPull == false) return null
        moveRequested = true
        return generation
    }

    fun recordDown(requestGeneration: Long, allowed: Boolean): Boolean {
        if (!canApply(requestGeneration)) return false
        downAllowsPull = allowed
        return allowed && moveAllowsPull
    }

    fun recordMove(requestGeneration: Long, allowed: Boolean): Boolean {
        if (!canApply(requestGeneration)) return false
        moveAllowsPull = allowed
        return downAllowsPull == true && allowed
    }
}
