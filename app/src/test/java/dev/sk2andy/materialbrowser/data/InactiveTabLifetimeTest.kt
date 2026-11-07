package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InactiveTabLifetimeTest {
    @Test
    fun `short fixed lifetimes use stable wire values and durations`() {
        mapOf(
            "30_minutes" to 1_800_000L,
            "1_hour" to 3_600_000L,
            "2_hours" to 7_200_000L,
        ).forEach { (wireValue, duration) ->
            val lifetime = InactiveTabLifetime.fromWireValue(wireValue)
            assertEquals(wireValue, lifetime.wireValue)
            assertEquals(duration, lifetime.maxAgeMillis)
        }
    }

    @Test
    fun `fixed timed choices stay ordered without a custom timer`() {
        assertEquals(
            listOf("30_minutes", "1_hour", "2_hours", "6_hours", "1_day", "3_days", "7_days", "30_days"),
            InactiveTabLifetime.entries.filter { it.maxAgeMillis != null }.map { it.wireValue },
        )
    }

    @Test
    fun durationsUseLongMillisecondValues() {
        assertNull(InactiveTabLifetime.Never.maxAgeMillis)
        assertNull(InactiveTabLifetime.Immediately.maxAgeMillis)
        assertNull(InactiveTabLifetime.WhenAppCloses.maxAgeMillis)
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

    @Test
    fun eventLifetimeWireValuesRoundTrip() {
        assertEquals(
            InactiveTabLifetime.Immediately,
            InactiveTabLifetime.fromWireValue(InactiveTabLifetime.Immediately.wireValue),
        )
        assertEquals(
            InactiveTabLifetime.WhenAppCloses,
            InactiveTabLifetime.fromWireValue(InactiveTabLifetime.WhenAppCloses.wireValue),
        )
    }
}
