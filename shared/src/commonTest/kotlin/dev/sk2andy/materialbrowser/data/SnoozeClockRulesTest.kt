package dev.sk2andy.materialbrowser.data

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SnoozeClockRulesTest {
    @Test
    fun `elapsed later today crosses spring gap without adding calendar hours`() {
        val now = Instant.parse("2026-03-29T00:30:00Z").toEpochMilliseconds()
        assertEquals(now + 10_800_000L, SnoozeClockRules.wakeAtMillis(SnoozePreset.LaterToday, now, "Europe/Berlin"))
    }

    @Test
    fun `tomorrow retains nine local across DST change`() {
        val now = Instant.parse("2026-03-28T20:00:00Z").toEpochMilliseconds()
        assertEquals(Instant.parse("2026-03-29T07:00:00Z").toEpochMilliseconds(), SnoozeClockRules.wakeAtMillis(SnoozePreset.Tomorrow, now, "Europe/Berlin"))
    }

    @Test
    fun `later today stays before local midnight`() {
        val now = Instant.parse("2026-08-08T21:30:00Z").toEpochMilliseconds()
        assertEquals(Instant.parse("2026-08-08T21:59:59.999Z").toEpochMilliseconds(), SnoozeClockRules.wakeAtMillis(SnoozePreset.LaterToday, now, "Europe/Berlin"))
    }

    @Test
    fun `next week means following Monday even when today is Monday`() {
        val now = Instant.parse("2026-08-10T10:00:00Z").toEpochMilliseconds()
        assertEquals(Instant.parse("2026-08-17T07:00:00Z").toEpochMilliseconds(), SnoozeClockRules.wakeAtMillis(SnoozePreset.NextWeek, now, "Europe/Berlin"))
    }

    @Test
    fun `custom gap shifts forward and overlap uses earlier offset`() {
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilliseconds(), SnoozeClockRules.customWakeAtMillis("2026-03-29", "02:30", "Europe/Berlin"))
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilliseconds(), SnoozeClockRules.customWakeAtMillis("2026-10-25", "02:30", "Europe/Berlin"))
    }

    @Test
    fun `invalid custom date and timezone fail without scheduling`() {
        assertNull(SnoozeClockRules.customWakeAtMillis("not-date", "02:30", "Europe/Berlin"))
        assertNull(SnoozeClockRules.wakeAtMillis(SnoozePreset.Tomorrow, 100, "not-a-zone"))
    }
}
