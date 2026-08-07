package dev.sk2andy.materialbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressLoadCapsuleRulesTest {
    @Test
    fun `idle load feedback stays hidden until an observed load settles`() {
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Hidden),
            AddressLoadCapsuleRules.resolve(
                isLoading = false,
                progressPercent = 0,
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
    fun `zero load progress is indeterminate`() {
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Indeterminate),
            AddressLoadCapsuleRules.resolve(
                isLoading = true,
                progressPercent = 0,
                observedActiveLoad = false,
            ),
        )
    }

    @Test
    fun `only a completed observed load settles`() {
        assertTrue(
            AddressLoadCapsuleRules.shouldSettle(
                observedActiveLoad = true,
                isLoading = false,
                progressPercent = 100,
            ),
        )
        assertFalse(
            AddressLoadCapsuleRules.shouldSettle(
                observedActiveLoad = true,
                isLoading = false,
                progressPercent = 72,
            ),
        )
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Hidden),
            AddressLoadCapsuleRules.resolve(
                observedActiveLoad = true,
                isLoading = false,
                progressPercent = 72,
            ),
        )
        assertFalse(
            AddressLoadCapsuleRules.shouldSettle(
                observedActiveLoad = false,
                isLoading = false,
                progressPercent = 100,
            ),
        )
    }

    @Test
    fun `reported load progress is determinate and bounded`() {
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Determinate, progress = 0.42f),
            AddressLoadCapsuleRules.resolve(
                isLoading = true,
                progressPercent = 42,
                observedActiveLoad = false,
            ),
        )
        assertEquals(
            AddressLoadFeedbackState(AddressLoadFeedbackMode.Determinate, progress = 1f),
            AddressLoadCapsuleRules.resolve(
                isLoading = true,
                progressPercent = 120,
                observedActiveLoad = false,
            ),
        )
    }

    @Test
    fun `breath curve is bounded and settles at both ends`() {
        assertEquals(0f, AddressLoadCapsuleRules.breathAmount(-1f), 0.001f)
        assertEquals(1f, AddressLoadCapsuleRules.breathAmount(0.5f), 0.001f)
        assertEquals(0f, AddressLoadCapsuleRules.breathAmount(2f), 0.001f)
    }

    @Test
    fun `indeterminate band keeps constant length while wrapping outline`() {
        listOf(-1f, 0f, 0.25f, 0.5f, 0.75f, 1f, 2f).forEach { phase ->
            val segments = AddressLoadCapsuleRules.indeterminateSegments(phase)

            assertTrue(segments.isNotEmpty())
            segments.forEach { segment ->
                assertTrue(segment.start in 0f..1f)
                assertTrue(segment.end in 0f..1f)
                assertTrue(segment.start < segment.end)
            }
            assertEquals(
                0.32f,
                segments.sumOf { (it.end - it.start).toDouble() }.toFloat(),
                0.001f,
            )
        }
    }

    @Test
    fun `indeterminate band splits only at outline seam`() {
        assertEquals(1, AddressLoadCapsuleRules.indeterminateSegments(0.5f).size)
        val wrapped = AddressLoadCapsuleRules.indeterminateSegments(0.9f)

        assertEquals(2, wrapped.size)
        assertEquals(0.9f, wrapped[0].start, 0.001f)
        assertEquals(1f, wrapped[0].end, 0.001f)
        assertEquals(0f, wrapped[1].start, 0.001f)
        assertEquals(0.22f, wrapped[1].end, 0.001f)
    }
}
