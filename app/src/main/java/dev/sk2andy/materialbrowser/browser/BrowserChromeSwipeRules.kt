package dev.sk2andy.materialbrowser.browser

import kotlin.math.abs

internal enum class BrowserChromeSwipeAxis {
    Undecided,
    Vertical,
    Ignored,
}

internal enum class BrowserChromeSwipeDirection {
    Up,
    Down,
}

internal data class BrowserChromeSwipeState(
    val initialX: Float,
    val initialY: Float,
    val previousY: Float = initialY,
    val direction: BrowserChromeSwipeDirection? = null,
    val accumulatedDistancePx: Float = 0f,
    val axis: BrowserChromeSwipeAxis = BrowserChromeSwipeAxis.Undecided,
)

internal data class BrowserChromeSwipeUpdate(
    val state: BrowserChromeSwipeState,
    val compact: Boolean?,
)

/** Observes finger movement without depending on renderer scroll position. */
internal object BrowserChromeSwipeRules {
    const val COLLAPSE_THRESHOLD_DP = 24f
    const val EXPAND_THRESHOLD_DP = 16f

    fun update(
        state: BrowserChromeSwipeState,
        touchX: Float,
        touchY: Float,
        touchSlop: Float,
        collapseThresholdPx: Float,
        expandThresholdPx: Float,
    ): BrowserChromeSwipeUpdate {
        if (state.axis == BrowserChromeSwipeAxis.Ignored) {
            return BrowserChromeSwipeUpdate(state = state, compact = null)
        }
        if (!state.initialX.isFinite() || !state.initialY.isFinite() ||
            !state.previousY.isFinite() || !state.accumulatedDistancePx.isFinite() ||
            state.accumulatedDistancePx < 0f || !touchX.isFinite() || !touchY.isFinite() ||
            !touchSlop.isFinite() || touchSlop < 0f ||
            !collapseThresholdPx.isFinite() || collapseThresholdPx <= 0f ||
            !expandThresholdPx.isFinite() || expandThresholdPx <= 0f
        ) {
            return ignored(state)
        }

        if (state.axis == BrowserChromeSwipeAxis.Undecided) {
            val deltaX = touchX - state.initialX
            val deltaY = touchY - state.initialY
            if (!deltaX.isFinite() || !deltaY.isFinite()) return ignored(state)
            if (abs(deltaX) <= touchSlop && abs(deltaY) <= touchSlop) {
                return BrowserChromeSwipeUpdate(state = state, compact = null)
            }
            if (abs(deltaX) >= abs(deltaY)) return ignored(state)
        }

        val delta = touchY - state.previousY
        if (!delta.isFinite()) return ignored(state)
        val direction = when {
            delta < 0f -> BrowserChromeSwipeDirection.Up
            delta > 0f -> BrowserChromeSwipeDirection.Down
            else -> null
        } ?: return BrowserChromeSwipeUpdate(state = state, compact = null)
        val accumulatedDistance = if (direction == state.direction) {
            state.accumulatedDistancePx + abs(delta)
        } else {
            abs(delta)
        }
        if (!accumulatedDistance.isFinite()) return ignored(state)
        val threshold = when (direction) {
            BrowserChromeSwipeDirection.Up -> collapseThresholdPx
            BrowserChromeSwipeDirection.Down -> expandThresholdPx
        }
        val thresholdReached = accumulatedDistance >= threshold
        return BrowserChromeSwipeUpdate(
            state = state.copy(
                previousY = touchY,
                direction = direction,
                accumulatedDistancePx = if (thresholdReached) 0f else accumulatedDistance,
                axis = BrowserChromeSwipeAxis.Vertical,
            ),
            compact = if (thresholdReached) direction == BrowserChromeSwipeDirection.Up else null,
        )
    }

    private fun ignored(state: BrowserChromeSwipeState): BrowserChromeSwipeUpdate =
        BrowserChromeSwipeUpdate(
            state = state.copy(axis = BrowserChromeSwipeAxis.Ignored),
            compact = null,
        )
}
