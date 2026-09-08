package dev.sk2andy.materialbrowser.browser

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

internal data class BrowserEngineScrollEvent(
    val scrollYPx: Int,
    val navigationGeneration: Int? = null,
)

internal fun interface BrowserEngineScrollListener {
    fun onScrollChanged(event: BrowserEngineScrollEvent)
}

/** Collapses renderer scroll bursts into bounded-rate browser-chrome updates. */
internal class BrowserEngineScrollRateDispatcher(
    private val schedule: (delayMillis: Long, dispatch: () -> Unit) -> Unit,
    private val nowMillis: () -> Long,
    private val dispatch: (BrowserEngineScrollEvent) -> Unit,
) : BrowserEngineScrollListener {
    private val latestEvent = AtomicReference<BrowserEngineScrollEvent?>()
    private val dispatchScheduled = AtomicBoolean(false)
    private val lastDispatchMillis = AtomicLong(NO_DISPATCH_MILLIS)

    override fun onScrollChanged(event: BrowserEngineScrollEvent) {
        latestEvent.set(event)
        scheduleIfNeeded()
    }

    private fun scheduleIfNeeded() {
        if (!dispatchScheduled.compareAndSet(false, true)) return
        val now = nowMillis()
        val previousDispatch = lastDispatchMillis.get()
        val delayMillis = if (previousDispatch == NO_DISPATCH_MILLIS) {
            0L
        } else {
            (previousDispatch + MIN_DISPATCH_INTERVAL_MILLIS - now).coerceAtLeast(0L)
        }
        schedule(delayMillis) {
            val event = latestEvent.getAndSet(null)
            if (event != null) {
                lastDispatchMillis.set(nowMillis())
                dispatch(event)
            }
            dispatchScheduled.set(false)
            if (latestEvent.get() != null) scheduleIfNeeded()
        }
    }

    private companion object {
        const val MAX_DISPATCHES_PER_SECOND = 15L
        const val MIN_DISPATCH_INTERVAL_MILLIS =
            (1_000L + MAX_DISPATCHES_PER_SECOND - 1L) / MAX_DISPATCHES_PER_SECOND
        const val NO_DISPATCH_MILLIS = Long.MIN_VALUE
    }
}

internal enum class BrowserScrollDirection {
    Down,
    Up,
}

internal data class BrowserChromeScrollState(
    val previousScrollYPx: Int? = null,
    val direction: BrowserScrollDirection? = null,
    val accumulatedDistancePx: Float = 0f,
)

internal data class BrowserChromeScrollUpdate(
    val state: BrowserChromeScrollState,
    val compact: Boolean?,
)

/** Pure scroll-to-chrome policy shared by platform renderer implementations. */
internal object BrowserChromeScrollRules {
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
