@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailForkLifecycle
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
internal fun CandyTrailScreen(
    tab: BrowserTab,
    trail: CandyTrail,
    favicon: Bitmap?,
    forkFavicons: Map<String, Bitmap>,
    sourceBounds: Rect?,
    predictiveBackProgress: Float,
    predictiveBackEdgeSign: Int,
    onOpenTabActions: () -> Unit,
    onSelectNode: (String) -> Unit,
    onForkNode: (String) -> Boolean,
    onSelectFork: (String) -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = remember(trail.nodes, trail.forks) { CandyTrailLayoutRules.layout(trail) }
    val entryProgress = remember(tab.id) { Animatable(if (sourceBounds == null) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val rootView = LocalView.current
    var scale by rememberSaveable(tab.id) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(tab.id) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(tab.id) { mutableFloatStateOf(0f) }
    var viewportInitialized by rememberSaveable(tab.id) { mutableFloatStateOf(0f) }
    var viewportSignature by rememberSaveable(tab.id) { mutableFloatStateOf(0f) }
    var actionNodeId by remember(tab.id) { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val currentEdgeColor = MaterialTheme.colorScheme.primary
    val openForkEdgeColor = MaterialTheme.colorScheme.tertiary
    val closedForkEdgeColor = MaterialTheme.colorScheme.outline

    LaunchedEffect(tab.id, sourceBounds) {
        if (sourceBounds != null) {
            entryProgress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(30f)
            .graphicsLayer {
                val hero = entryProgress.value
                val source = sourceBounds
                val heroScaleX = if (source == null || size.width <= 0f) 1f else
                    (source.width / size.width).coerceIn(0.01f, 0.99f)
                val heroScaleY = if (source == null || size.height <= 0f) 1f else
                    (source.height / size.height).coerceIn(0.01f, 0.99f)
                val backScale = 1f - 0.04f * predictiveBackProgress.coerceIn(0f, 1f)
                scaleX = (heroScaleX + (1f - heroScaleX) * hero) * backScale
                scaleY = (heroScaleY + (1f - heroScaleY) * hero) * backScale
                alpha = hero
                if (source != null) {
                    translationX = (source.center.x - size.width / 2f) * (1f - hero)
                    translationY = (source.center.y - size.height / 2f) * (1f - hero)
                }
                translationX += size.width * 0.04f * predictiveBackProgress *
                    predictiveBackEdgeSign.coerceIn(-1, 1)
                shape = RoundedCornerShape((28f * (1f - hero) + 28f * predictiveBackProgress).dp)
                clip = hero < 1f || predictiveBackProgress > 0f
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.surfaceContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                    radius = 1_600f,
                ),
            ),
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val graphWidthPx = with(density) { layout.width.dp.toPx() }
        val graphHeightPx = with(density) { layout.height.dp.toPx() }
        LaunchedEffect(
            tab.id,
            graphWidthPx,
            graphHeightPx,
            viewportWidthPx,
            viewportHeightPx,
            viewportInitialized,
        ) {
            val nextViewportSignature = viewportWidthPx * 31f + viewportHeightPx
            if (viewportSignature != 0f && viewportSignature != nextViewportSignature) {
                viewportInitialized = 0f
            }
            viewportSignature = nextViewportSignature
            if (viewportInitialized == 0f && graphWidthPx > 0f && graphHeightPx > 0f) {
                val minimumVisible = with(density) { 72.dp.toPx() }
                val fitScale = minOf(
                    (viewportWidthPx - with(density) { 32.dp.toPx() }) / graphWidthPx,
                    (viewportHeightPx - with(density) { 184.dp.toPx() }) / graphHeightPx,
                    1f,
                )
                scale = CandyTrailViewportRules.scale(fitScale)
                val currentPosition = layout.positions
                    .firstOrNull { it.nodeId == trail.currentNodeId }
                val focusCurrent = fitScale < CandyTrailViewportRules.MIN_SCALE &&
                    currentPosition != null
                panX = if (focusCurrent) {
                    CandyTrailViewportRules.centeredPan(
                        contentCenter = with(density) {
                            (currentPosition!!.x + CandyTrailLayoutRules.NODE_WIDTH / 2f).dp.toPx()
                        },
                        viewportSize = viewportWidthPx,
                        graphSize = graphWidthPx,
                        scale = scale,
                        minimumVisible = minimumVisible,
                    )
                } else {
                    (viewportWidthPx - graphWidthPx * scale) / 2f
                }
                panY = if (focusCurrent) {
                    CandyTrailViewportRules.centeredPan(
                        contentCenter = with(density) {
                            (currentPosition!!.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f).dp.toPx()
                        },
                        viewportSize = viewportHeightPx,
                        graphSize = graphHeightPx,
                        scale = scale,
                        minimumVisible = minimumVisible,
                    )
                } else {
                    (viewportHeightPx - graphHeightPx * scale) / 2f +
                        with(density) { 36.dp.toPx() }
                }
                viewportInitialized = 1f
            }
        }

        fun zoomAroundViewportCenter(factor: Float) {
            val oldScale = scale
            val newScale = CandyTrailViewportRules.scale(oldScale * factor)
            val minimumVisible = with(density) { 72.dp.toPx() }
            panX = CandyTrailViewportRules.zoomedPan(
                value = panX,
                focalPoint = viewportWidthPx / 2f,
                oldScale = oldScale,
                newScale = newScale,
                viewportSize = viewportWidthPx,
                graphSize = graphWidthPx,
                minimumVisible = minimumVisible,
            )
            panY = CandyTrailViewportRules.zoomedPan(
                value = panY,
                focalPoint = viewportHeightPx / 2f,
                oldScale = oldScale,
                newScale = newScale,
                viewportSize = viewportHeightPx,
                graphSize = graphHeightPx,
                minimumVisible = minimumVisible,
            )
            scale = newScale
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tab.id) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = CandyTrailViewportRules.scale(scale * zoom)
                        val scaleChange = newScale / oldScale
                        panX = centroid.x - (centroid.x - panX) * scaleChange + pan.x
                        panY = centroid.y - (centroid.y - panY) * scaleChange + pan.y
                        scale = newScale
                        val minimumVisible = with(density) { 72.dp.toPx() }
                        panX = CandyTrailViewportRules.pan(
                            panX,
                            viewportWidthPx,
                            graphWidthPx,
                            scale,
                            minimumVisible,
                        )
                        panY = CandyTrailViewportRules.pan(
                            panY,
                            viewportHeightPx,
                            graphHeightPx,
                            scale,
                            minimumVisible,
                        )
                    }
                },
        ) {
            if (layout.positions.isEmpty()) {
                Text(
                    text = stringResource(R.string.candy_trail_empty),
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(layout.width.dp, layout.height.dp)
                        .graphicsLayer {
                            translationX = panX
                            translationY = panY
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        },
                ) {
                    val nodeById = remember(trail.nodes) {
                        trail.nodes.associateBy(CandyTrailNode::id)
                    }
                    CandyTrailEdges(
                        trail = trail,
                        layout = layout,
                        progress = { entryProgress.value },
                        edgeColor = edgeColor,
                        currentEdgeColor = currentEdgeColor,
                        openForkEdgeColor = openForkEdgeColor,
                        closedForkEdgeColor = closedForkEdgeColor,
                    )
                    layout.positions.forEachIndexed { index, position ->
                        val node = nodeById.getValue(position.nodeId)
                        val isCurrent = node.id == trail.currentNodeId
                        CandyTrailNodeCard(
                            node = node,
                            isCurrent = isCurrent,
                            favicon = favicon.takeIf { isCurrent },
                            modifier = Modifier
                                .offset(position.x.dp, position.y.dp)
                                .graphicsLayer {
                                    val stagger = CandyTrailMotionRules.staggeredProgress(
                                        progress = entryProgress.value,
                                        index = index,
                                        count = layout.positions.size,
                                    )
                                    alpha = stagger
                                    scaleX = 0.82f + 0.18f * stagger
                                    scaleY = scaleX
                                    translationY = (1f - stagger) * 34f
                                },
                            onClick = {
                                rootView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                scope.launch {
                                    entryProgress.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                                    onSelectNode(node.id)
                                }
                            },
                            onMore = { actionNodeId = node.id },
                        )
                    }
                    val forkById = remember(trail.forks) {
                        trail.forks.associateBy(CandyTrailFork::id)
                    }
                    layout.forkPositions.forEachIndexed { index, position ->
                        val fork = forkById.getValue(position.forkId)
                        CandyTrailForkCard(
                            fork = fork,
                            favicon = fork.destinationTabId?.let(forkFavicons::get),
                            modifier = Modifier
                                .offset(position.x.dp, position.y.dp)
                                .graphicsLayer {
                                    val stagger = CandyTrailMotionRules.staggeredProgress(
                                        progress = entryProgress.value,
                                        index = layout.positions.size + index,
                                        count = layout.positions.size + layout.forkPositions.size,
                                    )
                                    alpha = stagger
                                    scaleX = 0.78f + 0.22f * stagger
                                    scaleY = scaleX
                                    translationX = (1f - stagger) * -42f
                                },
                            onClick = {
                                scope.launch {
                                    entryProgress.animateTo(
                                        0f,
                                        tween(180, easing = FastOutSlowInEasing),
                                    )
                                    if (onSelectFork(fork.id)) {
                                        rootView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    } else {
                                        entryProgress.animateTo(
                                            1f,
                                            tween(180, easing = FastOutSlowInEasing),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        CandyTrailTopBar(
            tab = tab,
            onBack = {
                scope.launch {
                    entryProgress.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                    onDismiss()
                }
            },
            onMore = onOpenTabActions,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        CandyTrailZoomControls(
            onZoomOut = {
                zoomAroundViewportCenter(1f / 1.2f)
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            onReset = {
                viewportInitialized = 0f
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            onZoomIn = {
                zoomAroundViewportCenter(1.2f)
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        CandyTrailNodeActionsSheet(
            node = actionNodeId?.let { nodeId -> trail.nodes.firstOrNull { it.id == nodeId } },
            onFork = { nodeId ->
                actionNodeId = null
                scope.launch {
                    entryProgress.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                    if (onForkNode(nodeId)) {
                        rootView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else {
                        entryProgress.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
                    }
                }
            },
            onDismiss = { actionNodeId = null },
        )
    }
}

@Composable
private fun CandyTrailEdges(
    trail: CandyTrail,
    layout: CandyTrailLayout,
    progress: () -> Float,
    edgeColor: Color,
    currentEdgeColor: Color,
    openForkEdgeColor: Color,
    closedForkEdgeColor: Color,
) {
    val density = LocalDensity.current
    val positions = remember(layout.positions) { layout.positions.associateBy(CandyTrailNodePosition::nodeId) }
    val currentPath = remember(trail.nodes, trail.currentNodeId) { currentAncestorIds(trail) }
    val edgePaths = remember(trail.nodes, positions, density.density) {
        trail.nodes.mapIndexedNotNull { index, node ->
            val parent = node.parentId?.let(positions::get) ?: return@mapIndexedNotNull null
            val child = positions[node.id] ?: return@mapIndexedNotNull null
            val start = Offset(
                x = (parent.x + CandyTrailLayoutRules.NODE_WIDTH) * density.density,
                y = (parent.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f) * density.density,
            )
            val end = Offset(
                x = child.x * density.density,
                y = (child.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f) * density.density,
            )
            val controlDistance = (end.x - start.x) * 0.52f
            CandyTrailEdgePath(
                nodeId = node.id,
                index = index,
                path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(
                        start.x + controlDistance,
                        start.y,
                        end.x - controlDistance,
                        end.y,
                        end.x,
                        end.y,
                    )
                },
                arrow = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(end.x - 12f, end.y - 7f)
                    lineTo(end.x - 12f, end.y + 7f)
                    close()
                },
            )
        }
    }
    val forkPositions = remember(layout.forkPositions) {
        layout.forkPositions.associateBy(CandyTrailForkPosition::forkId)
    }
    val forkEdgePaths = remember(trail.forks, positions, forkPositions, density.density) {
        trail.forks.mapIndexedNotNull { index, fork ->
            val parent = positions[fork.originNodeId] ?: return@mapIndexedNotNull null
            val child = forkPositions[fork.id] ?: return@mapIndexedNotNull null
            val start = Offset(
                x = (parent.x + CandyTrailLayoutRules.NODE_WIDTH) * density.density,
                y = (parent.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f) * density.density,
            )
            val end = Offset(
                x = child.x * density.density,
                y = (child.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f) * density.density,
            )
            val controlDistance = (end.x - start.x) * 0.52f
            CandyTrailForkEdgePath(
                index = index,
                lifecycle = fork.lifecycle,
                path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(
                        start.x + controlDistance,
                        start.y,
                        end.x - controlDistance,
                        end.y,
                        end.x,
                        end.y,
                    )
                },
                arrow = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(end.x - 12f, end.y - 7f)
                    lineTo(end.x - 12f, end.y + 7f)
                    close()
                },
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        edgePaths.forEach { edge ->
            val alpha = CandyTrailMotionRules.staggeredProgress(
                progress = progress(),
                index = edge.index,
                count = trail.nodes.size,
            )
            if (alpha <= 0f) return@forEach
            val edgeOnCurrentPath =
                edge.nodeId == trail.currentNodeId || edge.nodeId in currentPath
            drawPath(
                path = edge.path,
                color = (if (edgeOnCurrentPath) currentEdgeColor else edgeColor).copy(alpha = alpha),
                style = Stroke(width = if (edgeOnCurrentPath) 4f else 2.5f, cap = StrokeCap.Round),
            )
            drawPath(
                edge.arrow,
                (if (edgeOnCurrentPath) currentEdgeColor else edgeColor).copy(alpha = alpha),
            )
        }
        forkEdgePaths.forEach { edge ->
            val alpha = CandyTrailMotionRules.staggeredProgress(
                progress = progress(),
                index = trail.nodes.size + edge.index,
                count = trail.nodes.size + trail.forks.size,
            )
            if (alpha <= 0f) return@forEach
            val color = if (edge.lifecycle == CandyTrailForkLifecycle.Open) {
                openForkEdgeColor
            } else {
                closedForkEdgeColor
            }.copy(alpha = alpha)
            drawPath(
                path = edge.path,
                color = color,
                style = Stroke(
                    width = 3.5f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)),
                ),
            )
            drawPath(edge.arrow, color)
        }
    }
}

private data class CandyTrailEdgePath(
    val nodeId: String,
    val index: Int,
    val path: Path,
    val arrow: Path,
)

private data class CandyTrailForkEdgePath(
    val index: Int,
    val lifecycle: CandyTrailForkLifecycle,
    val path: Path,
    val arrow: Path,
)

@Composable
private fun CandyTrailNodeCard(
    node: CandyTrailNode,
    isCurrent: Boolean,
    favicon: Bitmap?,
    modifier: Modifier,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val host = remember(node.url) { runCatching { Uri.parse(node.url).host.orEmpty() }.getOrDefault("") }
    val title = node.title.ifBlank { host.ifBlank { node.url } }
    val description = stringResource(
        if (isCurrent) R.string.cd_candy_trail_current_node else R.string.cd_candy_trail_node,
        title,
        host,
    )
    Surface(
        modifier = modifier
            .width(CandyTrailLayoutRules.NODE_WIDTH.dp)
            .heightIn(min = CandyTrailLayoutRules.NODE_HEIGHT.dp)
            .semantics {
                contentDescription = description
                selected = isCurrent
            }
            .then(
                if (isCurrent) Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(26.dp),
                ) else Modifier,
            )
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tonalElevation = if (isCurrent) 8.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (favicon != null && !favicon.isRecycled) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = host.take(1).uppercase().ifBlank { "•" },
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                )
                Text(
                    text = host,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            IconButton(onClick = onMore) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.cd_candy_trail_node_actions, title),
                )
            }
        }
    }
}

@Composable
private fun CandyTrailForkCard(
    fork: CandyTrailFork,
    favicon: Bitmap?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val host = remember(fork.url) { runCatching { Uri.parse(fork.url).host.orEmpty() }.getOrDefault("") }
    val title = fork.title.ifBlank { host.ifBlank { fork.url } }
    val isOpen = fork.lifecycle == CandyTrailForkLifecycle.Open
    val status = stringResource(if (isOpen) R.string.fork_status_open else R.string.fork_status_closed)
    val description = stringResource(
        if (isOpen) R.string.cd_candy_trail_fork_open else R.string.cd_candy_trail_fork_closed,
        title,
        host,
    )
    Surface(
        modifier = modifier
            .width(CandyTrailLayoutRules.NODE_WIDTH.dp)
            .heightIn(min = CandyTrailLayoutRules.NODE_HEIGHT.dp)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(30.dp, 18.dp, 30.dp, 18.dp),
        color = if (isOpen) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tonalElevation = if (isOpen) 7.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (favicon != null && !favicon.isRecycled) {
                Image(
                    bitmap = favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isOpen) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = host.take(1).uppercase().ifBlank { "↗" },
                        color = if (isOpen) MaterialTheme.colorScheme.onTertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status,
                    color = if (isOpen) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CandyTrailNodeActionsSheet(
    node: CandyTrailNode?,
    onFork: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (node == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                node.title.ifBlank { node.url },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.fork_url_only_disclaimer),
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { onFork(node.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_fork_from_here))
            }
        }
    }
}

@Composable
private fun CandyTrailTopBar(
    tab: BrowserTab,
    onBack: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_close_candy_trail))
        }
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.candy_trail_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                tab.title.ifBlank { stringResource(R.string.new_tab_title) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.cd_tab_actions))
        }
    }
}

@Composable
private fun CandyTrailZoomControls(
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier,
) {
    val zoomOutDescription = stringResource(R.string.cd_zoom_out)
    val resetDescription = stringResource(R.string.action_reset_zoom)
    Surface(
        modifier = modifier.navigationBarsPadding().padding(bottom = 16.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.semantics {
                    contentDescription = zoomOutDescription
                },
            ) {
                Text("−", fontWeight = FontWeight.Bold)
            }
            FilledIconButton(
                onClick = onReset,
                modifier = Modifier.semantics { contentDescription = resetDescription },
            ) {
                Text("◎", fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_zoom_in))
            }
        }
    }
}

private fun currentAncestorIds(trail: CandyTrail): Set<String> {
    val byId = trail.nodes.associateBy(CandyTrailNode::id)
    val ancestors = mutableSetOf<String>()
    var cursor = byId[trail.currentNodeId]?.parentId
    while (cursor != null && ancestors.add(cursor)) cursor = byId[cursor]?.parentId
    return ancestors
}
