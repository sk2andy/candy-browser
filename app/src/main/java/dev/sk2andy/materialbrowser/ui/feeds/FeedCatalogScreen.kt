package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedCatalogPage
import dev.sk2andy.materialbrowser.feeds.FeedCatalogQuery
import dev.sk2andy.materialbrowser.feeds.FeedCatalogSource
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedRules
import dev.sk2andy.materialbrowser.ui.NativeScreenBackHandler
import dev.sk2andy.materialbrowser.ui.predictiveBackSurface
import dev.sk2andy.materialbrowser.ui.rememberNativeScreenBackState
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedCatalogScreen(
    repository: FeedRepository,
    onPreview: (String) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    searchCatalog: suspend (FeedCatalogQuery) -> Result<FeedCatalogPage> =
        repository::searchCatalog,
    backEnabled: Boolean = true,
) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var input by rememberSaveable(repository) { mutableStateOf("") }
    // Keep only the explicitly submitted intent in saved state. Results stay out of the Bundle.
    var submittedText by rememberSaveable(repository) { mutableStateOf<String?>(null) }
    var submittedTopic by rememberSaveable(repository) { mutableStateOf<String?>(null) }
    var appliedQuery by
        remember(repository) {
            mutableStateOf(
                if (submittedText != null || submittedTopic != null)
                    FeedCatalogQuery(text = submittedText.orEmpty(), topic = submittedTopic)
                else null
            )
        }
    var sources by remember(repository) { mutableStateOf(emptyList<FeedCatalogSource>()) }
    var nextOffset by remember(repository) { mutableStateOf<Int?>(null) }
    var loading by remember(repository) { mutableStateOf(false) }
    var failed by remember(repository) { mutableStateOf(false) }
    var moreFailed by remember(repository) { mutableStateOf(false) }
    var requestId by remember(repository) { mutableIntStateOf(0) }
    var job by remember(repository) { mutableStateOf<Job?>(null) }
    val gridState = rememberLazyGridState()
    DisposableEffect(repository) { onDispose { job?.cancel() } }
    fun search(query: FeedCatalogQuery, append: Boolean = false) {
        if (repository.isPrivate || query.text.isBlank() && query.topic.isNullOrBlank()) return
        focus.clearFocus()
        keyboard?.hide()
        job?.cancel()
        val identity = ++requestId
        loading = true
        failed = false
        moreFailed = false
        if (!append) {
            appliedQuery = query.copy(offset = 0)
            submittedText = query.text
            submittedTopic = query.topic
            sources = emptyList()
            nextOffset = null
        }
        job =
            scope.launch {
                if (!append) gridState.scrollToItem(0)
                try {
                    val result = searchCatalog(query)
                    if (identity != requestId) return@launch
                    result.fold(
                        onSuccess = { page ->
                            val combined = if (append) sources + page.sources else page.sources
                            sources =
                                combined.distinctBy { FeedRules.identity(it.feedUrl) }.take(500)
                            nextOffset = page.nextOffset?.takeIf { sources.size < 500 }
                        },
                        onFailure = {
                            if (it is CancellationException) throw it
                            if (append) moreFailed = true else failed = true
                        },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (identity == requestId) {
                        if (append) moreFailed = true else failed = true
                    }
                } finally {
                    if (identity == requestId) loading = false
                }
            }
    }
    fun reset() {
        job?.cancel()
        requestId++
        input = ""
        submittedText = null
        submittedTopic = null
        appliedQuery = null
        sources = emptyList()
        nextOffset = null
        loading = false
        failed = false
        moreFailed = false
        scope.launch { gridState.scrollToItem(0) }
    }
    LaunchedEffect(repository) {
        // Recreate the first page only after a previous explicit search, never on a fresh entry.
        // Do not replace input: it may contain a different, still unsubmitted draft.
        appliedQuery?.let { query -> search(query.copy(offset = 0)) }
    }
    val back = rememberNativeScreenBackState()
    LaunchedEffect(backEnabled) { if (backEnabled) back.progress.snapTo(0f) }
    NativeScreenBackHandler(
        state = back,
        enabled = backEnabled,
        retainProgressOnBack = true,
        onBack = onDismiss,
    )
    val filtered = sources
    Surface(
        modifier
            .fillMaxSize()
            .testTag("feed_catalog_screen")
            .predictiveBackSurface(back.progress.value, back.swipeEdgeSign),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
        ) {
            val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
            val columns =
                if (maxWidth >= 1000.dp * fontScale) 3
                else if (maxWidth >= 640.dp * fontScale) 2 else 1
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 1200.dp).fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("feed_catalog_back"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.action_back),
                            )
                        }
                        Text(
                            stringResource(R.string.feed_catalog_title),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!repository.isPrivate) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.widthIn(max = 720.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                FeedCatalogSearchField(
                                    query = input,
                                    onQueryChange = { input = it.take(200) },
                                    onSearch = {
                                        input
                                            .trim()
                                            .takeIf { it.isNotBlank() }
                                            ?.let { search(FeedCatalogQuery(text = it)) }
                                    },
                                )
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(
                                        onClick = onManual,
                                        modifier = Modifier.testTag("feed_catalog_manual"),
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Text(stringResource(R.string.feed_catalog_manual))
                                    }
                                }
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        modifier = Modifier.weight(1f).testTag("feed_catalog_results"),
                        contentPadding =
                            PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (repository.isPrivate) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                FeedCatalogMessage(
                                    stringResource(R.string.feed_catalog_private_title),
                                    stringResource(R.string.feed_catalog_private_body),
                                ) {
                                    FilledTonalButton(
                                        onClick = onManual,
                                        modifier = Modifier.testTag("feed_catalog_manual"),
                                    ) {
                                        Text(stringResource(R.string.feed_catalog_manual))
                                    }
                                }
                            }
                        } else if (appliedQuery == null) {
                            item(span = { GridItemSpan(maxLineSpan) }) { FeedCatalogHero() }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    feedCatalogTopics
                                        .chunked(
                                            if (columns > 1) 3 else if (fontScale > 1.3f) 1 else 2
                                        )
                                        .forEach { row ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                row.forEach { topic ->
                                                    FeedCatalogTopicCard(
                                                        topic,
                                                        feedCatalogTopics.indexOf(topic),
                                                        onClick = {
                                                            input = ""
                                                            search(
                                                                FeedCatalogQuery(topic = topic.slug)
                                                            )
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        imageSize =
                                                            if (columns > 1) 112.dp else 80.dp,
                                                    )
                                                }
                                            }
                                        }
                                }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    stringResource(R.string.feed_catalog_privacy),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val topic =
                                        feedCatalogTopics.firstOrNull {
                                            it.slug == appliedQuery?.topic
                                        }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            topic?.let { stringResource(it.label) }
                                                ?: stringResource(
                                                    R.string.feed_catalog_results,
                                                    appliedQuery?.text.orEmpty(),
                                                ),
                                            Modifier.weight(1f).semantics { heading() },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        TextButton(
                                            onClick = ::reset,
                                            modifier = Modifier.testTag("feed_catalog_reset"),
                                        ) {
                                            Text(stringResource(R.string.feed_catalog_reset))
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .testTag("feed_catalog_topic_filters"),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        feedCatalogTopics.forEach { topic ->
                                            FilterChip(
                                                selected = appliedQuery?.topic == topic.slug,
                                                onClick = {
                                                    input = ""
                                                    search(FeedCatalogQuery(topic = topic.slug))
                                                },
                                                label = { Text(stringResource(topic.label)) },
                                            )
                                        }
                                    }
                                    if (!loading || sources.isNotEmpty())
                                        Text(
                                            pluralStringResource(
                                                R.plurals.feed_catalog_loaded_count,
                                                filtered.size,
                                                filtered.size,
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier =
                                                Modifier.semantics {
                                                    liveRegion = LiveRegionMode.Polite
                                                },
                                        )
                                    if (loading && sources.isNotEmpty())
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                            if (loading && sources.isEmpty())
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    FeedCatalogMessage(
                                        stringResource(R.string.feed_catalog_loading),
                                        stringResource(R.string.feed_catalog_loading_hint),
                                        Modifier.testTag("feed_catalog_loading"),
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            else if (failed)
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    FeedCatalogMessage(
                                        stringResource(R.string.feed_catalog_error_title),
                                        stringResource(R.string.feed_catalog_error_body),
                                        Modifier.testTag("feed_catalog_error"),
                                    ) {
                                        Button(
                                            onClick = { appliedQuery?.let { search(it) } },
                                            modifier = Modifier.testTag("feed_catalog_retry"),
                                        ) {
                                            Text(stringResource(R.string.feed_catalog_retry))
                                        }
                                    }
                                }
                            else {
                                items(filtered, key = { it.feedUrl }) { source ->
                                    FeedCatalogSourceCard(
                                        source = source,
                                        subscribed =
                                            state.sources.any {
                                                FeedRules.identity(it.url) ==
                                                    FeedRules.identity(source.feedUrl)
                                            },
                                        displayLocale = locale,
                                        onPreview = {
                                            if (!repository.isPrivate) onPreview(source.feedUrl)
                                        },
                                    )
                                }
                                if (filtered.isEmpty())
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        FeedCatalogMessage(
                                            stringResource(R.string.feed_catalog_empty_title),
                                            stringResource(R.string.feed_catalog_empty_body),
                                            Modifier.testTag("feed_catalog_empty"),
                                        ) {
                                            TextButton(onClick = onManual) {
                                                Text(stringResource(R.string.feed_catalog_manual))
                                            }
                                        }
                                    }
                                if (nextOffset != null)
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            if (moreFailed)
                                                Text(
                                                    stringResource(
                                                        R.string.feed_catalog_more_failed
                                                    ),
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            FilledTonalButton(
                                                onClick = {
                                                    appliedQuery?.let { query ->
                                                        nextOffset?.let { offset ->
                                                            search(
                                                                query.copy(offset = offset),
                                                                append = true,
                                                            )
                                                        }
                                                    }
                                                },
                                                enabled = !loading,
                                                modifier = Modifier.testTag("feed_catalog_more"),
                                            ) {
                                                Text(
                                                    stringResource(
                                                        if (moreFailed) R.string.feed_catalog_retry
                                                        else R.string.feed_catalog_more
                                                    )
                                                )
                                            }
                                        }
                                    }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.feed_catalog_credit),
                                Modifier.fillMaxWidth().padding(top = 12.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
