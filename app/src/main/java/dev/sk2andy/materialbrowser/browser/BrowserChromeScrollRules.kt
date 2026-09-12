package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.data.BrowserChromeScrollDispatchMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

private const val MILLIS_PER_SECOND = 1_000L

internal class BrowserEngineScrollDispatchIntervalSequence {
    private var dispatchesPerSecond = 0L
    private var remainderPhase = 0L

    @Synchronized
    fun nextIntervalMillis(nextDispatchesPerSecond: Long): Long {
        require(nextDispatchesPerSecond in 1L..MILLIS_PER_SECOND)
        if (dispatchesPerSecond != nextDispatchesPerSecond) {
            dispatchesPerSecond = nextDispatchesPerSecond
            remainderPhase = 0L
        }
        val wholeMillis = MILLIS_PER_SECOND / dispatchesPerSecond
        remainderPhase += MILLIS_PER_SECOND % dispatchesPerSecond
        return if (remainderPhase > 0L) {
            remainderPhase -= dispatchesPerSecond
            wholeMillis + 1L
        } else {
            wholeMillis
        }
    }
}

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

internal data class BrowserEngineScrollDispatchTiming(
    val requestedDelayMillis: Long,
    val latenessMillis: Long,
)

/** Collapses renderer scroll bursts into bounded-rate browser-chrome updates. */
internal class BrowserEngineScrollRateDispatcher(
    private val schedule: (delayMillis: Long, dispatch: () -> Unit) -> Unit,
    private val nowMillis: () -> Long,
    private val maximumDispatchesPerSecond: () -> Long = {
        DEFAULT_MAXIMUM_DISPATCHES_PER_SECOND
    },
    private val onDispatchTiming: (BrowserEngineScrollDispatchTiming) -> Unit = {},
    private val dispatch: (BrowserEngineScrollEvent) -> Unit,
) : BrowserEngineScrollListener {
    private val dispatchIntervalSequence = BrowserEngineScrollDispatchIntervalSequence()
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
            val minimumDispatchIntervalMillis = dispatchIntervalSequence.nextIntervalMillis(
                maximumDispatchesPerSecond(),
            )
            (previousDispatch + minimumDispatchIntervalMillis - now).coerceAtLeast(0L)
        }
        val expectedDispatchMillis = now + delayMillis
        schedule(delayMillis) {
            val event = latestEvent.getAndSet(null)
            if (event != null) {
                val dispatchMillis = nowMillis()
                lastDispatchMillis.set(dispatchMillis)
                dispatch(event)
                onDispatchTiming(
                    BrowserEngineScrollDispatchTiming(
                        requestedDelayMillis = delayMillis,
                        latenessMillis = (dispatchMillis - expectedDispatchMillis).coerceAtLeast(0L),
                    ),
                )
            }
            dispatchScheduled.set(false)
            if (latestEvent.get() != null) scheduleIfNeeded()
        }
    }

    private companion object {
        const val DEFAULT_MAXIMUM_DISPATCHES_PER_SECOND = 60L
        const val NO_DISPATCH_MILLIS = Long.MIN_VALUE
    }
}

internal data class BrowserChromeScrollDispatchRateState(
    val mode: BrowserChromeScrollDispatchMode = BrowserChromeScrollDispatchMode.Default,
    val optimizedDispatchesPerSecond: Long = OPTIMIZED_MAXIMUM_DISPATCHES_PER_SECOND,
    val consecutiveLateDispatches: Int = 0,
) {
    companion object {
        const val OPTIMIZED_MAXIMUM_DISPATCHES_PER_SECOND = 60L
    }
}

internal object BrowserChromeScrollDispatchRateRules {
    const val LATE_DISPATCHES_BEFORE_DOWNGRADE = 30

    fun synchronizeMode(
        state: BrowserChromeScrollDispatchRateState,
        mode: BrowserChromeScrollDispatchMode,
    ): BrowserChromeScrollDispatchRateState = if (state.mode == mode) {
        state
    } else {
        BrowserChromeScrollDispatchRateState(mode = mode)
    }

