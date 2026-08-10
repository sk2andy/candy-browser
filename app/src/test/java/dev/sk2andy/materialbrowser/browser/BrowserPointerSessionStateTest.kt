package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPointerSessionStateTest {
    @Test
    fun `pointer release rejects pending result`() {
        val state = BrowserPointerSessionState()
        state.begin()
        val captured = state.snapshot()

        state.end()

        assertFalse(state.accepts(captured))
    }

    @Test
    fun `new pointer rejects result from previous generation`() {
        val state = BrowserPointerSessionState()
        state.begin()
        val captured = state.snapshot()

        state.end()
        state.begin()

        assertFalse(state.accepts(captured))
    }

    @Test
    fun `accessibility request without pointer remains valid`() {
        val state = BrowserPointerSessionState()
        val captured = state.snapshot()

        assertTrue(state.accepts(captured))
    }

    @Test
    fun `new pointer rejects pending accessibility result`() {
        val state = BrowserPointerSessionState()
        val captured = state.snapshot()

        state.begin()

        assertFalse(state.accepts(captured))
    }

    @Test
    fun `external invalidation rejects pending accessibility result`() {
        val state = BrowserPointerSessionState()
        val captured = state.snapshot()

        state.end()

        assertFalse(state.accepts(captured))
    }
}
