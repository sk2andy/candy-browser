package dev.sk2andy.materialbrowser.data

internal object DownloadDuplicateRules {
    fun candidates(entries: List<DownloadEntry>, request: BrowserDownloadRequest): List<DownloadEntry> =
        entries.filter { entry ->
            entry.status == DownloadStatus.Successful &&
                entry.name == request.fileName &&
                (entry.source.isBlank() || entry.source == request.url) &&
                (entry.sourceProfileId == null || entry.sourceProfileId == request.sourceProfileId)
        }.sortedWith(compareByDescending(DownloadEntry::lastModified).thenByDescending(DownloadEntry::id))
}
