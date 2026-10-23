package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import dev.sk2andy.materialbrowser.constellations.ConstellationGraph
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphRules
import dev.sk2andy.materialbrowser.constellations.ConstellationPresentationRules
import dev.sk2andy.materialbrowser.constellations.ConstellationSnapshot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ConstellationTestTags {
    const val Screen = "constellations_screen"
    const val Body = "constellations_body"
    const val Enable = "constellations_enable"
    const val Download = "constellations_download"
    const val Graph = "constellations_graph"
    const val OpenPage = "constellations_open_page"
    const val Disable = "constellations_disable"
    const val Search = "constellations_search"
    const val SearchClear = "constellations_search_clear"
    const val SearchNext = "constellations_search_next"
    const val SearchPrevious = "constellations_search_previous"
    const val SearchFocus = "constellations_search_focus"
    const val Expand = "constellations_expand"
    const val ListToggle = "constellations_list_toggle"
    const val PageList = "constellations_page_list"
    const val Details = "constellations_details"
    const val CloseDetails = "constellations_close_details"
    const val FitAll = "constellations_fit_all"
    fun group(id: String): String = "constellations_group_$id"
    fun page(url: String): String = "constellations_page_$url"
}

@Composable
internal fun ConstellationScreen(
    profileName: String,
    snapshot: ConstellationSnapshot,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDownloadModel: () -> Unit,
    onOpenPage: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backState = rememberNativeScreenBackState()
    NativeScreenBackHandler(state = backState, onBack = onDismiss)
    Surface(
        modifier = modifier.fillMaxSize().testTag(ConstellationTestTags.Screen)
            .predictiveBackSurface(backState.progress.value, backState.swipeEdgeSign),
        color = MaterialTheme.colorScheme.surface,
    ) {
        key(snapshot.profileId) {
            var menuExpanded by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.constellations_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.constellations_profile, profileName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (snapshot.enabled) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.cd_more_options))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.constellations_disable),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDisable()
                                    },
                                    modifier = Modifier.testTag(ConstellationTestTags.Disable),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
                ConstellationBody(snapshot, onEnable, onDownloadModel, onOpenPage)
            }
        }
    }
}

@Composable
private fun ConstellationBody(
    snapshot: ConstellationSnapshot,
    onEnable: () -> Unit,
    onDownloadModel: () -> Unit,
    onOpenPage: (String) -> Unit,
) {
    var computedGraph by remember {
        mutableStateOf<Pair<List<ConstellationDocument>, ConstellationGraph>?>(null)
    }
    LaunchedEffect(snapshot.profileId, snapshot.documents, snapshot.enabled, snapshot.modelReady) {
        if (snapshot.enabled && snapshot.modelReady) {
            val graph = withContext(Dispatchers.Default) {
                ConstellationGraphRules.build(snapshot.profileId, snapshot.documents)
            }
            computedGraph = snapshot.documents to graph
        } else {
            computedGraph = null
        }
    }
    val graph = remember(snapshot, computedGraph) {
        ConstellationPresentationRules.retainedGraph(snapshot, computedGraph?.second ?: ConstellationGraph())
    }
    val updating = snapshot.busy || computedGraph?.first != snapshot.documents
    if (snapshot.enabled && snapshot.modelReady && graph.nodes.isNotEmpty()) {
        ConstellationExplorer(graph, snapshot.error, updating, onOpenPage)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ConstellationTestTags.Body),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(20.dp),
    ) {
        item {
            Text(stringResource(R.string.constellations_description), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (snapshot.error) item { ConstellationError() }
        when {
            !snapshot.enabled -> item {
                Button(onClick = onEnable, modifier = Modifier.testTag(ConstellationTestTags.Enable)) {
                    Text(stringResource(R.string.constellations_enable))
                }
            }
            !snapshot.modelReady -> item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.constellations_download_description))
                    val progress = snapshot.downloadProgress
                    if (snapshot.busy || progress != null) {
                        if (progress != null && progress.isFinite()) {
                            val boundedProgress = progress.coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { boundedProgress }, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.constellations_downloading, (boundedProgress * 100).roundToInt()))
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.constellations_indexing))
                        }
                    } else {
                        Button(onClick = onDownloadModel, modifier = Modifier.testTag(ConstellationTestTags.Download)) {
                            Text(stringResource(if (snapshot.error) R.string.action_retry else R.string.constellations_download_model))
                        }
                    }
                }
            }
            updating -> item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.constellations_indexing))
                }
            }
            else -> item { Text(stringResource(R.string.constellations_empty)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConstellationSearchControls(
    query: String,
    onQueryChange: (String) -> Unit,
    searchActive: Boolean,
    matchCount: Int,
    focusedIndex: Int,
    focusedTitle: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    },
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.constellations_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.testTag(ConstellationTestTags.SearchClear),
                            ) {
                                Icon(Icons.Default.Close, stringResource(R.string.history_clear_search))
                            }
                        }
                    },
                    modifier = Modifier.testTag(ConstellationTestTags.Search),
                )
            },
            expanded = false,
            onExpandedChange = {},
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxWidth(),
        ) {}
        if (searchActive) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (matchCount == 0) {
                        stringResource(R.string.constellations_search_no_results)
                    } else {
                        stringResource(R.string.constellations_search_position, focusedIndex + 1, matchCount)
                    },
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                )
                IconButton(
                    onClick = onPrevious,
                    enabled = matchCount > 1,
                    modifier = Modifier.testTag(ConstellationTestTags.SearchPrevious),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.cd_find_in_page_previous))
                }
                IconButton(
                    onClick = onNext,
                    enabled = matchCount > 1,
                    modifier = Modifier.testTag(ConstellationTestTags.SearchNext),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.cd_find_in_page_next))
                }
            }
            if (focusedTitle != null) {
                Text(
                    focusedTitle,
                    modifier = Modifier.testTag(ConstellationTestTags.SearchFocus),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ConstellationError() {
    Text(stringResource(R.string.constellations_error), color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun ConstellationPageRow(
    document: ConstellationDocument,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    similarity: Float? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(document.title.ifBlank { document.url }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                document.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (similarity != null) {
                Text(
                    stringResource(R.string.constellations_connection, (similarity * 100).roundToInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
