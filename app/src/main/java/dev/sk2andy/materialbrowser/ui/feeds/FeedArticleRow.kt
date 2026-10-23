package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedArticleOrigin
import dev.sk2andy.materialbrowser.feeds.FeedOfflineStatus
import dev.sk2andy.materialbrowser.feeds.FeedState

@Composable
internal fun FeedArticleRow(
    article: FeedArticle,
    state: FeedState,
    onOpen: () -> Unit,
    onActions: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleQueue: () -> Unit,
) {
    val source =
        state.sources
            .filter { it.id in article.sourceIds }
            .joinToString { it.title }
            .ifBlank {
                stringResource(
                    if (article.origin == FeedArticleOrigin.Website) R.string.feeds_websites
                    else R.string.feeds_feed_articles
                )
            }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(
                        Modifier.size(32.dp).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            source.take(1).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Text(
                    source,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onActions,
                    modifier = Modifier.testTag("feed_actions_${article.id}"),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        stringResource(R.string.feeds_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth()
                    .testTag("feed_article_${article.id}")
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (article.summary.isNotBlank()) {
                    Text(
                        article.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (article.offlineStatus != FeedOfflineStatus.None) {
                        FeedArticleStatus(
                            text = stringResource(offlineLabel(article.offlineStatus)),
                            icon =
                                if (article.offlineStatus == FeedOfflineStatus.Ready)
                                    Icons.Default.Check
                                else null,
                            color =
                                when (article.offlineStatus) {
                                    FeedOfflineStatus.Ready ->
                                        MaterialTheme.colorScheme.secondaryContainer
                                    FeedOfflineStatus.Failed ->
                                        MaterialTheme.colorScheme.errorContainer
                                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                            contentColor =
                                when (article.offlineStatus) {
                                    FeedOfflineStatus.Ready ->
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    FeedOfflineStatus.Failed ->
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                    if (article.previousContent != null) {
                        FeedArticleStatus(
                            text = stringResource(R.string.feeds_updated),
                            icon = Icons.Default.Refresh,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                FeedArticleToggle(
                    checked = article.id in state.queue,
                    onToggle = onToggleQueue,
                    icon =
                        if (article.id in state.queue) R.drawable.ic_feed_queue_added
                        else R.drawable.ic_feed_queue,
                    description =
                        stringResource(
                            if (article.id in state.queue) R.string.feeds_queue_remove
                            else R.string.feeds_queue_add
                        ),
                    modifier = Modifier.testTag("feed_queue_${article.id}"),
                )
                FeedArticleToggle(
                    checked = article.isStarred,
                    onToggle = onToggleSaved,
                    icon =
                        if (article.isStarred) R.drawable.ic_feed_bookmark
                        else R.drawable.ic_feed_bookmark_border,
                    description =
                        stringResource(
                            if (article.isStarred) R.string.feeds_unsave else R.string.feeds_save
                        ),
                    modifier = Modifier.testTag("feed_bookmark_${article.id}"),
                )
            }
        }
    }
}

@Composable
internal fun FeedArticleToggle(
    checked: Boolean,
    onToggle: () -> Unit,
    icon: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle() },
        modifier = modifier,
        shape = if (checked) MaterialTheme.shapes.medium else CircleShape,
        colors =
            IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    ) {
        Icon(painterResource(icon), description)
    }
}

@Composable
private fun FeedArticleStatus(text: String, icon: ImageVector?, color: Color, contentColor: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color, contentColor = contentColor) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}
