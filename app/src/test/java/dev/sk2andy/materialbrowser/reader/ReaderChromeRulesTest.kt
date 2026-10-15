package dev.sk2andy.materialbrowser.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChromeRulesTest {
    @Test
    fun `downward scroll accumulates until browser collapse threshold`() {
        val first = ReaderChromeRules.onScroll(ReaderChromeMode.Expanded, 0f, 10f, atTop = false)
        assertEquals(ReaderChromeMode.Expanded, first.mode)
        assertEquals(10f, first.accumulatedDistanceDp)
        val second = ReaderChromeRules.onScroll(first.mode, first.accumulatedDistanceDp, 14f, atTop = false)
        assertEquals(ReaderChromeMode.Compact, second.mode)
        assertEquals(0f, second.accumulatedDistanceDp)
    }

    @Test
    fun `upward scroll expands at browser threshold and resets on reversal`() {
        val reversed = ReaderChromeRules.onScroll(ReaderChromeMode.Compact, 20f, -8f, atTop = false)
        assertEquals(ReaderChromeMode.Compact, reversed.mode)
        assertEquals(-8f, reversed.accumulatedDistanceDp)
        val expanded = ReaderChromeRules.onScroll(reversed.mode, reversed.accumulatedDistanceDp, -8f, atTop = false)
        assertEquals(ReaderChromeMode.Expanded, expanded.mode)
        assertEquals(0f, expanded.accumulatedDistanceDp)
        val downward = ReaderChromeRules.onScroll(ReaderChromeMode.Expanded, -15f, 23f, atTop = false)
        assertEquals(ReaderChromeMode.Expanded, downward.mode)
        assertEquals(23f, downward.accumulatedDistanceDp)
    }

    @Test
    fun `article top expands while hidden controls ignore all scrolling`() {
        assertEquals(
            ReaderChromeScrollUpdate(ReaderChromeMode.Expanded),
            ReaderChromeRules.onScroll(ReaderChromeMode.Compact, 10f, -1f, atTop = true),
        )
        for (atTop in listOf(false, true)) {
            for (delta in listOf(-100f, 0f, 100f)) {
                assertEquals(
                    ReaderChromeScrollUpdate(ReaderChromeMode.Hidden),
                    ReaderChromeRules.onScroll(ReaderChromeMode.Hidden, 10f, delta, atTop),
                )
            }
        }
    }

    @Test
    fun `stationary and invalid scroll do not switch controls`() {
        assertEquals(
            ReaderChromeScrollUpdate(ReaderChromeMode.Compact, -8f),
            ReaderChromeRules.onScroll(ReaderChromeMode.Compact, -8f, 0f, atTop = false),
        )
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(
                ReaderChromeScrollUpdate(ReaderChromeMode.Expanded),
                ReaderChromeRules.onScroll(ReaderChromeMode.Expanded, 0f, invalid, atTop = false),
            )
        }
    }

    @Test
    fun `compact address matches browser domain without path port or www`() {
        assertEquals("example.com", ReaderChromeRules.compactAddress("https://www.example.com:8443/article?q=1#part"))
        assertEquals("news.example.com", ReaderChromeRules.compactAddress("https://news.example.com?q=1"))
        assertEquals("example.com", ReaderChromeRules.compactAddress("HTTPS://WWW.EXAMPLE.COM#part"))
        assertEquals("localhost", ReaderChromeRules.compactAddress("http://localhost:8080/article"))
        assertEquals("[::1]", ReaderChromeRules.compactAddress("http://[::1]:8080/article"))
        assertEquals("invalid", ReaderChromeRules.compactAddress("invalid"))
    }

    @Test
    fun `back restores hidden controls to compact without exiting`() {
        assertEquals(ReaderChromeMode.Compact, ReaderChromeRules.restore(ReaderChromeMode.Hidden))
        assertEquals(ReaderChromeMode.Compact, ReaderChromeRules.restore(ReaderChromeMode.Compact))
        assertEquals(ReaderChromeMode.Expanded, ReaderChromeRules.restore(ReaderChromeMode.Expanded))
    }
}
