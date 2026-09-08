package dev.sk2andy.materialbrowser.browser

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
    fun `rate dispatcher waits 67 milliseconds before following update`() {
        var nowMillis = 1_000L
        val scheduledDispatches = ArrayDeque<Pair<Long, () -> Unit>>()
        val dispatched = mutableListOf<BrowserEngineScrollEvent>()
        lateinit var dispatcher: BrowserEngineScrollRateDispatcher
        dispatcher = BrowserEngineScrollRateDispatcher(
            schedule = { delayMillis, dispatch ->
                scheduledDispatches.addLast(delayMillis to dispatch)
            },
            nowMillis = { nowMillis },
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
