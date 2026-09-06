package dev.sk2andy.materialbrowser.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressLoadCapsuleRulesTest {
    @Test
    fun `load feedback resolves hidden active and settling states`() {
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Hidden),
            AddressLoadCapsuleRules.resolve(
                isLoading = false,
                progressPercent = 0,
                observedActiveLoad = false,
            ),
        )
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Indeterminate),
            AddressLoadCapsuleRules.resolve(
                isLoading = true,
                progressPercent = 0,
                observedActiveLoad = false,
            ),
        )
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Determinate, progress = 0.42f),
            AddressLoadCapsuleRules.resolve(
                isLoading = true,
                progressPercent = 42,
                observedActiveLoad = false,
            ),
        )
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Settling, progress = 1f),
            AddressLoadCapsuleRules.resolve(
                isLoading = false,
                progressPercent = 100,
                observedActiveLoad = true,
            ),
        )
    }

    @Test
    fun `only completed observed load settles`() {
        assertTrue(AddressLoadCapsuleRules.shouldSettle(true, false, 100))
        assertFalse(AddressLoadCapsuleRules.shouldSettle(true, false, 72))
        assertFalse(AddressLoadCapsuleRules.shouldSettle(false, false, 100))
    }

    @Test
    fun `motion phases are bounded and reject non finite input`() {
        assertEquals(0f, AddressLoadCapsuleRules.breathAmount(Float.NaN))
        assertEquals(1f, AddressLoadCapsuleRules.breathAmount(0.5f))
        assertEquals(
            AddressLoadSegment(start = 0f, end = 0.32f),
            AddressLoadCapsuleRules.indeterminateSegment(Float.NaN),
        )
    }

    @Test
    fun `indeterminate band keeps constant length while wrapping`() {
        listOf(-1f, 0f, 0.5f, 0.9f, 1f, 2f, Float.NaN).forEach { phase ->
            val segments = AddressLoadCapsuleRules.indeterminateSegments(phase)

            assertTrue(segments.isNotEmpty())
            assertEquals(
                0.32f,
                segments.sumOf { (it.end - it.start).toDouble() }.toFloat(),
                0.001f,
            )
        }
    }

    @Test
    fun `rainbow closes sweep and loops at full phase`() {
        val start = AddressLoadRainbowRules.shiftedColors(0f)
        val finish = AddressLoadRainbowRules.shiftedColors(1f)

        assertEquals(start, finish)
        assertEquals(start.first(), start.last())
        assertEquals(start, AddressLoadRainbowRules.shiftedColors(Float.NaN))
    }
}
