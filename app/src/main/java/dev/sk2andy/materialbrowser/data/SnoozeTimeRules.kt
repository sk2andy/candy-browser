package dev.sk2andy.materialbrowser.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Android date-picker types stay at the edge; calendar policy is common Kotlin. */
internal object SnoozeTimeRules {
    fun wakeAtMillis(preset: SnoozePreset, nowMillis: Long, zoneId: ZoneId): Long =
        requireNotNull(SnoozeClockRules.wakeAtMillis(preset, nowMillis, zoneId.id))

    fun customWakeAtMillis(date: LocalDate, time: LocalTime, zoneId: ZoneId): Long =
        requireNotNull(SnoozeClockRules.customWakeAtMillis(date.toString(), time.toString(), zoneId.id))
}