    fun maximumDispatchesPerSecond(state: BrowserChromeScrollDispatchRateState): Long =
        state.mode.fixedDispatchesPerSecond ?: state.optimizedDispatchesPerSecond

    fun recordTiming(
        state: BrowserChromeScrollDispatchRateState,
        timing: BrowserEngineScrollDispatchTiming,
    ): BrowserChromeScrollDispatchRateState {
        if (state.mode != BrowserChromeScrollDispatchMode.Optimized) return state
        val overloadLatenessMillis = overloadLatenessMillis(
            state.optimizedDispatchesPerSecond,
        )
        val overloaded = timing.latenessMillis >= overloadLatenessMillis
        if (!overloaded) {
            return state.copy(consecutiveLateDispatches = 0)
        }
        val lateDispatches = state.consecutiveLateDispatches + 1
        if (lateDispatches < LATE_DISPATCHES_BEFORE_DOWNGRADE) {
            return state.copy(consecutiveLateDispatches = lateDispatches)
        }
        return state.copy(
            optimizedDispatchesPerSecond = when (state.optimizedDispatchesPerSecond) {
                60L -> 30L
                else -> 15L
            },
            consecutiveLateDispatches = 0,
        )
    }

    private fun overloadLatenessMillis(dispatchesPerSecond: Long): Long {
        val maximumIntervalMillis =
            (MILLIS_PER_SECOND + dispatchesPerSecond - 1L) / dispatchesPerSecond
        return ((maximumIntervalMillis - 1L) / 2L).coerceAtLeast(1L)
    }
}

internal data class BrowserChromeScrollDispatchModeSelection(
    val mode: BrowserChromeScrollDispatchMode,
    val revision: Long = 0L,
)

internal class BrowserChromeScrollDispatchRateController(
    private val selection: () -> BrowserChromeScrollDispatchModeSelection,
) {
    private var appliedSelection = selection()
    private var state = BrowserChromeScrollDispatchRateState(mode = appliedSelection.mode)

    @Synchronized
    fun maximumDispatchesPerSecond(): Long {
        synchronizeMode()
        return BrowserChromeScrollDispatchRateRules.maximumDispatchesPerSecond(state)
    }

    @Synchronized
    fun recordTiming(timing: BrowserEngineScrollDispatchTiming) {
        synchronizeMode()
        state = BrowserChromeScrollDispatchRateRules.recordTiming(state, timing)
    }

    @Synchronized
    fun isOptimizedMode(): Boolean {
        synchronizeMode()
        return state.mode == BrowserChromeScrollDispatchMode.Optimized
    }

    @Synchronized
    fun resetPressure() {
        synchronizeMode()
        state = state.copy(consecutiveLateDispatches = 0)
    }

    private fun synchronizeMode() {
        val currentSelection = selection()
        state = if (currentSelection.revision != appliedSelection.revision) {
            BrowserChromeScrollDispatchRateState(mode = currentSelection.mode)
        } else {
            BrowserChromeScrollDispatchRateRules.synchronizeMode(
                state = state,
                mode = currentSelection.mode,
            )
        }
        appliedSelection = currentSelection
    }
}

