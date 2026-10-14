package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkPeekSwipeRulesTest {
    @Test
    fun `direction waits for slop and rejects vertical preview scrolling`() {
        assertEquals(LinkPeekSwipeDirection.Pending, LinkPeekSwipeRules.direction(7f, 3f, 8f))
        assertEquals(LinkPeekSwipeDirection.Horizontal, LinkPeekSwipeRules.direction(-20f, 6f, 8f))
        assertEquals(LinkPeekSwipeDirection.Horizontal, LinkPeekSwipeRules.direction(20f, -6f, 8f))
        assertEquals(LinkPeekSwipeDirection.Rejected, LinkPeekSwipeRules.direction(6f, 20f, 8f))
        assertEquals(LinkPeekSwipeDirection.Rejected, LinkPeekSwipeRules.direction(8f, 8f, 8f))
    }

    @Test
    fun `release maps physical left to close and right to chosen action at threshold`() {
        assertEquals(LinkPeekSwipeOutcome.Cancel, LinkPeekSwipeRules.release(95f, 96f))
        assertEquals(LinkPeekSwipeOutcome.Cancel, LinkPeekSwipeRules.release(-95f, 96f))
        assertEquals(LinkPeekSwipeOutcome.Close, LinkPeekSwipeRules.release(-96f, 96f))
        assertEquals(LinkPeekSwipeOutcome.RightAction, LinkPeekSwipeRules.release(96f, 96f))
        assertEquals(LinkPeekSwipeOutcome.Cancel, LinkPeekSwipeRules.release(0f, 96f))
    }

    @Test
    fun `invalid motion never commits and progress remains bounded`() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { threshold ->
            assertEquals(LinkPeekSwipeOutcome.Cancel, LinkPeekSwipeRules.release(100f, threshold))
            assertEquals(0f, LinkPeekSwipeRules.progress(100f, threshold), 0f)
        }
        assertEquals(LinkPeekSwipeOutcome.Cancel, LinkPeekSwipeRules.release(Float.NaN, 96f))
        assertEquals(LinkPeekSwipeDirection.Rejected, LinkPeekSwipeRules.direction(Float.NaN, 0f, 8f))
        assertEquals(0.5f, LinkPeekSwipeRules.progress(-48f, 96f), 0f)
        assertEquals(1f, LinkPeekSwipeRules.progress(500f, 96f), 0f)
    }
}
