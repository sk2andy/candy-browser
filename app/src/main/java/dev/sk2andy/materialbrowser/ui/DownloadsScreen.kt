package dev.sk2andy.materialbrowser.ui

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.DownloadEntry
import dev.sk2andy.materialbrowser.data.DownloadHistoryRules
import dev.sk2andy.materialbrowser.data.DownloadStatus
import dev.sk2andy.materialbrowser.data.DownloadTimeFilter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DownloadsScreen(
    downloads: List<DownloadEntry>,
    isClearing: Boolean = false,
    onClearFinished: (List<DownloadEntry>) -> Unit,
    onOpenDownload: (DownloadEntry) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val zoneId = remember { ZoneId.systemDefault() }
    val dateTimeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var timeFilterName by rememberSaveable { mutableStateOf(DownloadTimeFilter.All.name) }
    var clearConfirmationVisible by rememberSaveable { mutableStateOf(false) }
    val timeFilter = DownloadTimeFilter.valueOf(timeFilterName)
    val visibleDownloads = remember(downloads, query, timeFilter, zoneId) {
        DownloadHistoryRules.visibleEntries(
            entries = downloads,
            query = query,
            timeFilter = timeFilter,
            nowMillis = System.currentTimeMillis(),
            zoneId = zoneId,
        )
    }
    val clearableDownloads = remember(downloads) {
        downloads.filter { entry -> entry.status.isTerminal }
    }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { clearConfirmationVisible = true },
                        enabled = clearableDownloads.isNotEmpty() && !isClearing,
                        modifier = Modifier.testTag(DownloadsScreenTestTags.Clear),
                    ) {
                        Text(stringResource(R.string.downloads_clear))
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .navigationBarsPadding()
                .testTag(DownloadsScreenTestTags.List),
        ) {
            item(key = "search") {
                LibrarySearchBar(
                    query = query,
                    placeholder = stringResource(R.string.downloads_search),
                    clearContentDescription = stringResource(R.string.downloads_clear_search),
                    testTag = DownloadsScreenTestTags.SearchField,
                    onQueryChange = {
                        query = it.take(DownloadHistoryRules.MAX_QUERY_CHARS)
                    },
                )
            }

            item(key = "time-filter") {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadTimeFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = timeFilter == filter,
                            onClick = { timeFilterName = filter.name },
                            label = { Text(downloadTimeFilterLabel(filter)) },
                            modifier = Modifier.testTag(DownloadsScreenTestTags.timeFilter(filter)),
                        )
                    }
                }
            }

            if (visibleDownloads.isEmpty()) {
                item(key = "empty") {
                    DownloadsEmptyState(
                        searching = query.isNotBlank() || timeFilter != DownloadTimeFilter.All,
                        modifier = Modifier.fillParentMaxHeight(0.65f),
                    )
                }
            } else {
                items(items = visibleDownloads, key = DownloadEntry::id) { entry ->
                    DownloadRow(
                        entry = entry,
                        dateTime = dateTimeFormatter.format(
                            Instant.ofEpochMilli(entry.lastModified).atZone(zoneId),
                        ),
                        bytes = Formatter.formatShortFileSize(context, entry.bytes),
                        total = entry.total.takeIf { value -> value > 0L }?.let { total ->
                            Formatter.formatShortFileSize(context, total)
                        },
                        onOpen = { onOpenDownload(entry) },
                    )
                }
            }
        }
    }

    if (clearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { clearConfirmationVisible = false },
            modifier = Modifier.testTag(DownloadsScreenTestTags.ClearDialog),
            title = { Text(stringResource(R.string.downloads_clear_title)) },
            text = { Text(stringResource(R.string.downloads_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearConfirmationVisible = false
                        onClearFinished(clearableDownloads)
                    },
                    enabled = !isClearing,
                    modifier = Modifier.testTag(DownloadsScreenTestTags.ClearConfirm),
                ) {
                    Text(stringResource(R.string.downloads_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmationVisible = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    dateTime: String,
    bytes: String,
    total: String?,
    onOpen: () -> Unit,
) {
    val progress = DownloadHistoryRules.progress(entry)
    val statusColor = downloadStatusColor(entry.status)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = entry.status == DownloadStatus.Successful,
                role = Role.Button,
                onClick = onOpen,
            )
            .testTag(DownloadsScreenTestTags.download(entry.id)),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
            },
            supportingContent = {
                Column {
                    entry.sourceHost()?.let { host ->
                        Text(host, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = stringResource(downloadStatusLabel(entry.status), dateTime),
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.ic_reader_download),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = statusColor,
                )
            },
            trailingContent = {
                Text(
                    text = if (total == null) bytes else "$bytes / $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        if (entry.status.isActive) {
            if (progress == null) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag(DownloadsScreenTestTags.progress(entry.id)),
                )
            } else {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag(DownloadsScreenTestTags.progress(entry.id)),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DownloadsEmptyState(searching: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_reader_download),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    if (searching) R.string.downloads_no_results else R.string.downloads_empty,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DownloadEntry.sourceHost(): String? = source.takeIf(String::isNotBlank)
    ?.let(Uri::parse)
    ?.host
    ?.takeIf(String::isNotBlank)

@Composable
private fun downloadTimeFilterLabel(filter: DownloadTimeFilter): String = stringResource(
    when (filter) {
        DownloadTimeFilter.All -> R.string.downloads_time_all
        DownloadTimeFilter.Today -> R.string.downloads_time_today
        DownloadTimeFilter.Last7Days -> R.string.downloads_time_seven_days
        DownloadTimeFilter.Last30Days -> R.string.downloads_time_thirty_days
    },
)

private fun downloadStatusLabel(status: DownloadStatus): Int = when (status) {
    DownloadStatus.Pending -> R.string.downloads_status_pending
    DownloadStatus.Running -> R.string.downloads_status_running
    DownloadStatus.Paused -> R.string.downloads_status_paused
    DownloadStatus.Successful -> R.string.downloads_status_successful
    DownloadStatus.Failed -> R.string.downloads_status_failed
    DownloadStatus.Cancelled -> R.string.downloads_status_cancelled
}

@Composable
private fun downloadStatusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.Successful -> MaterialTheme.colorScheme.primary
    DownloadStatus.Failed,
    DownloadStatus.Cancelled,
    -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

internal object DownloadsScreenTestTags {
    const val List = "downloads_list"
    const val SearchField = "downloads_search_field"
    const val Clear = "downloads_clear"
    const val ClearDialog = "downloads_clear_dialog"
    const val ClearConfirm = "downloads_clear_confirm"

    fun timeFilter(filter: DownloadTimeFilter): String = "downloads_time_filter:${filter.name}"

    fun download(id: Long): String = "downloads_entry:$id"

    fun progress(id: Long): String = "downloads_progress:$id"
}
