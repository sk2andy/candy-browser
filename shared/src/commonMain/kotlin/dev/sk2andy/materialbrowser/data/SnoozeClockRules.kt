package dev.sk2andy.materialbrowser.data

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours

object SnoozeClockRules {
    fun wakeAtMillis(preset: SnoozePreset, nowMillis: Long, zoneId: String): Long? = runCatching {
        val zone = TimeZone.of(zoneId)
        val instant = Instant.fromEpochMilliseconds(nowMillis)
        val now = instant.toLocalDateTime(zone)
        val wakeAt = when (preset) {
            SnoozePreset.LaterToday -> {
                val later = instant + 3.hours
                if (later.toLocalDateTime(zone).date == now.date) later else {
                    LocalDateTime(now.date, LocalTime(23, 59, 59, 999_999_999)).toInstant(zone)
                }
            }
            SnoozePreset.Tomorrow -> LocalDateTime(now.date + DatePeriod(days = 1), LocalTime(9, 0))
                .toInstant(zone)
            SnoozePreset.NextWeek -> LocalDateTime(
                now.date + DatePeriod(days = 8 - now.dayOfWeek.isoDayNumber), LocalTime(9, 0),
            ).toInstant(zone)
        }
        wakeAt.toEpochMilliseconds()
    }.getOrNull()

    fun customWakeAtMillis(date: String, time: String, zoneId: String): Long? = runCatching {
        LocalDateTime(LocalDate.parse(date), LocalTime.parse(time)).toInstant(TimeZone.of(zoneId))
            .toEpochMilliseconds()
    }.getOrNull()
}
