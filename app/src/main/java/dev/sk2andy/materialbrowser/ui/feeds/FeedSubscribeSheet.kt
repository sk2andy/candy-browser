package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.feeds.FeedCandidate
import dev.sk2andy.materialbrowser.feeds.FeedPreview
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedRules
import dev.sk2andy.materialbrowser.feeds.FeedState
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedSubscribeDialog(
    repository: FeedRepository,
    state: FeedState,
    initialUrl: String,
    mutate: FeedMutation,
    onDismiss: () -> Unit,
    onSubscribed: () -> Unit = {},
    previewRequest: suspend (String) -> Result<FeedPreview> = repository::preview,
    discoverRequest: suspend (String) -> Result<List<FeedCandidate>> = repository::discover,
    subscribeRequest: suspend (FeedPreview, String?) -> Result<Unit> = repository::subscribe,
) {
    val urlState =
        if (repository.isPrivate) remember(initialUrl) { mutableStateOf(initialUrl) }
        else rememberSaveable(repository, initialUrl) { mutableStateOf(initialUrl) }
    var url by urlState
    var preview by remember(initialUrl) { mutableStateOf<FeedPreview?>(null) }
    var candidates by remember(initialUrl) { mutableStateOf(emptyList<FeedCandidate>()) }
    val folderState =
        if (repository.isPrivate) remember(initialUrl) { mutableStateOf<String?>(null) }
        else rememberSaveable(repository, initialUrl) { mutableStateOf<String?>(null) }
    var folderId by folderState
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var request by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val duplicate =
        preview?.let { item ->
            state.sources.any { FeedRules.identity(it.url) == FeedRules.identity(item.url) }
        } == true

    fun invalidate() {
        generation++
        request?.cancel()
        request = null
        preview = null
        candidates = emptyList()
        error = null
        loading = false
    }
    fun requestPreview(address: String) {
        invalidate()
        val valid = FeedRules.safeUrl(address.trim())
        if (valid == null) {
            error = R.string.feeds_preview_invalid
            return
        }
        url = valid
        val token = generation
        loading = true
        focusManager.clearFocus()
        request =
            scope.launch {
                try {
                    val result = previewRequest(valid)
                    coroutineContext.ensureActive()
                    if (token != generation) return@launch
                    result.fold(
                        onSuccess = { preview = it },
                        onFailure = { failure ->
                            if (failure is IOException)
                                error = R.string.feeds_preview_connection_error
                            else {
                                val discovery = discoverRequest(valid)
                                coroutineContext.ensureActive()
                                if (token != generation) return@launch
                                discovery.fold(
                                    onSuccess = {
                                        candidates = it
                                        if (it.isEmpty()) error = R.string.feeds_preview_invalid
                                    },
                                    onFailure = {
                                        error =
                                            if (it is IOException)
                                                R.string.feeds_preview_connection_error
                                            else R.string.feeds_preview_invalid
                                    },
                                )
                            }
                        },
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (token == generation) error = R.string.feeds_preview_connection_error
                } finally {
                    if (token == generation) loading = false
                }
            }
    }
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && url == initialUrl) requestPreview(initialUrl)
    }
    DisposableEffect(repository) { onDispose { request?.cancel() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 640.dp,
        modifier = Modifier.statusBarsPadding().testTag("feeds_subscribe_sheet"),
        dragHandle = {
            FeedSheetDragHandle(
                stringResource(R.string.feeds_cancel),
                Modifier.testTag("feeds_subscribe_handle"),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.feeds_add),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.feeds_manual_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        invalidate()
                        url = it
                    },
                    label = { Text(stringResource(R.string.feeds_url)) },
                    modifier = Modifier.fillMaxWidth().testTag("feeds_subscribe_url"),
                    singleLine = true,
                    enabled = !saving,
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { requestPreview(url) }),
                )
                if (repository.isPrivate) {
                    Text(
                        stringResource(R.string.feeds_private_preview),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (loading)
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().testTag("feeds_subscribe_loading")
                    )
                error?.let { message ->
                    Text(
                        stringResource(message),
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("feeds_subscribe_error"),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                candidates.forEach { candidate ->
                    TextButton(
                        onClick = { requestPreview(candidate.url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(candidate.title.ifBlank { candidate.url })
                    }
                }
                preview?.let { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                stringResource(R.string.feeds_preview_count, item.articles.size),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.feeds_latest_articles),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (item.articles.isEmpty())
                        Text(
                            stringResource(R.string.feeds_preview_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    item.articles.take(4).forEach { article ->
                        Text(
                            article.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FeedFolderChoice(state, folderId, { folderId = it })
                    if (duplicate) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Text(stringResource(R.string.feeds_subscribed))
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val item = preview
                    if (item == null) requestPreview(url)
                    else if (!repository.isPrivate && !duplicate && !saving) {
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                val result = subscribeRequest(item, folderId)
                                coroutineContext.ensureActive()
                                result.fold(
                                    onSuccess = {
                                        onSubscribed()
                                        onDismiss()
                                    },
                                    onFailure = { error = R.string.feeds_subscription_failed },
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                error = R.string.feeds_subscription_failed
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("feeds_subscribe_confirm"),
                enabled =
                    !loading &&
                        !saving &&
                        if (preview == null) url.isNotBlank()
                        else !duplicate && !repository.isPrivate,
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else
                    Text(
                        stringResource(
                            if (preview == null) R.string.feeds_preview
                            else R.string.feeds_subscribe
                        )
                    )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.feeds_cancel))
            }
        }
    }
}
