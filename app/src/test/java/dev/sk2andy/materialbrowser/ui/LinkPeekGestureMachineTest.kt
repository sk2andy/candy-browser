package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPeekGestureMachineTest {
    private fun machine() = LinkPeekGestureMachine(threshold = 100f, touchSlop = 8f)

    @Test
    fun `downward threshold opens exactly once on release`() {
        val machine = machine()

        assertEquals(LinkPeekGesturePhase.Tracking, machine.begin().phase)
        assertEquals(0.6f, machine.move(dragX = 2f, dragY = 60f).progress, 0.001f)
        val armed = machine.move(dragX = 3f, dragY = 100f)
        assertEquals(LinkPeekGesturePhase.Armed, armed.phase)
        assertTrue(armed.emitThresholdHaptic)

        assertTrue(machine.release().shouldOpen)
        assertFalse(machine.release().shouldOpen)
        assertEquals(LinkPeekGesturePhase.Completed, machine.phase)
    }

    @Test
    fun `release below threshold cancels without opening`() {
        val machine = machine()

        machine.begin()
        machine.move(dragX = 0f, dragY = 70f)
        val release = machine.release()

        assertFalse(release.shouldOpen)
        assertTrue(release.shouldDismiss)
        assertEquals(LinkPeekGesturePhase.Cancelled, release.phase)
    }

    @Test
    fun `upward and horizontal motion keep peek until release`() {
        val upward = machine().move(dragX = 1f, dragY = -8f)
        val horizontal = machine().move(dragX = 9f, dragY = 8f)

        assertEquals(LinkPeekGesturePhase.Tracking, upward.phase)
        assertFalse(upward.shouldDismiss)
        assertEquals(LinkPeekGesturePhase.Tracking, horizontal.phase)
        assertFalse(horizontal.shouldDismiss)
    }

    @Test
    fun `upward movement can recover into downward open before release`() {
        val machine = machine()

        val upward = machine.move(dragX = 0f, dragY = -20f)
        val armed = machine.move(dragX = 0f, dragY = 110f)
        val release = machine.release()

        assertEquals(LinkPeekGesturePhase.Tracking, upward.phase)
        assertFalse(upward.shouldDismiss)
        assertEquals(LinkPeekGesturePhase.Armed, armed.phase)
        assertTrue(release.shouldOpen)
    }

    @Test
    fun `returning below target disarms before release`() {
        val machine = machine()
        machine.move(dragX = 0f, dragY = 120f)

        val returned = machine.move(dragX = 0f, dragY = 40f)
        val release = machine.release()

        assertEquals(LinkPeekGesturePhase.Tracking, returned.phase)
        assertEquals(0.4f, returned.progress, 0.001f)
        assertFalse(release.shouldOpen)
        assertTrue(release.shouldDismiss)
    }

    @Test
    fun `moving upward after arming disarms but waits for release`() {
        val machine = machine()
        machine.move(dragX = 0f, dragY = 120f)

        val upward = machine.move(dragX = 0f, dragY = -9f)

        assertEquals(LinkPeekGesturePhase.Tracking, upward.phase)
        assertEquals(0f, upward.progress, 0.001f)
        assertFalse(upward.shouldDismiss)
        assertTrue(machine.release().shouldDismiss)
    }

    @Test
    fun `system cancel never opens`() {
        val machine = machine()
        machine.move(dragX = 0f, dragY = 120f)

        val cancelled = machine.cancel()

        assertEquals(LinkPeekGesturePhase.Cancelled, cancelled.phase)
        assertFalse(cancelled.shouldOpen)
        assertTrue(cancelled.shouldDismiss)
    }
}
