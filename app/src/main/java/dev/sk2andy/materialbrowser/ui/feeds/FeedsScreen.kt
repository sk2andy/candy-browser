package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedArticleOrigin
import dev.sk2andy.materialbrowser.feeds.FeedInboxFilter
import dev.sk2andy.materialbrowser.feeds.FeedOfflineStatus
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedRules
import dev.sk2andy.materialbrowser.feeds.FeedSearch
import dev.sk2andy.materialbrowser.feeds.FeedSimilarityRules
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import dev.sk2andy.materialbrowser.ui.LibrarySearchBar
import dev.sk2andy.materialbrowser.ui.NativeScreenBackHandler
import dev.sk2andy.materialbrowser.ui.predictiveBackSurface
import dev.sk2andy.materialbrowser.ui.rememberNativeScreenBackState
import kotlinx.coroutines.launch

internal enum class FeedDestination(val label: Int) {
    Inbox(R.string.feeds_inbox),
    Library(R.string.feeds_library),
    Sources(R.string.feeds_sources),
    Tools(R.string.feeds_tools),
}

internal typealias FeedMutation = (suspend () -> Result<Unit>) -> Unit

@Composable
internal fun FeedsScreen(
    repository: FeedRepository,
    library: ReaderLibraryState,
    libraryOnly: Boolean = false,
    initialFeedUrl: String? = null,
    libraryRequest: Int = 0,
    legacySnapshots: List<ReaderSnapshot> = emptyList(),
    onOpenArticle: (FeedArticle) -> Unit,
    onOpenSnapshot: (ReaderSnapshot) -> Unit,
    onOpenOriginal: (String) -> Unit,
    onDownloadArticle: (FeedArticle) -> Unit,
    onRemoveSnapshot: (ReaderSnapshot) -> Unit,
    onPlayQueue: () -> Unit,
    onImportOpml: () -> Unit,
    onExportOpml: () -> Unit,
    onNotificationPermission: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by repository.state.collectAsState()
    var destination by
        rememberSaveable(repository, libraryOnly) {
            mutableStateOf(if (libraryOnly) FeedDestination.Library else FeedDestination.Inbox)
        }
    var filter by rememberSaveable { mutableStateOf(FeedInboxFilter.Unread) }
    var query by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryFilter by rememberSaveable { mutableStateOf("all") }
    var origin by rememberSaveable { mutableStateOf("all") }
    var legacyExpanded by rememberSaveable { mutableStateOf(false) }
    var addSource by rememberSaveable { mutableStateOf(initialFeedUrl != null) }
    LaunchedEffect(initialFeedUrl) { if (initialFeedUrl != null) addSource = true }
    LaunchedEffect(libraryRequest) { if (libraryRequest > 0) destination = FeedDestination.Library }
    var articleActions by remember { mutableStateOf<FeedArticle?>(null) }
    var searchEditor by remember { mutableStateOf<FeedSearch?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val failure = stringResource(R.string.feeds_error)
    val markedRead = stringResource(R.string.feeds_marked_read)
    val undo = stringResource(R.string.feeds_undo)
    val mutate: FeedMutation = { operation ->
        scope.launch { if (operation().isFailure) snackbar.showSnackbar(failure) }
    }
    val back = rememberNativeScreenBackState()
    NativeScreenBackHandler(back, onBack = onDismiss)
    val articles =
        remember(state, filter, query, sourceId, folderId, tag) {
            FeedRules.filter(state, filter, query, sourceId, folderId, tag)
        }
    val articleGroups = remember(articles) { FeedSimilarityRules.groups(articles) }
    val inboxListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val openSearch: (FeedSearch) -> Unit = { search ->
        query = search.query
        sourceId = search.sourceId
        folderId = search.folderId
        tag = search.tag
        filter = if (search.unreadOnly) FeedInboxFilter.Unread else FeedInboxFilter.All
        destination = FeedDestination.Inbox
    }
    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .testTag("feeds_screen")
                .predictiveBackSurface(back.progress.value, back.swipeEdgeSign),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            val useRail = !libraryOnly && maxWidth >= 840.dp
            Row(Modifier.fillMaxSize()) {
                if (useRail) {
                    NavigationRail {
                        FeedDestination.entries.forEach { item ->
                            NavigationRailItem(
                                selected = destination == item,
                                onClick = { destination = item },
                                icon = { DestinationIcon(item, state.unreadCount) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.action_back),
                            )
                        }
                        Text(
                            stringResource(destination.label),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = onPlayQueue,
                            modifier = Modifier.testTag("feeds_open_queue"),
                        ) {
                            BadgedBox(
                                badge = {
                                    if (state.queue.isNotEmpty())
                                        Badge { Text(state.queue.size.toString()) }
                                }
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_feed_queue),
                                    stringResource(R.string.feeds_queue),
                                )
                            }
                        }
                        if (!libraryOnly) {
                            IconButton(
                                onClick = { mutate { repository.refresh() } },
                                enabled = !state.refreshing,
                            ) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.feeds_refresh))
                            }
                            IconButton(onClick = { addSource = true }) {
                                Icon(Icons.Default.Add, stringResource(R.string.feeds_add))
                            }
                        }
                    }
                    if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.error != null)
                        Text(
                            failure,
                            Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    when (destination) {
                        FeedDestination.Inbox ->
                            Column(Modifier.weight(1f)) {
                                FeedSearchField(query, { query = it })
                                val tags =
                                    remember(state.articles) {
                                        state.articles.flatMap { it.tags }.distinct().sorted()
                                    }
                                FeedFilterRow(
                                    filter = filter,
                                    onFilter = {
                                        filter = it
                                        if (it == FeedInboxFilter.All) {
                                            sourceId = null
                                            folderId = null
                                            tag = null
                                        }
                                    },
                                    hasScopeFilter = sourceId != null || folderId != null,
                                    tags = tags,
                                    selectedTag = tag,
                                    onTag = { tag = if (tag == it) null else it },
                                )
                                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                    TextButton(
                                        onClick = {
                                            val ids =
                                                articles
                                                    .filterNot(FeedArticle::isRead)
                                                    .map(FeedArticle::id)
                                                    .toSet()
                                            scope.launch {
                                                if (repository.setRead(ids, true).isFailure)
                                                    snackbar.showSnackbar(failure)
                                                else if (
                                                    snackbar.showSnackbar(markedRead, undo) ==
                                                        SnackbarResult.ActionPerformed
                                                ) {
                                                    if (repository.setRead(ids, false).isFailure)
                                                        snackbar.showSnackbar(failure)
                                                }
                                            }
                                        },
                                        enabled = articles.any { !it.isRead },
                                    ) {
                                        Text(stringResource(R.string.feeds_mark_all))
                                    }
                                    if (query.isNotBlank())
                                        TextButton(
                                            onClick = {
                                                searchEditor =
                                                    FeedSearch(
                                                        java.util.UUID.randomUUID().toString(),
                                                        query,
                                                        query,
                                                        sourceId,
                                                        folderId,
                                                        filter == FeedInboxFilter.Unread,
                                                        tag,
                                                    )
                                            }
                                        ) {
                                            Text(stringResource(R.string.feeds_save_search))
                                        }
                                }
                                LazyColumn(
                                    state = inboxListState,
                                    contentPadding = PaddingValues(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (articles.isEmpty())
                                        item {
                                            FeedEmptyState(
                                                title =
                                                    stringResource(
                                                        if (state.sources.isEmpty())
                                                            R.string.feeds_empty_title
                                                        else R.string.feeds_no_matches
                                                    ),
                                                body =
                                                    if (state.sources.isEmpty())
                                                        stringResource(R.string.feeds_empty_body)
                                                    else "",
                                            )
                                        }
                                    items(articleGroups, key = FeedSimilarityRules.Group::id) {
                                        group ->
                                        var expanded by
                                            rememberSaveable(group.id) { mutableStateOf(false) }
                                        val first = group.articles.first()
                                        FeedArticleRow(
                                            first,
                                            state,
                                            { onOpenArticle(first) },
                                            { articleActions = first },
                                            onToggleSaved = {
                                                mutate {
                                                    repository.setStarred(
                                                        first.id,
                                                        !first.isStarred,
                                                    )
                                                }
                                            },
                                            onToggleQueue = {
                                                mutate {
                                                    repository.setQueued(
                                                        first.id,
                                                        first.id !in state.queue,
                                                    )
                                                }
                                            },
                                        )
                                        if (group.articles.size > 1) {
                                            TextButton(onClick = { expanded = !expanded }) {
                                                Text(
                                                    stringResource(
                                                        R.string.feeds_similar_articles,
                                                        group.articles.size - 1,
                                                    )
                                                )
                                            }
                                            if (expanded)
                                                group.articles.drop(1).forEach { related ->
                                                    FeedArticleRow(
                                                        related,
                                                        state,
                                                        { onOpenArticle(related) },
                                                        { articleActions = related },
                                                        onToggleSaved = {
                                                            mutate {
                                                                repository.setStarred(
                                                                    related.id,
                                                                    !related.isStarred,
                                                                )
                                                            }
                                                        },
                                                        onToggleQueue = {
                                                            mutate {
                                                                repository.setQueued(
                                                                    related.id,
                                                                    related.id !in state.queue,
                                                                )
                                                            }
                                                        },
                                                    )
                                                }
                                        }
                                    }
                                }
                            }
                        FeedDestination.Library ->
                            Column(Modifier.weight(1f)) {
                                FeedLibraryTabs(origin, { origin = it })
                                FeedSearchField(query, { query = it })
                                FeedLibraryFilters(
                                    filter = libraryFilter,
                                    onFilter = { libraryFilter = it },
                                )
                                val feedItems =
                                    state.articles.filter { article ->
                                        (article.origin == FeedArticleOrigin.Website ||
                                            article.isStarred ||
                                            article.offlineStatus != FeedOfflineStatus.None) &&
                                            when (origin) {
                                                "web" -> article.origin == FeedArticleOrigin.Website
                                                "feed" ->
                                                    article.origin == FeedArticleOrigin.Feed ||
                                                        article.sourceIds.isNotEmpty()
                                                else -> true
                                            } &&
                                            article.title.contains(query, true) &&
                                            when (libraryFilter) {
                                                "offline" ->
                                                    article.offlineStatus == FeedOfflineStatus.Ready
                                                "unread" -> !article.isRead
                                                "saved" -> article.isStarred
                                                else -> true
                                            }
                                    }
                                val pendingDownloads =
                                    state.articles.filter {
                                        it.isStarred &&
                                            it.offlineStatus in
                                                setOf(
                                                    FeedOfflineStatus.None,
                                                    FeedOfflineStatus.Failed,
                                                )
                                    }
                                if (pendingDownloads.isNotEmpty()) {
                                    TextButton(
                                        onClick = { pendingDownloads.forEach(onDownloadArticle) },
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                    ) {
                                        Text(stringResource(R.string.feeds_download_saved))
                                    }
                                }
                                val snapshots =
                                    library.snapshots.filter { snapshot ->
                                        origin != "feed" &&
                                            libraryFilter != "saved" &&
                                            (libraryFilter != "unread" || snapshot.progress < 1f) &&
                                            snapshot.document.title.contains(query, true)
                                    }
                                LazyColumn(
                                    state = libraryListState,
                                    contentPadding = PaddingValues(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (snapshots.isEmpty() && feedItems.isEmpty())
                                        item {
                                            FeedEmptyState(
                                                stringResource(R.string.feeds_no_matches)
                                            )
                                        }
                                    items(snapshots, key = { "snapshot:${it.id}" }) { snapshot ->
                                        FeedSnapshotRow(
                                            snapshot,
                                            { onOpenSnapshot(snapshot) },
                                            { onRemoveSnapshot(snapshot) },
                                        )
                                    }
                                    items(feedItems, key = FeedArticle::id) { article ->
                                        FeedArticleRow(
                                            article,
                                            state,
                                            { onOpenArticle(article) },
                                            { articleActions = article },
                                            onToggleSaved = {
                                                mutate {
                                                    repository.setStarred(
                                                        article.id,
                                                        !article.isStarred,
                                                    )
                                                }
                                            },
                                            onToggleQueue = {
                                                mutate {
                                                    repository.setQueued(
                                                        article.id,
                                                        article.id !in state.queue,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                    item(key = "queue-entry") {
                                        FilledTonalButton(
                                            onClick = onPlayQueue,
                                            modifier =
                                                Modifier.fillMaxWidth()
                                                    .testTag("feeds_library_queue"),
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_feed_queue),
                                                contentDescription = null,
                                            )
                                            Text(
                                                stringResource(R.string.feeds_queue),
                                                Modifier.padding(horizontal = 12.dp),
                                            )
                                            if (state.queue.isNotEmpty())
                                                Badge { Text(state.queue.size.toString()) }
                                        }
                                    }
                                    if (legacySnapshots.isNotEmpty() && origin != "feed") {
                                        item {
                                            TextButton(
                                                onClick = { legacyExpanded = !legacyExpanded },
                                                modifier = Modifier.testTag("feeds_legacy_toggle"),
                                            ) {
                                                Text(stringResource(R.string.feeds_legacy))
                                            }
                                            if (legacyExpanded)
                                                Text(
                                                    stringResource(R.string.feeds_legacy_note),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                        }
                                        if (legacyExpanded)
                                            items(legacySnapshots, key = { "legacy:${it.id}" }) {
                                                snapshot ->
                                                FeedSnapshotRow(
                                                    snapshot,
                                                    { onOpenSnapshot(snapshot) },
                                                    { onRemoveSnapshot(snapshot) },
                                                )
                                            }
                                    }
                                }
                            }
                        FeedDestination.Sources ->
                            FeedSourcesScreen(
                                state,
                                repository,
                                mutate,
                                { addSource = true },
                                onOpenSource = {
                                    sourceId = it
                                    folderId = null
                                    filter = FeedInboxFilter.All
                                    destination = FeedDestination.Inbox
                                },
                                onNotificationPermission = onNotificationPermission,
                                modifier = Modifier.weight(1f),
                            )
                        FeedDestination.Tools ->
                            FeedToolsScreen(
                                state,
                                repository,
                                mutate,
                                openSearch,
                                onPlayQueue,
                                onImportOpml,
                                onExportOpml,
                                onNotificationPermission,
                                Modifier.weight(1f),
                            )
                    }
                    SnackbarHost(snackbar)
                    if (!libraryOnly && !useRail) {
                        NavigationBar {
                            FeedDestination.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = destination == item,
                                    onClick = { destination = item },
                                    icon = { DestinationIcon(item, state.unreadCount) },
                                    label = { Text(stringResource(item.label)) },
                                )
                            }
                        }
                    } else Column(Modifier.navigationBarsPadding()) {}
                }
            }
        }
    }
    if (addSource)
        FeedSubscribeDialog(
            repository,
            state,
            initialFeedUrl.orEmpty(),
            mutate,
            { addSource = false },
        )
    articleActions?.let { article ->
        FeedArticleActions(
            article = state.articles.firstOrNull { it.id == article.id } ?: article,
            state = state,
            repository = repository,
            mutate = mutate,
            onOpenOriginal = { onOpenOriginal(article.url) },
            onDownload = { onDownloadArticle(article) },
            onDismiss = { articleActions = null },
        )
    }
    searchEditor?.let { search ->
        FeedSearchEditor(
            search,
            state,
            onSave = {
                mutate { repository.saveSearch(it) }
                searchEditor = null
            },
            onDismiss = { searchEditor = null },
        )
    }
}

