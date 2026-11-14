package dev.sk2andy.materialbrowser.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationQueueTest {
    @Test
    fun `coalesces progress across transfers and prioritizes state changes`() {
        val clock = TestClock()
        val sent = mutableListOf<String>()
        val queue = DownloadNotificationQueue<String>(Any(), { clock.now }, clock::schedule) { sent += it }

        queue.enqueue(1, "started one", priority = true)
        queue.enqueue(1, "ten percent", priority = false)
        queue.enqueue(1, "thirty percent", priority = false)
        queue.enqueue(2, "started two", priority = true)
        assertEquals(listOf("started one"), sent)

        clock.advance(500)
        assertEquals(listOf("started one", "started two"), sent)
        clock.advance(500)
        assertEquals(listOf("started one", "started two", "thirty percent"), sent)
    }

    @Test
    fun `terminal state replaces pending progress and cancellation removes it`() {
        val clock = TestClock()
        val sent = mutableListOf<String>()
        val queue = DownloadNotificationQueue<String>(Any(), { clock.now }, clock::schedule) { sent += it }

        queue.enqueue(1, "started", priority = true)
        queue.enqueue(1, "progress", priority = false)
        queue.enqueue(1, "paused", priority = true)
        queue.enqueue(1, "resumed", priority = true)
        queue.enqueue(1, "complete", priority = true)
        queue.enqueue(2, "cancelled progress", priority = false)
        queue.remove(2)
        clock.advance(500)
        clock.advance(500)

        assertEquals(listOf("started", "complete"), sent)
    }

    private class TestClock {
        var now = 0L
        private var pending: Pair<Long, () -> Unit>? = null

        fun schedule(delay: Long, action: () -> Unit) {
            check(pending == null)
            pending = (now + delay) to action
        }

        fun advance(millis: Long) {
            now += millis
            while (pending?.first?.let { it <= now } == true) {
                val action = checkNotNull(pending).second
                pending = null
                action()
            }
        }
    }
}
