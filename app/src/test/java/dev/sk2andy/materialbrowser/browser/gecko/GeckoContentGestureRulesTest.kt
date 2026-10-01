package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoContentGestureRulesTest {
    @Test
    fun `handled down becomes active touch stream`() {
        val state = GeckoContentGestureRules.onDown(
            state = GeckoContentGestureState(),
            downTime = 42L,
            handled = true,
        )

        assertEquals(42L, state.activeTouchDownTime)
    }

    @Test
    fun `unhandled down does not become active touch stream`() {
        val state = GeckoContentGestureRules.onDown(
            state = GeckoContentGestureState(activeTouchDownTime = 11L),
            downTime = 42L,
            handled = false,
        )

        assertNull(state.activeTouchDownTime)
    }

    @Test
    fun `focus loss cancels active touch exactly once`() {
        val active = GeckoContentGestureRules.onDown(
            state = GeckoContentGestureState(),
            downTime = 42L,
            handled = true,
        )
        val first = GeckoContentGestureRules.cancel(active)
        val repeated = GeckoContentGestureRules.cancel(first.state)

        assertEquals(42L, first.dispatchCancelDownTime)
        assertNull(first.state.activeTouchDownTime)
        assertTrue(GeckoContentGestureRules.hasCancelledStream(first.state))
        assertNull(repeated.dispatchCancelDownTime)
        assertTrue(GeckoContentGestureRules.hasCancelledStream(repeated.state))
    }

    @Test
    fun `terminal event prevents later synthetic cancel`() {
        val active = GeckoContentGestureRules.onDown(
            state = GeckoContentGestureState(),
            downTime = 42L,
            handled = true,
        )
        val terminal = GeckoContentGestureRules.onTerminal(active, downTime = 42L)

        assertNull(GeckoContentGestureRules.cancel(terminal).dispatchCancelDownTime)
    }

    @Test
    fun `terminal event from stale stream does not clear active touch`() {
        val active = GeckoContentGestureState(activeTouchDownTime = 42L)

        val state = GeckoContentGestureRules.onTerminal(active, downTime = 11L)

        assertEquals(42L, state.activeTouchDownTime)
    }

    @Test
    fun `new handled down replaces stale stream identity`() {
        val replaced = GeckoContentGestureRules.onDown(
            state = GeckoContentGestureState(
                activeTouchDownTime = 11L,
                cancelledTouchDownTime = 7L,
            ),
            downTime = 42L,
            handled = true,
        )

        assertTrue(replaced.activeTouchDownTime == 42L)
        assertNull(replaced.cancelledTouchDownTime)
    }

    @Test
    fun `native cancel blocks delayed context menu until fresh down`() {
        val active = GeckoContentGestureState(activeTouchDownTime = 42L)
        val canceled = GeckoContentGestureRules.onCancel(active)
        val terminal = GeckoContentGestureRules.onTerminal(canceled, downTime = 42L)
        val next = GeckoContentGestureRules.onDown(terminal, downTime = 43L, handled = true)

        assertNull(canceled.activeTouchDownTime)
        assertEquals(42L, canceled.cancelledTouchDownTime)
        assertTrue(GeckoContentGestureRules.hasCancelledStream(terminal))
        assertNull(next.cancelledTouchDownTime)
        assertEquals(43L, next.activeTouchDownTime)
    }

    @Test
    fun `native cancel never dispatches a second synthetic cancel`() {
        val canceled = GeckoContentGestureRules.onCancel(
            GeckoContentGestureState(activeTouchDownTime = 42L),
        )
        val repeated = GeckoContentGestureRules.onCancel(canceled)

        assertEquals(canceled, repeated)
        assertNull(GeckoContentGestureRules.cancel(repeated).dispatchCancelDownTime)
    }


    @Test
    fun `native cancel blocks context callback even when Gecko down was unhandled`() {
        val down = GeckoContentGestureRules.onDown(
            GeckoContentGestureState(),
            downTime = 42L,
            handled = false,
        )
        val canceled = GeckoContentGestureRules.onCancel(down)

        assertNull(down.activeTouchDownTime)
        assertEquals(42L, canceled.cancelledTouchDownTime)
        assertTrue(GeckoContentGestureRules.hasCancelledStream(canceled))
        assertNull(GeckoContentGestureRules.cancel(canceled).dispatchCancelDownTime)
    }


    @Test
    fun `up finishes unhandled stream before later cancellation`() {
        val down = GeckoContentGestureRules.onDown(
            GeckoContentGestureState(),
            downTime = 42L,
            handled = false,
        )
        val terminal = GeckoContentGestureRules.onTerminal(down, downTime = 42L)

        assertNull(terminal.observedTouchDownTime)
        assertEquals(terminal, GeckoContentGestureRules.onCancel(terminal))
        assertNull(GeckoContentGestureRules.cancel(terminal).state.cancelledTouchDownTime)
    }


    @Test
    fun `synthetic cancel blocks unhandled stream without dispatching an event`() {
        val down = GeckoContentGestureRules.onDown(
            GeckoContentGestureState(),
            downTime = 42L,
            handled = false,
        )

        val transition = GeckoContentGestureRules.cancel(down)

        assertEquals(42L, transition.state.cancelledTouchDownTime)
        assertNull(transition.dispatchCancelDownTime)
        assertNull(transition.state.observedTouchDownTime)
    }

}
