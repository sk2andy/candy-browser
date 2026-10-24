package dev.sk2andy.materialbrowser.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import dev.sk2andy.materialbrowser.reader.rss.RssArticle
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssInboxRules
import java.util.Date
import kotlinx.coroutines.launch

internal enum class ReaderStudioHomeTab { Library, Feeds }

private enum class ReaderFeedFilter { All, Unread }

private val ReadArticleKeysSaver = listSaver<Set<String>, String>(
    save = { keys -> keys.toList() },
    restore = { keys -> keys.toSet() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderStudioHomeScreen(
    library: ReaderLibraryState,
    feeds: List<RssFeed>,
    readArticleKeys: Set<String>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenSnapshot: (ReaderSnapshot) -> Unit,
    onDeleteSnapshot: (ReaderSnapshot) -> Unit,
    onOpenArticle: (RssFeed, RssArticle) -> Unit,
    onManageFeeds: () -> Unit,
    initialTab: ReaderStudioHomeTab = ReaderStudioHomeTab.Library,
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    var selectedSourceId by rememberSaveable { mutableStateOf(ALL_FEEDS_ID) }
    var selectedFilter by rememberSaveable { mutableStateOf(ReaderFeedFilter.All) }
    var sourceSheetVisible by rememberSaveable { mutableStateOf(false) }
    var sourceQuery by rememberSaveable { mutableStateOf("") }
    var appliedReadKeys by rememberSaveable(stateSaver = ReadArticleKeysSaver) {
        mutableStateOf(readArticleKeys)
    }
    var hasInitializedReadKeys by rememberSaveable { mutableStateOf(false) }
    val libraryListState = rememberLazyListState()
    val feedListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateFormatter = remember(context) { DateFormat.getMediumDateFormat(context) }
    val timeFormatter = remember(context) { DateFormat.getTimeFormat(context) }

    LaunchedEffect(isLoading) {
        if (!isLoading && !hasInitializedReadKeys) {
            appliedReadKeys = readArticleKeys
            hasInitializedReadKeys = true
        }
    }

    val orderedFeeds = remember(feeds) { feeds.sortedBy { it.title.lowercase() } }
    val snapshots = remember(library.snapshots) {
        library.snapshots.sortedByDescending(ReaderSnapshot::savedAtMillis)
    }
    val selectedFeed = orderedFeeds.firstOrNull { it.id == selectedSourceId }
    LaunchedEffect(feeds, selectedSourceId, isLoading) {
        if (!isLoading && selectedSourceId != ALL_FEEDS_ID && selectedFeed == null) {
            selectedSourceId = ALL_FEEDS_ID
        }
    }
    val articles = remember(feeds) {
        feeds.flatMap { feed -> feed.articles.map { article -> feed to article } }
            .sortedWith(
                compareByDescending<Pair<RssFeed, RssArticle>> { it.second.publishedAt }
                    .thenBy { it.first.title.lowercase() },
            )
    }
    val visibleArticles = remember(articles, selectedSourceId, selectedFilter, appliedReadKeys) {
        articles.filter { (feed, article) ->
            (selectedSourceId == ALL_FEEDS_ID || feed.id == selectedSourceId) &&
                (selectedFilter == ReaderFeedFilter.All ||
                    RssInboxRules.articleKey(feed.id, article.id) !in appliedReadKeys)
        }
    }
    val unreadCount = remember(articles, selectedSourceId, readArticleKeys) {
        articles.count { (feed, article) ->
            (selectedSourceId == ALL_FEEDS_ID || feed.id == selectedSourceId) &&
                RssInboxRules.articleKey(feed.id, article.id) !in readArticleKeys
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reader_home_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.reader_home_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onManageFeeds) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.browser_menu_discover_feeds),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == ReaderStudioHomeTab.Library,
                    onClick = { selectedTab = ReaderStudioHomeTab.Library },
                    modifier = Modifier.testTag("reader-home-library-tab"),
                    text = { Text(stringResource(R.string.reader_home_library_tab)) },
                )
                Tab(
                    selected = selectedTab == ReaderStudioHomeTab.Feeds,
                    onClick = { selectedTab = ReaderStudioHomeTab.Feeds },
                    modifier = Modifier.testTag("reader-home-feeds-tab"),
                    text = { Text(stringResource(R.string.reader_home_feeds_tab)) },
                )
            }

            if (selectedTab == ReaderStudioHomeTab.Feeds) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Surface(
                        onClick = {
                            sourceQuery = ""
                            sourceSheetVisible = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("reader-home-source-button"),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_rss),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = selectedFeed?.title
                                    ?: stringResource(R.string.reader_home_all_feeds),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedFilter == ReaderFeedFilter.All,
                            onClick = {
                                selectedFilter = ReaderFeedFilter.All
                                appliedReadKeys = readArticleKeys
                                coroutineScope.launch { feedListState.scrollToItem(0) }
                            },
                            label = { Text(stringResource(R.string.reader_home_all_posts)) },
                            leadingIcon = if (selectedFilter == ReaderFeedFilter.All) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                        FilterChip(
                            selected = selectedFilter == ReaderFeedFilter.Unread,
                            onClick = {
                                selectedFilter = ReaderFeedFilter.Unread
                                appliedReadKeys = readArticleKeys
                                coroutineScope.launch { feedListState.scrollToItem(0) }
                            },
                            label = {
                                Text(stringResource(R.string.reader_home_unread_count, unreadCount))
                            },
                            leadingIcon = if (selectedFilter == ReaderFeedFilter.Unread) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
                when {
                    isLoading && feeds.isEmpty() -> ReaderHomeLoading(Modifier.weight(1f))
                    feeds.isEmpty() -> ReaderHomeEmpty(
                        title = stringResource(R.string.reader_home_no_feeds_title),
                        message = stringResource(R.string.reader_home_no_feeds_message),
                        action = stringResource(R.string.browser_menu_discover_feeds),
                        onAction = onManageFeeds,
                        modifier = Modifier.weight(1f),
                    )
                    visibleArticles.isEmpty() -> ReaderHomeEmpty(
                        title = if (selectedFilter == ReaderFeedFilter.Unread) {
                            stringResource(R.string.reader_home_all_read_title)
                        } else {
                            stringResource(R.string.reader_home_no_articles_title)
                        },
                        message = if (selectedFilter == ReaderFeedFilter.Unread) {
                            stringResource(R.string.reader_home_all_read_message)
                        } else {
                            stringResource(R.string.reader_home_no_articles_message)
                        },
                        action = stringResource(R.string.browser_menu_discover_feeds),
                        onAction = onManageFeeds,
                        modifier = Modifier.weight(1f),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f).testTag("reader-home-feed-list"),
                        state = feedListState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "feed-heading") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.reader_home_article_count,
                                        visibleArticles.size,
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.reader_home_newest_first),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            items = visibleArticles,
                            key = { (feed, article) -> RssInboxRules.articleKey(feed.id, article.id) },
                        ) { (feed, article) ->
                            FeedArticleCard(
                                feed = feed,
                                article = article,
                                read = RssInboxRules.articleKey(feed.id, article.id) in readArticleKeys,
                                dateText = article.publishedAt.takeIf { it > 0L }
                                    ?.let { dateFormatter.format(Date(it)) },
                                timeText = article.publishedAt.takeIf { it > 0L }
                                    ?.let { timeFormatter.format(Date(it)) },
                                onClick = { onOpenArticle(feed, article) },
                                modifier = Modifier.testTag("reader-home-article-${article.id}"),
                            )
                        }
                        item(key = "feed-footer") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    stringResource(R.string.reader_home_all_available_shown),
                                    modifier = Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else if (isLoading && library.snapshots.isEmpty()) {
                ReaderHomeLoading(Modifier.weight(1f))
            } else if (library.snapshots.isEmpty()) {
                ReaderHomeEmpty(
                    title = stringResource(R.string.reader_home_library_empty_title),
                    message = stringResource(R.string.reader_home_library_empty_message),
                    action = null,
                    onAction = {},
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = libraryListState,
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(snapshots, key = ReaderSnapshot::id) { snapshot ->
                        LibrarySnapshotCard(
                            snapshot = snapshot,
                            progress = library.progressByUrl[snapshot.document.sourceUrl]
                                ?: snapshot.progress,
                            onOpen = { onOpenSnapshot(snapshot) },
                            onDelete = { onDeleteSnapshot(snapshot) },
                            modifier = Modifier.testTag("reader-home-snapshot-${snapshot.id}"),
                        )
                    }
                }
            }
        }
    }

    if (sourceSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sourceSheetVisible = false },
            sheetState = sheetState,
            contentWindowInsets = { WindowInsets.navigationBars },
        ) {
            FeedSourcePicker(
                feeds = orderedFeeds,
                readArticleKeys = readArticleKeys,
                selectedSourceId = selectedSourceId,
                query = sourceQuery,
                onQueryChanged = { sourceQuery = it },
                onSelect = { sourceId ->
                    selectedSourceId = sourceId
                    appliedReadKeys = readArticleKeys
                    sourceSheetVisible = false
                    coroutineScope.launch { feedListState.scrollToItem(0) }
                },
                onManageFeeds = {
                    sourceSheetVisible = false
                    onManageFeeds()
                },
            )
        }
    }
}

