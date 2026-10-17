package dev.sk2andy.materialbrowser.browser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserChromeScrollControllerTest {
    @Test
    fun `downward travel compacts at twenty four points and upward travel expands at sixteen`() {
        val controller = BrowserChromeScrollController()

        assertFalse(controller.update(23.0))
        assertTrue(controller.update(24.0))
        assertTrue(controller.update(9.0))
        assertFalse(controller.update(8.0))
    }

    @Test
    fun `direction change restarts travel instead of adding opposite movement`() {
        val controller = BrowserChromeScrollController()
        controller.update(20.0)
        controller.update(16.0)

        assertFalse(controller.update(39.0))
        assertTrue(controller.update(40.0))
    }

    @Test
    fun `coalesced scroll retains complete travel and document top immediately expands`() {
        val controller = BrowserChromeScrollController()

        assertTrue(controller.update(200.0))
        assertFalse(controller.update(0.0))
        assertFalse(controller.update(-12.0))
    }

    @Test
    fun `reset seeds selected renderer offset without reusing old document travel`() {
        val controller = BrowserChromeScrollController()
        controller.update(200.0)

        controller.reset(scrollY = 400.0)

        assertFalse(controller.compact)
        assertFalse(controller.update(423.0))
        assertTrue(controller.update(424.0))
        controller.reset()
        assertFalse(controller.compact)
        assertTrue(controller.update(24.0))
    }

    @Test
    fun `invalid native offsets cannot change compact state or poison next movement`() {
        val controller = BrowserChromeScrollController()
        controller.update(23.0)

        assertFalse(controller.update(Double.NaN))
        assertFalse(controller.update(Double.POSITIVE_INFINITY))
        assertTrue(controller.update(24.0))
        assertTrue(controller.update(Double.NEGATIVE_INFINITY))
        controller.reset(Double.NaN)
        assertFalse(controller.compact)
        assertTrue(controller.update(24.0))
    }
}
