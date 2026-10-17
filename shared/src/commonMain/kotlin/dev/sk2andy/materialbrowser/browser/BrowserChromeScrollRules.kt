package dev.sk2andy.materialbrowser.browser

import kotlin.math.abs

data class BrowserEngineScrollEvent(
    val scrollYPx: Int,
    val navigationGeneration: Int? = null,
    val source: BrowserEngineScrollEventSource = BrowserEngineScrollEventSource.Renderer,
)

enum class BrowserEngineScrollEventSource {
    DocumentMetrics,
    Renderer,
}

enum class BrowserScrollDirection {
    Down,
    Up,
}

data class BrowserChromeScrollState(
    val previousScrollYPx: Int? = null,
    val direction: BrowserScrollDirection? = null,
    val accumulatedDistancePx: Float = 0f,
)

data class BrowserChromeScrollUpdate(
    val state: BrowserChromeScrollState,
    val compact: Boolean?,
)

/** Pure scroll-to-chrome policy shared by platform renderer implementations. */
object BrowserChromeScrollRules {
    fun accepts(
        eventTabId: String,
        selectedTabId: String,
        tabExists: Boolean,
        rendererIsCurrent: Boolean,
        destroyed: Boolean,
    ): Boolean =
        !destroyed &&
            tabExists &&
            rendererIsCurrent &&
            eventTabId == selectedTabId

    fun update(
        state: BrowserChromeScrollState,
        event: BrowserEngineScrollEvent,
        collapseThresholdPx: Float,
        expandThresholdPx: Float,
    ): BrowserChromeScrollUpdate {
        require(collapseThresholdPx > 0f)
        require(expandThresholdPx > 0f)

        val scrollY = event.scrollYPx.coerceAtLeast(0)
        if (scrollY == 0) {
            return BrowserChromeScrollUpdate(
                state = BrowserChromeScrollState(previousScrollYPx = 0),
                compact = false,
            )
        }

        val previousScrollY = state.previousScrollYPx ?: return BrowserChromeScrollUpdate(
            state = state.copy(previousScrollYPx = scrollY),
            compact = null,
        )
        val delta = scrollY - previousScrollY
        val direction = when {
            delta > 0 -> BrowserScrollDirection.Down
            delta < 0 -> BrowserScrollDirection.Up
            else -> null
        } ?: return BrowserChromeScrollUpdate(
            state = state.copy(previousScrollYPx = scrollY),
            compact = null,
        )
        val accumulatedDistance = if (direction == state.direction) {
            state.accumulatedDistancePx + abs(delta.toFloat())
        } else {
            abs(delta.toFloat())
        }
        val threshold = when (direction) {
            BrowserScrollDirection.Down -> collapseThresholdPx
            BrowserScrollDirection.Up -> expandThresholdPx
        }
        val thresholdReached = accumulatedDistance >= threshold
        return BrowserChromeScrollUpdate(
            state = BrowserChromeScrollState(
                previousScrollYPx = scrollY,
                direction = direction,
                accumulatedDistancePx = if (thresholdReached) 0f else accumulatedDistance,
            ),
            compact = if (thresholdReached) direction == BrowserScrollDirection.Down else null,
        )
    }
}