@Composable
private fun DestinationIcon(destination: FeedDestination, unread: Int) {
    BadgedBox(
        badge = {
            if (destination == FeedDestination.Inbox && unread > 0)
                Badge { Text(unread.toString()) }
        }
    ) {
        Icon(
            painterResource(
                when (destination) {
                    FeedDestination.Inbox -> R.drawable.ic_rss_feed
                    FeedDestination.Library -> R.drawable.ic_reader_library
                    FeedDestination.Sources -> R.drawable.ic_folder
                    FeedDestination.Tools -> R.drawable.ic_settings
                }
            ),
            contentDescription = null,
        )
    }
}

@Composable
internal fun FeedSearchField(query: String, onQuery: (String) -> Unit) {
    LibrarySearchBar(
        query = query,
        placeholder = stringResource(R.string.feeds_search),
        clearContentDescription = stringResource(R.string.feeds_clear_search),
        testTag = "feeds_search",
        onQueryChange = onQuery,
    )
}

@Composable
internal fun FeedFilterRow(
    filter: FeedInboxFilter,
    onFilter: (FeedInboxFilter) -> Unit,
    hasScopeFilter: Boolean,
    tags: List<String>,
    selectedTag: String?,
    onTag: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .testTag("feeds_inbox_filters")
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedInboxFilter.entries.forEach { item ->
            FilterChip(
                selected =
                    item == filter &&
                        (item != FeedInboxFilter.All || (!hasScopeFilter && selectedTag == null)),
                onClick = { onFilter(item) },
                label = {
                    Text(
                        stringResource(
                            when (item) {
                                FeedInboxFilter.Unread -> R.string.feeds_unread
                                FeedInboxFilter.Today -> R.string.feeds_today
                                FeedInboxFilter.Starred -> R.string.feeds_starred
                                FeedInboxFilter.All -> R.string.feeds_all
                                FeedInboxFilter.Hidden -> R.string.feeds_hidden
                            }
                        )
                    )
                },
            )
        }
        tags.forEach { value ->
            FilterChip(
                selected = value == selectedTag,
                onClick = { onTag(value) },
                label = { Text(value) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun FeedLibraryTabs(origin: String, onOrigin: (String) -> Unit) {
    val tabs =
        listOf(
            "all" to R.string.feeds_all,
            "web" to R.string.feeds_websites,
            "feed" to R.string.feeds_title,
        )
    PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == origin }.coerceAtLeast(0)) {
        tabs.forEach { (key, label) ->
            Tab(
                selected = origin == key,
                onClick = { onOrigin(key) },
                text = { Text(stringResource(label)) },
                modifier = Modifier.testTag("feeds_library_origin_$key"),
            )
        }
    }
}

@Composable
internal fun FeedLibraryFilters(filter: String, onFilter: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .testTag("feeds_library_filters")
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == "all",
            onClick = { onFilter("all") },
            label = { Text(stringResource(R.string.feeds_all)) },
        )
        listOf(
                "offline" to R.string.feeds_offline,
                "unread" to R.string.feeds_unread,
                "saved" to R.string.feeds_starred,
            )
            .forEach { (key, label) ->
                FilterChip(
                    selected = key == filter,
                    onClick = { onFilter(if (filter == key) "all" else key) },
                    label = { Text(stringResource(label)) },
                )
            }
    }
}

