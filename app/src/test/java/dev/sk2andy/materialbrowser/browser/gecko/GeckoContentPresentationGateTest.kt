package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertEquals
import org.junit.Test

class GeckoContentPresentationGateTest {
    @Test
    fun `compositor alone cannot release preview handoff`() {
        val gate = GeckoContentPresentationGate()
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
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }

        assertEquals(1, presentations)
    }

    @Test
    fun `paint reset blocks reuse until Gecko paints valid content again`() {
        val gate = GeckoContentPresentationGate()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onPaintStatusReset()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }

        assertEquals(0, presentations)
        gate.onFirstContentfulPaint()
        assertEquals(1, presentations)
    }

    @Test
    fun `detached surface keeps page paint but needs a new composite`() {
        val gate = GeckoContentPresentationGate()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.onSurfaceDetached()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onFirstComposite()

        assertEquals(1, presentations)
    }

    @Test
    fun `closed session needs a new composite and paint`() {
        val gate = GeckoContentPresentationGate()
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()
        gate.close()
        var presentations = 0

        gate.awaitContentPresented { presentations++ }
        gate.onFirstComposite()
        assertEquals(0, presentations)
        gate.onFirstContentfulPaint()

        assertEquals(1, presentations)
    }

    @Test
    fun `new pending request replaces stale host callback`() {
        val gate = GeckoContentPresentationGate()
        var stalePresentations = 0
        var currentPresentations = 0

        gate.awaitContentPresented { stalePresentations++ }
        gate.awaitContentPresented { currentPresentations++ }
        gate.onFirstComposite()
        gate.onFirstContentfulPaint()

        assertEquals(0, stalePresentations)
        assertEquals(1, currentPresentations)
    }
}
