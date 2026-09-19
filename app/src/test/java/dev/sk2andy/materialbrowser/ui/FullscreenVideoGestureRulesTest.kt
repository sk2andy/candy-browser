package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenVideoGestureRulesTest {
    @Test
    fun `screen thirds select brightness dismiss and volume`() {
        assertEquals(
            FullscreenVideoGestureKind.Brightness,
            FullscreenVideoGestureRules.kind(pointerX = 100f, viewportWidth = 1_000f),
        )
        assertEquals(
            FullscreenVideoGestureKind.Dismiss,
            FullscreenVideoGestureRules.kind(pointerX = 500f, viewportWidth = 1_000f),
        )
        assertEquals(
            FullscreenVideoGestureKind.Volume,
            FullscreenVideoGestureRules.kind(pointerX = 900f, viewportWidth = 1_000f),
        )
    }

    @Test
    fun `up raises level and down lowers level within bounds`() {
        assertEquals(
            0.75f,
            FullscreenVideoGestureRules.adjustedLevel(
                startLevel = 0.5f,
                dragY = -250f,
                viewportHeight = 1_000f,
            ),
        )
        assertEquals(
            0f,
            FullscreenVideoGestureRules.adjustedLevel(
                startLevel = 0.1f,
                dragY = 500f,
                viewportHeight = 1_000f,
            ),
        )
        assertEquals(
            1f,
            FullscreenVideoGestureRules.adjustedLevel(
                startLevel = 0.9f,
                dragY = -500f,
                viewportHeight = 1_000f,
            ),
        )
    }

    @Test
    fun `dismiss accepts downward threshold and rejects upward drag`() {
        assertEquals(0f, FullscreenVideoGestureRules.dismissDragDistance(-300f))
        assertFalse(
            FullscreenVideoGestureRules.shouldDismiss(
                dragY = 129f,
                viewportHeight = 1_000f,
            ),
        )
        assertTrue(
            FullscreenVideoGestureRules.shouldDismiss(
                dragY = 130f,
                viewportHeight = 1_000f,
            ),
        )
    }

    @Test
    fun `dismiss threshold entry is reported only toward dismissal`() {
        assertTrue(
            FullscreenVideoGestureRules.enteredDismissThreshold(
                previousDragY = 129f,
                currentDragY = 130f,
                viewportHeight = 1_000f,
            ),
        )
        assertFalse(
            FullscreenVideoGestureRules.enteredDismissThreshold(
                previousDragY = 130f,
                currentDragY = 129f,
                viewportHeight = 1_000f,
            ),
        )
        assertFalse(
            FullscreenVideoGestureRules.enteredDismissThreshold(
                previousDragY = 130f,
                currentDragY = 180f,
                viewportHeight = 1_000f,
            ),
        )
    }

    @Test
    fun `dismiss stays near anchor until lower threshold breaks`() {
        assertEquals(
            0f,
            FullscreenVideoGestureRules.dismissOffset(
                dragY = -100f,
                viewportHeight = 1_000f,
            ),
        )
        assertTrue(
            FullscreenVideoGestureRules.dismissOffset(
                dragY = 100f,
                viewportHeight = 1_000f,
            ) < 24f,
        )
        assertEquals(
            43.4f,
            FullscreenVideoGestureRules.dismissOffset(
                dragY = 150f,
                viewportHeight = 1_000f,
            ),
            0.001f,
        )
    }

    @Test
    fun `dismiss transform follows finger and gains expressive shape`() {
        assertEquals(
            FullscreenVideoGestureTransform(
                translationY = 0f,
                scale = 1f,
                cornerRadiusDp = 0f,
            ),
            FullscreenVideoGestureRules.transform(offsetY = 0f, viewportHeight = 1_000f),
        )
        val moved = FullscreenVideoGestureRules.transform(
            offsetY = 225f,
            viewportHeight = 1_000f,
        )
        assertEquals(225f, moved.translationY)
        assertTrue(moved.scale in 0.88f..<1f)
        assertTrue(moved.cornerRadiusDp in 0f..32f)
    }

    @Test
    fun `level haptic grows stronger and denser toward maximum`() {
        assertTrue(
            FullscreenVideoGestureRules.levelHapticStrength(0.8f) >
                FullscreenVideoGestureRules.levelHapticStrength(0.2f),
        )
        val lowRangeSteps = FullscreenVideoGestureRules.levelHapticStep(0.3f) -
            FullscreenVideoGestureRules.levelHapticStep(0.2f)
        val highRangeSteps = FullscreenVideoGestureRules.levelHapticStep(0.9f) -
            FullscreenVideoGestureRules.levelHapticStep(0.8f)
        assertTrue(highRangeSteps > lowRangeSteps)
    }

    @Test
    fun `level haptic inputs are bounded and deterministic`() {
        assertEquals(
            FullscreenVideoGestureRules.levelHapticStep(0f),
            FullscreenVideoGestureRules.levelHapticStep(Float.NaN),
        )
        assertEquals(
            FullscreenVideoGestureRules.levelHapticStep(1f),
            FullscreenVideoGestureRules.levelHapticStep(2f),
        )
        assertEquals(
            FullscreenVideoGestureRules.levelHapticStrength(0f),
            FullscreenVideoGestureRules.levelHapticStrength(-1f),
        )
    }
}
