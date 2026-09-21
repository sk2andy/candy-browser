package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPullGestureRulesTest {
    @Test
    fun `pull starts throughout expanded top zone`() {
        assertTrue(
            BrowserPullGestureRules.canStartInTopZone(
                touchY = 191f,
                topInsetPx = 32,
                zoneHeightPx = 160,
            ),
        )
        assertFalse(
            BrowserPullGestureRules.canStartInTopZone(
                touchY = 193f,
                topInsetPx = 32,
                zoneHeightPx = 160,
            ),
        )
    }

    @Test
    fun `invalid touch geometry cannot start a pull`() {
        assertFalse(BrowserPullGestureRules.canStartInTopZone(Float.NaN, 0, 160))
        assertFalse(BrowserPullGestureRules.canStartInTopZone(-1f, 0, 160))
        assertFalse(BrowserPullGestureRules.canStartInTopZone(1f, -1, 160))
        assertFalse(BrowserPullGestureRules.canStartInTopZone(1f, 0, 0))
    }

    @Test
    fun `movement inside touch slop stays undecided`() {
        assertEquals(
            BrowserPullGestureDirection.Undecided,
            BrowserPullGestureRules.resolve(
                current = BrowserPullGestureDirection.Undecided,
                deltaX = 8f,
                deltaY = 8f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `downward vertical movement owns pull stream`() {
        assertEquals(
            BrowserPullGestureDirection.Pull,
            BrowserPullGestureRules.resolve(
                current = BrowserPullGestureDirection.Undecided,
                deltaX = 8f,
                deltaY = 24f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `horizontal and upward movement stay with web content`() {
        assertEquals(
            BrowserPullGestureDirection.PassThrough,
            BrowserPullGestureRules.resolve(
                current = BrowserPullGestureDirection.Undecided,
                deltaX = 24f,
                deltaY = 12f,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            BrowserPullGestureDirection.PassThrough,
            BrowserPullGestureRules.resolve(
                current = BrowserPullGestureDirection.Undecided,
                deltaX = 0f,
                deltaY = -24f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `resolved stream keeps its owner`() {
        assertEquals(
            BrowserPullGestureDirection.PassThrough,
            BrowserPullGestureRules.resolve(
                current = BrowserPullGestureDirection.PassThrough,
                deltaX = 0f,
                deltaY = 100f,
                touchSlop = 8f,
            ),
        )
    }
}
