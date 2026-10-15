package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.geckoview.PanZoomController

class GeckoPullToRefreshRulesTest {
    @Test
    fun `content reported down needs top overscroll before a move can be checked`() {
        assertTrue(GeckoPullToRefreshRules.canCheckPullOnMove(2, 4, 3))
        assertFalse(GeckoPullToRefreshRules.canCheckPullOnMove(2, 5, 3))
        assertFalse(GeckoPullToRefreshRules.canCheckPullOnMove(2, 4, 0))
    }

    @Test
    fun `ignored or unknown down cannot check a parent pull`() {
        assertFalse(GeckoPullToRefreshRules.canCheckPullOnMove(3, 4, 3))
        assertFalse(GeckoPullToRefreshRules.canCheckPullOnMove(-1, 4, 3))
    }

    @Test
    fun `aborted stream reply cannot release or corrupt replacement stream`() {
        val state = GeckoPullToRefreshTouchState()
        val first = state.start()
        state.recordDown(first, true)
        state.finish()
        val second = state.start()
        assertFalse(state.recordDown(second, true))
        assertFalse(state.recordMove(first, true))
        assertTrue(state.recordMove(second, true))
    }
    @Test
    fun `down alone cannot surrender a stream before first move`() {
        val state = GeckoPullToRefreshTouchState()
        val request = state.start()
        assertFalse(state.recordDown(request, true))
        assertTrue(state.recordMove(request, true))
    }

    @Test
    fun `move before down answer still needs both permissions`() {
        val state = GeckoPullToRefreshTouchState()
        val request = state.start()
        assertFalse(state.recordMove(request, true))
        assertTrue(state.recordDown(request, true))
    }

    @Test
    fun `scrolled down target cannot be reclaimed after reaching top`() {
        val state = GeckoPullToRefreshTouchState()
        val request = state.start()
        assertFalse(state.recordDown(request, false))
        assertFalse(state.recordMove(request, true))
        assertNull(state.requestMove())
    }

    @Test
    fun `move consumed by page keeps parent interception blocked`() {
        val state = GeckoPullToRefreshTouchState()
        val request = state.start()
        assertFalse(state.recordDown(request, true))
        assertFalse(state.recordMove(request, false))
    }

    @Test
    fun `only one move detail is requested per active stream`() {
        val state = GeckoPullToRefreshTouchState()
        val request = state.start()
        assertEquals(request, state.requestMove())
        assertNull(state.requestMove())
        state.finish()
        assertFalse(state.recordMove(request, true))
    }

    @Test
    fun `touched container with room above keeps the gesture`() {
        assertFalse(allow(scroll = PanZoomController.SCROLLABLE_FLAG_TOP or PanZoomController.SCROLLABLE_FLAG_BOTTOM))
    }

    @Test
    fun `container at top permits vertical overscroll`() {
        assertTrue(allow(scroll = PanZoomController.SCROLLABLE_FLAG_BOTTOM))
        assertTrue(allow(scroll = PanZoomController.SCROLLABLE_FLAG_RIGHT))
    }

    @Test
    fun `non scrolling content permits allowed vertical overscroll`() {
        assertTrue(allow(handled = PanZoomController.INPUT_RESULT_UNHANDLED))
    }

    @Test
    fun `page owned ignored or unknown input cannot refresh`() {
        assertFalse(allow(handled = PanZoomController.INPUT_RESULT_HANDLED_CONTENT))
        assertFalse(allow(handled = PanZoomController.INPUT_RESULT_IGNORED))
        assertFalse(allow(handled = -1))
    }

    @Test
    fun `contained or horizontal only overscroll cannot refresh`() {
        assertFalse(allow(overscroll = PanZoomController.OVERSCROLL_FLAG_NONE))
        assertFalse(allow(overscroll = PanZoomController.OVERSCROLL_FLAG_HORIZONTAL))
    }

    @Test
    fun `new down rejects the preceding asynchronous answer`() {
        val state = GeckoPullToRefreshTouchState()
        val first = state.start()
        assertTrue(state.canApply(first))
        val second = state.start()
        assertFalse(state.canApply(first))
        assertTrue(state.canApply(second))
    }

    @Test
    fun `terminal or interrupted stream rejects delayed answer`() {
        val state = GeckoPullToRefreshTouchState()
        val request = state.start()
        state.finish()
        assertFalse(state.canApply(request))
        assertTrue(state.canApply(state.start()))
        assertFalse(state.canApply(request))
    }

    private fun allow(
        handled: Int = PanZoomController.INPUT_RESULT_HANDLED,
        scroll: Int = PanZoomController.SCROLLABLE_FLAG_NONE,
        overscroll: Int = PanZoomController.OVERSCROLL_FLAG_VERTICAL,
    ): Boolean = GeckoPullToRefreshRules.canAllowParentIntercept(handled, scroll, overscroll)
}
