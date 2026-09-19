package dev.sk2andy.materialbrowser.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FullscreenVideoGestureStateTest {
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `dismiss rubberband confirms only outward threshold entry`() {
        var rubberbandStarts = 0
        var rubberbandStops = 0
        var confirms = 0
        val state = gestureState(
            haptics = FullscreenVideoGestureHaptics(
                startRubberband = { rubberbandStarts++ },
                stopRubberband = { rubberbandStops++ },
                confirm = { confirms++ },
                levelTick = {},
            ),
        )

        state.setEnabled(true)
        state.begin(pointerX = 500f, width = 1_000f, height = 1_000f)
        state.drag(100f)
        state.drag(31f)
        state.drag(-2f)

        assertEquals(1, confirms)
        assertEquals(2, rubberbandStarts)
        assertTrue(rubberbandStops >= 1)

        state.setEnabled(false)
        assertTrue(rubberbandStops >= 2)
    }

    @Test
    fun `level ticks deduplicate steps and strengthen with applied level`() {
        val strengths = mutableListOf<Float>()
        var brightness = 0.2f
        val state = gestureState(
            currentBrightness = { brightness },
            setBrightness = { requested ->
                brightness = requested
                brightness
            },
            haptics = FullscreenVideoGestureHaptics(
                startRubberband = {},
                stopRubberband = {},
                confirm = {},
                levelTick = strengths::add,
            ),
        )

        state.setEnabled(true)
        state.begin(pointerX = 100f, width = 1_000f, height = 1_000f)
        state.drag(-100f)
        state.drag(0f)
        state.drag(-200f)

        assertEquals(2, strengths.size)
        assertTrue(strengths[1] > strengths[0])
    }

    @Test
    fun `new side gesture clears interrupted dismiss transform`() {
        val state = gestureState()

        state.setEnabled(true)
        state.begin(pointerX = 500f, width = 1_000f, height = 1_000f)
        state.drag(100f)
        assertTrue(state.dismissOffsetPx > 0f)

        state.begin(pointerX = 100f, width = 1_000f, height = 1_000f)

        assertEquals(0f, state.dismissOffsetPx)
    }

    private fun gestureState(
        currentBrightness: () -> Float = { 0.5f },
        setBrightness: (Float) -> Float = { it },
        haptics: FullscreenVideoGestureHaptics = FullscreenVideoGestureHaptics(
            startRubberband = {},
            stopRubberband = {},
            confirm = {},
            levelTick = {},
        ),
    ): FullscreenVideoGestureState = FullscreenVideoGestureState(
        scope = scope,
        currentBrightness = currentBrightness,
        setBrightness = setBrightness,
        currentVolume = { 0.5f },
        setVolume = { it },
        dismissFullscreen = {},
        haptics = haptics,
    )
}
