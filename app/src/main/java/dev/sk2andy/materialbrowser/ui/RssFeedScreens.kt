package dev.sk2andy.materialbrowser.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.RssSettingsStore
import dev.sk2andy.materialbrowser.reader.rss.RssArticle
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssRefreshScheduler
import dev.sk2andy.materialbrowser.shared.ui.LocalCandyBrowserChromeEffects
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderArtwork
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderIllustration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RssFeedsScreen(
    pageUrl: String,
    candidates: List<Pair<String, String>>,
    feeds: List<RssFeed>,
    isWorking: Boolean,
    errorMessage: String,
    statusMessage: String,
    onBack: () -> Unit,
    onDiscover: () -> Unit,
    onSubscribe: (String) -> Unit,
    onRemove: (String) -> Unit,
    onNotificationsChanged: (String, Boolean) -> Unit,
    onOpenArticle: (String) -> Unit,
) {
    var manualFeedUrl by remember { mutableStateOf("") }
    var showManualEntry by remember { mutableStateOf(false) }
    var selectedFeedId by remember { mutableStateOf<String?>(null) }
    val selectedFeed = feeds.firstOrNull { it.id == selectedFeedId }

    val reduceMotion = LocalCandyBrowserChromeEffects.current.reducesMotion()
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (selectedFeed != null) Modifier.clearAndSetSemantics {} else Modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.rss_feeds_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
        ) { insets ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(insets),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (pageUrl.isNotBlank()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.rss_discovery_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    pageUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = onDiscover, enabled = !isWorking) {
                                        Text(stringResource(R.string.rss_discover_action))
                                    }
                                    if (isWorking)
                                        CircularProgressIndicator(
                                            Modifier.padding(start = 12.dp).size(22.dp)
                                        )
                                }
                            }
                        }
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { showManualEntry = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(
                            stringResource(R.string.rss_manual_entry_action),
                            Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (candidates.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.rss_available_feeds),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(candidates, key = { it.first }) { candidate ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        candidate.second,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        candidate.first,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                    )
                                }
                                Button(
                                    onClick = { onSubscribe(candidate.first) },
                                    enabled = !isWorking,
                                ) {
                                    Text(stringResource(R.string.rss_subscribe_action))
                                }
                            }
                        }
                    }
                }
                if (errorMessage.isNotBlank())
                    item { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
                if (statusMessage.isNotBlank())
                    item { Text(statusMessage, color = MaterialTheme.colorScheme.primary) }
                item {
                    Text(
                        stringResource(R.string.rss_subscribed_feeds),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                if (feeds.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.rss_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(feeds, key = RssFeed::id) { feed ->
                    RssFeedSourceRow(feed = feed, onClick = { selectedFeedId = feed.id })
                }
                item { Spacer(Modifier.size(16.dp)) }
            }
        }

        AnimatedContent(
            targetState = selectedFeed,
            contentKey = { it?.id },
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                else
                    (slideInHorizontally(spatial) { it / 8 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { it / 8 } + fadeOut(effects))
            },
            label = "rss_source_detail",
        ) { feed ->
            if (feed != null) {
                RssFeedDetailScreen(
                    feed = feed,
                    backEnabled = selectedFeed?.id == feed.id,
                    onBack = { selectedFeedId = null },
                    onOpenArticle = onOpenArticle,
                    onRemove = {
                        onRemove(feed.id)
                        selectedFeedId = null
                    },
                    onNotificationsChanged = { onNotificationsChanged(feed.id, it) },
                )
            }
        }
    }

    if (showManualEntry) {
        AlertDialog(
            onDismissRequest = { showManualEntry = false },
            title = { Text(stringResource(R.string.rss_feed_address)) },
            text = {
                OutlinedTextField(
                    value = manualFeedUrl,
                    onValueChange = { manualFeedUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.rss_feed_address_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualEntry = false
                        onSubscribe(manualFeedUrl.trim())
                        manualFeedUrl = ""
                    },
                    enabled = !isWorking && manualFeedUrl.isNotBlank(),
                ) {
                    Text(stringResource(R.string.rss_subscribe_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualEntry = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RssFeedSourceRow(feed: RssFeed, onClick: () -> Unit) {
    val host = Uri.parse(feed.siteUrl.ifBlank { feed.feedUrl }).host.orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rss),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(feed.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    host,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.rss_open_feed, feed.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssFeedDetailScreen(
    feed: RssFeed,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onRemove: () -> Unit,
    onNotificationsChanged: (Boolean) -> Unit,
    backEnabled: Boolean,
) {
    val back = rememberNativeScreenBackState()
    LaunchedEffect(backEnabled) { if (backEnabled) back.progress.snapTo(0f) }
    NativeScreenBackHandler(
        back,
        enabled = backEnabled,
        retainProgressOnBack = true,
        onBack = onBack,
    )
    Scaffold(
        modifier =
            Modifier.testTag("rss_source_detail")
                .predictiveBackSurface(back.progress.value, back.swipeEdgeSign),
        topBar = {
            TopAppBar(
                title = { Text(feed.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.rss_remove_feed),
                        )
                    }
                },
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onNotificationsChanged(!feed.notify) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.rss_notifications_per_feed),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.rss_notifications_feed_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = feed.notify, onCheckedChange = onNotificationsChanged)
                }
            }
            if (feed.articles.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.rss_no_articles),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(feed.articles, key = RssArticle::id) { article ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { onOpenArticle(article.url) }
                        .padding(vertical = 10.dp)
                ) {
                    Text(article.title, style = MaterialTheme.typography.titleMedium)
                    if (article.summary.isNotBlank()) {
                        Text(
                            article.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun RssSettingsPage(onBack: () -> Unit, onDiscoverFeeds: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { RssSettingsStore(context) }
    var interval by remember(store) { mutableLongStateOf(store.refreshIntervalMinutes()) }
    SettingsPage(title = stringResource(R.string.rss_settings_title), onBack = onBack) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsHeaderIllustration(SettingsHeaderArtwork.Rss)
            Text(
                stringResource(R.string.rss_refresh_interval),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.rss_refresh_interval_summary),
                style = MaterialTheme.typography.bodyMedium,
            )
            RssRefreshScheduler.INTERVAL_OPTIONS_MINUTES.forEach { minutes ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (store.setRefreshIntervalMinutes(minutes)) interval = minutes
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = interval == minutes,
                        onClick = {
                            if (store.setRefreshIntervalMinutes(minutes)) interval = minutes
                        },
                    )
                    Text(stringResource(refreshIntervalString(minutes)))
                }
            }
            Text(
                stringResource(R.string.rss_refresh_android_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onDiscoverFeeds,
                modifier = Modifier.testTag("rss_settings_discover"),
            ) {
                Text(stringResource(R.string.browser_menu_discover_feeds))
            }
        }
    }
}

private fun refreshIntervalString(minutes: Long): Int =
    when (minutes) {
        15L -> R.string.rss_refresh_15
        30L -> R.string.rss_refresh_30
        60L -> R.string.rss_refresh_60
        180L -> R.string.rss_refresh_180
        360L -> R.string.rss_refresh_360
        720L -> R.string.rss_refresh_720
        else -> R.string.rss_refresh_1440
    }
