package dev.sk2andy.materialbrowser.browser

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

internal data class BrowserEngineScrollEvent(
    val scrollYPx: Int,
    val navigationGeneration: Int? = null,
    val source: BrowserEngineScrollEventSource = BrowserEngineScrollEventSource.Renderer,
)

internal enum class BrowserEngineScrollEventSource {
    DocumentMetrics,
    Renderer,
}

internal fun interface BrowserEngineScrollListener {
    fun onScrollChanged(event: BrowserEngineScrollEvent)
}

/** Collapses renderer scroll bursts into bounded-rate browser-chrome updates. */
internal class BrowserEngineScrollRateDispatcher(
    private val schedule: (delayMillis: Long, dispatch: () -> Unit) -> Unit,
    private val nowMillis: () -> Long,
    maximumDispatchesPerSecond: Long = DEFAULT_MAXIMUM_DISPATCHES_PER_SECOND,
    private val dispatch: (BrowserEngineScrollEvent) -> Unit,
) : BrowserEngineScrollListener {
    private val minimumDispatchIntervalMillis = minimumDispatchIntervalMillis(
        maximumDispatchesPerSecond,
    )
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
            (previousDispatch + minimumDispatchIntervalMillis - now).coerceAtLeast(0L)
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
        const val DEFAULT_MAXIMUM_DISPATCHES_PER_SECOND = 15L
        const val MILLIS_PER_SECOND = 1_000L
        const val NO_DISPATCH_MILLIS = Long.MIN_VALUE

        fun minimumDispatchIntervalMillis(maximumDispatchesPerSecond: Long): Long {
            require(maximumDispatchesPerSecond > 0L)
            return MILLIS_PER_SECOND / maximumDispatchesPerSecond +
                if (MILLIS_PER_SECOND % maximumDispatchesPerSecond == 0L) 0L else 1L
        }
    }
}

/** Routes engine scroll sources to independently bounded chrome and scrollbar consumers. */
internal class BrowserEngineScrollDispatchers(
    schedule: (delayMillis: Long, dispatch: () -> Unit) -> Unit,
    nowMillis: () -> Long,
    dispatchChrome: (BrowserEngineScrollEvent) -> Unit,
    dispatchScrollBar: (BrowserEngineScrollEvent) -> Unit,
) {
    private val chrome = BrowserEngineScrollRateDispatcher(
        schedule = schedule,
        nowMillis = nowMillis,
        maximumDispatchesPerSecond = CHROME_MAXIMUM_DISPATCHES_PER_SECOND,
        dispatch = dispatchChrome,
    )
    private val scrollBar = BrowserEngineScrollRateDispatcher(
        schedule = schedule,
        nowMillis = nowMillis,
        maximumDispatchesPerSecond = SCROLL_BAR_MAXIMUM_DISPATCHES_PER_SECOND,
        dispatch = dispatchScrollBar,
    )

    fun onScrollChanged(
        event: BrowserEngineScrollEvent,
        scrollBarEnabled: Boolean,
    ) {
        if (event.source == BrowserEngineScrollEventSource.Renderer) {
            chrome.onScrollChanged(event)
        }
        if (scrollBarEnabled) scrollBar.onScrollChanged(event)
    }

    private companion object {
        const val CHROME_MAXIMUM_DISPATCHES_PER_SECOND = 15L
        const val SCROLL_BAR_MAXIMUM_DISPATCHES_PER_SECOND = 60L
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
