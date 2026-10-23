package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotation
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationCoordinator
import dev.sk2andy.materialbrowser.browser.annotation.WebAnnotationMarkdownRules

@Composable
internal fun WebAnnotationDialogs(
    coordinator: WebAnnotationCoordinator,
    onOpenLink: (String) -> Unit,
) {
    if (coordinator.isLibraryVisible && coordinator.editor == null && coordinator.reader == null) {
        WebAnnotationLibrarySheet(
            annotations = coordinator.pageAnnotations,
            onScrollTo = coordinator::scrollTo,
            onRead = coordinator::read,
            onEdit = coordinator::edit,
            onDismiss = coordinator::dismissLibrary,
        )
    }
    coordinator.reader?.let { annotation ->
        WebAnnotationReaderSheet(
            annotation = annotation,
            scrollFailed = coordinator.scrollFailed,
            onOpenLink = onOpenLink,
            onEdit = { coordinator.edit(annotation) },
            onScrollTo = { coordinator.scrollTo(annotation) },
            onDismiss = coordinator::dismissReader,
        )
    }
    coordinator.editor?.let { annotation ->
        WebAnnotationNoteEditor(
            annotation = annotation,
            failed = coordinator.failed,
            saving = coordinator.isSaving,
            canDelete = annotation in coordinator.pageAnnotations,
            onSave = coordinator::save,
            onDelete = { coordinator.delete(annotation) },
            onDismiss = coordinator::dismissEditor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebAnnotationLibrarySheet(
    annotations: List<WebAnnotation>,
    onScrollTo: (WebAnnotation) -> Unit,
    onRead: (WebAnnotation) -> Unit,
    onEdit: (WebAnnotation) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.web_annotation_title)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.web_annotation_close))
            }
        }
        Text(
            stringResource(
                if (annotations.isEmpty()) R.string.web_annotation_empty else R.string.web_annotation_list_hint,
            ),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).testTag("web_annotation_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(annotations, key = { _, value -> value.id }) { index, value ->
                WebAnnotationListItem(
                    annotation = value,
                    first = index == 0,
                    last = index == annotations.lastIndex,
                    onScrollTo = { onScrollTo(value) },
                    onRead = { onRead(value) },
                    onEdit = { onEdit(value) },
                )
            }
        }
    }
}

@Composable
private fun WebAnnotationListItem(
    annotation: WebAnnotation,
    first: Boolean,
    last: Boolean,
    onScrollTo: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
) {
    var menuExpanded by remember(annotation.id) { mutableStateOf(false) }
    val preview = remember(annotation.note) { WebAnnotationMarkdownRules.plainText(annotation.note) }
    val goToLabel = stringResource(R.string.web_annotation_go_to)
    val moreLabel = stringResource(R.string.cd_more_options)
    val outerCorners = MaterialTheme.shapes.large
    val innerCorners = MaterialTheme.shapes.extraSmall
    val shape = innerCorners.copy(
        topStart = if (first) outerCorners.topStart else innerCorners.topStart,
        topEnd = if (first) outerCorners.topEnd else innerCorners.topEnd,
        bottomStart = if (last) outerCorners.bottomStart else innerCorners.bottomStart,
        bottomEnd = if (last) outerCorners.bottomEnd else innerCorners.bottomEnd,
    )
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClickLabel = goToLabel, onClick = onScrollTo)
            .testTag("web_annotation_item_${annotation.id}"),
        headlineContent = {
            Text(annotation.quote, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                preview.ifBlank { stringResource(R.string.web_annotation_no_note) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .testTag("web_annotation_actions_${annotation.id}")
                        .semantics { contentDescription = "$moreLabel: ${annotation.quote}" },
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.web_annotation_read)) },
                        onClick = {
                            menuExpanded = false
                            onRead()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.web_annotation_edit)) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebAnnotationReaderSheet(
    annotation: WebAnnotation,
    scrollFailed: Boolean,
    onOpenLink: (String) -> Unit,
    onEdit: () -> Unit,
    onScrollTo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.web_annotation_read)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = onEdit) { Text(stringResource(R.string.web_annotation_edit)) }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.web_annotation_close))
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { WebAnnotationQuote(annotation.quote) }
            if (scrollFailed) {
                item {
                    Text(
                        stringResource(R.string.web_annotation_not_found),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                if (annotation.note.isBlank()) {
                    Text(
                        stringResource(R.string.web_annotation_no_note),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    WebAnnotationMarkdown(note = annotation.note, onOpenLink = onOpenLink)
                }
            }
            item {
                TextButton(onClick = onScrollTo) { Text(stringResource(R.string.web_annotation_go_to)) }
            }
        }
    }
}

@Composable
internal fun WebAnnotationQuote(quote: String, maxLines: Int = Int.MAX_VALUE) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            quote,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
