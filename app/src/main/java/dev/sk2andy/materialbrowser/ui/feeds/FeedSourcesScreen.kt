package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedFolder
import dev.sk2andy.materialbrowser.feeds.FeedReadingMode
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedSource
import dev.sk2andy.materialbrowser.feeds.FeedState
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
internal fun FeedSourcesScreen(
    state: FeedState,
    repository: FeedRepository,
    mutate: FeedMutation,
    onAdd: () -> Unit,
    onOpenSource: (String) -> Unit,
    onNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
    onManual: () -> Unit = onAdd,
) {
    var editSource by remember { mutableStateOf<FeedEditorSession<FeedSource>?>(null) }
    var editFolder by remember { mutableStateOf<FeedEditorSession<FeedFolder>?>(null) }
    var deleteSource by remember { mutableStateOf<FeedEditorSession<FeedSource>?>(null) }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, modifier = Modifier.testTag("feeds_sources_add")) { Text(stringResource(R.string.feeds_add)) }
                TextButton(onClick = onManual, modifier = Modifier.testTag("feeds_sources_manual")) {
                    Text(stringResource(R.string.feeds_manual_address))
                }
                TextButton(
                    onClick = { editFolder = FeedEditorSession(FeedFolder(UUID.randomUUID().toString(), "")) }
                ) {
                    Text(stringResource(R.string.feeds_add_folder))
                }
            }
        }
        if (state.sources.isEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.feeds_sources_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.feeds_sources_empty_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        (listOf<FeedFolder?>(null) + state.folders).forEach { folder ->
            val sources = state.sources.filter { it.folderId == folder?.id }
            if (folder != null || sources.isNotEmpty()) {
                item(key = "folder_${folder?.id ?: "unfiled"}") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            folder?.name ?: stringResource(R.string.feeds_no_folder),
                            Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (folder != null) {
                            TextButton(onClick = { editFolder = FeedEditorSession(folder) }, modifier = Modifier.testTag("feeds_folder_edit_${folder.id}")) { Text(stringResource(R.string.feeds_edit)) }
                            TextButton(onClick = { mutate { repository.removeFolder(folder.id) } }) { Text(stringResource(R.string.feeds_delete)) }
                        }
                    }
                }
                items(sources, key = { "source_${it.id}" }) { source ->
                    FeedSourceCard(
                        source = source,
                        refreshing = state.refreshing,
                        onOpen = { onOpenSource(source.id) },
                        onEdit = { editSource = FeedEditorSession(source) },
                        onRefresh = { mutate { repository.refresh(source.id) } },
                        onDelete = { deleteSource = FeedEditorSession(source) },
                    )
                }
            }
        }
    }
    editFolder?.let { session ->
        val folder = session.value
        FeedNameDialog(
            folder.name,
            R.string.feeds_folder,
            onSave = {
                mutate {
                    repository.saveFolder(folder.copy(name = it)).also { result ->
                        if (result.isSuccess && editFolder === session) editFolder = null
                    }
                }
            },
            onDismiss = { editFolder = null },
        )
    }
    editSource?.let { session ->
        val source = session.value
        FeedSourceEditor(
            source,
            state,
            onNotificationPermission,
            onSave = {
                mutate {
                    repository.updateSource(it).also { result ->
                        if (result.isSuccess && editSource === session) editSource = null
                    }
                }
            },
            onDismiss = { editSource = null },
        )
    }
    deleteSource?.let { session ->
        val source = session.value
        AlertDialog(
            onDismissRequest = { deleteSource = null },
            title = { Text(stringResource(R.string.feeds_confirm_delete)) },
            text = {
                Text(source.title + "\n\n" + stringResource(R.string.feeds_remove_source_note))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mutate {
                            repository.removeSource(source.id).also { result ->
                                if (result.isSuccess && deleteSource === session) deleteSource = null
                            }
                        }
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
private fun FeedSourceCard(
    source: FeedSource,
    refreshing: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("feeds_source_${source.id}"),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(source.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            Text(source.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (source.error != null) stringResource(R.string.feeds_source_error)
                else if (source.lastSuccessMillis == 0L) stringResource(R.string.feeds_never_refreshed)
                else stringResource(
                    R.string.feeds_last_refresh,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(source.lastSuccessMillis)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (source.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.feeds_edit)) }
                TextButton(onClick = onRefresh, enabled = !refreshing) { Text(stringResource(R.string.feeds_refresh)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.feeds_remove_source)) }
            }
        }
    }
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
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("feeds_folder_choices"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected == null,
            { onSelected(null) },
            label = { Text(stringResource(R.string.feeds_no_folder)) },
        )
        state.folders.forEach { folder ->
            FilterChip(
                selected == folder.id,
                { onSelected(folder.id) },
                label = { Text(folder.name, maxLines = 1) },
            )
        }
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
                modifier = Modifier.testTag("feeds_name_input"),
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