internal class BrowserChromeScrollFrameLoadMonitor(
    private val scheduleFrame: (onFrame: (frameTimeNanos: Long) -> Unit) -> Unit,
    private val nowMillis: () -> Long,
    private val shouldMonitor: () -> Boolean,
    private val maximumDispatchesPerSecond: () -> Long,
    private val onFrameTiming: (BrowserEngineScrollDispatchTiming) -> Unit,
    private val onMonitoringStopped: () -> Unit,
) {
    private var activeUntilMillis = 0L
    private var frameScheduled = false
    private var previousFrameTimeNanos: Long? = null

    @Synchronized
    fun onScrollChanged() {
        if (!shouldMonitor()) {
            activeUntilMillis = 0L
            previousFrameTimeNanos = null
            onMonitoringStopped()
            return
        }
        activeUntilMillis = nowMillis() + ACTIVE_AFTER_SCROLL_MILLIS
        scheduleNextFrameIfNeeded()
    }

    @Synchronized
    private fun onFrame(frameTimeNanos: Long) {
        frameScheduled = false
        if (!shouldMonitor() || nowMillis() > activeUntilMillis) {
            previousFrameTimeNanos = null
            onMonitoringStopped()
            return
        }
        previousFrameTimeNanos?.let { previousFrameTime ->
            val dispatchesPerSecond = maximumDispatchesPerSecond()
            val expectedFrameMillis =
                (MILLIS_PER_SECOND + dispatchesPerSecond - 1L) / dispatchesPerSecond
            val elapsedFrameMillis =
                ((frameTimeNanos - previousFrameTime) / NANOS_PER_MILLISECOND)
                    .coerceAtLeast(0L)
            onFrameTiming(
                BrowserEngineScrollDispatchTiming(
                    requestedDelayMillis = expectedFrameMillis,
                    latenessMillis = (elapsedFrameMillis - expectedFrameMillis)
                        .coerceAtLeast(0L),
                ),
            )
        }
        previousFrameTimeNanos = frameTimeNanos
        scheduleNextFrameIfNeeded()
    }

    private fun scheduleNextFrameIfNeeded() {
        if (frameScheduled) return
        frameScheduled = true
        scheduleFrame(::onFrame)
    }

    private companion object {
        const val ACTIVE_AFTER_SCROLL_MILLIS = 250L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

/** Routes engine scroll sources to independently bounded chrome and scrollbar consumers. */
internal class BrowserEngineScrollDispatchers(
    schedule: (delayMillis: Long, dispatch: () -> Unit) -> Unit,
    scheduleFrame: ((onFrame: (frameTimeNanos: Long) -> Unit) -> Unit)? = null,
    nowMillis: () -> Long,
    chromeDispatchModeSelection: () -> BrowserChromeScrollDispatchModeSelection = {
        BrowserChromeScrollDispatchModeSelection(
            mode = BrowserChromeScrollDispatchMode.Default,
        )
    },
    dispatchChrome: (BrowserEngineScrollEvent) -> Unit,
    dispatchScrollBar: (BrowserEngineScrollEvent) -> Unit,
) {
    private val chromeRateController = BrowserChromeScrollDispatchRateController(
        selection = chromeDispatchModeSelection,
    )
    private val chrome = BrowserEngineScrollRateDispatcher(
        schedule = schedule,
        nowMillis = nowMillis,
        maximumDispatchesPerSecond = chromeRateController::maximumDispatchesPerSecond,
        dispatch = dispatchChrome,
    )
    private val chromeFrameLoadMonitor = scheduleFrame?.let { frameScheduler ->
        BrowserChromeScrollFrameLoadMonitor(
            scheduleFrame = frameScheduler,
            nowMillis = nowMillis,
            shouldMonitor = chromeRateController::isOptimizedMode,
            maximumDispatchesPerSecond = chromeRateController::maximumDispatchesPerSecond,
            onFrameTiming = chromeRateController::recordTiming,
            onMonitoringStopped = chromeRateController::resetPressure,
        )
    }
    private val scrollBar = BrowserEngineScrollRateDispatcher(
        schedule = schedule,
        nowMillis = nowMillis,
        maximumDispatchesPerSecond = { SCROLL_BAR_MAXIMUM_DISPATCHES_PER_SECOND },
        dispatch = dispatchScrollBar,
    )

    fun onScrollChanged(
        event: BrowserEngineScrollEvent,
        scrollBarEnabled: Boolean,
    ) {
        if (event.source == BrowserEngineScrollEventSource.Renderer) {
            chromeFrameLoadMonitor?.onScrollChanged()
            chrome.onScrollChanged(event)
        }
        if (scrollBarEnabled) scrollBar.onScrollChanged(event)
    }

    private companion object {
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
