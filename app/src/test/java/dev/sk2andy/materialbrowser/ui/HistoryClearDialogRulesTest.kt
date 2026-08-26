package dev.sk2andy.materialbrowser.ui

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryClearDialogRulesTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun `moving since past until moves until to same moment`() {
        val range = HistoryClearDateTimeRange(
            since = LocalDateTime.of(2026, 8, 10, 8, 0),
            until = LocalDateTime.of(2026, 8, 20, 18, 0),
        )

        assertEquals(
            HistoryClearDateTimeRange(
                since = LocalDateTime.of(2026, 8, 25, 12, 30),
                until = LocalDateTime.of(2026, 8, 25, 12, 30),
            ),
            HistoryClearDialogRules.updateMoment(
                range = range,
                field = HistoryClearDateField.Since,
                selectedMoment = LocalDateTime.of(2026, 8, 25, 12, 30),
                zoneId = utc,
            ),
        )
    }

    @Test
    fun `moving until before since moves since to same moment`() {
        val range = HistoryClearDateTimeRange(
            since = LocalDateTime.of(2026, 8, 10, 8, 0),
            until = LocalDateTime.of(2026, 8, 20, 18, 0),
        )

        assertEquals(
            HistoryClearDateTimeRange(
                since = LocalDateTime.of(2026, 8, 5, 7, 15),
                until = LocalDateTime.of(2026, 8, 5, 7, 15),
            ),
            HistoryClearDialogRules.updateMoment(
                range = range,
                field = HistoryClearDateField.Until,
                selectedMoment = LocalDateTime.of(2026, 8, 5, 7, 15),
                zoneId = utc,
            ),
        )
    }

    @Test
    fun `moving since past until on same day clamps at minute precision`() {
        val range = HistoryClearDateTimeRange(
            since = LocalDateTime.of(2026, 8, 10, 8, 0),
            until = LocalDateTime.of(2026, 8, 10, 18, 0),
        )
        val selected = LocalDateTime.of(2026, 8, 10, 18, 1)

        assertEquals(
            HistoryClearDateTimeRange(selected, selected),
            HistoryClearDialogRules.updateMoment(
                range = range,
                field = HistoryClearDateField.Since,
                selectedMoment = selected,
                zoneId = utc,
            ),
        )
    }

    @Test
    fun `spring gap time normalizes before range clamping`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val range = HistoryClearDateTimeRange(
            since = LocalDateTime.of(2026, 3, 29, 1, 0),
            until = LocalDateTime.of(2026, 3, 29, 3, 0),
        )

        assertEquals(
            HistoryClearDateTimeRange(
                since = LocalDateTime.of(2026, 3, 29, 3, 30),
                until = LocalDateTime.of(2026, 3, 29, 3, 30),
            ),
            HistoryClearDialogRules.updateMoment(
                range = range,
                field = HistoryClearDateField.Since,
                selectedMoment = LocalDateTime.of(2026, 3, 29, 2, 30),
                zoneId = berlin,
            ),
        )
    }

    @Test
    fun `clear request includes until minute and both fall overlap occurrences`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val repeatedMinute = LocalDateTime.of(2026, 10, 25, 2, 30)

        val request = HistoryClearDialogRules.clearRequest(
            range = HistoryClearDateTimeRange(repeatedMinute, repeatedMinute),
            profileIds = setOf("personal"),
            zoneId = berlin,
        )

        assertEquals(61 * 60_000L, request.untilExclusiveMillis - request.sinceInclusiveMillis)
        assertEquals(
            repeatedMinute.atZone(berlin)
                .withLaterOffsetAtOverlap()
                .plusMinutes(1)
                .toInstant()
                .toEpochMilli(),
            request.untilExclusiveMillis,
        )
    }
}
