package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.recall.RecallMatch

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
        val metadataMatches = BrowsingHistoryRules.visibleEntries(
            history = history,
            selectedProfileIds = selectedProfileIds,
            query = query,
        )
        if (query.isBlank() || selectedProfileIds.isEmpty()) {
            return HistoryRecallSnapshot(metadataMatches, emptyMap())
        }
        val historyByDocument = history.associateBy(::documentKey)
        val recallItems = recallMatches.asSequence()
            .filter { match -> match.profileId in selectedProfileIds }
            .mapNotNull { match ->
                val key = documentKey(match.profileId, match.url) ?: return@mapNotNull null
                val entry = historyByDocument[key] ?: HistoryEntry(
                    url = match.url,
                    title = match.title,
                    lastVisitedAt = match.visitedAt,
                    profileId = match.profileId,
                )
                entry to match.excerpt
            }
            .toList()
        val entries = (metadataMatches + recallItems.map { (entry, _) -> entry })
            .distinctBy(::documentKey)
            .sortedWith(
                compareByDescending(HistoryEntry::lastVisitedAt)
                    .thenBy(HistoryEntry::profileId)
                    .thenBy(HistoryEntry::url),
            )
        val excerpts = recallItems.associate { (entry, excerpt) ->
            BrowsingHistoryRules.entryKey(entry) to excerpt
        }
        return HistoryRecallSnapshot(entries, excerpts)
    }

    private fun documentKey(entry: HistoryEntry): String? =
        documentKey(entry.profileId, entry.url)

    private fun documentKey(profileId: String, url: String): String? =
        CanonicalWebUrl.key(url)?.let { canonical -> "$profileId\u0000$canonical" }
}
