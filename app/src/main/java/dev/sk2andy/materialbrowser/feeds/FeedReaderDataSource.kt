package dev.sk2andy.materialbrowser.feeds

import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderLibraryDataSource
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.reader.ReaderSettings
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Feed downloads keep their profile ownership and are not subject to Reader's legacy item cap. */
class FeedReaderDataSource(
    private val feeds: FeedRepository,
    private val reader: ReaderLibraryDataSource,
    private val scope: CoroutineScope,
    private val articleId: String,
    private val progressScope: CoroutineScope = scope,
) : ReaderLibraryDataSource by reader {
    override fun updateSettings(
        settings: ReaderSettings,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        reader.updateSettings(settings, isPrivate) { load(isPrivate, onUpdated) }
    }

    override fun updateProgress(sourceUrl: String, progress: Float, isPrivate: Boolean) {
        if (!isPrivate)
            progressScope.launch { feeds.updateProgress(FeedRules.identity(sourceUrl), progress) }
    }

    override fun load(isPrivate: Boolean, onLoaded: (ReaderLibraryState) -> Unit) {
        if (isPrivate) return onLoaded(ReaderLibraryState())
        reader.load(false) { state ->
            scope.launch {
                val document = feeds.loadOfflineDocument(articleId).getOrNull()
                onLoaded(combine(state, document))
            }
        }
    }

    override fun saveSnapshot(
        document: ReaderDocument,
        progress: Float,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        if (isPrivate) return onUpdated(ReaderLibraryState())
        scope.launch {
            feeds.updateOffline(
                FeedRules.identity(document.sourceUrl),
                FeedOfflineStatus.Ready,
                document,
            )
            load(false, onUpdated)
        }
    }

    override fun deleteSnapshot(
        snapshotId: String,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        if (isPrivate) return onUpdated(ReaderLibraryState())
        scope.launch {
            feeds.updateOffline(snapshotId, FeedOfflineStatus.None)
            load(false, onUpdated)
        }
    }

    private fun combine(state: ReaderLibraryState, document: ReaderDocument?): ReaderLibraryState =
        state.copy(
            progressByUrl = feeds.state.value.articles.associate { it.url to it.readingProgress },
            snapshots =
                feeds.state.value.articles
                    .filter { it.id == articleId }
                    .mapNotNull { article ->
                        document?.let { document ->
                            ReaderSnapshot(
                                id = article.id,
                                document = document,
                                progress = article.readingProgress,
                                savedAtMillis = article.receivedAtMillis,
                                sourceProfileId = feeds.profileId,
                            )
                        }
                    },
        )
}
