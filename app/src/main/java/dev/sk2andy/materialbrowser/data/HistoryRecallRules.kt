package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.recall.RecallRules

internal data class HistoryRecallSnapshot(
    val entries: List<HistoryEntry>,
    val excerptsByEntryKey: Map<String, String>,
)

internal object HistoryRecallRules {
    fun merge(
        history: List<HistoryEntry>,
        selectedProfileIds: Set<String>,
        query: String,
        recallMatches: List<RecallMatch>,
    ): HistoryRecallSnapshot {
        val boundedQuery = query.take(RecallRules.MAX_QUERY_CHARS)
        val metadataMatches = BrowsingHistoryRules.visibleEntries(
            history = history,
            selectedProfileIds = selectedProfileIds,
            query = boundedQuery,
        )
        if (boundedQuery.isBlank() || selectedProfileIds.isEmpty()) {
            return HistoryRecallSnapshot(metadataMatches, emptyMap())
        }
        val historyByDocument = history
            .sortedByDescending(HistoryEntry::lastVisitedAt)
            .associateByKeepingFirst(::documentKey)
        val recallItems = recallMatches.asSequence()
            .filter { match -> match.profileId in selectedProfileIds }
            .mapNotNull { match ->
                val key = documentKey(match.profileId, match.url) ?: return@mapNotNull null
                val entry = historyByDocument[key] ?: return@mapNotNull null
                entry to match.excerpt
            }
            .toList()
        val metadataDocumentKeys = metadataMatches.mapNotNullTo(hashSetOf(), ::documentKey)
        val entries = (metadataMatches + recallItems.mapNotNull { (entry, _) ->
            entry.takeIf { documentKey(it) !in metadataDocumentKeys }
        })
            .distinctBy(BrowsingHistoryRules::entryKey)
            .sortedByDescending(HistoryEntry::lastVisitedAt)
        val excerpts = recallItems.associate { (entry, excerpt) ->
            BrowsingHistoryRules.entryKey(entry) to excerpt
        }
        return HistoryRecallSnapshot(entries, excerpts)
    }

    private fun documentKey(entry: HistoryEntry): String? =
        documentKey(entry.profileId, entry.url)

    private fun documentKey(profileId: String, url: String): String? =
        CanonicalWebUrl.key(url)?.let { canonical -> "$profileId\u0000$canonical" }

    private fun <K, V> Iterable<V>.associateByKeepingFirst(
        keySelector: (V) -> K,
    ): Map<K, V> = buildMap {
        this@associateByKeepingFirst.forEach { value ->
            putIfAbsent(keySelector(value), value)
        }
    }
}
