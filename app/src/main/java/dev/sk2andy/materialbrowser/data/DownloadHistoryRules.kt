package dev.sk2andy.materialbrowser.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

internal data class DownloadEntry(
    val id: Long,
    val name: String,
    val source: String,
    val status: DownloadStatus,
    val bytes: Long,
    val total: Long,
    val lastModified: Long,
    val mime: String,
)

internal enum class DownloadStatus(val isActive: Boolean) {
    Pending(isActive = true),
    Running(isActive = true),
    Paused(isActive = true),
    Successful(isActive = false),
    Failed(isActive = false),
    Cancelled(isActive = false),
    ;

    val isTerminal: Boolean
        get() = !isActive
}

internal enum class DownloadTimeFilter {
    All,
    Today,
    Last7Days,
    Last30Days,
}

internal object DownloadHistoryRules {
    const val MAX_QUERY_CHARS = 256

    fun visibleEntries(
        entries: List<DownloadEntry>,
        query: String,
        timeFilter: DownloadTimeFilter,
        nowMillis: Long,
        zoneId: ZoneId,
    ): List<DownloadEntry> {
        val normalizedQuery = query.trim().take(MAX_QUERY_CHARS).lowercase(Locale.ROOT)
        val earliestMillis = earliestMillis(timeFilter, nowMillis, zoneId)
        return entries.asSequence()
            .filter { entry -> earliestMillis == null || entry.lastModified >= earliestMillis }
            .filter { entry ->
                normalizedQuery.isEmpty() ||
                    entry.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    entry.source.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .sortedWith(
                compareByDescending(DownloadEntry::lastModified)
                    .thenByDescending(DownloadEntry::id),
            )
            .toList()
    }

    fun progress(entry: DownloadEntry): Float? {
        if (entry.total <= 0L) return null
        return (entry.bytes.toDouble() / entry.total.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    fun clearableTerminalIds(entries: List<DownloadEntry>): Set<Long> = entries.asSequence()
        .filter { entry -> entry.status.isTerminal }
        .map(DownloadEntry::id)
        .toSet()

    private fun earliestMillis(
        timeFilter: DownloadTimeFilter,
        nowMillis: Long,
        zoneId: ZoneId,
    ): Long? {
        if (timeFilter == DownloadTimeFilter.All) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val earliestDate = when (timeFilter) {
            DownloadTimeFilter.All -> return null
            DownloadTimeFilter.Today -> today
            DownloadTimeFilter.Last7Days -> today.minusDays(6)
            DownloadTimeFilter.Last30Days -> today.minusDays(29)
        }
        return earliestDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
