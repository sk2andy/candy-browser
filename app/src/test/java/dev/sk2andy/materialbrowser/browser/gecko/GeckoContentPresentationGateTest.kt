package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoContentPresentationGateTest {
    @Test
    fun `hidden document paint survives delayed paint reset until surface composites again`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onSurfaceDestroyed()
        gate.onPaintStatusReset()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        assertFalse(gate.isContentPresented)
        gate.onSurfaceCreated()
        assertFalse(gate.isContentPresented)
        gate.onFirstComposite()

        assertTrue(gate.isContentPresented)
        assertEquals(1, presentations)
    }

    @Test
    fun `compositor alone cannot release preview handoff`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onFirstComposite()

        assertEquals(0, presentations)
        gate.onFirstContentfulPaint()
        assertEquals(1, presentations)
    }

    @Test
    fun `content paint before compositor waits for first composite`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onFirstContentfulPaint()

        assertEquals(0, presentations)
        gate.onFirstComposite()
        assertEquals(1, presentations)
    }

    @Test
    fun `valid reused surface can report immediately`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }

        assertEquals(1, presentations)
        assertTrue(gate.isContentPresented)
        assertEquals(1L, gate.presentationGeneration)
    }

    @Test
    fun `paint reset blocks reuse until Gecko paints valid content again`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onPaintStatusReset()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }

        assertEquals(0, presentations)
        assertFalse(gate.isContentPresented)
        gate.onFirstContentfulPaint()
        assertEquals(1, presentations)
    }

    @Test
    fun `detached surface keeps page paint but needs a new composite`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onSurfaceDetached()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onSurfaceCreated()
        gate.onFirstComposite()

        assertEquals(1, presentations)
    }

    @Test
    fun `closed session needs a new composite and paint`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.close()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        assertEquals(0, presentations)
        gate.onFirstContentfulPaint()

        assertEquals(1, presentations)
    }

    @Test
    fun `new pending request replaces stale host callback`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        var stalePresentations = 0
        var currentPresentations = 0

        gate.awaitContentPresented { stalePresentations++ }
        gate.awaitContentPresented { currentPresentations++ }
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()

        assertEquals(0, stalePresentations)
        assertEquals(1, currentPresentations)
    }

    @Test
    fun `unavailable surface ignores composite and waits for its own composite`() {
        val gate = GeckoContentPresentationGate()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onFirstContentfulPaint()
        gate.onFirstComposite()

        assertFalse(gate.isContentPresented)
        assertEquals(0, presentations)
        assertEquals(0L, gate.presentationGeneration)

        gate.onSurfaceCreated()

        assertFalse(gate.isContentPresented)
        assertEquals(0, presentations)

        gate.onFirstComposite()

        assertTrue(gate.isContentPresented)
        assertEquals(1, presentations)
    }

    @Test
    fun `transient surface loss keeps paint and pending waiter until new composite`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onSurfaceDestroyed()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onFirstComposite()

        assertFalse(gate.isContentPresented)
        assertEquals(0, presentations)
        assertEquals(2L, gate.presentationGeneration)

        gate.onSurfaceCreated()

        assertFalse(gate.isContentPresented)
        assertEquals(0, presentations)
        assertEquals(3L, gate.presentationGeneration)

        gate.onFirstComposite()
        gate.onFirstComposite()

        assertTrue(gate.isContentPresented)
        assertEquals(1, presentations)
    }

    @Test
    fun `waiter registered before temporary surface loss survives recreation`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstContentfulPaint()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onSurfaceDestroyed()
        gate.onSurfaceCreated()
        gate.onFirstComposite()

        assertEquals(1, presentations)
    }

    @Test
    fun `new navigation after surface loss cannot reuse previous document paint`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onSurfaceDestroyed()
        gate.onNavigationStarted()
        gate.onPaintStatusReset()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onSurfaceCreated()
        gate.onFirstComposite()

        assertFalse(gate.isContentPresented)
        assertEquals(0, presentations)

        gate.onFirstContentfulPaint()

        assertTrue(gate.isContentPresented)
        assertEquals(1, presentations)
    }

    @Test
    fun `visible paint reset after recovery still waits for content paint`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onSurfaceDestroyed()
        gate.onPaintStatusReset()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onPaintStatusReset()

        gate.onFirstComposite()

        assertFalse(gate.isContentPresented)
        gate.onFirstContentfulPaint()
        assertTrue(gate.isContentPresented)
    }

    @Test
    fun `detached document survives delayed paint reset without another content paint`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onSurfaceDetached()
        gate.onPaintStatusReset()

        gate.onFirstComposite()
        assertFalse(gate.isContentPresented)
        gate.onSurfaceCreated()
        gate.onFirstComposite()

        assertTrue(gate.isContentPresented)
    }

    @Test
    fun `navigation on a live surface needs its own content paint`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onNavigationStarted()

        gate.onFirstComposite()
        assertFalse(gate.isContentPresented)
        gate.onFirstContentfulPaint()

        assertTrue(gate.isContentPresented)
    }

    @Test
    fun `duplicate surface callbacks preserve retained presentation and generation`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()

        gate.onSurfaceCreated()

        assertTrue(gate.isContentPresented)
        assertEquals(1L, gate.presentationGeneration)

        gate.onSurfaceDestroyed()
        gate.onSurfaceDestroyed()

        assertFalse(gate.isContentPresented)
        assertEquals(2L, gate.presentationGeneration)
    }

    @Test
    fun `ownership detach clears pending waiter and invalidates its generation`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstContentfulPaint()
        var stalePresentations = 0

        gate.awaitContentPresented { stalePresentations++ }
        gate.onSurfaceDetached()

        assertFalse(gate.isContentPresented)
        assertEquals(2L, gate.presentationGeneration)

        gate.onSurfaceCreated()
        gate.onFirstComposite()

        assertTrue(gate.isContentPresented)
        assertEquals(0, stalePresentations)
    }

    @Test
    fun `close clears pending waiter and invalidates its generation`() {
        val gate = GeckoContentPresentationGate()
        gate.onSurfaceCreated()
        gate.onFirstContentfulPaint()
        var stalePresentations = 0

        gate.awaitContentPresented { stalePresentations++ }
        gate.close()

        assertFalse(gate.isContentPresented)
        assertEquals(2L, gate.presentationGeneration)

        gate.onSurfaceCreated()
        gate.onFirstComposite()

        assertFalse(gate.isContentPresented)

        gate.onFirstContentfulPaint()

        assertTrue(gate.isContentPresented)
        assertEquals(0, stalePresentations)
    }
}
