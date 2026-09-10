package dev.sk2andy.materialbrowser.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadHistoryRulesTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 8, 20)
    private val nowMillis = today.atTime(12, 0).atZone(berlin).toInstant().toEpochMilli()

    @Test
    fun `statuses distinguish active and terminal downloads`() {
        assertTrue(DownloadStatus.Pending.isActive)
        assertTrue(DownloadStatus.Running.isActive)
        assertTrue(DownloadStatus.Paused.isActive)
        assertFalse(DownloadStatus.Successful.isActive)
        assertFalse(DownloadStatus.Failed.isActive)
        assertFalse(DownloadStatus.Cancelled.isActive)
        assertTrue(DownloadStatus.Successful.isTerminal)
        assertFalse(DownloadStatus.Running.isTerminal)
    }

    @Test
    fun `search matches name and source ignoring case and whitespace`() {
        val named = entry(id = 1, name = "Candy Manual.pdf", source = "https://example.com")
        val sourced = entry(id = 2, name = "Archive.zip", source = "https://CANDY.example")
        val unrelated = entry(id = 3, name = "Notes.txt", source = "https://other.example")

        assertEquals(
            listOf(sourced, named),
            visible(listOf(named, unrelated, sourced), query = "  candy  "),
        )
    }

    @Test
    fun `entries are ordered newest first with stable id tie breaker`() {
        val older = entry(id = 9, lastModified = 10)
        val tiedLowerId = entry(id = 2, lastModified = 20)
        val tiedHigherId = entry(id = 3, lastModified = 20)

        assertEquals(
            listOf(tiedHigherId, tiedLowerId, older),
            visible(listOf(older, tiedLowerId, tiedHigherId)),
        )
    }

    @Test
    fun `today filter uses local day boundary`() {
        val beforeToday = entry(
            id = 1,
            lastModified = today.minusDays(1).atTime(23, 59).atZone(berlin).toInstant().toEpochMilli(),
        )
        val atToday = entry(
            id = 2,
            lastModified = today.atStartOfDay(berlin).toInstant().toEpochMilli(),
        )

        assertEquals(
            listOf(atToday),
            visible(listOf(beforeToday, atToday), timeFilter = DownloadTimeFilter.Today),
        )
    }

    @Test
    fun `seven and thirty day filters include complete local boundary day`() {
        val sevenDayBoundary = entry(id = 7, lastModified = startOfDay(today.minusDays(6)))
        val beforeSevenDays = entry(id = 8, lastModified = startOfDay(today.minusDays(7)))
        val thirtyDayBoundary = entry(id = 30, lastModified = startOfDay(today.minusDays(29)))
        val beforeThirtyDays = entry(id = 31, lastModified = startOfDay(today.minusDays(30)))

        assertEquals(
            listOf(sevenDayBoundary),
            visible(
                listOf(beforeSevenDays, sevenDayBoundary),
                timeFilter = DownloadTimeFilter.Last7Days,
            ),
        )
        assertEquals(
            listOf(sevenDayBoundary, beforeSevenDays, thirtyDayBoundary),
            visible(
                listOf(beforeThirtyDays, thirtyDayBoundary, beforeSevenDays, sevenDayBoundary),
                timeFilter = DownloadTimeFilter.Last30Days,
            ),
        )
    }

    @Test
    fun `all filter retains entries regardless of age`() {
        val old = entry(id = 1, lastModified = startOfDay(today.minusDays(365)))

        assertEquals(listOf(old), visible(listOf(old), timeFilter = DownloadTimeFilter.All))
    }

    @Test
    fun `progress clamps byte counts and keeps unknown totals indeterminate`() {
        assertEquals(0f, DownloadHistoryRules.progress(entry(bytes = -10, total = 100)))
        assertEquals(0.25f, DownloadHistoryRules.progress(entry(bytes = 25, total = 100)))
        assertEquals(1f, DownloadHistoryRules.progress(entry(bytes = 125, total = 100)))
        assertNull(DownloadHistoryRules.progress(entry(bytes = 25, total = 0)))
        assertNull(DownloadHistoryRules.progress(entry(bytes = 25, total = -1)))
    }

    @Test
    fun `clearable IDs contain only terminal downloads`() {
        val entries = DownloadStatus.entries.mapIndexed { index, status ->
            entry(id = index.toLong(), status = status)
        }

        assertEquals(
            setOf(3L, 4L, 5L),
            DownloadHistoryRules.clearableTerminalIds(entries),
        )
    }

    private fun visible(
        entries: List<DownloadEntry>,
        query: String = "",
        timeFilter: DownloadTimeFilter = DownloadTimeFilter.All,
    ): List<DownloadEntry> = DownloadHistoryRules.visibleEntries(
        entries = entries,
        query = query,
        timeFilter = timeFilter,
        nowMillis = nowMillis,
        zoneId = berlin,
    )

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(berlin).toInstant().toEpochMilli()

    private fun entry(
        id: Long = 1,
        name: String = "file.bin",
        source: String = "https://example.com/file.bin",
        status: DownloadStatus = DownloadStatus.Successful,
        bytes: Long = 100,
        total: Long = 100,
        lastModified: Long = nowMillis,
        mime: String = "application/octet-stream",
    ) = DownloadEntry(
        id = id,
        name = name,
        source = source,
        status = status,
        bytes = bytes,
        total = total,
        lastModified = lastModified,
        mime = mime,
    )
}
