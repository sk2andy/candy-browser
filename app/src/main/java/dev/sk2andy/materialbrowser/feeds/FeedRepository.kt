package dev.sk2andy.materialbrowser.feeds

import android.content.Context
import dev.sk2andy.materialbrowser.browser.ProfileProtectionSession
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One serialized state owner per normal profile. Private sessions can preview, never persist. */
class FeedRepository
private constructor(context: Context, val profileId: String, val isPrivate: Boolean) {
    private val appContext = context.applicationContext
    private val store = FeedStore(appContext, profileId)
    private val profiles = BrowserSessionStore(context.applicationContext)
    private val mutex = Mutex()
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = mutableState.asStateFlow()
    private val catalog by lazy {
        FeedCuratedCatalog {
            appContext.assets.open("feed_catalog/plenary.json").bufferedReader().use {
                it.readText()
            }
        }
    }

    suspend fun searchCatalog(query: FeedCatalogQuery): Result<FeedCatalogPage> =
        try {
            FeedCatalogRules.requireNormalProfile(isPrivate)
            requireAccess()
            val page = withContext(Dispatchers.IO) { catalog.search(query) }
            requireAccess()
            Result.success(page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A discovery outage must not leave an unrelated error in the subscription inbox.
            Result.failure(error)
        }

    suspend fun load(): Result<Unit> = outcome {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                requireAccess()
                if (isPrivate) mutableState.value = FeedState(loaded = true)
                else if (!mutableState.value.loaded) {
                    val loaded = store.read()
                    requireAccess()
                    mutableState.value = loaded
                }
            }
        }
    }

    suspend fun preview(url: String): Result<FeedPreview> = outcome {
        withContext(Dispatchers.IO) {
            requireAccess()
            val response = FeedNetwork.fetchResponse(url)
            requireAccess()
            val parsed = FeedParser.parse(response.text, response.url)
            requireAccess()
            parsed
        }
    }

    suspend fun discover(url: String): Result<List<FeedCandidate>> = outcome {
        withContext(Dispatchers.IO) {
            requireAccess()
            val response = FeedNetwork.fetchResponse(url)
            requireAccess()
            val candidates =
                runCatching { FeedParser.parse(response.text, response.url) }
                    .getOrNull()
                    ?.let { listOf(FeedCandidate(it.url, it.title)) }
                    ?: FeedParser.discover(response.text, response.url)
            requireAccess()
            candidates
        }
    }

    suspend fun subscribe(preview: FeedPreview, folderId: String? = null): Result<Unit> =
        mutate { before ->
            val url = requireNotNull(FeedRules.safeUrl(preview.url)) { "Invalid feed URL" }
            val id = FeedRules.identity(url)
            if (before.sources.any { it.id == id }) return@mutate before
            require(before.sources.size < FeedRules.MAX_SOURCES) { "Source limit reached" }
            val now = System.currentTimeMillis()
            val source =
                FeedSource(
                    id = id,
                    url = url,
                    title = preview.title.take(500),
                    siteUrl = FeedRules.safeUrl(preview.siteUrl).orEmpty(),
                    folderId = folderId?.takeIf { id -> before.folders.any { it.id == id } },
                    lastCheckedMillis = now,
                    lastSuccessMillis = now,
                )
            FeedRules.merge(
                before.copy(sources = before.sources + source),
                source,
                preview.articles,
                now,
            )
        }

    suspend fun refresh(sourceId: String? = null): Result<Unit> = outcome {
        check(!isPrivate) { "Private sessions cannot refresh subscriptions" }
        load().getOrThrow()
        if (!refreshMutex.tryLock()) return@outcome
        try {
            mutableState.value = mutableState.value.copy(refreshing = true, error = null)
            val sources = state.value.sources.filter { sourceId == null || it.id == sourceId }
            sources.forEach { source ->
                requireAccess()
                val result = preview(source.url)
                val now = System.currentTimeMillis()
                mutate { before ->
                        val current =
                            before.sources.firstOrNull { it.id == source.id }
                                ?: return@mutate before
                        val updated =
                            current.copy(
                                lastCheckedMillis = now,
                                lastSuccessMillis =
                                    if (result.isSuccess) now else current.lastSuccessMillis,
                                error = result.exceptionOrNull()?.message?.take(500),
                            )
                        val after =
                            before.copy(
                                sources =
                                    before.sources.map { if (it.id == source.id) updated else it }
                            )
                        result.getOrNull()?.let {
                            FeedRules.merge(after, updated, it.articles, now)
                        } ?: after
                    }
                    .getOrThrow()
            }
        } finally {
            mutableState.value = mutableState.value.copy(refreshing = false)
            refreshMutex.unlock()
        }
    }

    suspend fun updateSource(source: FeedSource): Result<Unit> = mutate { before ->
        val url = requireNotNull(FeedRules.safeUrl(source.url)) { "Invalid feed URL" }
        require(source.id == FeedRules.identity(url))
        before.copy(
            sources =
                before.sources.map {
                    if (it.id == source.id) source.copy(url = url, title = source.title.take(500))
                    else it
                }
        )
    }

    suspend fun removeSource(id: String): Result<Unit> = mutate { before ->
        before.copy(
            sources = before.sources.filterNot { it.id == id },
            articles =
                before.articles
                    .map { article -> article.copy(sourceIds = article.sourceIds - id) }
                    .filter {
                        it.origin == FeedArticleOrigin.Website ||
                            it.sourceIds.isNotEmpty() ||
                            it.isStarred ||
                            it.offlineDocument != null ||
                            it.offlineDocumentId != null
                    },
        )
    }

    suspend fun saveFolder(folder: FeedFolder): Result<Unit> = mutate { before ->
        require(folder.id.length in 1..128 && folder.name.isNotBlank())
        require(before.folders.size < 500 || before.folders.any { it.id == folder.id })
        before.copy(
            folders =
                before.folders.filterNot { it.id == folder.id } +
                    folder.copy(name = folder.name.trim().take(100))
        )
    }

    suspend fun removeFolder(id: String): Result<Unit> = mutate { before ->
        before.copy(
            folders = before.folders.filterNot { it.id == id },
            sources = before.sources.map { if (it.folderId == id) it.copy(folderId = null) else it },
        )
    }

    suspend fun setRead(ids: Set<String>, read: Boolean): Result<Unit> = mutate { before ->
        before.copy(
            articles = before.articles.map { if (it.id in ids) it.copy(isRead = read) else it }
        )
    }

    suspend fun updateProgress(id: String, progress: Float): Result<Unit> =
        updateArticle(id) {
            it.copy(readingProgress = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f)
        }

    suspend fun setStarred(id: String, starred: Boolean): Result<Unit> =
        updateArticle(id) { it.copy(isStarred = starred) }

    /** Explicit Reader action: retain the full document and apply only the requested marker. */
    suspend fun saveWebsiteArticle(
        document: ReaderDocument,
        starred: Boolean? = null,
        queued: Boolean? = null,
    ): Result<Unit> = mutate { before ->
        val url = requireNotNull(FeedRules.safeUrl(document.sourceUrl))
        val id = FeedRules.identity(url)
        val existing = before.articles.firstOrNull { it.id == id }
        require(existing != null || before.articles.size < 20_000)
        require(queued != true || id in before.queue || before.queue.size < 500)
        val article =
            (existing
                    ?: FeedArticle(
                        id = id,
                        sourceIds = emptySet(),
                        url = url,
                        title = document.title.take(1_000),
                        origin = FeedArticleOrigin.Website,
                        content =
                            document.blocks
                                .joinToString("\n\n") { it.text }
                                .take(FeedRules.MAX_TEXT_CHARS),
                        receivedAtMillis = System.currentTimeMillis(),
                    ))
                .copy(
                    origin = FeedArticleOrigin.Website,
                    isStarred = starred ?: existing?.isStarred ?: false,
                    offlineStatus = FeedOfflineStatus.Ready,
                    offlineDocument = document,
                    offlineContentVersion = 1,
                )
        before.copy(
            articles = before.articles.filterNot { it.id == id } + article,
            queue =
                when (queued) {
                    true -> (before.queue + id).distinct()
                    false -> before.queue - id
                    null -> before.queue
                },
        )
    }

    /**
     * Tombstones prevent pending legacy imports from resurrecting an explicitly deleted article.
     */
    suspend fun removeWebsiteArticle(id: String): Result<Unit> = mutate { before ->
        val article = before.articles.firstOrNull { it.id == id } ?: return@mutate before
        require(article.origin == FeedArticleOrigin.Website)
        // The Reader copy may not have finished importing when the user deletes this article.
        val earlierCopies =
            ReaderLibraryStore(appContext)
                .load(isPrivate = false)
                .snapshots
                .filter {
                    !it.isPrivate &&
                        it.sourceProfileId == profileId &&
                        FeedRules.identity(it.document.sourceUrl) == id
                }
                .map { it.id }
        val deleted =
            before.deletedWebsiteSnapshotIds +
                earlierCopies +
                listOfNotNull(article.websiteSnapshotId)
        require(deleted.size <= 20_000) { "Deleted snapshot limit reached" }
        before.copy(
            articles =
                before.articles.mapNotNull {
                    when {
                        it.id != id -> it
                        it.sourceIds.isEmpty() -> null
                        else ->
                            it.copy(
                                origin = FeedArticleOrigin.Feed,
                                websiteSnapshotId = null,
                                isStarred = false,
                                offlineStatus = FeedOfflineStatus.None,
                                offlineDocument = null,
                                offlineDocumentId = null,
                            )
                    }
                },
            queue = before.queue - id,
            deletedWebsiteSnapshotIds = deleted,
        )
    }

    suspend fun setHidden(id: String, hidden: Boolean): Result<Unit> =
        updateArticle(id) { it.copy(isHidden = hidden) }

    suspend fun setTags(id: String, tags: Set<String>): Result<Unit> =
        updateArticle(id) {
            it.copy(
                tags =
                    tags
                        .map { tag -> tag.trim().take(80) }
                        .filter(String::isNotBlank)
                        .take(50)
                        .toSet()
            )
        }

    suspend fun saveSearch(search: FeedSearch): Result<Unit> = mutate { before ->
        require(search.id.length in 1..128 && search.name.isNotBlank())
        require(before.searches.size < 100 || before.searches.any { it.id == search.id })
        val bounded = search.copy(name = search.name.take(100), query = search.query.take(500))
        before.copy(searches = before.searches.filterNot { it.id == search.id } + bounded)
    }

    suspend fun removeSearch(id: String): Result<Unit> = mutate { before ->
        before.copy(searches = before.searches.filterNot { it.id == id })
    }

    suspend fun saveRule(rule: FeedRule): Result<Unit> = mutate { before ->
        require(rule.id.length in 1..128 && rule.name.isNotBlank())
        require(before.rules.size < 100 || before.rules.any { it.id == rule.id })
        require(rule.action != FeedRuleAction.Tag || rule.tag.isNotBlank()) { "Tag required" }
        val bounded =
            rule.copy(
                name = rule.name.take(100),
                query = rule.query.take(500),
                tag = rule.tag.trim().take(80),
            )
        before.copy(rules = before.rules.filterNot { it.id == rule.id } + bounded)
    }

    suspend fun removeRule(id: String): Result<Unit> = mutate { before ->
        before.copy(rules = before.rules.filterNot { it.id == id })
    }

    suspend fun updateSettings(settings: FeedSettings): Result<Unit> = mutate {
        it.copy(
            settings =
                settings.copy(
                    offlineBudgetMb = settings.offlineBudgetMb.coerceIn(10, 250),
                    digestHour = settings.digestHour.coerceIn(0, 23),
                    quietStartHour = settings.quietStartHour.coerceIn(0, 23),
                    quietEndHour = settings.quietEndHour.coerceIn(0, 23),
                )
        )
    }

    suspend fun setQueue(ids: List<String>): Result<Unit> = mutate { before ->
        before.copy(
            queue = ids.distinct().filter { id -> before.articles.any { it.id == id } }.take(500)
        )
    }

    suspend fun setQueued(id: String, queued: Boolean): Result<Unit> = mutate { before ->
        if (queued) {
            require(before.articles.any { it.id == id })
            require(id in before.queue || before.queue.size < 500)
            before.copy(queue = (before.queue + id).distinct())
        } else {
            before.copy(queue = before.queue - id)
        }
    }

    suspend fun completeQueuedArticle(id: String): Result<Unit> = mutate { before ->
        if (before.queue.firstOrNull() != id) return@mutate before
        before.copy(
            articles =
                before.articles.map {
                    if (it.id == id) it.copy(isRead = true, readingProgress = 1f) else it
                },
            queue = before.queue.drop(1),
        )
    }

    /** Copy only explicitly owned snapshots; the original Reader record is never removed. */
    suspend fun importWebsiteSnapshot(snapshot: ReaderSnapshot): Result<Unit> = mutate { before ->
        require(!snapshot.isPrivate && snapshot.sourceProfileId == profileId) {
            "Snapshot does not belong to this profile"
        }
        if (snapshot.id in before.deletedWebsiteSnapshotIds) return@mutate before
        val url =
            requireNotNull(FeedRules.safeUrl(snapshot.document.sourceUrl)) { "Invalid article URL" }
        val id = FeedRules.identity(url)
        val existing = before.articles.firstOrNull { it.id == id }
        require(snapshot.id.isNotBlank() && snapshot.id.length <= 256) {
            "Invalid snapshot identity"
        }
        if (existing?.origin == FeedArticleOrigin.Website) {
            if (existing.websiteSnapshotId == snapshot.id) return@mutate before
            val updated =
                if (existing.websiteSnapshotId == null) {
                    // Adopt legacy provenance without restoring an explicitly removed download.
                    existing.copy(websiteSnapshotId = snapshot.id)
                } else {
                    val content =
                        snapshot.document.blocks
                            .joinToString("\n\n") { it.text }
                            .take(FeedRules.MAX_TEXT_CHARS)
                    val title = snapshot.document.title.take(1_000)
                    existing.copy(
                        websiteSnapshotId = snapshot.id,
                        title = title,
                        content = content,
                        updatedAtMillis = snapshot.savedAtMillis,
                        previousContent =
                            if (content != existing.content || title != existing.title) {
                                existing.content
                            } else existing.previousContent,
                        readingProgress =
                            snapshot.progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
                        offlineStatus = FeedOfflineStatus.Ready,
                        offlineDocument = snapshot.document,
                        offlineContentVersion = 1,
                    )
                }
            return@mutate before.copy(
                articles = before.articles.map { if (it.id == id) updated else it }
            )
        }
        if (existing != null) {
            val joined =
                existing.copy(
                    origin = FeedArticleOrigin.Website,
                    websiteSnapshotId = snapshot.id,
                    offlineDocument = snapshot.document,
                    offlineContentVersion = 1,
                    offlineStatus = FeedOfflineStatus.Ready,
                )
            return@mutate before.copy(
                articles = before.articles.map { if (it.id == id) joined else it }
            )
        }
        require(before.articles.size < 20_000) { "Library article limit reached" }
        val article =
            FeedArticle(
                id = id,
                sourceIds = emptySet(),
                url = url,
                title = snapshot.document.title.take(1_000),
                origin = FeedArticleOrigin.Website,
                websiteSnapshotId = snapshot.id,
                content =
                    snapshot.document.blocks
                        .joinToString("\n\n") { it.text }
                        .take(FeedRules.MAX_TEXT_CHARS),
                publishedAtMillis = snapshot.savedAtMillis,
                receivedAtMillis = snapshot.savedAtMillis,
                readingProgress = snapshot.progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
                offlineStatus = FeedOfflineStatus.Ready,
                offlineDocument = snapshot.document,
                offlineContentVersion = 1,
            )
        before.copy(articles = before.articles + article)
    }

    suspend fun updateOffline(
        id: String,
        status: FeedOfflineStatus,
        document: ReaderDocument? = null,
    ): Result<Unit> = mutate { before ->
        val article = before.articles.firstOrNull { it.id == id } ?: return@mutate before
        require(status != FeedOfflineStatus.Ready || document != null) {
            "Offline document missing"
        }
        require(document == null || FeedRules.identity(document.sourceUrl) == article.id) {
            "Article identity mismatch"
        }
        // A failed replacement must not discard a previously committed offline copy.
        if (
            article.offlineDocumentId != null &&
                status != FeedOfflineStatus.None &&
                document == null
        ) {
            return@mutate before
        }
        val updated =
            article.copy(
                offlineStatus = status,
                offlineContentVersion = if (status == FeedOfflineStatus.Ready) 1 else 0,
                offlineDocument = document,
                offlineDocumentId =
                    article.offlineDocumentId.takeUnless { status == FeedOfflineStatus.None },
            )
        before.copy(articles = before.articles.map { if (it.id == id) updated else it })
    }

    suspend fun loadOfflineDocument(id: String): Result<ReaderDocument?> = outcome {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                requireAccess()
                if (isPrivate) return@withLock null
                val loaded = mutableState.value.takeIf { it.loaded } ?: store.read()
                val article = loaded.articles.firstOrNull { it.id == id } ?: return@withLock null
                val document = store.loadDocument(article)
                requireAccess()
                document
            }
        }
    }

    fun previewOpml(xml: String): Result<FeedImportPreview> = runCatching {
        FeedOpml.preview(xml, state.value.sources)
    }

    suspend fun exportOpml(): String {
        requireAccess()
        return FeedOpml.export(state.value)
    }

    suspend fun importOpml(preview: FeedImportPreview): Result<Unit> = mutate { before ->
        val sources = before.sources.toMutableList()
        val folders = before.folders.toMutableList()
        preview.entries
            .filter { it.status == FeedImportStatus.New }
            .forEach { entry ->
                val url = FeedRules.safeUrl(entry.url) ?: return@forEach
                val id = FeedRules.identity(url)
                if (sources.any { it.id == id }) return@forEach
                require(sources.size < FeedRules.MAX_SOURCES) { "Source limit reached" }
                val folder =
                    entry.folder?.take(100)?.let { name ->
                        folders.firstOrNull { it.name == name }
                            ?: FeedFolder(UUID.randomUUID().toString(), name).also(folders::add)
                    }
                sources.add(FeedSource(id, url, entry.title.take(500), folderId = folder?.id))
            }
        before.copy(sources = sources, folders = folders)
    }

    private suspend fun updateArticle(
        id: String,
        transform: (FeedArticle) -> FeedArticle,
    ): Result<Unit> = mutate { before ->
        before.copy(articles = before.articles.map { if (it.id == id) transform(it) else it })
    }

    private suspend fun mutate(transform: (FeedState) -> FeedState): Result<Unit> = outcome {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                check(!isPrivate) { "Private sessions cannot save feed data" }
                requireAccess()
                val before = mutableState.value.takeIf { it.loaded } ?: store.read()
                val after = transform(before).copy(error = null)
                requireAccess()
                val committed = if (after != before) store.write(after, before) else before
                requireAccess()
                mutableState.value = committed
            }
        }
    }

    internal suspend fun requireAccess() =
        withContext(Dispatchers.IO) {
            val accessible =
                !AppDataTransferLock.isActive(appContext) &&
                    ProfileProtectionSession.isAccessible(
                        profiles.loadProfiles().first.firstOrNull { it.id == profileId }
                    )
            if (!accessible) {
                mutableState.value = FeedState(error = "Profile unavailable or locked")
                error("Profile unavailable or locked")
            }
        }

    private suspend fun <T> outcome(action: suspend () -> T): Result<T> =
        try {
            Result.success(action())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(error = error.message?.take(500))
            Result.failure(error)
        }

    companion object {
        private val repositories = mutableMapOf<String, FeedRepository>()

        /** Clear observable data immediately when the browser locks a profile. */
        @Synchronized
        fun invalidate(profileIds: Collection<String>) {
            profileIds.forEach { repositories[it]?.mutableState?.value = FeedState() }
        }

        suspend fun deleteProfile(context: Context, profileId: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                val repository = synchronized(this@Companion) { repositories[profileId] }
                try {
                    if (repository == null) {
                        FeedStore(context.applicationContext, profileId).delete()
                    } else {
                        repository.mutex.withLock {
                            repository.mutableState.value = FeedState()
                            repository.store.delete()
                        }
                    }
                    synchronized(this@Companion) { repositories.remove(profileId) }
                    Result.success(Unit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }

        @Synchronized
        fun get(context: Context, profileId: String, isPrivate: Boolean = false): FeedRepository {
            require(profileId.isNotBlank() && profileId.length <= 128)
            return if (isPrivate) FeedRepository(context.applicationContext, profileId, true)
            else
                repositories.getOrPut(profileId) {
                    FeedRepository(context.applicationContext, profileId, false)
                }
        }
    }
}
