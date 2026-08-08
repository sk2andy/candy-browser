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
            PredictiveBackTransform(scale = 1f, translationX = 1_000f),
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
            PredictiveBackTransform(scale = 1f, translationX = 500f),
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
            1_000f,
            PredictiveBackMotion.transform(
                progress = 1f,
                width = 1_000f,
                swipeEdgeSign = 1,
            ).translationX,
        )
        assertEquals(
            -1_000f,
            PredictiveBackMotion.transform(
                progress = 1f,
                width = 1_000f,
                swipeEdgeSign = -1,
            ).translationX,
        )
    }

    @Test
    fun `entry moves from right edge to resting position`() {
        assertEquals(
            1_000f,
            PredictiveBackMotion.entryTranslation(progress = 0f, width = 1_000f),
        )
        assertEquals(
            500f,
            PredictiveBackMotion.entryTranslation(progress = 0.5f, width = 1_000f),
        )
        assertEquals(
            0f,
            PredictiveBackMotion.entryTranslation(progress = 1f, width = 1_000f),
        )
    }

    @Test
    fun `predictive commit only animates the remaining distance`() {
        assertEquals(220, PredictiveBackMotion.remainingDurationMillis(progress = -1f))
        assertEquals(110, PredictiveBackMotion.remainingDurationMillis(progress = 0.5f))
        assertEquals(0, PredictiveBackMotion.remainingDurationMillis(progress = 2f))
    }
}
