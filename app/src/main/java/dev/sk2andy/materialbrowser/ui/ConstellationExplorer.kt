@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import dev.sk2andy.materialbrowser.constellations.ConstellationGraph
import dev.sk2andy.materialbrowser.constellations.ConstellationPresentationRules
import dev.sk2andy.materialbrowser.constellations.ConstellationSearchRules

@Composable
internal fun ConstellationExplorer(
    graph: ConstellationGraph,
    error: Boolean,
    updating: Boolean,
    onOpenPage: (String) -> Unit,
) {
    MaterialTheme(motionScheme = remember { MotionScheme.expressive() }) {
        ConstellationExplorerContent(graph, error, updating, onOpenPage)
    }
}

@Composable
private fun ConstellationExplorerContent(
    graph: ConstellationGraph,
    error: Boolean,
    updating: Boolean,
    onOpenPage: (String) -> Unit,
) {
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var focusedMatchUrl by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var listVisible by rememberSaveable { mutableStateOf(false) }
    val selectedPage = graph.nodes.firstOrNull { it.document.url == selectedUrl }?.document
    val groups = remember(graph) { ConstellationPresentationRules.groups(graph) }
    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
    LaunchedEffect(graph.nodes, groups) {
        if (selectedUrl != null && selectedPage == null) selectedUrl = null
        if (selectedGroupId != null && selectedGroup == null) selectedGroupId = null
    }
    val profileId = graph.nodes.firstOrNull()?.document?.profileId.orEmpty()
    val searchActive = ConstellationSearchRules.isSearching(query)
    val searchIndex = remember(profileId, graph.nodes) { ConstellationSearchRules.index(profileId, graph.nodes) }
    val matches = remember(profileId, searchIndex, query) {
        ConstellationSearchRules.matchingUrls(profileId, searchIndex, query)
    }
    val matchedUrls = remember(matches) { matches.toSet() }
    val focusedUrl = if (searchActive) focusedMatchUrl?.takeIf { it in matchedUrls } ?: matches.firstOrNull() else null
    val focusedPage = graph.nodes.firstOrNull { it.document.url == focusedUrl }?.document
    val listedNodes = graph.nodes.filter {
        (!searchActive || it.document.url in matchedUrls) &&
            (selectedGroup == null || it.document.url in selectedGroup.urls)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val selectPage: (String) -> Unit = {
        keyboard?.hide()
        focusManager.clearFocus()
        selectedUrl = it
        listVisible = false
    }
    BackHandler(enabled = selectedPage != null || listVisible || expanded) {
        when {
            selectedPage != null -> selectedUrl = null
            listVisible -> listVisible = false
            else -> expanded = false
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag(ConstellationTestTags.Body)) {
        val compactHeight = maxHeight < 520.dp
        val panelHeight = (maxHeight * 0.43f).coerceAtMost(320.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (expanded) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!compactHeight || selectedPage == null) ConstellationSearchControls(
                query = query,
                onQueryChange = {
                    query = ConstellationSearchRules.boundQuery(it)
                    focusedMatchUrl = null
                    selectedUrl = null
                    if (ConstellationSearchRules.isSearching(it)) selectedGroupId = null
                },
                searchActive = searchActive,
                matchCount = matches.size,
                focusedIndex = matches.indexOf(focusedUrl),
                focusedTitle = focusedPage?.let { it.title.ifBlank { it.url } },
                onPrevious = {
                    selectedUrl = null
                    focusedMatchUrl = ConstellationSearchRules.step(matches, focusedUrl, -1)
                },
                onNext = {
                    selectedUrl = null
                    focusedMatchUrl = ConstellationSearchRules.step(matches, focusedUrl, 1)
                },
            )
            if (error) ConstellationError()
            if (updating) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (!compactHeight && !expanded) Text(
                        stringResource(R.string.constellations_explore),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.constellations_page_count, graph.nodes.size),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = {
                        listVisible = !listVisible
                        selectedUrl = null
                    },
                    modifier = Modifier.testTag(ConstellationTestTags.ListToggle),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        stringResource(if (listVisible) R.string.constellations_hide_list else R.string.constellations_show_list),
                        tint = if (listVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        expanded = !expanded
                        if (expanded) listVisible = false
                    },
                    modifier = Modifier.testTag(ConstellationTestTags.Expand),
                ) {
                    Icon(
                        painterResource(if (expanded) R.drawable.ic_constellation_collapse else R.drawable.ic_constellation_expand),
                        stringResource(if (expanded) R.string.constellations_collapse_map else R.string.constellations_expand_map),
                    )
                }
            }
            if (!expanded && !compactHeight && groups.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { selectedGroupId = null },
                        label = { Text(stringResource(R.string.constellations_all_topics)) },
                    )
                    groups.forEach { group ->
                        FilterChip(
                            selected = selectedGroup?.id == group.id,
                            onClick = {
                                selectedGroupId = group.id
                                selectedUrl = null
                                query = ""
                            },
                            label = { Text(group.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.widthIn(max = 220.dp).testTag(ConstellationTestTags.group(group.id)),
                        )
                    }
                }
            }
            ConstellationMap(
                graph = graph,
                groups = groups,
                selectedUrl = selectedPage?.url,
                selectedGroup = selectedGroup,
                searchActive = searchActive,
                matchedUrls = matchedUrls,
                focusedUrl = focusedUrl,
                onSelect = selectPage,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            if (selectedPage != null) {
                ConstellationPageDetails(
                    document = selectedPage,
                    graph = graph,
                    onSelect = selectPage,
                    onOpenPage = onOpenPage,
                    onDismiss = { selectedUrl = null },
                    modifier = Modifier.fillMaxWidth().height(panelHeight),
                )
            } else if (listVisible) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(panelHeight).testTag(ConstellationTestTags.PageList),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    if (listedNodes.isEmpty()) item { Text(stringResource(R.string.constellations_search_no_results)) }
                    items(listedNodes, key = { it.document.url }) { node ->
                        ConstellationPageRow(node.document, onSelect = { selectPage(node.document.url) },
                            modifier = Modifier.testTag(ConstellationTestTags.page(node.document.url)))
                    }
                }
            } else if (!expanded) {
                Text(stringResource(R.string.constellations_explore_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun ConstellationPageDetails(
    document: ConstellationDocument,
    graph: ConstellationGraph,
    onSelect: (String) -> Unit,
    onOpenPage: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neighbors = remember(graph, document.url) {
        graph.edges.mapNotNull { edge ->
            val url = when (document.url) {
                edge.sourceUrl -> edge.targetUrl
                edge.targetUrl -> edge.sourceUrl
                else -> return@mapNotNull null
            }
            graph.nodes.firstOrNull { it.document.url == url }?.document?.let { it to edge.similarity }
        }.sortedByDescending { it.second }
    }
    Surface(
        modifier = modifier.testTag(ConstellationTestTags.Details),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = document.title.ifBlank { document.url },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag(ConstellationTestTags.CloseDetails)) {
                    Icon(Icons.Default.Close, stringResource(R.string.constellations_close_details))
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(document.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (document.excerpt.isNotBlank()) item { Text(document.excerpt, style = MaterialTheme.typography.bodyMedium) }
                item { Text(stringResource(R.string.constellations_related_pages), style = MaterialTheme.typography.titleSmall) }
                if (neighbors.isEmpty()) item { Text(stringResource(R.string.constellations_no_connections)) }
                items(neighbors, key = { it.first.url }) { (neighbor, similarity) ->
                    ConstellationPageRow(neighbor, onSelect = { onSelect(neighbor.url) }, similarity = similarity)
                }
            }
            Button(
                onClick = { onOpenPage(document.url) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(ConstellationTestTags.OpenPage),
            ) {
                Text(stringResource(R.string.action_open_original_page))
            }
        }
    }
}