@Composable
private fun FeedArticleCard(
    feed: RssFeed,
    article: RssArticle,
    read: Boolean,
    dateText: String?,
    timeText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!read) {
                    Surface(
                        modifier = Modifier.size(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ) {}
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = feed.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dateText != null && timeText != null) {
                    Text(
                        text = "$dateText · $timeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = article.title,
                modifier = Modifier.padding(top = 9.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (article.summary.isNotBlank()) {
                Text(
                    text = article.summary,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibrarySnapshotCard(
    snapshot: ReaderSnapshot,
    progress: Float,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    snapshot.document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    snapshot.document.siteName,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.reader_home_progress, (progress * 100).toInt()),
                    modifier = Modifier.padding(top = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.reader_home_delete_article),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedSourcePicker(
    feeds: List<RssFeed>,
    readArticleKeys: Set<String>,
    selectedSourceId: String,
    query: String,
    onQueryChanged: (String) -> Unit,
    onSelect: (String) -> Unit,
    onManageFeeds: () -> Unit,
) {
    val normalizedQuery = query.trim().lowercase()
    val visibleFeeds = remember(feeds, normalizedQuery) {
        feeds.filter { feed ->
            normalizedQuery.isBlank() || feed.title.lowercase().contains(normalizedQuery) ||
                feed.feedUrl.lowercase().contains(normalizedQuery)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("reader-home-source-sheet"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.reader_home_choose_feed),
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text(stringResource(R.string.reader_home_search_feeds)) },
            singleLine = true,
        )
        LazyColumn(
            modifier = Modifier.heightIn(min = 120.dp, max = 320.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = ALL_FEEDS_ID) {
                FeedSourceRow(
                    title = stringResource(R.string.reader_home_all_feeds),
                    subtitle = stringResource(R.string.reader_home_feed_count, feeds.size),
                    unreadCount = feeds.sumOf { feed ->
                        feed.articles.count { article ->
                            RssInboxRules.articleKey(feed.id, article.id) !in readArticleKeys
                        }
                    },
                    selected = selectedSourceId == ALL_FEEDS_ID,
                    onClick = { onSelect(ALL_FEEDS_ID) },
                    modifier = Modifier.testTag("reader-home-source-row-$ALL_FEEDS_ID"),
                )
            }
            items(visibleFeeds, key = RssFeed::id) { feed ->
                FeedSourceRow(
                    title = feed.title,
                    subtitle = android.net.Uri.parse(feed.siteUrl.ifBlank { feed.feedUrl }).host
                        .orEmpty(),
                    unreadCount = feed.articles.count { article ->
                        RssInboxRules.articleKey(feed.id, article.id) !in readArticleKeys
                    },
                    selected = selectedSourceId == feed.id,
                    onClick = { onSelect(feed.id) },
                    modifier = Modifier.testTag("reader-home-source-row-${feed.id}"),
                )
            }
            if (visibleFeeds.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.reader_home_no_feed_match),
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(
            onClick = onManageFeeds,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.browser_menu_discover_feeds))
        }
    }
}

@Composable
private fun FeedSourceRow(
    title: String,
    subtitle: String,
    unreadCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                stringResource(R.string.reader_home_source_subtitle, subtitle, unreadCount),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rss),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        trailingContent = {
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.outline,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outline,
                    ),
                ) {}
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 1.dp),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    )
}

@Composable
private fun ReaderHomeLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReaderHomeEmpty(
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rss),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                message,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (action != null) {
                TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) {
                    Text(action)
                }
            }
        }
    }
}

private const val ALL_FEEDS_ID = "__all_feeds__"
