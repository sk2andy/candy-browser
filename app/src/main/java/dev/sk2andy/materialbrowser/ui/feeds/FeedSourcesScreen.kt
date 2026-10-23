package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedCandidate
import dev.sk2andy.materialbrowser.feeds.FeedFolder
import dev.sk2andy.materialbrowser.feeds.FeedPreview
import dev.sk2andy.materialbrowser.feeds.FeedReadingMode
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedSource
import dev.sk2andy.materialbrowser.feeds.FeedState
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun FeedSourcesScreen(
    state: FeedState,
    repository: FeedRepository,
    mutate: FeedMutation,
    onAdd: () -> Unit,
    onOpenSource: (String) -> Unit,
    onNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editSource by remember { mutableStateOf<FeedSource?>(null) }
    var editFolder by remember { mutableStateOf<FeedFolder?>(null) }
    var deleteSource by remember { mutableStateOf<FeedSource?>(null) }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text(stringResource(R.string.feeds_add)) }
                TextButton(
                    onClick = { editFolder = FeedFolder(UUID.randomUUID().toString(), "") }
                ) {
                    Text(stringResource(R.string.feeds_add_folder))
                }
            }
        }
        items(listOf<FeedFolder?>(null) + state.folders, key = { it?.id ?: "unfiled" }) { folder ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        folder?.name ?: stringResource(R.string.feeds_no_folder),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (folder != null) {
                        TextButton(onClick = { editFolder = folder }) {
                            Text(stringResource(R.string.feeds_edit))
                        }
                        TextButton(onClick = { mutate { repository.removeFolder(folder.id) } }) {
                            Text(stringResource(R.string.feeds_delete))
                        }
                    }
                }
                state.sources
                    .filter { it.folderId == folder?.id }
                    .forEach { source ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                TextButton(onClick = { onOpenSource(source.id) }) {
                                    Text(source.title, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(source.url, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (source.error != null)
                                        stringResource(R.string.feeds_source_error)
                                    else if (source.lastSuccessMillis == 0L)
                                        stringResource(R.string.feeds_never_refreshed)
                                    else
                                        stringResource(
                                            R.string.feeds_last_refresh,
                                            DateFormat.getDateTimeInstance(
                                                    DateFormat.SHORT,
                                                    DateFormat.SHORT,
                                                )
                                                .format(Date(source.lastSuccessMillis)),
                                        ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (source.error != null) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row {
                                    TextButton(onClick = { editSource = source }) {
                                        Text(stringResource(R.string.feeds_edit))
                                    }
                                    TextButton(
                                        onClick = { mutate { repository.refresh(source.id) } }
                                    ) {
                                        Text(stringResource(R.string.feeds_refresh))
                                    }
                                    TextButton(onClick = { deleteSource = source }) {
                                        Text(stringResource(R.string.feeds_remove_source))
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
    editFolder?.let { folder ->
        FeedNameDialog(
            folder.name,
            R.string.feeds_folder,
            onSave = {
                mutate { repository.saveFolder(folder.copy(name = it)) }
                editFolder = null
            },
            onDismiss = { editFolder = null },
        )
    }
    editSource?.let { source ->
        FeedSourceEditor(
            source,
            state,
            onNotificationPermission,
            onSave = {
                mutate { repository.updateSource(it) }
                editSource = null
            },
            onDismiss = { editSource = null },
        )
    }
    deleteSource?.let { source ->
        AlertDialog(
            onDismissRequest = { deleteSource = null },
            title = { Text(stringResource(R.string.feeds_confirm_delete)) },
            text = {
                Text(source.title + "\n\n" + stringResource(R.string.feeds_remove_source_note))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mutate { repository.removeSource(source.id) }
                        deleteSource = null
                    }
                ) {
                    Text(stringResource(R.string.feeds_remove_source))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSource = null }) {
                    Text(stringResource(R.string.feeds_cancel))
                }
            },
        )
    }
}

@Composable
internal fun FeedSubscribeDialog(
    repository: FeedRepository,
    state: FeedState,
    initialUrl: String,
    mutate: FeedMutation,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var preview by remember { mutableStateOf<FeedPreview?>(null) }
    var candidates by remember { mutableStateOf(emptyList<FeedCandidate>()) }
    var folderId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorResource by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val requestPreview: (String) -> Unit = { address ->
        scope.launch {
            loading = true
            errorResource = null
            preview = null
            candidates = emptyList()
            repository
                .preview(address)
                .fold(
                    onSuccess = { preview = it },
                    onFailure = { previewError ->
                        if (previewError is IOException) {
                            errorResource = R.string.feeds_preview_connection_error
                        } else {
                            repository
                                .discover(address)
                                .fold(
                                    onSuccess = {
                                        candidates = it
                                        if (it.isEmpty())
                                            errorResource = R.string.feeds_preview_invalid
                                    },
                                    onFailure = { discoveryError ->
                                        errorResource =
                                            if (discoveryError is IOException) {
                                                R.string.feeds_preview_connection_error
                                            } else {
                                                R.string.feeds_preview_invalid
                                            }
                                    },
                                )
                        }
                    },
                )
            loading = false
        }
    }
    LaunchedEffect(initialUrl) {
        url = initialUrl
        preview = null
        candidates = emptyList()
        errorResource = null
    }
    val duplicate = preview?.let { item -> state.sources.any { it.url == item.url } } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_add)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    url,
                    {
                        url = it
                        preview = null
                        candidates = emptyList()
                        errorResource = null
                    },
                    label = { Text(stringResource(R.string.feeds_url)) },
                    singleLine = true,
                    enabled = !loading,
                )
                if (repository.isPrivate)
                    Text(
                        stringResource(R.string.feeds_private_preview),
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (loading) CircularProgressIndicator()
                errorResource?.let { message ->
                    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                }
                candidates.forEach { candidate ->
                    TextButton(
                        onClick = {
                            url = candidate.url
                            requestPreview(candidate.url)
                        }
                    ) {
                        Text(candidate.title.ifBlank { candidate.url })
                    }
                }
                preview?.let { item ->
                    Text(item.title, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.feeds_preview_count, item.articles.size))
                    if (item.articles.isEmpty())
                        Text(
                            stringResource(R.string.feeds_preview_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    item.articles.take(4).forEach {
                        Text(it.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    FeedFolderChoice(state, folderId, { folderId = it })
                    if (duplicate) Text(stringResource(R.string.feeds_subscribed))
                }
            }
        },
        confirmButton = {
            if (preview == null)
                TextButton(
                    onClick = { requestPreview(url) },
                    enabled = !loading && url.isNotBlank(),
                ) {
                    Text(stringResource(R.string.feeds_preview))
                }
            else
                TextButton(
                    onClick = {
                        preview?.let { item -> mutate { repository.subscribe(item, folderId) } }
                        onDismiss()
                    },
                    enabled = !duplicate && !repository.isPrivate,
                ) {
                    Text(stringResource(R.string.feeds_subscribe))
                }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) }
        },
    )
}

