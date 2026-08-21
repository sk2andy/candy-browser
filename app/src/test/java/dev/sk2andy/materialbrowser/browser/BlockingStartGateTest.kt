package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingStartGateTest {
    @Test
    fun `starts remain pending until ready and drain once`() {
        val gate = BlockingStartGate<String>()
        gate.enqueue("tab", "restore")

        assertTrue(!gate.isReady)
        assertEquals(mapOf("tab" to "restore"), gate.markReady())
        assertTrue(gate.isReady)
        assertTrue(gate.markReady().isEmpty())
    }

    @Test
    fun `latest start replaces older restore for same tab`() {
        val gate = BlockingStartGate<String>()
        gate.enqueue("tab", "restore")
        gate.enqueue("tab", "typed-url")

        assertEquals(mapOf("tab" to "typed-url"), gate.markReady())
    }

    @Test
    fun `cancel and cancel all discard stale starts`() {
        val gate = BlockingStartGate<String>()
        gate.enqueue("closed", "url-a")
        gate.enqueue("stopped", "url-b")
        gate.cancel("closed")
        gate.cancelAll()

        assertTrue(gate.markReady().isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun `ready gate rejects accidental requeue`() {
        val gate = BlockingStartGate<String>()
        gate.markReady()

        gate.enqueue("tab", "late")
    }
}
