package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SnoozeTimeRulesTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `later today is three elapsed hours across spring DST gap`() {
        val now = Instant.parse("2026-03-29T00:30:00Z").toEpochMilli()

        val wakeAt = SnoozeTimeRules.wakeAtMillis(SnoozePreset.LaterToday, now, berlin)

        assertEquals(3 * 60 * 60 * 1_000L, wakeAt - now)
    }

    @Test
    fun `tomorrow stays at nine local across spring DST change`() {
        val now = Instant.parse("2026-03-28T20:00:00Z").toEpochMilli()

        val wakeAt = SnoozeTimeRules.wakeAtMillis(SnoozePreset.Tomorrow, now, berlin)

        assertEquals("2026-03-29T07:00:00Z", Instant.ofEpochMilli(wakeAt).toString())
    }

    @Test
    fun `later today never crosses local date late at night`() {
        listOf("2026-08-08T19:30:00Z", "2026-08-08T21:30:00Z").forEach { timestamp ->
            val now = Instant.parse(timestamp).toEpochMilli()
            val wakeAt = SnoozeTimeRules.wakeAtMillis(SnoozePreset.LaterToday, now, berlin)

            assertEquals(
                Instant.ofEpochMilli(now).atZone(berlin).toLocalDate(),
                Instant.ofEpochMilli(wakeAt).atZone(berlin).toLocalDate(),
            )
            assertTrue(wakeAt > now)
        }
    }

    @Test
    fun `next week selects following Monday at nine local`() {
        val now = Instant.parse("2026-08-08T10:00:00Z").toEpochMilli()

        val wakeAt = SnoozeTimeRules.wakeAtMillis(SnoozePreset.NextWeek, now, berlin)

        assertEquals("2026-08-10T07:00:00Z", Instant.ofEpochMilli(wakeAt).toString())
    }

    @Test
    fun `custom time in DST gap resolves to first valid local time`() {
        val wakeAt = SnoozeTimeRules.customWakeAtMillis(
            LocalDate.of(2026, 3, 29),
            LocalTime.of(2, 30),
            berlin,
        )

        assertEquals("2026-03-29T01:30:00Z", Instant.ofEpochMilli(wakeAt).toString())
    }

    @Test
    fun `custom overlap uses earlier offset and remains deterministic`() {
        val wakeAt = SnoozeTimeRules.customWakeAtMillis(
            LocalDate.of(2026, 10, 25),
            LocalTime.of(2, 30),
            berlin,
        )

        assertEquals("2026-10-25T00:30:00Z", Instant.ofEpochMilli(wakeAt).toString())
        assertTrue(wakeAt > 0L)
    }
}
