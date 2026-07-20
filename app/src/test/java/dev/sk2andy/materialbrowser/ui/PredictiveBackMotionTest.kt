package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictiveBackMotionTest {
    @Test
    fun `progress is clamped to motion bounds`() {
        assertEquals(
            PredictiveBackTransform(scale = 1f, translationX = 0f),
            PredictiveBackMotion.transform(
                progress = -0.5f,
                width = 1_000f,
                swipeEdgeSign = 1,
            ),
        )
        assertEquals(
            PredictiveBackTransform(scale = 0.96f, translationX = 40f),
            PredictiveBackMotion.transform(
                progress = 1.5f,
                width = 1_000f,
                swipeEdgeSign = 1,
            ),
        )
    }

    @Test
    fun `motion scales and translates with progress`() {
        assertEquals(
            PredictiveBackTransform(scale = 0.98f, translationX = 20f),
            PredictiveBackMotion.transform(
                progress = 0.5f,
                width = 1_000f,
                swipeEdgeSign = 1,
            ),
        )
    }

    @Test
    fun `swipe edge controls horizontal direction`() {
        assertEquals(
            40f,
            PredictiveBackMotion.transform(
                progress = 1f,
                width = 1_000f,
                swipeEdgeSign = 1,
            ).translationX,
        )
        assertEquals(
            -40f,
            PredictiveBackMotion.transform(
                progress = 1f,
                width = 1_000f,
                swipeEdgeSign = -1,
            ).translationX,
        )
    }
}
