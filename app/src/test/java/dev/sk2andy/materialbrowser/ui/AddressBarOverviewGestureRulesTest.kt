package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarOverviewGestureRulesTest {
    @Test
    fun `direction waits for slop then locks only upward vertical motion`() {
        assertEquals(
            AddressBarOverviewGestureDirection.Pending,
            AddressBarOverviewGestureRules.direction(
                dragX = 3f,
                dragY = -7f,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            AddressBarOverviewGestureDirection.Upward,
            AddressBarOverviewGestureRules.direction(
                dragX = 4f,
                dragY = -12f,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            AddressBarOverviewGestureDirection.Rejected,
            AddressBarOverviewGestureRules.direction(
                dragX = 12f,
                dragY = -9f,
                touchSlop = 8f,
            ),
        )
        assertEquals(
            AddressBarOverviewGestureDirection.Rejected,
            AddressBarOverviewGestureRules.direction(
                dragX = 1f,
                dragY = 9f,
                touchSlop = 8f,
            ),
        )
    }

    @Test
    fun `existing threshold commits overview`() {
        val belowThreshold = AddressBarOverviewGestureRules.update(
            state = AddressBarOverviewGestureRules.Idle,
            deltaY = -55f,
            threshold = 56f,
        )
        val atThreshold = AddressBarOverviewGestureRules.update(
            state = AddressBarOverviewGestureRules.Idle,
            deltaY = -56f,
            threshold = 56f,
        )

        assertFalse(belowThreshold.shouldCommit)
        assertTrue(atThreshold.shouldCommit)
        assertEquals(1f, atThreshold.progress, 0.001f)
    }

    @Test
    fun `progress clamps downward and excess upward motion`() {
        assertEquals(0f, AddressBarOverviewGestureRules.progress(20f, 56f), 0.001f)
        assertEquals(0.5f, AddressBarOverviewGestureRules.progress(-28f, 56f), 0.001f)
        assertEquals(1f, AddressBarOverviewGestureRules.progress(-112f, 56f), 0.001f)
        assertEquals(0f, AddressBarOverviewGestureRules.progress(-28f, 0f), 0.001f)
    }

    @Test
    fun `cancellation restores exact idle state`() {
        val cancelled = AddressBarOverviewGestureRules.cancel()

        assertEquals(AddressBarOverviewGestureRules.Idle, cancelled.state)
        assertEquals(0f, cancelled.progress, 0.001f)
        assertFalse(cancelled.shouldCommit)
    }

    @Test
    fun `regrab continues from current progress`() {
        val regrabbed = AddressBarOverviewGestureRules.update(
            state = AddressBarOverviewGestureRules.stateForProgress(
                progress = 0.5f,
                threshold = 56f,
            ),
            deltaY = -14f,
            threshold = 56f,
        )

        assertEquals(0.75f, regrabbed.progress, 0.001f)
        assertFalse(regrabbed.shouldCommit)
    }

    @Test
    fun `threshold commit is one shot`() {
        val committed = AddressBarOverviewGestureRules.update(
            state = AddressBarOverviewGestureRules.Idle,
            deltaY = -56f,
            threshold = 56f,
        )
        val continued = AddressBarOverviewGestureRules.update(
            state = committed.state,
            deltaY = -24f,
            threshold = 56f,
        )

        assertTrue(committed.shouldCommit)
        assertFalse(continued.shouldCommit)
        assertEquals(1f, continued.progress, 0.001f)
    }

    @Test
    fun `elastic resistance stays bounded and reaches threshold`() {
        assertEquals(0f, AddressBarOverviewGestureRules.resistedProgress(-1f), 0.001f)
        assertEquals(0.425f, AddressBarOverviewGestureRules.resistedProgress(0.5f), 0.001f)
        assertEquals(1f, AddressBarOverviewGestureRules.resistedProgress(1f), 0.001f)
        assertEquals(1f, AddressBarOverviewGestureRules.resistedProgress(2f), 0.001f)
    }
}
