package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.data.BrowserChromeScrollDispatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserChromeScrollRulesTest {
    @Test
    fun `rate dispatcher keeps latest position and schedules one immediate update`() {
        val scheduledDispatches = ArrayDeque<Pair<Long, () -> Unit>>()
        val dispatched = mutableListOf<BrowserEngineScrollEvent>()
        val dispatcher = BrowserEngineScrollRateDispatcher(
            schedule = { delayMillis, dispatch ->
                scheduledDispatches.addLast(delayMillis to dispatch)
            },
            nowMillis = { 1_000L },
            dispatch = dispatched::add,
        )

        dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 10))
        dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 30))
        dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 80))

        assertEquals(1, scheduledDispatches.size)
        val scheduled = scheduledDispatches.removeFirst()
        assertEquals(0L, scheduled.first)
        scheduled.second.invoke()
        assertEquals(listOf(BrowserEngineScrollEvent(scrollYPx = 80)), dispatched)
    }

    @Test
    fun `15 hertz rate dispatcher waits 67 milliseconds before following update`() {
        var nowMillis = 1_000L
        val scheduledDispatches = ArrayDeque<Pair<Long, () -> Unit>>()
        val dispatched = mutableListOf<BrowserEngineScrollEvent>()
        lateinit var dispatcher: BrowserEngineScrollRateDispatcher
        dispatcher = BrowserEngineScrollRateDispatcher(
            schedule = { delayMillis, dispatch ->
                scheduledDispatches.addLast(delayMillis to dispatch)
            },
            nowMillis = { nowMillis },
            maximumDispatchesPerSecond = { 15L },
        ) { event ->
            dispatched += event
            if (event.scrollYPx == 10) {
                dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 40))
            }
        }

        dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 10))
        scheduledDispatches.removeFirst().second.invoke()
        assertEquals(1, scheduledDispatches.size)
        val following = scheduledDispatches.removeFirst()
        assertEquals(67L, following.first)
        nowMillis += following.first
        following.second.invoke()

        assertEquals(
            listOf(
                BrowserEngineScrollEvent(scrollYPx = 10),
                BrowserEngineScrollEvent(scrollYPx = 40),
            ),
            dispatched,
        )
    }

    @Test
    fun `120 hertz rate dispatcher starts with 9 millisecond interval`() {
        var nowMillis = 1_000L
        val scheduledDispatches = ArrayDeque<Pair<Long, () -> Unit>>()
        lateinit var dispatcher: BrowserEngineScrollRateDispatcher
        dispatcher = BrowserEngineScrollRateDispatcher(
            schedule = { delayMillis, dispatch ->
                scheduledDispatches.addLast(delayMillis to dispatch)
            },
            nowMillis = { nowMillis },
            maximumDispatchesPerSecond = { 120L },
        ) { event ->
            if (event.scrollYPx == 10) {
                dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 40))
            }
        }

        dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 10))
        scheduledDispatches.removeFirst().second.invoke()
        val following = scheduledDispatches.removeFirst()

        assertEquals(9L, following.first)
        nowMillis += following.first
        following.second.invoke()
        assertTrue(scheduledDispatches.isEmpty())
    }

    @Test
    fun `rate dispatcher reports main scheduler lateness`() {
        var nowMillis = 1_000L
        val scheduledDispatches = ArrayDeque<Pair<Long, () -> Unit>>()
        val timings = mutableListOf<BrowserEngineScrollDispatchTiming>()
        val dispatcher = BrowserEngineScrollRateDispatcher(
            schedule = { delayMillis, dispatch ->
                scheduledDispatches.addLast(delayMillis to dispatch)
            },
            nowMillis = { nowMillis },
            onDispatchTiming = timings::add,
            dispatch = {},
        )

        dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 10))
        scheduledDispatches.removeFirst().second.invoke()
        dispatcher.onScrollChanged(BrowserEngineScrollEvent(scrollYPx = 40))
        val following = scheduledDispatches.removeFirst()
        nowMillis += following.first + 5L
        following.second.invoke()

        assertEquals(
            BrowserEngineScrollDispatchTiming(
                requestedDelayMillis = 17L,
                latenessMillis = 5L,
            ),
            timings.last(),
        )
    }

    @Test
    fun `interval sequence keeps every supported rate exact over one second`() {
        listOf(120L, 60L, 30L, 15L).forEach { dispatchesPerSecond ->
            val sequence = BrowserEngineScrollDispatchIntervalSequence()
            val intervals = List(dispatchesPerSecond.toInt()) {
                sequence.nextIntervalMillis(dispatchesPerSecond)
            }

            assertEquals(1_000L, intervals.sum())
        }
    }

    @Test
    fun `fixed 15 hertz mode keeps scrollbar at 60 hertz`() {
        var nowMillis = 1_000L
        val scheduledDispatches = ArrayDeque<Pair<Long, () -> Unit>>()
        val chromeEvents = mutableListOf<BrowserEngineScrollEvent>()
        val scrollBarEvents = mutableListOf<BrowserEngineScrollEvent>()
        val dispatchers = BrowserEngineScrollDispatchers(
            schedule = { delayMillis, dispatch ->
                scheduledDispatches.addLast(delayMillis to dispatch)
            },
            nowMillis = { nowMillis },
            chromeDispatchModeSelection = {
                BrowserChromeScrollDispatchModeSelection(
                    mode = BrowserChromeScrollDispatchMode.Fixed15Hz,
                )
            },
            dispatchChrome = chromeEvents::add,
            dispatchScrollBar = scrollBarEvents::add,
        )

        dispatchers.onScrollChanged(
            event = BrowserEngineScrollEvent(scrollYPx = 10),
            scrollBarEnabled = true,
        )
        assertEquals(listOf(0L, 0L), scheduledDispatches.map { it.first })
        repeat(2) { scheduledDispatches.removeFirst().second.invoke() }

        dispatchers.onScrollChanged(
            event = BrowserEngineScrollEvent(scrollYPx = 40),
            scrollBarEnabled = true,
        )
        assertEquals(listOf(17L, 67L), scheduledDispatches.map { it.first }.sorted())

        val chromeFollowing = scheduledDispatches.removeFirst()
        val scrollBarFollowing = scheduledDispatches.removeFirst()
        assertEquals(67L, chromeFollowing.first)
        assertEquals(17L, scrollBarFollowing.first)
        nowMillis += 17L
        scrollBarFollowing.second.invoke()
        nowMillis += 50L
        chromeFollowing.second.invoke()
        assertEquals(listOf(10, 40), chromeEvents.map { it.scrollYPx })
        assertEquals(listOf(10, 40), scrollBarEvents.map { it.scrollYPx })
    }

    @Test
    fun `optimized mode steps from 60 to 30 to 15 after sustained lateness`() {
        var state = BrowserChromeScrollDispatchRateState()
        val lateAt60Hz = BrowserEngineScrollDispatchTiming(
            requestedDelayMillis = 17L,
            latenessMillis = 8L,
        )
        repeat(BrowserChromeScrollDispatchRateRules.LATE_DISPATCHES_BEFORE_DOWNGRADE - 1) {
            state = BrowserChromeScrollDispatchRateRules.recordTiming(state, lateAt60Hz)
        }
        assertEquals(
            60L,
            BrowserChromeScrollDispatchRateRules.maximumDispatchesPerSecond(state),
        )

        state = BrowserChromeScrollDispatchRateRules.recordTiming(state, lateAt60Hz)
        assertEquals(
            30L,
            BrowserChromeScrollDispatchRateRules.maximumDispatchesPerSecond(state),
        )

        val lateAt30Hz = BrowserEngineScrollDispatchTiming(
            requestedDelayMillis = 34L,
            latenessMillis = 16L,
        )
        repeat(BrowserChromeScrollDispatchRateRules.LATE_DISPATCHES_BEFORE_DOWNGRADE) {
            state = BrowserChromeScrollDispatchRateRules.recordTiming(state, lateAt30Hz)
        }
        assertEquals(
            15L,
            BrowserChromeScrollDispatchRateRules.maximumDispatchesPerSecond(state),
        )
    }

    @Test
    fun `optimized mode requires consecutive missed intervals`() {
        var state = BrowserChromeScrollDispatchRateState()
        val late = BrowserEngineScrollDispatchTiming(
            requestedDelayMillis = 17L,
            latenessMillis = 8L,
        )
        repeat(BrowserChromeScrollDispatchRateRules.LATE_DISPATCHES_BEFORE_DOWNGRADE - 1) {
            state = BrowserChromeScrollDispatchRateRules.recordTiming(state, late)
        }

        state = BrowserChromeScrollDispatchRateRules.recordTiming(
            state,
            BrowserEngineScrollDispatchTiming(
                requestedDelayMillis = 0L,
                latenessMillis = 7L,
            ),
        )

        assertEquals(BrowserChromeScrollDispatchRateState(), state)
    }

    @Test
    fun `fixed modes keep their selected rates despite late dispatches`() {
        val timing = BrowserEngineScrollDispatchTiming(
            requestedDelayMillis = 9L,
            latenessMillis = 100L,
        )
        val expectedRates = mapOf(
            BrowserChromeScrollDispatchMode.Fixed120Hz to 120L,
            BrowserChromeScrollDispatchMode.Fixed60Hz to 60L,
            BrowserChromeScrollDispatchMode.Fixed30Hz to 30L,
            BrowserChromeScrollDispatchMode.Fixed15Hz to 15L,
        )

        expectedRates.forEach { (mode, expectedRate) ->
            val state = BrowserChromeScrollDispatchRateRules.recordTiming(
                BrowserChromeScrollDispatchRateState(mode = mode),
                timing,
            )
            assertEquals(
                expectedRate,
                BrowserChromeScrollDispatchRateRules.maximumDispatchesPerSecond(state),
            )
            assertEquals(0, state.consecutiveLateDispatches)
        }
    }

    @Test
    fun `rate controller applies live mode changes and resets optimized tier`() {
        var selection = BrowserChromeScrollDispatchModeSelection(
            mode = BrowserChromeScrollDispatchMode.Optimized,
        )
        val controller = BrowserChromeScrollDispatchRateController { selection }
        val late = BrowserEngineScrollDispatchTiming(
            requestedDelayMillis = 0L,
            latenessMillis = 8L,
        )
        repeat(BrowserChromeScrollDispatchRateRules.LATE_DISPATCHES_BEFORE_DOWNGRADE) {
            controller.recordTiming(late)
        }
        assertEquals(30L, controller.maximumDispatchesPerSecond())

        selection = BrowserChromeScrollDispatchModeSelection(
            mode = BrowserChromeScrollDispatchMode.Fixed15Hz,
            revision = 1L,
        )
        selection = BrowserChromeScrollDispatchModeSelection(
            mode = BrowserChromeScrollDispatchMode.Optimized,
            revision = 2L,
        )
        assertEquals(60L, controller.maximumDispatchesPerSecond())
    }

    @Test
    fun `frame monitor steps optimized mode down for sustained slow frames`() {
        var nowMillis = 1_000L
        var frameTimeNanos = 0L
        val scheduledFrames = ArrayDeque<(Long) -> Unit>()
        val controller = BrowserChromeScrollDispatchRateController {
            BrowserChromeScrollDispatchModeSelection(
                mode = BrowserChromeScrollDispatchMode.Optimized,
            )
        }
        val monitor = BrowserChromeScrollFrameLoadMonitor(
            scheduleFrame = scheduledFrames::addLast,
            nowMillis = { nowMillis },
            shouldMonitor = controller::isOptimizedMode,
            maximumDispatchesPerSecond = controller::maximumDispatchesPerSecond,
            onFrameTiming = controller::recordTiming,
            onMonitoringStopped = controller::resetPressure,
        )

        repeat(BrowserChromeScrollDispatchRateRules.LATE_DISPATCHES_BEFORE_DOWNGRADE + 1) {
            monitor.onScrollChanged()
            nowMillis += 25L
            frameTimeNanos += 25_000_000L
            scheduledFrames.removeFirst().invoke(frameTimeNanos)
        }
        assertEquals(30L, controller.maximumDispatchesPerSecond())

        repeat(BrowserChromeScrollDispatchRateRules.LATE_DISPATCHES_BEFORE_DOWNGRADE) {
            monitor.onScrollChanged()
            nowMillis += 50L
            frameTimeNanos += 50_000_000L
            scheduledFrames.removeFirst().invoke(frameTimeNanos)
        }
        assertEquals(15L, controller.maximumDispatchesPerSecond())
    }

    @Test
    fun `frame monitor ignores sparse scroll events while frames stay healthy`() {
        var nowMillis = 1_000L
        var frameTimeNanos = 0L
        val scheduledFrames = ArrayDeque<(Long) -> Unit>()
        val controller = BrowserChromeScrollDispatchRateController {
            BrowserChromeScrollDispatchModeSelection(
                mode = BrowserChromeScrollDispatchMode.Optimized,
            )
        }
        val monitor = BrowserChromeScrollFrameLoadMonitor(
            scheduleFrame = scheduledFrames::addLast,
            nowMillis = { nowMillis },
            shouldMonitor = controller::isOptimizedMode,
            maximumDispatchesPerSecond = controller::maximumDispatchesPerSecond,
            onFrameTiming = controller::recordTiming,
            onMonitoringStopped = controller::resetPressure,
        )

        repeat(60) { frameIndex ->
            if (frameIndex % 2 == 0) monitor.onScrollChanged()
            nowMillis += 16L
            frameTimeNanos += 16_000_000L
            scheduledFrames.removeFirst().invoke(frameTimeNanos)
        }

        assertEquals(60L, controller.maximumDispatchesPerSecond())
    }

    @Test
    fun `frame monitor resets slow frame streak after scroll inactivity`() {
        var nowMillis = 1_000L
        var frameTimeNanos = 0L
        val scheduledFrames = ArrayDeque<(Long) -> Unit>()
        val controller = BrowserChromeScrollDispatchRateController {
            BrowserChromeScrollDispatchModeSelection(
                mode = BrowserChromeScrollDispatchMode.Optimized,
            )
        }
        val monitor = BrowserChromeScrollFrameLoadMonitor(
            scheduleFrame = scheduledFrames::addLast,
            nowMillis = { nowMillis },
            shouldMonitor = controller::isOptimizedMode,
            maximumDispatchesPerSecond = controller::maximumDispatchesPerSecond,
            onFrameTiming = controller::recordTiming,
            onMonitoringStopped = controller::resetPressure,
        )

        repeat(BrowserChromeScrollDispatchRateRules.LATE_DISPATCHES_BEFORE_DOWNGRADE) {
            monitor.onScrollChanged()
            nowMillis += 25L
            frameTimeNanos += 25_000_000L
            scheduledFrames.removeFirst().invoke(frameTimeNanos)
        }
        assertEquals(60L, controller.maximumDispatchesPerSecond())

        nowMillis += 251L
        frameTimeNanos += 251_000_000L
        scheduledFrames.removeFirst().invoke(frameTimeNanos)
        monitor.onScrollChanged()
        repeat(2) {
            nowMillis += 25L
            frameTimeNanos += 25_000_000L
            scheduledFrames.removeFirst().invoke(frameTimeNanos)
        }

        assertEquals(60L, controller.maximumDispatchesPerSecond())
    }

    @Test
    fun `frame monitor stays idle in fixed modes`() {
        val scheduledFrames = ArrayDeque<(Long) -> Unit>()
        val controller = BrowserChromeScrollDispatchRateController {
            BrowserChromeScrollDispatchModeSelection(
                mode = BrowserChromeScrollDispatchMode.Fixed120Hz,
            )
        }
        val monitor = BrowserChromeScrollFrameLoadMonitor(
            scheduleFrame = scheduledFrames::addLast,
            nowMillis = { 1_000L },
            shouldMonitor = controller::isOptimizedMode,
            maximumDispatchesPerSecond = controller::maximumDispatchesPerSecond,
            onFrameTiming = controller::recordTiming,
            onMonitoringStopped = controller::resetPressure,
        )

        monitor.onScrollChanged()

        assertTrue(scheduledFrames.isEmpty())
        assertEquals(120L, controller.maximumDispatchesPerSecond())
    }

    @Test
    fun `disabled scrollbar keeps only chrome dispatcher active`() {
        val scheduledDispatches = ArrayDeque<Pair<Long, () -> Unit>>()
        val dispatchers = BrowserEngineScrollDispatchers(
            schedule = { delayMillis, dispatch ->
                scheduledDispatches.addLast(delayMillis to dispatch)
            },
            nowMillis = { 1_000L },
            dispatchChrome = {},
            dispatchScrollBar = {},
        )

        dispatchers.onScrollChanged(
            event = BrowserEngineScrollEvent(scrollYPx = 10),
            scrollBarEnabled = false,
        )

        assertEquals(1, scheduledDispatches.size)
        assertEquals(0L, scheduledDispatches.single().first)
    }

    @Test
    fun `document metrics update scrollbar without changing chrome position`() {
        val scheduledDispatches = ArrayDeque<Pair<Long, () -> Unit>>()
        val chromeEvents = mutableListOf<BrowserEngineScrollEvent>()
        val scrollBarEvents = mutableListOf<BrowserEngineScrollEvent>()
        val dispatchers = BrowserEngineScrollDispatchers(
            schedule = { delayMillis, dispatch ->
                scheduledDispatches.addLast(delayMillis to dispatch)
            },
            nowMillis = { 1_000L },
            dispatchChrome = chromeEvents::add,
            dispatchScrollBar = scrollBarEvents::add,
        )
        val rendererEvent = BrowserEngineScrollEvent(scrollYPx = 100)
        val documentMetricsEvent = BrowserEngineScrollEvent(
            scrollYPx = 300,
            source = BrowserEngineScrollEventSource.DocumentMetrics,
        )

        dispatchers.onScrollChanged(rendererEvent, scrollBarEnabled = true)
        dispatchers.onScrollChanged(documentMetricsEvent, scrollBarEnabled = true)
        repeat(2) { scheduledDispatches.removeFirst().second.invoke() }

        assertEquals(listOf(rendererEvent), chromeEvents)
        assertEquals(listOf(documentMetricsEvent), scrollBarEvents)
    }

    @Test
    fun `downward distance collapses only after collapse threshold`() {
        var state = BrowserChromeScrollState()

        state = update(state, 0).state
        state = update(state, 8).also { assertNull(it.compact) }.state
        state = update(state, 23).also { assertNull(it.compact) }.state
        val collapsed = update(state, 24)

        assertEquals(true, collapsed.compact)
        assertEquals(0f, collapsed.state.accumulatedDistancePx)
        assertEquals(BrowserScrollDirection.Down, collapsed.state.direction)
    }

    @Test
    fun `upward distance expands after smaller expand threshold`() {
        var state = BrowserChromeScrollState(previousScrollYPx = 80)

        state = update(state, 70).also { assertNull(it.compact) }.state
        val expanded = update(state, 64)

        assertEquals(false, expanded.compact)
        assertEquals(0f, expanded.state.accumulatedDistancePx)
        assertEquals(BrowserScrollDirection.Up, expanded.state.direction)
    }

    @Test
    fun `top expands immediately and resets accumulated direction`() {
        val expanded = update(
            BrowserChromeScrollState(
                previousScrollYPx = 80,
                direction = BrowserScrollDirection.Down,
                accumulatedDistancePx = 20f,
            ),
            0,
        )

        assertEquals(false, expanded.compact)
        assertEquals(BrowserChromeScrollState(previousScrollYPx = 0), expanded.state)
    }

    @Test
    fun `direction change resets accumulated distance before applying new delta`() {
        val downward = update(BrowserChromeScrollState(previousScrollYPx = 40), 60).state
        val changedDirection = update(downward, 55)

        assertNull(changedDirection.compact)
        assertEquals(BrowserScrollDirection.Up, changedDirection.state.direction)
        assertEquals(5f, changedDirection.state.accumulatedDistancePx)
    }

    @Test
    fun `stale closed and background renderer events are rejected`() {
        assertTrue(
            accepts(
                eventTabId = "selected",
                selectedTabId = "selected",
            ),
        )
        assertFalse(
            accepts(
                eventTabId = "background",
                selectedTabId = "selected",
            ),
        )
        assertFalse(accepts(tabExists = false))
        assertFalse(accepts(rendererIsCurrent = false))
        assertFalse(accepts(destroyed = true))
    }

    private fun update(
        state: BrowserChromeScrollState,
        scrollYPx: Int,
    ): BrowserChromeScrollUpdate = BrowserChromeScrollRules.update(
        state = state,
        event = BrowserEngineScrollEvent(scrollYPx),
        collapseThresholdPx = 24f,
        expandThresholdPx = 16f,
    )

    private fun accepts(
        eventTabId: String = "selected",
        selectedTabId: String = "selected",
        tabExists: Boolean = true,
        rendererIsCurrent: Boolean = true,
        destroyed: Boolean = false,
    ): Boolean = BrowserChromeScrollRules.accepts(
        eventTabId = eventTabId,
        selectedTabId = selectedTabId,
        tabExists = tabExists,
        rendererIsCurrent = rendererIsCurrent,
        destroyed = destroyed,
    )
}
