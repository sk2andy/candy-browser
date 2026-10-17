package dev.sk2andy.materialbrowser.reader

class ReaderLibraryController {
    var state: ReaderLibraryState = ReaderLibraryState()
        private set

    fun restore(raw: String?): ReaderLibraryState = update(ReaderLibraryCodec.decode(raw))

    fun persistenceJson(): String {
        state = ReaderLibraryCodec.bounded(state)
        return ReaderLibraryCodec.encode(state)
    }

    fun visibleState(isPrivate: Boolean): ReaderLibraryState = ReaderLibraryRules.visibleState(state, isPrivate)

    fun updateSettings(settings: ReaderSettings, isPrivate: Boolean): ReaderLibraryState = update(
        ReaderLibraryRules.updateSettings(state, settings, isPrivate),
    )

    fun updateProgress(sourceUrl: String, progress: Float, isPrivate: Boolean): ReaderLibraryState = update(
        ReaderLibraryRules.updateProgress(state, sourceUrl, progress, isPrivate),
    )

    fun saveSnapshot(
        document: ReaderDocument,
        progress: Float,
        isPrivate: Boolean,
        nowMillis: Long,
        snapshotId: String,
        sourceProfileId: String?,
    ): ReaderLibraryState {
        if (isPrivate || !ReaderLibraryCodec.validSnapshotId(snapshotId) || nowMillis < 0) return state
        val safeDocument = ReaderLibraryCodec.sanitizeDocument(document) ?: return state
        val existing = state.snapshots.firstOrNull { it.document == safeDocument }
        val safeProfileId = if (existing != null) existing.sourceProfileId else {
            sourceProfileId?.takeIf {
                it.isNotBlank() && it.length <= 128 && it.none { character -> character.code < 32 || character.code in 127..159 }
            }
        }
        return update(
            ReaderLibraryRules.saveSnapshot(
                state = state,
                snapshot = ReaderSnapshot(
                    id = snapshotId,
                    document = safeDocument,
                    progress = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
                    savedAtMillis = nowMillis,
                    sourceProfileId = safeProfileId,
                ),
                isPrivate = false,
            ),
        )
    }

    fun deleteSnapshot(snapshotId: String, isPrivate: Boolean): ReaderLibraryState = update(
        ReaderLibraryRules.deleteSnapshot(state, snapshotId, isPrivate),
    )

    private fun update(value: ReaderLibraryState): ReaderLibraryState {
        state = value
        return value
    }
}
