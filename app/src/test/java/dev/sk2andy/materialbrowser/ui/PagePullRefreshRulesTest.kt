package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePullRefreshRulesTest {
    @Test
    fun `gesture can start near page top`() {
        assertTrue(PagePullRefreshRules.isEligible(startScrollY = 96f, maxStartScroll = 96f))
        assertFalse(PagePullRefreshRules.isEligible(startScrollY = 97f, maxStartScroll = 96f))
    }

    @Test
    fun `direction waits for touch slop then locks only downward drag`() {
        assertEquals(
            PullGestureDirection.Undecided,
            PagePullRefreshRules.direction(dragX = 2f, dragY = 7f, touchSlop = 8f),
        )
        assertEquals(
            PullGestureDirection.Down,
            PagePullRefreshRules.direction(dragX = 6f, dragY = 9f, touchSlop = 8f),
        )
        assertEquals(
            PullGestureDirection.Rejected,
            PagePullRefreshRules.direction(dragX = 9f, dragY = 8f, touchSlop = 8f),
        )
        assertEquals(
            PullGestureDirection.Rejected,
            PagePullRefreshRules.direction(dragX = 0f, dragY = -9f, touchSlop = 8f),
        )
    }

    @Test
    fun `scroll distance to top is removed from pull distance`() {
        assertEquals(
            0.5f,
            PagePullRefreshRules.progress(
                startScrollY = 36f,
                dragX = 0f,
                dragY = 72f,
                triggerDistance = 72f,
            ),
            0f,
        )
    }

    @Test
    fun `full downward pull reaches refresh threshold`() {
        assertEquals(
            1f,
            PagePullRefreshRules.progress(
                startScrollY = 24f,
                dragX = 8f,
                dragY = 96f,
                triggerDistance = 72f,
            ),
            0f,
        )
    }

    @Test
    fun `upward or horizontal drag never progresses`() {
        assertEquals(
            0f,
            PagePullRefreshRules.progress(0f, dragX = 0f, dragY = -80f, triggerDistance = 72f),
            0f,
        )
        assertEquals(
            0f,
            PagePullRefreshRules.progress(0f, dragX = 80f, dragY = 60f, triggerDistance = 72f),
            0f,
        )
    }
}
