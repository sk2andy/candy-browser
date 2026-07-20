package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InactiveTabLifetimeTest {
    @Test
    fun durationsUseLongMillisecondValues() {
        assertNull(InactiveTabLifetime.Never.maxAgeMillis)
        assertEquals(21_600_000L, InactiveTabLifetime.SixHours.maxAgeMillis)
        assertEquals(86_400_000L, InactiveTabLifetime.OneDay.maxAgeMillis)
        assertEquals(259_200_000L, InactiveTabLifetime.ThreeDays.maxAgeMillis)
        assertEquals(604_800_000L, InactiveTabLifetime.SevenDays.maxAgeMillis)
        assertEquals(2_592_000_000L, InactiveTabLifetime.ThirtyDays.maxAgeMillis)
    }

    @Test
    fun unknownPersistedValueFallsBackToNever() {
        assertEquals(InactiveTabLifetime.Never, InactiveTabLifetime.fromWireValue("future_value"))
        assertEquals(InactiveTabLifetime.Never, InactiveTabLifetime.fromWireValue(null))
    }
}
