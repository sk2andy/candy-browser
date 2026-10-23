package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedArticleOrigin
import dev.sk2andy.materialbrowser.feeds.FeedImportPreview
import dev.sk2andy.materialbrowser.feeds.FeedImportStatus
import dev.sk2andy.materialbrowser.feeds.FeedOfflineStatus
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedArticleActions(
    article: FeedArticle,
    state: FeedState,
    repository: FeedRepository,
    mutate: FeedMutation,
    onOpenOriginal: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tagsVisible by remember { mutableStateOf(false) }
    var revisionVisible by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = 640.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.statusBarsPadding(),
        dragHandle = {
            FeedSheetDragHandle(
                stringResource(R.string.feeds_cancel),
                Modifier.testTag("feeds_article_actions_handle"),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(article.title, style = MaterialTheme.typography.titleLarge)
            TextButton(
                onClick = {
                    mutate { repository.setRead(setOf(article.id), !article.isRead) }
                    onDismiss()
                }
            ) {
                Text(
                    stringResource(
                        if (article.isRead) R.string.feeds_unread_action else R.string.feeds_read
                    )
                )
            }
            TextButton(
                onClick = {
                    mutate { repository.setStarred(article.id, !article.isStarred) }
                    onDismiss()
                }
            ) {
                Text(
                    stringResource(
                        if (article.isStarred) R.string.feeds_unsave else R.string.feeds_save
                    )
                )
            }
            TextButton(onClick = { tagsVisible = true }) {
                Text(stringResource(R.string.feeds_tags))
            }
            if (article.offlineStatus == FeedOfflineStatus.Ready) {
                TextButton(
                    onClick = {
                        mutate { repository.updateOffline(article.id, FeedOfflineStatus.None) }
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.feeds_remove_download))
                }
            } else {
                TextButton(
                    onClick = {
                        onDownload()
                        onDismiss()
                    },
                    enabled = article.offlineStatus != FeedOfflineStatus.Downloading,
                ) {
                    Text(stringResource(offlineLabel(article.offlineStatus)))
                }
            }
            TextButton(
                onClick = {
                    mutate { repository.setQueued(article.id, article.id !in state.queue) }
                    onDismiss()
                }
            ) {
                Text(
                    stringResource(
                        if (article.id in state.queue) R.string.feeds_queue_remove
                        else R.string.feeds_queue_add
                    )
                )
            }
            TextButton(
                onClick = {
                    mutate { repository.setHidden(article.id, !article.isHidden) }
                    onDismiss()
                }
            ) {
                Text(
                    stringResource(
                        if (article.isHidden) R.string.feeds_restore else R.string.feeds_hide
                    )
                )
            }
            if (article.previousContent != null)
                TextButton(onClick = { revisionVisible = true }) {
                    Text(stringResource(R.string.feeds_revision))
                }
            if (article.origin == FeedArticleOrigin.Website) {
                TextButton(
                    onClick = {
                        mutate {
                            repository.removeWebsiteArticle(article.id).also {
                                if (it.isSuccess) onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.testTag("feed_article_delete"),
                ) {
                    Text(
                        stringResource(R.string.feeds_delete_article),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenOriginal()
                }
            ) {
                Text(stringResource(R.string.feeds_original))
            }
        }
    }
    if (tagsVisible)
        FeedTagsDialog(
            article.tags,
            onSave = { tags ->
                mutate { repository.setTags(article.id, tags) }
                tagsVisible = false
            },
            onDismiss = { tagsVisible = false },
        )
    if (revisionVisible)
        AlertDialog(
            onDismissRequest = { revisionVisible = false },
            title = { Text(stringResource(R.string.feeds_revision)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.feeds_previous),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        article.previousContent.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.feeds_current),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(article.content, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { revisionVisible = false }) {
                    Text(stringResource(R.string.feeds_cancel))
                }
            },
        )
}

@Composable
private fun FeedTagsDialog(
    tags: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_tags)) },
        text = {
            OutlinedTextField(
                text,
                { text = it },
                label = { Text(stringResource(R.string.feeds_tags_hint)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(text.split(',').map(String::trim).filter(String::isNotBlank).toSet())
                }
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
internal fun FeedImportDialog(
    preview: FeedImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_import_preview)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                preview.entries.forEach { entry ->
                    Text(
                        entry.title.ifBlank { entry.url },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            when (entry.status) {
                                FeedImportStatus.New -> R.string.feeds_import_new
                                FeedImportStatus.Existing -> R.string.feeds_import_existing
                                FeedImportStatus.Invalid -> R.string.feeds_import_invalid
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    entry.folder?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = preview.entries.any { it.status == FeedImportStatus.New },
            ) {
                Text(stringResource(R.string.feeds_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feeds_cancel)) }
        },
    )
}
