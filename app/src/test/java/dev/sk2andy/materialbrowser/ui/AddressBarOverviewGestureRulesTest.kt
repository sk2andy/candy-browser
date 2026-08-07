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
    fun `crossing threshold waits for pointer release`() {
        val belowThreshold = AddressBarOverviewGestureRules.update(
            state = AddressBarOverviewGestureRules.Idle,
            deltaY = -119f,
            threshold = 120f,
        )
        val atThreshold = AddressBarOverviewGestureRules.update(
            state = AddressBarOverviewGestureRules.Idle,
            deltaY = -120f,
            threshold = 120f,
        )

        assertFalse(belowThreshold.shouldCommit)
        assertFalse(atThreshold.shouldCommit)
        assertTrue(atThreshold.state.thresholdCrossed)
        assertEquals(1f, atThreshold.progress, 0.001f)
        assertTrue(AddressBarOverviewGestureRules.release(atThreshold.state).shouldCommit)
    }

    @Test
    fun `progress clamps downward and excess upward motion`() {
        assertEquals(0f, AddressBarOverviewGestureRules.progress(20f, 120f), 0.001f)
        assertEquals(0.5f, AddressBarOverviewGestureRules.progress(-60f, 120f), 0.001f)
        assertEquals(1f, AddressBarOverviewGestureRules.progress(-240f, 120f), 0.001f)
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
                threshold = 120f,
            ),
            deltaY = -30f,
            threshold = 120f,
        )

        assertEquals(0.75f, regrabbed.progress, 0.001f)
        assertFalse(regrabbed.shouldCommit)
    }

    @Test
    fun `dragging back below threshold cancels release`() {
        val crossed = AddressBarOverviewGestureRules.update(
            state = AddressBarOverviewGestureRules.Idle,
            deltaY = -132f,
            threshold = 120f,
        )
        val returned = AddressBarOverviewGestureRules.update(
            state = crossed.state,
            deltaY = 24f,
            threshold = 120f,
        )

        assertTrue(crossed.state.thresholdCrossed)
        assertFalse(returned.state.thresholdCrossed)
        assertFalse(AddressBarOverviewGestureRules.release(returned.state).shouldCommit)
        assertEquals(0.9f, returned.progress, 0.001f)
    }

    @Test
    fun `elastic resistance stays bounded and reaches threshold`() {
        assertEquals(0f, AddressBarOverviewGestureRules.resistedProgress(-1f), 0.001f)
        assertEquals(0.425f, AddressBarOverviewGestureRules.resistedProgress(0.5f), 0.001f)
        assertEquals(1f, AddressBarOverviewGestureRules.resistedProgress(1f), 0.001f)
        assertEquals(1f, AddressBarOverviewGestureRules.resistedProgress(2f), 0.001f)
    }

    @Test
    fun `morph fades address content into stable plus icon`() {
        assertEquals(1f, AddressBarOverviewGestureRules.contentAlpha(0f), 0.001f)
        assertEquals(0f, AddressBarOverviewGestureRules.contentAlpha(1f), 0.001f)
        assertEquals(0f, AddressBarOverviewGestureRules.targetAlpha(0f), 0.001f)
        assertEquals(1f, AddressBarOverviewGestureRules.targetAlpha(1f), 0.001f)
        assertEquals(0.72f, AddressBarOverviewGestureRules.targetScale(0f), 0.001f)
        assertEquals(1f, AddressBarOverviewGestureRules.targetScale(1f), 0.001f)
        assertEquals(
            0.2f,
            AddressBarOverviewGestureRules.containerScale(1f, 280f, 56f),
            0.001f,
        )
        assertEquals(
            24f,
            AddressBarOverviewGestureRules.landingTranslation(1f, 100f, 124f),
            0.001f,
        )
    }

    @Test
    fun `morph keeps displayed corners circular during non uniform scaling`() {
        val sourceWidth = 280f
        val sourceHeight = 48f
        val targetSize = 56f

        mapOf(
            0f to 24f,
            0.5f to 25.7f,
            1f to 28f,
        ).forEach { (progress, expectedDisplayedRadius) ->
            val radii = AddressBarOverviewGestureRules.morphCornerRadii(
                progress = progress,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                targetSize = targetSize,
            )
            val displayedHorizontal = radii.horizontal *
                AddressBarOverviewGestureRules.containerScale(
                    progress = progress,
                    sourceSize = sourceWidth,
                    targetSize = targetSize,
                )
            val displayedVertical = radii.vertical *
                AddressBarOverviewGestureRules.containerScale(
                    progress = progress,
                    sourceSize = sourceHeight,
                    targetSize = targetSize,
                )

            assertEquals(displayedHorizontal, displayedVertical, 0.001f)
            assertEquals(expectedDisplayedRadius, displayedHorizontal, 0.001f)
        }
    }

    @Test
    fun `morph corner radii reject invalid geometry`() {
        assertEquals(
            AddressBarMorphCornerRadii(horizontal = 0f, vertical = 0f),
            AddressBarOverviewGestureRules.morphCornerRadii(
                progress = 0.5f,
                sourceWidth = Float.NaN,
                sourceHeight = 48f,
                targetSize = 56f,
            ),
        )
    }
}
