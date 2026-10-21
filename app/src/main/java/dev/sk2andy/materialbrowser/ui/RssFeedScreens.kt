package dev.sk2andy.materialbrowser.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.RssSettingsStore
import dev.sk2andy.materialbrowser.reader.rss.RssFeed
import dev.sk2andy.materialbrowser.reader.rss.RssRefreshScheduler
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderArtwork
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHeaderIllustration

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
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
                            Text(stringResource(R.string.rss_discovery_title), style = MaterialTheme.typography.titleMedium)
                            Text(pageUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = onDiscover, enabled = !isWorking) {
                                    Text(stringResource(R.string.rss_discover_action))
                                }
                                if (isWorking) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(22.dp))
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = manualFeedUrl,
                    onValueChange = { manualFeedUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.rss_feed_address)) },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { onSubscribe(manualFeedUrl) }, enabled = !isWorking) {
                            Text(stringResource(R.string.rss_subscribe_action))
                        }
                    },
                )
            }
            if (candidates.isNotEmpty()) {
                item { Text(stringResource(R.string.rss_available_feeds), style = MaterialTheme.typography.titleMedium) }
                items(candidates, key = { it.first }) { candidate ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(14.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.second, style = MaterialTheme.typography.labelMedium)
                                Text(candidate.first, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            Button(onClick = { onSubscribe(candidate.first) }, enabled = !isWorking) {
                                Text(stringResource(R.string.rss_subscribe_action))
                            }
                        }
                    }
                }
            }
            if (errorMessage.isNotBlank()) item { Text(errorMessage, color = MaterialTheme.colorScheme.error) }
            if (statusMessage.isNotBlank()) item { Text(statusMessage, color = MaterialTheme.colorScheme.primary) }
            item { Text(stringResource(R.string.rss_subscribed_feeds), style = MaterialTheme.typography.titleMedium) }
            if (feeds.isEmpty()) {
                item { Text(stringResource(R.string.rss_empty_state), style = MaterialTheme.typography.bodyMedium) }
            }
            items(feeds, key = RssFeed::id) { feed ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(feed.title, style = MaterialTheme.typography.titleMedium)
                                Text(feed.feedUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            IconButton(onClick = { onRemove(feed.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.rss_remove_feed))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = feed.notify,
                                onCheckedChange = { onNotificationsChanged(feed.id, it) },
                            )
                            Text(stringResource(R.string.rss_notifications_per_feed))
                        }
                        if (feed.articles.isEmpty()) {
                            Text(stringResource(R.string.rss_no_articles), style = MaterialTheme.typography.bodySmall)
                        }
                        feed.articles.take(5).forEachIndexed { index, article ->
                            if (index > 0) HorizontalDivider()
                            Column(
                                Modifier.fillMaxWidth().clickable { onOpenArticle(article.url) }.padding(vertical = 10.dp),
                            ) {
                                Text(article.title, style = MaterialTheme.typography.bodyLarge)
                                if (article.summary.isNotBlank()) {
                                    Text(article.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { RssSettingsStore(context) }
    var interval by remember(store) { mutableLongStateOf(store.refreshIntervalMinutes()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rss_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsHeaderIllustration(SettingsHeaderArtwork.Rss)
            Text(stringResource(R.string.rss_refresh_interval), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.rss_refresh_interval_summary), style = MaterialTheme.typography.bodyMedium)
            RssRefreshScheduler.INTERVAL_OPTIONS_MINUTES.forEach { minutes ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (store.setRefreshIntervalMinutes(minutes)) interval = minutes
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = interval == minutes,
                        onClick = { if (store.setRefreshIntervalMinutes(minutes)) interval = minutes },
                    )
                    Text(stringResource(refreshIntervalString(minutes)))
                }
            }
            Text(
                stringResource(R.string.rss_refresh_android_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = {
                context.startActivity(Intent(context, dev.sk2andy.materialbrowser.RssFeedsActivity::class.java))
            }) {
                Text(stringResource(R.string.rss_manage_feeds))
            }
        }
    }
}

private fun refreshIntervalString(minutes: Long): Int = when (minutes) {
    15L -> R.string.rss_refresh_15
    30L -> R.string.rss_refresh_30
    60L -> R.string.rss_refresh_60
    180L -> R.string.rss_refresh_180
    360L -> R.string.rss_refresh_360
    720L -> R.string.rss_refresh_720
    else -> R.string.rss_refresh_1440
}
