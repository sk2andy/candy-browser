package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserChromeSwipeRulesTest {
    @Test
    fun `upward finger distance collapses at collapse threshold`() {
        var state = BrowserChromeSwipeState(initialX = 50f, initialY = 100f)

        state = update(state, touchY = 92f).also { assertNull(it.compact) }.state
        state = update(state, touchY = 77f).also { assertNull(it.compact) }.state
        val collapsed = update(state, touchY = 76f)

        assertEquals(true, collapsed.compact)
        assertEquals(BrowserChromeSwipeDirection.Up, collapsed.state.direction)
        assertEquals(0f, collapsed.state.accumulatedDistancePx)
    }

    @Test
    fun `downward finger distance expands at smaller expand threshold`() {
        val initial = BrowserChromeSwipeState(initialX = 50f, initialY = 100f)
        val started = update(initial, touchY = 115f)

        assertNull(started.compact)
        val expanded = update(started.state, touchY = 116f)

        assertEquals(false, expanded.compact)
        assertEquals(BrowserChromeSwipeDirection.Down, expanded.state.direction)
        assertEquals(0f, expanded.state.accumulatedDistancePx)
    }

    @Test
    fun `movement at touch slop remains undecided`() {
        val initial = BrowserChromeSwipeState(initialX = 50f, initialY = 100f)
        val update = update(initial, touchX = 58f, touchY = 108f)

        assertNull(update.compact)
        assertEquals(initial, update.state)
    }

    @Test
    fun `tap cannot change chrome even with small thresholds`() {
        val initial = BrowserChromeSwipeState(initialX = 50f, initialY = 100f)
        val update = BrowserChromeSwipeRules.update(
            state = initial,
            touchX = 50f,
            touchY = 104f,
            touchSlop = 8f,
            collapseThresholdPx = 1f,
            expandThresholdPx = 1f,
        )

        assertNull(update.compact)
        assertEquals(initial, update.state)
    }

    @Test
    fun `horizontal gesture stays ignored when later movement turns vertical`() {
        val initial = BrowserChromeSwipeState(initialX = 50f, initialY = 100f)
        val horizontal = update(initial, touchX = 74f, touchY = 112f)
        val vertical = update(horizontal.state, touchX = 74f, touchY = 200f)

        assertNull(horizontal.compact)
        assertNull(vertical.compact)
        assertEquals(BrowserChromeSwipeAxis.Ignored, vertical.state.axis)
        assertEquals(horizontal.state, vertical.state)
    }

    @Test
    fun `diagonal tie beyond touch slop stays with web content`() {
        val update = update(
            BrowserChromeSwipeState(initialX = 50f, initialY = 100f),
            touchX = 74f,
            touchY = 124f,
        )

        assertNull(update.compact)
        assertEquals(BrowserChromeSwipeAxis.Ignored, update.state.axis)
    }

    @Test
    fun `accepted vertical stream survives lateral drift`() {
        val started = update(
            BrowserChromeSwipeState(initialX = 50f, initialY = 100f),
            touchY = 90f,
        )
        val collapsed = update(started.state, touchX = 100f, touchY = 76f)

        assertEquals(true, collapsed.compact)
        assertEquals(BrowserChromeSwipeAxis.Vertical, collapsed.state.axis)
    }

    @Test
    fun `direction reversal discards previous distance`() {
        val upward = update(
            BrowserChromeSwipeState(initialX = 50f, initialY = 100f),
            touchY = 80f,
        )
        val reversed = update(upward.state, touchY = 85f)

        assertNull(reversed.compact)
        assertEquals(BrowserChromeSwipeDirection.Down, reversed.state.direction)
        assertEquals(5f, reversed.state.accumulatedDistancePx)
        assertEquals(false, update(reversed.state, touchY = 96f).compact)
    }

    @Test
    fun `stationary movement preserves vertical distance`() {
        val started = update(
            BrowserChromeSwipeState(initialX = 50f, initialY = 100f),
            touchY = 90f,
        )
        val stationary = update(started.state, touchY = 90f)

        assertNull(stationary.compact)
        assertEquals(started.state, stationary.state)
        assertEquals(true, update(stationary.state, touchY = 76f).compact)
    }

    @Test
    fun `new gesture starts without prior accumulated movement`() {
        val first = update(
            BrowserChromeSwipeState(initialX = 50f, initialY = 100f),
            touchY = 80f,
        )
        val second = update(
            BrowserChromeSwipeState(initialX = 50f, initialY = 80f),
            touchY = 70f,
        )

        assertNull(first.compact)
        assertNull(second.compact)
        assertEquals(10f, second.state.accumulatedDistancePx)
    }

    @Test
    fun `density scaled thresholds keep same finger direction`() {
        val initial = BrowserChromeSwipeState(initialX = 50f, initialY = 100f)
        val below = BrowserChromeSwipeRules.update(
            state = initial,
            touchX = 50f,
            touchY = 53f,
            touchSlop = 16f,
            collapseThresholdPx = 48f,
            expandThresholdPx = 32f,
        )
        val collapsed = BrowserChromeSwipeRules.update(
            state = below.state,
            touchX = 50f,
            touchY = 52f,
            touchSlop = 16f,
            collapseThresholdPx = 48f,
            expandThresholdPx = 32f,
        )

        assertNull(below.compact)
        assertEquals(true, collapsed.compact)
    }

    @Test
    fun `invalid geometry disables stream through later valid movement`() {
        val initial = BrowserChromeSwipeState(initialX = 50f, initialY = 100f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            val invalidX = update(initial, touchX = invalid, touchY = 80f)
            val invalidY = update(initial, touchY = invalid)

            listOf(invalidX, invalidY).forEach { invalidUpdate ->
                assertNull(invalidUpdate.compact)
                assertEquals(BrowserChromeSwipeAxis.Ignored, invalidUpdate.state.axis)
                assertNull(update(invalidUpdate.state, touchY = 50f).compact)
            }
        }
    }

    @Test
    fun `invalid starting geometry and overflowed deltas stay ignored`() {
        val starts = listOf(
            BrowserChromeSwipeState(initialX = Float.NaN, initialY = 100f),
            BrowserChromeSwipeState(initialX = 50f, initialY = Float.POSITIVE_INFINITY),
            BrowserChromeSwipeState(initialX = 50f, initialY = 100f, previousY = Float.NaN),
            BrowserChromeSwipeState(initialX = 50f, initialY = -Float.MAX_VALUE),
        )
        starts.forEach { state ->
            val update = update(state, touchY = Float.MAX_VALUE)

            assertNull(update.compact)
            assertEquals(BrowserChromeSwipeAxis.Ignored, update.state.axis)
        }
    }

    @Test
    fun `invalid slop and thresholds cannot change chrome`() {
        val initial = BrowserChromeSwipeState(initialX = 50f, initialY = 100f)
        val inputs = listOf(
            Triple(-1f, 24f, 16f),
            Triple(Float.NaN, 24f, 16f),
            Triple(Float.POSITIVE_INFINITY, 24f, 16f),
            Triple(8f, 0f, 16f),
            Triple(8f, -1f, 16f),
            Triple(8f, Float.NaN, 16f),
            Triple(8f, Float.POSITIVE_INFINITY, 16f),
            Triple(8f, 24f, 0f),
            Triple(8f, 24f, -1f),
            Triple(8f, 24f, Float.NaN),
            Triple(8f, 24f, Float.POSITIVE_INFINITY),
        )
        inputs.forEach { (touchSlop, collapseThresholdPx, expandThresholdPx) ->
            val update = BrowserChromeSwipeRules.update(
                state = initial,
                touchX = 50f,
                touchY = 50f,
                touchSlop = touchSlop,
                collapseThresholdPx = collapseThresholdPx,
                expandThresholdPx = expandThresholdPx,
            )

            assertNull(update.compact)
            assertEquals(BrowserChromeSwipeAxis.Ignored, update.state.axis)
        }
    }

    private fun update(
        state: BrowserChromeSwipeState,
        touchX: Float = state.initialX,
        touchY: Float,
    ): BrowserChromeSwipeUpdate = BrowserChromeSwipeRules.update(
        state = state,
        touchX = touchX,
        touchY = touchY,
        touchSlop = 8f,
        collapseThresholdPx = 24f,
        expandThresholdPx = 16f,
    )
}