@Composable
private fun FeedSourceEditor(
    source: FeedSource,
    state: FeedState,
    onNotificationPermission: () -> Unit,
    onSave: (FeedSource) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(source.title) }
    var folder by remember { mutableStateOf(source.folderId) }
    var mode by remember { mutableStateOf(source.readingMode) }
    var notifications by remember { mutableStateOf(source.notificationsEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_edit)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(stringResource(R.string.feeds_name)) },
                )
                FeedFolderChoice(state, folder, { folder = it })
                Text(
                    stringResource(R.string.feeds_reading_mode),
                    style = MaterialTheme.typography.titleSmall,
                )
                FeedReadingMode.entries.forEach { value ->
                    FilterChip(
                        mode == value,
                        { mode = value },
                        label = {
                            Text(
                                stringResource(
                                    when (value) {
                                        FeedReadingMode.Feed -> R.string.feeds_mode_feed
                                        FeedReadingMode.Reader -> R.string.feeds_mode_reader
                                        FeedReadingMode.Original -> R.string.feeds_mode_original
                                    }
                                )
                            )
                        },
                    )
                }
                FeedSwitchRow(R.string.feeds_notifications, notifications) {
                    notifications = it
                    if (it) onNotificationPermission()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        source.copy(
                            title = title.trim(),
                            folderId = folder,
                            readingMode = mode,
                            notificationsEnabled = notifications,
                        )
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.feeds_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) }
        },
    )
}

@Composable
internal fun FeedFolderChoice(state: FeedState, selected: String?, onSelected: (String?) -> Unit) {
    Text(stringResource(R.string.feeds_folder), style = MaterialTheme.typography.titleSmall)
    FilterChip(
        selected == null,
        { onSelected(null) },
        label = { Text(stringResource(R.string.feeds_no_folder)) },
    )
    state.folders.forEach { folder ->
        FilterChip(selected == folder.id, { onSelected(folder.id) }, label = { Text(folder.name) })
    }
}

@Composable
internal fun FeedNameDialog(
    initial: String,
    title: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value,
                { value = it },
                label = { Text(stringResource(R.string.feeds_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.feeds_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) }
        },
    )
}

@Composable
internal fun FeedSwitchRow(label: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(label),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked, onChecked)
    }
}
