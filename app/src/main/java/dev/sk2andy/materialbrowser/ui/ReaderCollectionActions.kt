package dev.sk2andy.materialbrowser.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedRules
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.ui.feeds.FeedArticleToggle
import kotlinx.coroutines.launch

/** Browser Reader and feed Reader share one profile-owned collection and listening queue. */
@Composable
internal fun ReaderCollectionActions(document: ReaderDocument, profileId: String) {
    val context = LocalContext.current
    val repository = remember(context, profileId) { FeedRepository.get(context, profileId) }
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val error = stringResource(R.string.feeds_error)
    val id = remember(document.sourceUrl) { FeedRules.identity(document.sourceUrl) }
    val article = state.articles.firstOrNull { it.id == id }
    val saved = article?.isStarred == true
    val queued = id in state.queue
    LaunchedEffect(repository) { repository.load() }
    fun update(starred: Boolean? = null, inQueue: Boolean? = null) {
        scope.launch {
            val result =
                if (article == null || starred == true || inQueue == true) {
                    repository.saveWebsiteArticle(document, starred, inQueue)
                } else if (starred != null) {
                    repository.setStarred(id, starred)
                } else {
                    repository.setQueued(id, inQueue == true)
                }
            if (result.isFailure) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeedArticleToggle(
                checked = saved,
                onToggle = { update(starred = !saved) },
                icon =
                    if (saved) R.drawable.ic_feed_bookmark else R.drawable.ic_feed_bookmark_border,
                description =
                    stringResource(if (saved) R.string.feeds_unsave else R.string.feeds_save),
                modifier = Modifier.testTag("reader_collection_bookmark"),
            )
            Text(
                stringResource(if (saved) R.string.feeds_unsave else R.string.feeds_save),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FeedArticleToggle(
                checked = queued,
                onToggle = { update(inQueue = !queued) },
                icon = if (queued) R.drawable.ic_feed_queue_added else R.drawable.ic_feed_queue,
                description =
                    stringResource(
                        if (queued) R.string.feeds_queue_remove else R.string.feeds_queue_add
                    ),
                modifier = Modifier.testTag("reader_collection_queue"),
            )
            Text(stringResource(R.string.feeds_queue), style = MaterialTheme.typography.labelLarge)
        }
    }
}
