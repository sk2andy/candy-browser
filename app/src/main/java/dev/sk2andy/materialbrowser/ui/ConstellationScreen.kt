package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import dev.sk2andy.materialbrowser.constellations.ConstellationGraph
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphRules
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphLabelAnchor
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphLabelRules
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphViewport
import dev.sk2andy.materialbrowser.constellations.ConstellationSearchRules
import dev.sk2andy.materialbrowser.constellations.ConstellationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

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
        computedGraph = null
        if (snapshot.enabled && snapshot.modelReady) {
            val graph = withContext(Dispatchers.Default) {
                ConstellationGraphRules.build(snapshot.profileId, snapshot.documents)
            }
            computedGraph = snapshot.documents to graph
        }
    }
    val graph = computedGraph?.takeIf {
        snapshot.enabled && snapshot.modelReady && it.first == snapshot.documents
    }?.second ?: ConstellationGraph()
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    val selectedPage = graph.nodes.firstOrNull { it.document.url == selectedUrl }?.document
    var query by remember { mutableStateOf("") }
    var focusedMatchUrl by remember { mutableStateOf<String?>(null) }
    val searchActive = ConstellationSearchRules.isSearching(query)
    val searchIndex = remember(snapshot.profileId, graph.nodes) {
        ConstellationSearchRules.index(snapshot.profileId, graph.nodes)
    }
    val matches = remember(snapshot.profileId, searchIndex, query) {
        ConstellationSearchRules.matchingUrls(snapshot.profileId, searchIndex, query)
    }
    val matchedUrls = remember(matches) { matches.toSet() }
    val focusedUrl = if (searchActive) {
        focusedMatchUrl?.takeIf { it in matchedUrls } ?: matches.firstOrNull()
    } else {
        null
    }
    val focusedPage = graph.nodes.firstOrNull { it.document.url == focusedUrl }?.document
    val listedNodes = if (searchActive) graph.nodes.filter { it.document.url in matchedUrls } else graph.nodes
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(ConstellationTestTags.Body),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
    ) {
        item {
            Text(
                stringResource(R.string.constellations_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                        Button(
                            onClick = onDownloadModel,
                            modifier = Modifier.testTag(ConstellationTestTags.Download),
                        ) {
                            Text(
                                stringResource(
                                    if (snapshot.error) R.string.action_retry else R.string.constellations_download_model,
                                ),
                            )
                        }
                    }
                }
            }
            else -> {
                if (snapshot.busy || computedGraph == null) item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.constellations_indexing))
                    }
                }
                if (graph.nodes.isEmpty()) {
                    if (!snapshot.busy && computedGraph != null) item {
                        Text(stringResource(R.string.constellations_empty))
                    }
                } else {
                    item {
                        ConstellationSearchControls(
                            query = query,
                            onQueryChange = { value ->
                                query = ConstellationSearchRules.boundQuery(value)
                                focusedMatchUrl = null
                            },
                            searchActive = searchActive,
                            matchCount = matches.size,
                            focusedIndex = matches.indexOf(focusedUrl),
                            focusedTitle = focusedPage?.let { it.title.ifBlank { it.url } },
                            onPrevious = { focusedMatchUrl = ConstellationSearchRules.step(matches, focusedUrl, -1) },
                            onNext = { focusedMatchUrl = ConstellationSearchRules.step(matches, focusedUrl, 1) },
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.constellations_page_count, graph.nodes.size))
                            ConstellationMap(
                                graph = graph,
                                selectedUrl = selectedUrl,
                                searchActive = searchActive,
                                matchedUrls = matchedUrls,
                                focusedUrl = focusedUrl,
                                onSelect = { selectedUrl = it },
                            )
                            Text(
                                stringResource(R.string.constellations_graph_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.constellations_pages),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    items(listedNodes, key = { it.document.url }) { node ->
                        ConstellationPageRow(
                            document = node.document,
                            onSelect = { selectedUrl = node.document.url },
                            modifier = Modifier.testTag(ConstellationTestTags.page(node.document.url)),
                        )
                    }
                }
            }
        }
    }
    if (selectedPage != null) {
        ConstellationPageDialog(
            document = selectedPage,
            graph = graph,
            onSelect = { selectedUrl = it },
            onOpenPage = onOpenPage,
            onDismiss = { selectedUrl = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConstellationSearchControls(
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
private fun ConstellationError() {
    Text(stringResource(R.string.constellations_error), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ConstellationPageRow(
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

@Composable
private fun ConstellationPageDialog(
    document: ConstellationDocument,
    graph: ConstellationGraph,
    onSelect: (String) -> Unit,
    onOpenPage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val neighbors = graph.edges.mapNotNull { edge ->
        val url = when (document.url) {
            edge.sourceUrl -> edge.targetUrl
            edge.targetUrl -> edge.sourceUrl
            else -> return@mapNotNull null
        }
        graph.nodes.firstOrNull { it.document.url == url }?.document?.let { it to edge.similarity }
    }.sortedByDescending { it.second }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.title.ifBlank { document.url }, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(document.url, style = MaterialTheme.typography.bodySmall) }
                if (document.excerpt.isNotBlank()) item { Text(document.excerpt, maxLines = 8, overflow = TextOverflow.Ellipsis) }
                item {
                    Text(stringResource(R.string.constellations_related_pages), style = MaterialTheme.typography.titleSmall)
                }
                if (neighbors.isEmpty()) item { Text(stringResource(R.string.constellations_no_connections)) }
                items(neighbors, key = { it.first.url }) { (neighbor, similarity) ->
                    ConstellationPageRow(neighbor, onSelect = { onSelect(neighbor.url) }, similarity = similarity)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onOpenPage(document.url) },
                modifier = Modifier.testTag(ConstellationTestTags.OpenPage),
            ) { Text(stringResource(R.string.action_open_original_page)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        },
    )
}

@Composable
private fun ConstellationMap(
    graph: ConstellationGraph,
    selectedUrl: String?,
    searchActive: Boolean,
    matchedUrls: Set<String>,
    focusedUrl: String?,
    onSelect: (String) -> Unit,
) {
    var viewport by remember { mutableStateOf(ConstellationGraphViewport()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(searchActive, focusedUrl, canvasSize) {
        if (!searchActive) {
            viewport = ConstellationGraphViewport()
        } else {
            graph.nodes.firstOrNull { it.document.url == focusedUrl }?.let { node ->
                viewport = ConstellationSearchRules.focusViewport(
                    node, viewport, canvasSize.width.toFloat(), canvasSize.height.toFloat(),
                )
            }
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val zoomOutDescription = stringResource(R.string.cd_zoom_out)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val labelPadding = with(density) { 3.dp.toPx() }
    val labelMaxWidth = with(density) {
        minOf(144.dp.roundToPx(), (canvasSize.width - 30.dp.roundToPx()).coerceAtLeast(1))
    }
    val labelLayouts = remember(graph.nodes, textMeasurer, labelStyle, labelMaxWidth) {
        graph.nodes.associate { node ->
            node.document.url to textMeasurer.measure(
                node.document.title.ifBlank { node.document.url },
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = labelMaxWidth),
            )
        }
    }
    val positions = remember(graph, canvasSize, viewport) {
        val radius = min(canvasSize.width, canvasSize.height) * 0.42f * viewport.scale
        graph.nodes.associate { node ->
            node.document.url to Offset(
                canvasSize.width / 2f + node.x * radius + viewport.panX,
                canvasSize.height / 2f + node.y * radius + viewport.panY,
            )
        }
    }
    val highlightedUrl = focusedUrl ?: selectedUrl
    val labels = remember(graph, positions, highlightedUrl, viewport.scale, labelLayouts, density, searchActive, matchedUrls) {
        ConstellationGraphLabelRules.layout(
            anchors = graph.nodes.map { node ->
                val position = positions.getValue(node.document.url)
                val showLabel = node.document.url == highlightedUrl || node.document.url in matchedUrls && searchActive ||
                    graph.nodes.size <= 24 || viewport.scale >= 2f
                val text = labelLayouts.getValue(node.document.url)
                ConstellationGraphLabelAnchor(
                    id = node.document.url,
                    x = position.x,
                    y = position.y,
                    nodeRadius = with(density) { 14.dp.toPx() },
                    labelWidth = if (showLabel) text.size.width + labelPadding * 2 else 0f,
                    labelHeight = if (showLabel) text.size.height + labelPadding * 2 else 0f,
                    selected = node.document.url == highlightedUrl,
                )
            },
            width = canvasSize.width.toFloat(),
            height = canvasSize.height.toFloat(),
            padding = with(density) { 12.dp.toPx() },
            gap = with(density) { 4.dp.toPx() },
        )
    }
    Column {
        Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
            Canvas(
                modifier = Modifier.fillMaxWidth().height(320.dp).clipToBounds().testTag(ConstellationTestTags.Graph)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            viewport = ConstellationGraphRules.transformViewport(
                                viewport, zoom, pan.x, pan.y,
                                centroid.x - size.width / 2f, centroid.y - size.height / 2f,
                            )
                        }
                    }
                    .pointerInput(positions) {
                        detectTapGestures { location ->
                            positions.entries.minByOrNull { (_, position) -> (position - location).getDistanceSquared() }
                                ?.takeIf { (_, position) -> (position - location).getDistance() <= 28.dp.toPx() }
                                ?.let { onSelect(it.key) }
                        }
                    },
            ) {
                graph.edges.forEach { edge ->
                    val selected = highlightedUrl == edge.sourceUrl || highlightedUrl == edge.targetUrl
                    val dimmed = searchActive && edge.sourceUrl !in matchedUrls && edge.targetUrl !in matchedUrls
                    drawLine(
                        color = (if (selected) colors.primary else colors.outlineVariant)
                            .copy(alpha = if (dimmed) 0.2f else 1f),
                        start = positions.getValue(edge.sourceUrl),
                        end = positions.getValue(edge.targetUrl),
                        strokeWidth = if (selected) 2.dp.toPx() else 1.dp.toPx(),
                    )
                }
                labels.forEach { label ->
                    val position = positions.getValue(label.id)
                    val dimmed = searchActive && label.id !in matchedUrls
                    drawLine(
                        color = colors.outlineVariant.copy(alpha = if (dimmed) 0.2f else 1f),
                        start = position,
                        end = Offset(
                            position.x.coerceIn(label.left, label.right),
                            position.y.coerceIn(label.top, label.bottom),
                        ),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                graph.nodes.forEach { node ->
                    val position = positions.getValue(node.document.url)
                    if (position.x !in -80f..size.width + 80f || position.y !in -40f..size.height + 40f) return@forEach
                    val selected = node.document.url == highlightedUrl
                    val dimmed = searchActive && node.document.url !in matchedUrls
                    drawCircle(colors.primary.copy(alpha = if (dimmed) 0.03f else 0.12f), radius = 14.dp.toPx(), center = position)
                    drawCircle(
                        colors.primary.copy(alpha = if (dimmed) 0.2f else 1f),
                        radius = if (selected) 8.dp.toPx() else 5.dp.toPx(),
                        center = position,
                    )
                    if (selected) drawCircle(colors.primary, radius = 13.dp.toPx(), center = position, style = Stroke(1.dp.toPx()))
                }
                labels.forEach { label ->
                    drawRoundRect(
                        colors.surfaceContainerLow,
                        topLeft = Offset(label.left, label.top),
                        size = androidx.compose.ui.geometry.Size(label.width, label.height),
                    )
                    drawText(
                        labelLayouts.getValue(label.id),
                        color = colors.onSurface.copy(alpha = if (searchActive && label.id !in matchedUrls) 0.3f else 1f),
                        topLeft = Offset(label.left + labelPadding, label.top + labelPadding),
                    )
                }
            }
        }
        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { viewport = ConstellationGraphRules.transformViewport(viewport, 0.8f, 0f, 0f) },
                modifier = Modifier.semantics { contentDescription = zoomOutDescription },
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(onClick = { viewport = ConstellationGraphViewport() }) {
                Text(stringResource(R.string.action_reset_zoom))
            }
            IconButton(onClick = { viewport = ConstellationGraphRules.transformViewport(viewport, 1.25f, 0f, 0f) }) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_zoom_in))
            }
        }
    }
}
