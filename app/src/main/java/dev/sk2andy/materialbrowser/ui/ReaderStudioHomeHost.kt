package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sk2andy.materialbrowser.data.RssFeedStore
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import dev.sk2andy.materialbrowser.reader.rss.RssArticle
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssInboxRepository
import dev.sk2andy.materialbrowser.reader.rss.RssInboxState
import dev.sk2andy.materialbrowser.reader.rss.RssRefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ReaderStudioHomeHost(
    repository: ReaderLibraryRepository,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onOpenSnapshot: (ReaderSnapshot) -> Unit,
    onOpenArticle: (RssFeed, RssArticle) -> Unit,
    onManageFeeds: () -> Unit,
    initialTab: ReaderStudioHomeTab = ReaderStudioHomeTab.Library,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inboxRepository = remember(context) { RssInboxRepository.get(context) }
    val backState = rememberNativeScreenBackState()
    var library by remember { mutableStateOf(ReaderLibraryState()) }
    var inbox by remember { mutableStateOf(RssInboxState()) }
    var libraryLoaded by remember { mutableStateOf(false) }
    var inboxLoaded by remember { mutableStateOf(false) }
    var resumeGeneration by remember { mutableIntStateOf(0) }
    val feedGeneration by RssFeedStore.changes.collectAsState()
    var attached by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { attached = false }
    }
    LaunchedEffect(inboxRepository) {
        withContext(Dispatchers.IO) {
            RssRefreshScheduler.schedule(context)
            RssRefreshScheduler.refreshNow(context)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeGeneration++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(repository, inboxRepository, isActive, resumeGeneration, feedGeneration) {
        var active = isActive
        if (active) {
            repository.load(isPrivate = false) { loaded ->
                if (active) {
                    library = loaded
                    libraryLoaded = true
                }
            }
            inboxRepository.load { loaded ->
                if (active) {
                    inbox = loaded
                    inboxLoaded = true
                }
            }
        }
        onDispose { active = false }
    }
    NativeScreenBackHandler(state = backState, enabled = isActive, onBack = onDismiss)
    Box(
        modifier = Modifier.zIndex(20f).predictiveBackSurface(
            progress = backState.progress.value,
            swipeEdgeSign = backState.swipeEdgeSign,
        ).then(if (isActive) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        ReaderStudioHomeScreen(
            library = library,
            feeds = inbox.feeds,
            readArticleKeys = inbox.readArticleKeys,
            isLoading = !libraryLoaded || !inboxLoaded,
            onDismiss = onDismiss,
            onOpenSnapshot = { snapshot ->
                onOpenSnapshot(
                    snapshot.copy(progress = library.progressByUrl[snapshot.document.sourceUrl] ?: snapshot.progress),
                )
            },
            onDeleteSnapshot = { snapshot ->
                repository.deleteSnapshot(snapshot.id, isPrivate = false) { updated ->
                    if (attached) library = updated
                }
            },
            onOpenArticle = onOpenArticle,
            onManageFeeds = onManageFeeds,
            initialTab = initialTab,
        )
    }
}