@Composable
internal fun FeedEmptyState(title: String, body: String = "") {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (body.isNotBlank())
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

internal fun offlineLabel(status: FeedOfflineStatus): Int =
    when (status) {
        FeedOfflineStatus.None -> R.string.feeds_download
        FeedOfflineStatus.Waiting -> R.string.feeds_waiting
        FeedOfflineStatus.Downloading -> R.string.feeds_downloading
        FeedOfflineStatus.Ready -> R.string.feeds_offline
        FeedOfflineStatus.Failed -> R.string.feeds_download_failed
    }

@Composable
private fun FeedSnapshotRow(snapshot: ReaderSnapshot, onOpen: () -> Unit, onRemove: () -> Unit) {
    var confirmRemove by remember { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .testTag("feed_snapshot_${snapshot.id}")
                .clickable(onClick = onOpen)
                .padding(16.dp)
        ) {
            Text(
                snapshot.document.siteName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                snapshot.document.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.feeds_offline),
                style = MaterialTheme.typography.labelSmall,
            )
            TextButton(onClick = { confirmRemove = true }) {
                Text(stringResource(R.string.feeds_remove_download))
            }
        }
    }
    if (confirmRemove)
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.feeds_remove_download)) },
            text = { Text(snapshot.document.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove()
                        confirmRemove = false
                    }
                ) {
                    Text(stringResource(R.string.feeds_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.feeds_cancel))
                }
            },
        )
}
