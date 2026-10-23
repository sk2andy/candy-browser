@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.constellations.ConstellationGraph
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphLabelAnchor
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphLabelRules
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphRules
import dev.sk2andy.materialbrowser.constellations.ConstellationGraphViewport
import dev.sk2andy.materialbrowser.constellations.ConstellationPresentationRules
import dev.sk2andy.materialbrowser.constellations.ConstellationSearchRules
import dev.sk2andy.materialbrowser.constellations.ConstellationTopicGroup
import kotlin.math.min

internal val ConstellationViewportKey = SemanticsPropertyKey<ConstellationGraphViewport>("ConstellationViewport")

@Composable
internal fun ConstellationMap(
    graph: ConstellationGraph,
    groups: List<ConstellationTopicGroup>,
    selectedUrl: String?,
    selectedGroup: ConstellationTopicGroup?,
    searchActive: Boolean,
    matchedUrls: Set<String>,
    focusedUrl: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var viewport by remember { mutableStateOf(ConstellationGraphViewport()) }
    var beforeSearch by remember { mutableStateOf<ConstellationGraphViewport?>(null) }
    var directManipulation by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var lastSelectedUrl by remember { mutableStateOf<String?>(null) }
    var lastGroupId by remember { mutableStateOf<String?>(null) }
    val highlightedUrl = selectedUrl ?: focusedUrl
    val neighbors = remember(graph, highlightedUrl) { ConstellationPresentationRules.neighbors(graph, highlightedUrl) }
    val focusUrls = when {
        highlightedUrl != null -> neighbors + highlightedUrl
        selectedGroup != null -> selectedGroup.urls
        else -> emptySet()
    }
    LaunchedEffect(searchActive, focusedUrl, selectedUrl, selectedGroup?.id, canvasSize) {
        directManipulation = false
        val selectionChanged = selectedUrl != null && selectedUrl != lastSelectedUrl
        val groupChanged = selectedGroup?.id != lastGroupId
        lastSelectedUrl = selectedUrl
        lastGroupId = selectedGroup?.id
        if (searchActive && beforeSearch == null) beforeSearch = viewport
        if (!searchActive && beforeSearch != null) {
            val restored = beforeSearch!!
            beforeSearch = null
            if (!selectionChanged && !groupChanged) {
                viewport = restored
                return@LaunchedEffect
            }
        }
        if (selectedUrl != null || selectedGroup != null && !searchActive) {
            viewport = ConstellationPresentationRules.focusViewport(graph, focusUrls, canvasSize.width.toFloat(), canvasSize.height.toFloat())
        } else if (searchActive) {
            graph.nodes.firstOrNull { it.document.url == focusedUrl }?.let {
                viewport = ConstellationSearchRules.focusViewport(it, viewport, canvasSize.width.toFloat(), canvasSize.height.toFloat())
            }
        } else if (groupChanged) {
            viewport = ConstellationGraphViewport()
        }
    }
    val cameraSpec = if (directManipulation) snap() else MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val cameraScale by animateFloatAsState(viewport.scale, cameraSpec, label = "constellationScale")
    val cameraX by animateFloatAsState(viewport.panX, cameraSpec, label = "constellationPanX")
    val cameraY by animateFloatAsState(viewport.panY, cameraSpec, label = "constellationPanY")
    val renderedViewport = ConstellationGraphViewport(cameraScale, cameraX, cameraY)
    val currentViewport by rememberUpdatedState(renderedViewport)
    val positions = graph.nodes.associate { node ->
        key(node.document.url) {
            val position by animateOffsetAsState(Offset(node.x, node.y), MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "constellationNode")
            val radius = min(canvasSize.width, canvasSize.height) * 0.42f * cameraScale
            node.document.url to Offset(canvasSize.width / 2f + position.x * radius + cameraX,
                canvasSize.height / 2f + position.y * radius + cameraY)
        }
    }
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, colors.secondary)
    val groupByUrl = remember(groups) { groups.flatMapIndexed { index, group -> group.urls.map { it to index } }.toMap() }
    val nodeEmphasis = graph.nodes.associate { node ->
        key(node.document.url) {
            val url = node.document.url
            val active = when {
                selectedUrl != null -> url in focusUrls
                searchActive -> url in matchedUrls
                focusUrls.isNotEmpty() -> url in focusUrls
                else -> true
            }
            val alpha by animateFloatAsState(if (active) 1f else 0.22f,
                MaterialTheme.motionScheme.fastEffectsSpec(), label = "constellationEmphasis")
            val radius by animateFloatAsState(if (url == highlightedUrl) 11f else if (url in neighbors) 7f else 5f,
                MaterialTheme.motionScheme.fastSpatialSpec(), label = "constellationNodeRadius")
            url to (alpha to radius)
        }
    }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelPadding = with(density) { 4.dp.toPx() }
    val labelMaxWidth = with(density) { minOf(160.dp.roundToPx(), (canvasSize.width - 32.dp.roundToPx()).coerceAtLeast(1)) }
    val labelLayouts = remember(graph.nodes, textMeasurer, labelStyle, labelMaxWidth) {
        graph.nodes.associate { node ->
            node.document.url to textMeasurer.measure(node.document.title.ifBlank { node.document.url },
                style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = labelMaxWidth))
        }
    }
    val visibleUrls = positions.filterValues { it.x in 0f..canvasSize.width.toFloat() && it.y in 0f..canvasSize.height.toFloat() }.keys
    val priorities = ConstellationPresentationRules.labelPriorities(graph, highlightedUrl,
        if (searchActive) matchedUrls else emptySet(), cameraScale,
        canvasSize.width / density.density, canvasSize.height / density.density, visibleUrls)
    val labels = ConstellationGraphLabelRules.layout(
        anchors = graph.nodes.map { node ->
            val position = positions.getValue(node.document.url)
            val text = labelLayouts.getValue(node.document.url)
            val showLabel = node.document.url in priorities
            ConstellationGraphLabelAnchor(id = node.document.url, x = position.x, y = position.y,
                nodeRadius = with(density) { 16.dp.toPx() },
                labelWidth = if (showLabel) text.size.width + labelPadding * 2 else 0f,
                labelHeight = if (showLabel) text.size.height + labelPadding * 2 else 0f,
                selected = node.document.url == highlightedUrl, priority = priorities[node.document.url] ?: 0)
        },
        width = canvasSize.width.toFloat(), height = canvasSize.height.toFloat(),
        padding = with(density) { 12.dp.toPx() }, gap = with(density) { 4.dp.toPx() },
    )
    val currentPositions by rememberUpdatedState(positions)
    val currentLabels by rememberUpdatedState(labels)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val zoomOutDescription = stringResource(R.string.cd_zoom_out)
    val graphDescription = stringResource(R.string.constellations_map_description, graph.nodes.size)
    Surface(modifier, shape = RoundedCornerShape(32.dp), color = colors.surfaceContainerLow) {
        Column {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Canvas(
                    Modifier.fillMaxSize().clipToBounds().testTag(ConstellationTestTags.Graph)
                        .semantics {
                            contentDescription = graphDescription
                            this[ConstellationViewportKey] = renderedViewport
                        }
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                directManipulation = true
                                viewport = ConstellationGraphRules.transformViewport(currentViewport, zoom, pan.x, pan.y,
                                    centroid.x - size.width / 2f, centroid.y - size.height / 2f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { location ->
                                val label = currentLabels.firstOrNull {
                                    location.x in it.left..it.right && location.y in it.top..it.bottom
                                }
                                val nearest = currentPositions.entries
                                    .minByOrNull { (_, position) -> (position - location).getDistanceSquared() }
                                    ?.takeIf { (_, position) -> (position - location).getDistance() <= 28.dp.toPx() }?.key
                                (label?.id ?: nearest)?.let(currentOnSelect)
                            }
                        },
                ) {
                    groups.forEachIndexed { index, group ->
                        val points = group.urls.mapNotNull(positions::get)
                        if (points.isNotEmpty()) {
                            val center = Offset(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
                            val radius = (points.maxOf { (it - center).getDistance() } + 48.dp.toPx()).coerceAtLeast(48.dp.toPx())
                            val color = palette[index % palette.size]
                            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = if (selectedGroup?.id == group.id) 0.18f else 0.09f),
                                color.copy(alpha = 0f)), center, radius), radius, center)
                        }
                    }
                    graph.edges.forEach { edge ->
                        val selected = highlightedUrl == edge.sourceUrl || highlightedUrl == edge.targetUrl
                        val alpha = min(nodeEmphasis.getValue(edge.sourceUrl).first, nodeEmphasis.getValue(edge.targetUrl).first)
                        drawLine((if (selected) colors.primary else colors.outlineVariant).copy(alpha = alpha),
                            positions.getValue(edge.sourceUrl), positions.getValue(edge.targetUrl),
                            strokeWidth = if (selected) 2.5.dp.toPx() else 1.dp.toPx())
                    }
                    labels.forEach { label ->
                        val position = positions.getValue(label.id)
                        drawLine(colors.outlineVariant.copy(alpha = nodeEmphasis.getValue(label.id).first), position,
                            Offset(position.x.coerceIn(label.left, label.right), position.y.coerceIn(label.top, label.bottom)),
                            strokeWidth = 1.dp.toPx())
                    }
                    graph.nodes.forEach { node ->
                        val url = node.document.url
                        val position = positions.getValue(url)
                        val (alpha, radius) = nodeEmphasis.getValue(url)
                        val color = if (url == highlightedUrl) colors.primary else palette[(groupByUrl[url] ?: 0) % palette.size]
                        drawCircle(color.copy(alpha = alpha * 0.12f), radius = (radius + 9).dp.toPx(), center = position)
                        drawCircle(color.copy(alpha = alpha), radius = radius.dp.toPx(), center = position)
                        if (url == highlightedUrl) {
                            drawCircle(colors.surface, radius = 4.dp.toPx(), center = position)
                            drawCircle(color, radius = (radius + 6).dp.toPx(), center = position, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                    labels.forEach { label ->
                        val selected = label.id == highlightedUrl
                        drawRoundRect(
                            color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
                            topLeft = Offset(label.left, label.top),
                            size = Size(label.width, label.height),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                        )
                        val textColor = if (selected) colors.onPrimaryContainer else colors.onSurface
                        drawText(
                            textLayoutResult = labelLayouts.getValue(label.id),
                            color = textColor.copy(alpha = nodeEmphasis.getValue(label.id).first),
                            topLeft = Offset(label.left + labelPadding, label.top + labelPadding),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    directManipulation = false
                    viewport = ConstellationGraphRules.transformViewport(currentViewport, 0.8f, 0f, 0f)
                }) {
                    Text(
                        "−",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { contentDescription = zoomOutDescription },
                    )
                }
                FilledTonalButton(onClick = {
                    directManipulation = false
                    viewport = ConstellationGraphViewport()
                }, modifier = Modifier.testTag(ConstellationTestTags.FitAll)) {
                    Text(stringResource(R.string.constellations_fit_all))
                }
                IconButton(onClick = {
                    directManipulation = false
                    viewport = ConstellationGraphRules.transformViewport(currentViewport, 1.25f, 0f, 0f)
                }) {
                    Icon(Icons.Default.Add, stringResource(R.string.cd_zoom_in))
                }
            }
        }
    }
}
