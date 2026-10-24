package dev.sk2andy.materialbrowser.ui.feeds

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedArticleLoader
import dev.sk2andy.materialbrowser.feeds.FeedArticleOrigin
import dev.sk2andy.materialbrowser.feeds.FeedImportPreview
import dev.sk2andy.materialbrowser.feeds.FeedInboxFilter
import dev.sk2andy.materialbrowser.feeds.FeedOfflineStatus
import dev.sk2andy.materialbrowser.feeds.FeedReaderDataSource
import dev.sk2andy.materialbrowser.feeds.FeedReadingMode
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedRules
import dev.sk2andy.materialbrowser.feeds.FeedWorkScheduler
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.ui.ReaderStudioScreen
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class FeedsEntry(
    val libraryOnly: Boolean = false,
    val discoveryUrl: String? = null,
    val queueRequestId: Int = 0,
    val openCatalog: Boolean = false,
    val invocationToken: String = UUID.randomUUID().toString(),
)

/** Owns platform launchers and Reader transitions without creating browser history entries. */
@Composable
internal fun FeedsHost(
    repository: FeedRepository,
    entry: FeedsEntry,
    progressScope: CoroutineScope,
    onOpenOriginal: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by repository.state.collectAsState()
    val reader = remember(context) { ReaderLibraryRepository.get(context) }
    val loader = remember(repository) { FeedArticleLoader(context, repository) }
    var library by remember { mutableStateOf(ReaderLibraryState()) }
    var document by remember { mutableStateOf<ReaderDocument?>(null) }
    var articleId by remember { mutableStateOf<String?>(null) }
    val feedReader =
        remember(repository, reader, scope, progressScope, articleId) {
            articleId?.let {
                FeedReaderDataSource(
                    repository,
                    reader.forProfile(repository.profileId),
                    scope,
                    it,
                    progressScope,
                )
            }
        }
    var importPreview by remember { mutableStateOf<FeedImportPreview?>(null) }
    val launchState = rememberFeedLaunchState(repository.profileId, repository.isPrivate)
    LaunchedEffect(repository, entry.invocationToken, entry.queueRequestId) {
        if (!repository.isPrivate)
            launchState.acceptQueueRequest(entry.invocationToken, entry.queueRequestId)
    }
    var loadingArticle by remember { mutableStateOf(false) }
    var failedArticle by remember { mutableStateOf<FeedArticle?>(null) }
    var libraryRequest by remember { mutableStateOf(0) }
    val errorText = stringResource(R.string.feeds_error)
    fun showFailure() {
        Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
    }
    DisposableEffect(reader, repository, document) {
        var active = true
        reader.load(repository.isPrivate) { if (active) library = it }
        onDispose { active = false }
    }
    LaunchedEffect(repository) {
        repository.load()
        if (repository.state.value.articles.any { it.offlineStatus == FeedOfflineStatus.Waiting }) {
            FeedWorkScheduler.enqueueDownloads(context, repository.profileId)
        }
        if (!repository.isPrivate && repository.state.value.settings.refreshOnOpen)
            repository.refresh()
    }
    LaunchedEffect(repository, library.snapshots) {
        if (!repository.isPrivate)
            library.snapshots
                .filter { it.sourceProfileId == repository.profileId && !it.isPrivate }
                .forEach { repository.importWebsiteSnapshot(it) }
    }
    LaunchedEffect(
        repository,
        library.snapshots,
        state.articles.map { Triple(it.id, it.offlineStatus, it.websiteSnapshotId) },
        state.deletedWebsiteSnapshotIds,
    ) {
        // Removing a website download also removes its earlier Reader copy.
        val removed =
            state.articles
                .filter {
                    it.origin == FeedArticleOrigin.Website &&
                        it.offlineStatus == FeedOfflineStatus.None
                }
                .associate { it.id to it.websiteSnapshotId }
        library.snapshots
            .filter {
                it.sourceProfileId == repository.profileId &&
                    (removed[FeedRules.identity(it.document.sourceUrl)] == it.id ||
                        it.id in state.deletedWebsiteSnapshotIds)
            }
            .forEach { snapshot -> reader.deleteSnapshot(snapshot.id, false) { library = it } }
    }
    LaunchedEffect(
        repository,
        state.settings,
        state.sources.map { it.notificationsEnabled },
        state.rules,
    ) {
        if (!repository.isPrivate && state.loaded)
            FeedWorkScheduler.update(context, repository.profileId, state)
    }
    LaunchedEffect(repository, entry.invocationToken, entry.discoveryUrl) {
        entry.discoveryUrl
            ?.takeIf { launchState.needsDiscovery(entry.invocationToken, it) }
            ?.let { url ->
                repository
                    .discover(url)
                    .onSuccess { candidates ->
                        launchState.completeDiscovery(
                            entry.invocationToken,
                            url,
                            candidates.map { it.url },
                        )
                    }
                    .onFailure {
                        launchState.completeDiscovery(entry.invocationToken, url, emptyList())
                        showFailure()
                    }
            }
    }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bytes =
                                    context.contentResolver.openInputStream(uri)?.use {
                                        it.readNBytes(FeedRules.MAX_FEED_BYTES + 1)
                                    } ?: error("Cannot open document")
                                require(bytes.size <= FeedRules.MAX_FEED_BYTES)
                                repository.previewOpml(bytes.toString(Charsets.UTF_8)).getOrThrow()
                            }
                        }
                    result.onSuccess { importPreview = it }.onFailure { showFailure() }
                }
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) {
            uri ->
            if (uri != null)
                scope.launch {
                    val result =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val xml = repository.exportOpml()
                                context.contentResolver.openOutputStream(uri, "wt")?.use {
                                    it.write(xml.toByteArray())
                                } ?: error("Cannot write document")
                            }
                        }
                    if (result.isFailure) showFailure()
                }
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted)
                Toast.makeText(context, R.string.feeds_notifications_denied, Toast.LENGTH_LONG)
                    .show()
        }
    fun openArticle(article: FeedArticle) {
        if (loadingArticle) return
        val source = state.sources.firstOrNull { it.id in article.sourceIds }
        if (
            source?.readingMode == FeedReadingMode.Original &&
                article.offlineStatus != FeedOfflineStatus.Ready
        ) {
            scope.launch {
                if (repository.setRead(setOf(article.id), true).isFailure) showFailure()
                onOpenOriginal(article.url)
            }
        } else
            scope.launch {
                loadingArticle = true
                try {
                    val result = loader.read(article)
                    repository.requireAccess()
                    repository.setRead(setOf(article.id), true)
                    articleId = article.id
                    document = result
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failedArticle = article
                } finally {
                    loadingArticle = false
                }
            }
    }
    Box(Modifier.fillMaxSize()) {
        FeedsScreen(
            repository = repository,
            library =
                library.copy(
                    snapshots =
                        library.snapshots.filter { snapshot ->
                            snapshot.sourceProfileId == repository.profileId &&
                                !snapshot.isPrivate &&
                                snapshot.id !in state.deletedWebsiteSnapshotIds &&
                                state.articles.none {
                                    it.origin == FeedArticleOrigin.Website &&
                                        it.id == FeedRules.identity(snapshot.document.sourceUrl) &&
                                        it.websiteSnapshotId == snapshot.id
                                }
                        }
                ),
            legacySnapshots =
                library.snapshots.filter { it.sourceProfileId == null && !it.isPrivate },
            libraryOnly = entry.libraryOnly,
            initialCatalog = entry.openCatalog,
            catalogRequestToken = entry.invocationToken,
            initialFeedUrl = launchState.selectedFeedUrl,
            onInitialFeedConsumed = launchState::consumeSelectedFeed,
            libraryRequest = libraryRequest,
            onOpenArticle = ::openArticle,
            onOpenSnapshot = {
                articleId = null
                document = it.document
            },
            onOpenOriginal = onOpenOriginal,
            onDownloadArticle = { article ->
                scope.launch {
                    if (loader.download(article).isFailure) showFailure()
                    FeedWorkScheduler.enqueueDownloads(context, repository.profileId)
                }
            },
            onRemoveSnapshot = { snapshot ->
                reader.deleteSnapshot(snapshot.id, false) { library = it }
            },
            onPlayQueue = { if (!repository.isPrivate) launchState.queueVisible = true },
            onImportOpml = {
                importLauncher.launch(
                    arrayOf("text/*", "application/xml", "application/octet-stream")
                )
            },
            onExportOpml = { exportLauncher.launch("candy-feeds.opml") },
            onNotificationPermission = {
                if (Build.VERSION.SDK_INT >= 33)
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = onDismiss,
        )
        document
            ?.takeUnless { launchState.queueVisible }
            ?.let { opened ->
                ReaderStudioScreen(
                    result = ReaderExtractionResult.Success(opened),
                    sourceUrl = opened.sourceUrl,
                    isPrivate = repository.isPrivate,
                    repository = reader,
                    sourceProfileId = repository.profileId,
                    libraryDataSource = feedReader.takeIf { articleId != null },
                    onRetry = {},
                    onDismiss = { document = null },
                    onOpenOriginal = onOpenOriginal,
                    onOpenLink = onOpenOriginal,
                    onOpenLibrary = {
                        document = null
                        libraryRequest++
                    },
                    extraActions = {
                        state.articles
                            .firstOrNull { it.id == articleId }
                            ?.let { current ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FeedArticleToggle(
                                        checked = current.isStarred,
                                        onToggle = {
                                            scope.launch {
                                                if (
                                                    repository
                                                        .setStarred(current.id, !current.isStarred)
                                                        .isFailure
                                                )
                                                    showFailure()
                                            }
                                        },
                                        icon =
                                            if (current.isStarred) R.drawable.ic_feed_bookmark
                                            else R.drawable.ic_feed_bookmark_border,
                                        description =
                                            stringResource(
                                                if (current.isStarred) R.string.feeds_unsave
                                                else R.string.feeds_save
                                            ),
                                        modifier = Modifier.testTag("feed_reader_bookmark"),
                                    )
                                    FeedArticleToggle(
                                        checked = current.id in state.queue,
                                        onToggle = {
                                            scope.launch {
                                                if (
                                                    repository
                                                        .setQueued(
                                                            current.id,
                                                            current.id !in state.queue,
                                                        )
                                                        .isFailure
                                                )
                                                    showFailure()
                                            }
                                        },
                                        icon =
                                            if (current.id in state.queue)
                                                R.drawable.ic_feed_queue_added
                                            else R.drawable.ic_feed_queue,
                                        description =
                                            stringResource(
                                                if (current.id in state.queue)
                                                    R.string.feeds_queue_remove
                                                else R.string.feeds_queue_add
                                            ),
                                        modifier = Modifier.testTag("feed_reader_queue"),
                                    )
                                    TextButton(
                                        onClick = { launchState.queueVisible = true },
                                        modifier = Modifier.testTag("feed_reader_open_queue"),
                                    ) {
                                        Text(stringResource(R.string.feeds_queue))
                                    }
                                }
                            }
                        val next =
                            FeedRules.filter(state, FeedInboxFilter.Unread).firstOrNull {
                                it.id != articleId
                            }
                        if (articleId != null && next != null)
                            TextButton(onClick = { openArticle(next) }) {
                                Text(stringResource(R.string.feeds_next_unread))
                            }
                    },
                )
            }
        if (loadingArticle) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (launchState.queueVisible)
            FeedQueueSheet(repository, onDismiss = { launchState.queueVisible = false })
    }
    failedArticle?.let { article ->
        AlertDialog(
            onDismissRequest = { failedArticle = null },
            title = { Text(stringResource(R.string.feeds_full_article_unavailable)) },
            text = { Text(stringResource(R.string.feeds_full_article_unavailable_summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        failedArticle = null
                        onOpenOriginal(article.url)
                    }
                ) {
                    Text(stringResource(R.string.feeds_original))
                }
            },
            dismissButton = {
                TextButton(onClick = { failedArticle = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    importPreview?.let { preview ->
        FeedImportDialog(
            preview,
            onConfirm = {
                scope.launch {
                    if (repository.importOpml(preview).isFailure) showFailure()
                    importPreview = null
                }
            },
            onDismiss = { importPreview = null },
        )
    }
    if (launchState.discoveryCandidates.isNotEmpty())
        AlertDialog(
            onDismissRequest = launchState::dismissCandidates,
            title = { Text(stringResource(R.string.feeds_sources)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    launchState.discoveryCandidates.forEach { url ->
                        TextButton(onClick = { launchState.selectCandidate(url) }) { Text(url) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = launchState::dismissCandidates) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
}
