package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoSafeAreaSettingsTest {
    @Test
    fun `defaults enable bounded interaction gated corrections`() {
        val settings = GeckoSafeAreaSettings()

        assertTrue(settings.enabled)
        assertTrue(settings.recheckAddedElements)
        assertTrue(settings.recheckChangedElements)
        assertTrue(settings.requireInteractionForUpdates)
        assertTrue(settings.recheckOnResize)
        assertEquals(1_000, settings.interactionWindowMillis)
        assertEquals(150, settings.mutationDebounceMillis)
        assertEquals(16, settings.maxElementsPerBatch)
        assertEquals(4, settings.maxBatchDurationMillis)
        assertEquals(512, settings.maxInitialElements)
        assertEquals(settings, settings.normalized())
        assertTrue(settings.hasDefaultSettings)
    }

    @Test
    fun `extreme values clamp before rounding without overflow`() {
        val minimum = GeckoSafeAreaSettings(
            interactionWindowMillis = Int.MIN_VALUE,
            mutationDebounceMillis = Int.MIN_VALUE,
            maxElementsPerBatch = Int.MIN_VALUE,
            maxBatchDurationMillis = Int.MIN_VALUE,
            maxInitialElements = Int.MIN_VALUE,
        ).normalized()
        val maximum = GeckoSafeAreaSettings(
            interactionWindowMillis = Int.MAX_VALUE,
            mutationDebounceMillis = Int.MAX_VALUE,
            maxElementsPerBatch = Int.MAX_VALUE,
            maxBatchDurationMillis = Int.MAX_VALUE,
            maxInitialElements = Int.MAX_VALUE,
        ).normalized()

        assertEquals(100, minimum.interactionWindowMillis)
        assertEquals(50, minimum.mutationDebounceMillis)
        assertEquals(4, minimum.maxElementsPerBatch)
        assertEquals(1, minimum.maxBatchDurationMillis)
        assertEquals(64, minimum.maxInitialElements)
        assertEquals(5_000, maximum.interactionWindowMillis)
        assertEquals(1_000, maximum.mutationDebounceMillis)
        assertEquals(64, maximum.maxElementsPerBatch)
        assertEquals(8, maximum.maxBatchDurationMillis)
        assertEquals(2_048, maximum.maxInitialElements)
    }

    @Test
    fun `step rounding uses nearest step with midpoint rounded up`() {
        val belowMidpoint = GeckoSafeAreaSettings(
            interactionWindowMillis = 149,
            mutationDebounceMillis = 74,
            maxElementsPerBatch = 5,
            maxInitialElements = 95,
        ).normalized()
        val midpoint = GeckoSafeAreaSettings(
            interactionWindowMillis = 150,
            mutationDebounceMillis = 75,
            maxElementsPerBatch = 6,
            maxInitialElements = 96,
        ).normalized()

        assertEquals(100, belowMidpoint.interactionWindowMillis)
        assertEquals(50, belowMidpoint.mutationDebounceMillis)
        assertEquals(4, belowMidpoint.maxElementsPerBatch)
        assertEquals(64, belowMidpoint.maxInitialElements)
        assertEquals(200, midpoint.interactionWindowMillis)
        assertEquals(100, midpoint.mutationDebounceMillis)
        assertEquals(8, midpoint.maxElementsPerBatch)
        assertEquals(128, midpoint.maxInitialElements)
    }

    @Test
    fun `normalization is idempotent and preserves all switches independently`() {
        val settings = GeckoSafeAreaSettings(
            enabled = false,
            recheckAddedElements = false,
            recheckChangedElements = false,
            requireInteractionForUpdates = false,
            recheckOnResize = false,
            interactionWindowMillis = 1_234,
            mutationDebounceMillis = 278,
            maxElementsPerBatch = 31,
            maxBatchDurationMillis = 6,
            maxInitialElements = 777,
        ).normalized()

        assertFalse(settings.enabled)
        assertFalse(settings.recheckAddedElements)
        assertFalse(settings.recheckChangedElements)
        assertFalse(settings.requireInteractionForUpdates)
        assertFalse(settings.recheckOnResize)
        assertEquals(1_200, settings.interactionWindowMillis)
        assertEquals(300, settings.mutationDebounceMillis)
        assertEquals(32, settings.maxElementsPerBatch)
        assertEquals(6, settings.maxBatchDurationMillis)
        assertEquals(768, settings.maxInitialElements)
        assertEquals(settings, settings.normalized())
    }

    @Test
    fun `reset restores switches and budgets`() {
        val settings = GeckoSafeAreaSettings(enabled = false, maxElementsPerBatch = 64)

        assertFalse(settings.hasDefaultSettings)
        assertEquals(GeckoSafeAreaSettings(), settings.withDefaults())
        assertTrue(settings.withDefaults().hasDefaultSettings)
    }
}
