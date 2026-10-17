package dev.sk2andy.materialbrowser.reader

import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules

interface ReaderLibraryDataSource {
    fun load(isPrivate: Boolean, onLoaded: (ReaderLibraryState) -> Unit)

    fun updateSettings(
        settings: ReaderSettings,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    )

    fun updateProgress(sourceUrl: String, progress: Float, isPrivate: Boolean)

    fun saveSnapshot(
        document: ReaderDocument,
        progress: Float,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    )

    fun deleteSnapshot(
        snapshotId: String,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    )
}

enum class ReaderTheme { System, Light, Paper, Night }

enum class ReaderFont { Serif, Sans }

enum class ReaderTextAlignment { Start, Justified }

data class ReaderSettings(
    val fontScale: Float = 1f,
    val theme: ReaderTheme = ReaderTheme.Paper,
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.Start,
    val font: ReaderFont = ReaderFont.Sans,
)

data class ReaderSnapshot(
    val id: String,
    val document: ReaderDocument,
    val progress: Float,
    val savedAtMillis: Long,
    val sourceProfileId: String? = null,
    val isPrivate: Boolean = false,
)

data class ReaderLibraryState(
    val settings: ReaderSettings = ReaderSettings(),
    val progressByUrl: Map<String, Float> = emptyMap(),
    val snapshots: List<ReaderSnapshot> = emptyList(),
)

object ReaderLibraryRules {
    const val MAX_SNAPSHOTS = 20
    const val MAX_PROGRESS_ENTRIES = 500

    fun snapshotForSourceUrl(
        state: ReaderLibraryState,
        sourceUrl: String,
        isPrivate: Boolean,
    ): ReaderSnapshot? {
        if (isPrivate) return null
        val key = BrowserUrlRules.httpUrlKey(sourceUrl) ?: return null
        return state.snapshots.firstOrNull {
            !it.isPrivate && BrowserUrlRules.httpUrlKey(it.document.sourceUrl) == key
        }
    }

    fun visibleState(state: ReaderLibraryState, isPrivate: Boolean): ReaderLibraryState =
        if (isPrivate) ReaderLibraryState() else state

    fun updateSettings(
        state: ReaderLibraryState,
        settings: ReaderSettings,
        isPrivate: Boolean,
    ): ReaderLibraryState = if (isPrivate) {
        state
    } else {
        state.copy(
            settings = settings.copy(fontScale = settings.fontScale.takeIf(Float::isFinite)?.coerceIn(0.8f, 1.6f) ?: 1f),
        )
    }

    fun updateProgress(
        state: ReaderLibraryState,
        sourceUrl: String,
        progress: Float,
        isPrivate: Boolean,
    ): ReaderLibraryState = if (isPrivate || ReaderExtractionContract.safeHttpUrl(sourceUrl) == null) {
        state
    } else {
        state.copy(
            progressByUrl = (state.progressByUrl + (sourceUrl to (progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f)))
                .entries
                .toList()
                .takeLast(MAX_PROGRESS_ENTRIES)
                .associate { it.toPair() },
        )
    }

    fun saveSnapshot(
        state: ReaderLibraryState,
        snapshot: ReaderSnapshot,
        isPrivate: Boolean,
    ): ReaderLibraryState = if (isPrivate) {
        state
    } else {
        state.copy(
            snapshots = (listOf(snapshot) + state.snapshots.filterNot {
                it.id == snapshot.id || it.document.sourceUrl == snapshot.document.sourceUrl
            })
                .sortedByDescending(ReaderSnapshot::savedAtMillis)
                .take(MAX_SNAPSHOTS),
        )
    }

    fun deleteSnapshot(
        state: ReaderLibraryState,
        snapshotId: String,
        isPrivate: Boolean,
    ): ReaderLibraryState = if (isPrivate) {
        state
    } else {
        state.copy(snapshots = state.snapshots.filterNot { it.id == snapshotId })
    }

    fun progress(scrollValue: Int, maxScrollValue: Int): Float = when {
        maxScrollValue <= 0 -> 0f
        else -> (scrollValue.toFloat() / maxScrollValue).coerceIn(0f, 1f)
    }

    fun resumeProgress(
        state: ReaderLibraryState,
        snapshotProgress: Float?,
        sourceUrl: String,
    ): Float = state.progressByUrl[sourceUrl] ?: snapshotProgress ?: 0f

    fun shouldJustify(kind: ReaderBlockKind, alignment: ReaderTextAlignment): Boolean =
        alignment == ReaderTextAlignment.Justified &&
            (kind == ReaderBlockKind.Paragraph || kind == ReaderBlockKind.ListItem)

}

object ReaderStorageBudget {
    fun <T> evictOldestUntil(
        newestFirst: List<T>,
        maxBytes: Int,
        encodedBytes: (List<T>) -> Int,
    ): List<T> {
        var bounded = newestFirst
        while (bounded.isNotEmpty() && encodedBytes(bounded) > maxBytes) {
            bounded = bounded.dropLast(1)
        }
        return bounded
    }
}
