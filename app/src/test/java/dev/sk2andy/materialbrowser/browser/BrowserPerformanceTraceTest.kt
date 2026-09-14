package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPerformanceTraceTest {
    @Test
    fun `trace labels are fixed short Candy labels without page data`() {
        val labels = BrowserPerformanceTrace.Phase.entries.map { it.label } +
            BrowserPerformanceTrace.Counter.entries.map { it.label }

        assertEquals(labels.size, labels.toSet().size)
        assertTrue(labels.all { it.length < 127 && it.matches(Regex("Candy\\.[A-Za-z.]+")) })
    }

    @Test
    fun `clear chrome has explicit zero participant counts`() {
        assertEquals(BrowserBlurTraceState.Counts(0, 0, 0, 0), BrowserBlurTraceState().counts())
    }

    @Test
    fun `disabled participant cannot hide another enabled surface`() {
        val state = BrowserBlurTraceState()
        val addressBar = Any()
        val menu = Any()
        val source = Any()
        state.update(addressBar, BrowserBlurTraceState.Kind.View, enabled = true)
        state.update(menu, BrowserBlurTraceState.Kind.View, enabled = false)
        state.update(source, BrowserBlurTraceState.Kind.Source, enabled = true)

        assertEquals(BrowserBlurTraceState.Counts(2, 1, 1, 1), state.counts())
        state.update(menu, BrowserBlurTraceState.Kind.View, enabled = true)
        state.update(source, BrowserBlurTraceState.Kind.Source, enabled = false)
        assertEquals(BrowserBlurTraceState.Counts(2, 2, 1, 0), state.counts())
        state.remove(addressBar)
        state.remove(addressBar)
        state.remove(menu)
        state.remove(source)
        assertEquals(BrowserBlurTraceState.Counts(0, 0, 0, 0), state.counts())
    }

    @Test
    fun `participant accounting uses identity rather than value equality`() {
        val state = BrowserBlurTraceState()
        val first = listOf("surface")
        val second = listOf("surface")
        state.update(first, BrowserBlurTraceState.Kind.View, enabled = true)
        state.update(second, BrowserBlurTraceState.Kind.View, enabled = false)

        assertEquals(BrowserBlurTraceState.Counts(2, 1, 0, 0), state.counts())
        state.remove(second)
        assertEquals(BrowserBlurTraceState.Counts(1, 1, 0, 0), state.counts())
    }

    @Test
    fun `software source child drawing is not classified as snapshot capture`() {
        assertEquals(
            BrowserPerformanceTrace.Phase.BlurSourceChildDraw,
            BrowserPerformanceTrace.sourceDrawPhase(hardwareAccelerated = false),
        )
        assertEquals(
            BrowserPerformanceTrace.Phase.BlurCapture,
            BrowserPerformanceTrace.sourceDrawPhase(hardwareAccelerated = true),
        )
    }

    @Test
    fun `clear frosted clear intent remains covered after participants are detached`() {
        val state = BrowserBlurTraceState()
        val configuration = Any()
        val source = Any()
        val view = Any()
        state.update(configuration, BrowserBlurTraceState.Kind.Configuration, enabled = false)
        assertEquals(BrowserBlurTraceState.Counts(0, 0, 0, 0, 1, 0), state.counts())

        state.update(configuration, BrowserBlurTraceState.Kind.Configuration, enabled = true)
        state.update(source, BrowserBlurTraceState.Kind.Source, enabled = true)
        state.update(view, BrowserBlurTraceState.Kind.View, enabled = true)
        assertEquals(BrowserBlurTraceState.Counts(1, 1, 1, 1, 1, 1), state.counts())

        state.update(configuration, BrowserBlurTraceState.Kind.Configuration, enabled = false)
        state.remove(source)
        state.remove(view)
        assertEquals(BrowserBlurTraceState.Counts(0, 0, 0, 0, 1, 0), state.counts())
        state.remove(configuration)
        assertEquals(BrowserBlurTraceState.Counts(0, 0, 0, 0, 0, 0), state.counts())
    }

    @Test
    fun `unpublished source does not overwrite another chrome request`() {
        val state = BrowserBlurTraceState()
        val addressBar = Any()
        val menu = Any()
        state.update(addressBar, BrowserBlurTraceState.Kind.Configuration, enabled = true)
        state.update(menu, BrowserBlurTraceState.Kind.Configuration, enabled = false)
        state.update(Any(), BrowserBlurTraceState.Kind.Source, enabled = false)

        assertEquals(BrowserBlurTraceState.Counts(0, 0, 1, 0, 2, 1), state.counts())
    }
}
