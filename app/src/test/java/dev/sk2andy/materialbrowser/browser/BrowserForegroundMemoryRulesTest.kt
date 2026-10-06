package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserForegroundMemoryRulesTest {
    @Test
    fun `idle deadline includes exactly three minutes`() {
        assertEquals(
            emptyList<String>(),
            BrowserForegroundMemoryRules.evictionOrder(
                residentTabIds = setOf("idle"),
                lastAccessElapsedRealtime = mapOf("idle" to 1_000L),
                protectedTabIds = emptySet(),
                nowElapsedRealtime = 180_999L,
                idleTimeoutMillis = 180_000L,
            ),
        )
        assertEquals(
            listOf("idle"),
            BrowserForegroundMemoryRules.evictionOrder(
                residentTabIds = setOf("idle"),
                lastAccessElapsedRealtime = mapOf("idle" to 1_000L),
                protectedTabIds = emptySet(),
                nowElapsedRealtime = 181_000L,
                idleTimeoutMillis = 180_000L,
            ),
        )
    }

    @Test
    fun `selected and protected sessions survive expired idle deadlines`() {
        assertEquals(
            listOf("eligible"),
            BrowserForegroundMemoryRules.evictionOrder(
                residentTabIds = setOf("selected", "audio", "private", "eligible"),
                lastAccessElapsedRealtime = mapOf(
                    "selected" to 0L,
                    "audio" to 0L,
                    "private" to 0L,
                    "eligible" to 0L,
                ),
                protectedTabIds = setOf("selected", "audio", "private"),
                nowElapsedRealtime = 180_000L,
                idleTimeoutMillis = 180_000L,
            ),
        )
    }

    @Test
    fun `missing or future access times retain sessions`() {
        assertEquals(
            emptyList<String>(),
            BrowserForegroundMemoryRules.evictionOrder(
                residentTabIds = setOf("unknown", "future", "recent"),
                lastAccessElapsedRealtime = mapOf("future" to 200_000L, "recent" to 100_000L),
                protectedTabIds = emptySet(),
                nowElapsedRealtime = 180_000L,
                idleTimeoutMillis = 180_000L,
            ),
        )
    }

    @Test
    fun `expired sessions evict by oldest access then stable tab identity`() {
        assertEquals(
            listOf("oldest", "a", "z"),
            BrowserForegroundMemoryRules.evictionOrder(
                residentTabIds = setOf("z", "a", "oldest", "recent"),
                lastAccessElapsedRealtime = mapOf(
                    "oldest" to 1L,
                    "a" to 2L,
                    "z" to 2L,
                    "recent" to 180_000L,
                ),
                protectedTabIds = emptySet(),
                nowElapsedRealtime = 181_000L,
                idleTimeoutMillis = 180_000L,
            ),
        )
    }

    @Test
    fun `configured timeout changes eligible sessions`() {
        assertEquals(
            listOf("one-minute-old"),
            BrowserForegroundMemoryRules.evictionOrder(
                residentTabIds = setOf("one-minute-old", "recent"),
                lastAccessElapsedRealtime = mapOf("one-minute-old" to 0L, "recent" to 59_000L),
                protectedTabIds = emptySet(),
                nowElapsedRealtime = 60_000L,
                idleTimeoutMillis = 60_000L,
            ),
        )
    }
}
