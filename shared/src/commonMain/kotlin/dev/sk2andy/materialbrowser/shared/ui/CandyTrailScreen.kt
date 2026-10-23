@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailForkLifecycle
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import dev.sk2andy.materialbrowser.ui.CandyTrailDirectedEdge
import dev.sk2andy.materialbrowser.ui.CandyTrailForkPosition
import dev.sk2andy.materialbrowser.ui.CandyTrailGraphMotionRules
import dev.sk2andy.materialbrowser.ui.CandyTrailGraphSnapshot
import dev.sk2andy.materialbrowser.ui.CandyTrailGraphTarget
import dev.sk2andy.materialbrowser.ui.CandyTrailLayout
import dev.sk2andy.materialbrowser.ui.CandyTrailLayoutRules
import dev.sk2andy.materialbrowser.ui.CandyTrailMotionRules
import dev.sk2andy.materialbrowser.ui.CandyTrailNodePosition
import dev.sk2andy.materialbrowser.ui.CandyTrailPathSegment
import dev.sk2andy.materialbrowser.ui.CandyTrailViewportRules
import dev.sk2andy.materialbrowser.ui.CandyTrailPresentationRules
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class CandyTrailStrings(
    val empty: String,
    val nodeDescription: (isCurrent: Boolean, title: String, host: String) -> String,
    val nodeActionsDescription: (title: String) -> String,
    val forkStatus: (isOpen: Boolean) -> String,
    val forkDescription: (isOpen: Boolean, title: String, host: String) -> String,
    val forkUrlOnlyDisclaimer: String,
    val forkFromHere: String,
    val close: String,
    val title: String,
    val newTabTitle: String,
    val zoomOut: String,
    val resetZoom: String,
    val zoomIn: String,
    val fitAll: String,
    val currentPage: String,
    val graphView: String,
    val listView: String,
    val focusRoute: String,
    val allBranches: String,
    val collapseBranch: String,
    val expandBranch: String,
    val hiddenPages: (Int) -> String,
)

enum class CandyTrailHaptic {
    Confirm,
    VirtualKey,
}

@Composable
fun CandyTrailScreenContent(
    tab: BrowserTab,
    trail: CandyTrail,
    favicon: ImageBitmap?,
    forkFavicons: Map<String, ImageBitmap>,
    strings: CandyTrailStrings,
    nodeActionsContainerColor: Color,
    onSelectNode: (String) -> Boolean,
    onNodeSelectionFinished: () -> Unit,
    onForkNode: (String) -> String?,
    onForkCreationFinished: (String) -> Unit,
    onSelectFork: (String) -> String?,
    onForkSelectionFinished: (String) -> Unit,
    onDismiss: () -> Unit,
    onHaptic: (CandyTrailHaptic) -> Unit,
    platformSurfaceModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    pageFavicons: Map<String, ImageBitmap> = emptyMap(),
) {
    var focusRoute by rememberSaveable(tab.id) { mutableStateOf(false) }
    var listVisible by rememberSaveable(tab.id) { mutableStateOf(false) }
    var collapsedNodeIds by rememberSaveable(tab.id) { mutableStateOf(emptyList<String>()) }
    val visibleTrail = remember(trail, focusRoute, collapsedNodeIds) {
        CandyTrailPresentationRules.visibleTrail(trail, focusRoute, collapsedNodeIds.toSet())
    }
    val hiddenCounts = remember(trail, collapsedNodeIds) {
        collapsedNodeIds.associateWith { CandyTrailPresentationRules.hiddenCount(trail, it) }
    }
    val targetLayout = remember(visibleTrail.nodes, visibleTrail.forks) {
        CandyTrailLayoutRules.layout(visibleTrail)
    }
    val layoutProgress = remember(tab.id) { Animatable(1f) }
    var layoutFrom by remember(tab.id) { mutableStateOf(targetLayout) }
    var layoutTo by remember(tab.id) { mutableStateOf(targetLayout) }
    val layout = CandyTrailPresentationRules.interpolate(layoutFrom, layoutTo, layoutProgress.value)
    // M3 Expressive default spatial / fast effects tokens. Shared Material 3 has no MotionScheme yet.
    val spatialSpec = spring<Float>(
        dampingRatio = CandyTrailGraphMotionRules.TARGET_SPRING_DAMPING_RATIO,
        stiffness = CandyTrailGraphMotionRules.TARGET_SPRING_STIFFNESS,
    )
    val effectsSpec = spring<Float>(
        dampingRatio = 1f,
        stiffness = CandyTrailGraphMotionRules.EFFECTS_SPRING_STIFFNESS,
    )
    LaunchedEffect(targetLayout) {
        layoutFrom = CandyTrailPresentationRules.interpolate(layoutFrom, layoutTo, layoutProgress.value)
        layoutTo = targetLayout
        layoutProgress.snapTo(0f)
        layoutProgress.animateTo(1f, spatialSpec)
    }
    val entryProgress = remember(tab.id) { Animatable(0f) }
    val edgeRevealProgress = remember(tab.id) {
        Animatable(1f).apply { updateBounds(lowerBound = 0f, upperBound = 1f) }
    }
    val targetRevealProgress = remember(tab.id) {
        Animatable(1f).apply { updateBounds(lowerBound = 0f, upperBound = 1f) }
    }
    val pathPulseProgress = remember(tab.id) {
        Animatable(0f).apply { updateBounds(lowerBound = 0f, upperBound = 1f) }
    }
    var scale by rememberSaveable(tab.id) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(tab.id) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(tab.id) { mutableFloatStateOf(0f) }
    var viewportInitialized by rememberSaveable(tab.id) { mutableFloatStateOf(0f) }
    var viewportSignature by rememberSaveable(tab.id) { mutableFloatStateOf(0f) }
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    var controlsHeightPx by remember { mutableFloatStateOf(0f) }
    var actionNodeId by remember(tab.id) { mutableStateOf<String?>(null) }
    var revealTargets by remember(tab.id) { mutableStateOf(emptySet<CandyTrailGraphTarget>()) }
    var pulsePath by remember(tab.id) { mutableStateOf(emptyList<CandyTrailDirectedEdge>()) }
    var commitInFlight by remember(tab.id) { mutableStateOf(false) }
    var revealGeneration by remember(tab.id) { mutableStateOf(0) }
    var pulseGeneration by remember(tab.id) { mutableStateOf(0) }
    val graphSnapshot = remember(trail.nodes, trail.forks) {
        CandyTrailGraphMotionRules.snapshot(trail)
    }
    var knownGraphSnapshot by remember(tab.id) { mutableStateOf(graphSnapshot) }
    val density = LocalDensity.current
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val currentEdgeColor = MaterialTheme.colorScheme.primary
    val openForkEdgeColor = MaterialTheme.colorScheme.tertiary
    val closedForkEdgeColor = MaterialTheme.colorScheme.outline
    val pulseColor = MaterialTheme.colorScheme.secondary

    suspend fun revealCreatedTargets(targets: Set<CandyTrailGraphTarget>) {
        if (targets.isEmpty()) return
        val generation = revealGeneration + 1
        revealGeneration = generation
        edgeRevealProgress.snapTo(0f)
        targetRevealProgress.snapTo(0f)
        revealTargets = targets
        try {
            withFrameNanos { }
            coroutineScope {
                launch {
                    edgeRevealProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = effectsSpec,
                    )
                }
                launch {
                    targetRevealProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = spatialSpec,
                    )
                }
            }
        } finally {
            if (revealGeneration == generation) revealTargets = emptySet()
        }
    }

    suspend fun runPathPulse(path: List<CandyTrailDirectedEdge>) {
        if (path.isEmpty()) return
        val generation = pulseGeneration + 1
        pulseGeneration = generation
        pathPulseProgress.snapTo(0f)
        pulsePath = path
        try {
            withFrameNanos { }
            pathPulseProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = CandyTrailGraphMotionRules.PATH_PULSE_DURATION_MILLIS,
                    easing = LinearEasing,
                ),
            )
        } finally {
            if (pulseGeneration == generation) pulsePath = emptyList()
        }
    }

    fun selectNode(nodeId: String) {
        if (commitInFlight) return
        commitInFlight = true
        try {
            if (onSelectNode(nodeId)) {
                onHaptic(CandyTrailHaptic.Confirm)
                onNodeSelectionFinished()
            }
        } finally {
            commitInFlight = false
        }
    }

    fun selectFork(forkId: String) {
        if (commitInFlight) return
        commitInFlight = true
        try {
            onSelectFork(forkId)?.let { destinationId ->
                onHaptic(CandyTrailHaptic.Confirm)
                onForkSelectionFinished(destinationId)
            }
        } finally {
            commitInFlight = false
        }
    }

    LaunchedEffect(tab.id) {
        entryProgress.animateTo(1f, effectsSpec)
        val currentNodeId = trail.currentNodeId ?: return@LaunchedEffect
        val activeRoute = CandyTrailGraphMotionRules.pathTargets(
            trail = trail,
            target = CandyTrailGraphTarget.Node(currentNodeId),
        ).map { target -> CandyTrailDirectedEdge(target = target, reversed = false) }
        runPathPulse(activeRoute)
    }

    LaunchedEffect(tab.id, graphSnapshot) {
        val additions = CandyTrailGraphMotionRules.additions(knownGraphSnapshot, graphSnapshot)
        knownGraphSnapshot = graphSnapshot
        revealCreatedTargets(additions)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(30f)
            .then(platformSurfaceModifier)
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
        val graphWidthPx = with(density) { targetLayout.width.dp.toPx() }
        val graphHeightPx = with(density) { targetLayout.height.dp.toPx() }
        LaunchedEffect(
            tab.id,
            graphWidthPx,
            graphHeightPx,
            viewportWidthPx,
            viewportHeightPx,
            headerHeightPx,
            controlsHeightPx,
            viewportInitialized,
        ) {
            val nextViewportSignature = viewportWidthPx * 31f + viewportHeightPx + headerHeightPx * 17f + controlsHeightPx * 13f
            if (viewportSignature != 0f && viewportSignature != nextViewportSignature) {
                viewportInitialized = 0f
            }
            viewportSignature = nextViewportSignature
            if (viewportInitialized == 0f && graphWidthPx > 0f && graphHeightPx > 0f) {
                val minimumVisible = with(density) { 72.dp.toPx() }
                val availableHeight = (viewportHeightPx - headerHeightPx - controlsHeightPx).coerceAtLeast(1f)
                val fitScale = minOf(
                    (viewportWidthPx - with(density) { 32.dp.toPx() }) / graphWidthPx,
                    (availableHeight - with(density) { 32.dp.toPx() }).coerceAtLeast(1f) / graphHeightPx,
                    1f,
                )
                scale = CandyTrailViewportRules.scale(fitScale)
                val currentPosition = targetLayout.positions
                    .firstOrNull { it.nodeId == trail.currentNodeId }
                val focusCurrent = fitScale < CandyTrailViewportRules.MIN_SCALE &&
                    currentPosition != null
                panX = if (focusCurrent) {
                    CandyTrailViewportRules.centeredPan(
                        contentCenter = with(density) {
                            (currentPosition.x + CandyTrailLayoutRules.NODE_WIDTH / 2f).dp.toPx()
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
                            (currentPosition.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f).dp.toPx()
                        },
                        viewportSize = viewportHeightPx,
                        graphSize = graphHeightPx,
                        scale = scale,
                        minimumVisible = minimumVisible,
                    )
                } else {
                    headerHeightPx + (availableHeight - graphHeightPx * scale) / 2f
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

        fun focusPosition(x: Float, y: Float) {
            val minimumVisible = with(density) { 72.dp.toPx() }
            scale = 1f
            panX = CandyTrailViewportRules.centeredPan(
                with(density) { (x + CandyTrailLayoutRules.NODE_WIDTH / 2f).dp.toPx() },
                viewportWidthPx, graphWidthPx, scale, minimumVisible,
            )
            val centerY = headerHeightPx + (viewportHeightPx - headerHeightPx - controlsHeightPx) / 2f
            panY = CandyTrailViewportRules.pan(
                centerY - with(density) { (y + CandyTrailLayoutRules.NODE_HEIGHT / 2f).dp.toPx() },
                viewportHeightPx, graphHeightPx, scale, minimumVisible,
            )
        }

        fun focusNode(nodeId: String) {
            targetLayout.positions.firstOrNull { it.nodeId == nodeId }?.let {
                focusPosition(it.x, it.y)
            }
        }

        if (listVisible) {
            CandyTrailList(
                trail = visibleTrail,
                favicon = favicon,
                pageFavicons = pageFavicons,
                forkFavicons = forkFavicons,
                strings = strings,
                onSelectNode = ::selectNode,
                onSelectFork = ::selectFork,
                onMore = { actionNodeId = it },
                hiddenCounts = hiddenCounts,
                headerHeight = with(density) { headerHeightPx.toDp() },
            )
        } else Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tab.id, layout, scale < CandyTrailPresentationRules.OVERVIEW_SCALE) {
                    if (scale < CandyTrailPresentationRules.OVERVIEW_SCALE) {
                        detectTapGestures { point ->
                            val target = CandyTrailPresentationRules.nearestTarget(
                                layout,
                                (point.x - panX) / scale / density.density,
                                (point.y - panY) / scale / density.density,
                            )
                            when (target) {
                                is CandyTrailGraphTarget.Node -> focusNode(target.id)
                                is CandyTrailGraphTarget.Fork -> layout.forkPositions.firstOrNull { it.forkId == target.id }
                                    ?.let { focusPosition(it.x, it.y) }
                                null -> Unit
                            }
                        }
                    }
                }
                .pointerInput(tab.id, graphWidthPx, graphHeightPx, viewportWidthPx, viewportHeightPx) {
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
                    text = strings.empty,
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
                        trail = visibleTrail,
                        layout = layout,
                        progress = { entryProgress.value },
                        revealTargets = revealTargets,
                        revealProgress = { edgeRevealProgress.value },
                        pulsePath = pulsePath,
                        pulseProgress = { pathPulseProgress.value },
                        edgeColor = edgeColor,
                        currentEdgeColor = currentEdgeColor,
                        openForkEdgeColor = openForkEdgeColor,
                        closedForkEdgeColor = closedForkEdgeColor,
                        pulseColor = pulseColor,
                        compactScale = scale.takeIf(CandyTrailPresentationRules::compact),
                    )
                    layout.positions.forEachIndexed { index, position ->
                        val node = nodeById[position.nodeId] ?: return@forEachIndexed
                        val isCurrent = node.id == trail.currentNodeId
                        CandyTrailNodeCard(
                            node = node,
                            isCurrent = isCurrent,
                            favicon = pageFavicons[node.url] ?: favicon.takeIf { isCurrent },
                            strings = strings,
                            compactScale = scale.takeIf(CandyTrailPresentationRules::compact),
                            onInspect = { focusNode(node.id) },
                            hiddenCount = hiddenCounts[node.id] ?: 0,
                            modifier = Modifier
                                .offset(position.x.dp, position.y.dp)
                                .graphicsLayer {
                                    val stagger = CandyTrailMotionRules.staggeredProgress(
                                        progress = entryProgress.value,
                                        index = index,
                                        count = layout.positions.size,
                                    )
                                    val createdProgress = if (
                                        CandyTrailGraphTarget.Node(node.id) in revealTargets
                                    ) {
                                        targetRevealProgress.value
                                    } else {
                                        1f
                                    }
                                    alpha = stagger * createdProgress
                                    scaleX = (0.82f + 0.18f * stagger) *
                                        CandyTrailGraphMotionRules.targetScale(createdProgress)
                                    scaleY = scaleX
                                    translationY = (1f - stagger) * 34f
                                },
                            onClick = { selectNode(node.id) },
                            onMore = { actionNodeId = node.id },
                        )
                    }
                    val forkById = remember(trail.forks) {
                        trail.forks.associateBy(CandyTrailFork::id)
                    }
                    layout.forkPositions.forEachIndexed { index, position ->
                        val fork = forkById[position.forkId] ?: return@forEachIndexed
                        CandyTrailForkCard(
                            fork = fork,
                            favicon = fork.destinationTabId?.let(forkFavicons::get),
                            strings = strings,
                            compactScale = scale.takeIf(CandyTrailPresentationRules::compact),
                            onInspect = { focusPosition(position.x, position.y) },
                            modifier = Modifier
                                .offset(position.x.dp, position.y.dp)
                                .graphicsLayer {
                                    val stagger = CandyTrailMotionRules.staggeredProgress(
                                        progress = entryProgress.value,
                                        index = layout.positions.size + index,
                                        count = layout.positions.size + layout.forkPositions.size,
                                    )
                                    val createdProgress = if (
                                        CandyTrailGraphTarget.Fork(fork.id) in revealTargets
                                    ) {
                                        targetRevealProgress.value
                                    } else {
                                        1f
                                    }
                                    alpha = stagger * createdProgress
                                    scaleX = (0.78f + 0.22f * stagger) *
                                        CandyTrailGraphMotionRules.targetScale(createdProgress)
                                    scaleY = scaleX
                                    translationX = (1f - stagger) * -42f
                                },
                            onClick = { selectFork(fork.id) },
                        )
                    }
                }
            }
        }

        CandyTrailTopBar(
            tab = tab,
            onBack = onDismiss,
            strings = strings,
            listVisible = listVisible,
            focusRoute = focusRoute,
            onListVisibleChange = { listVisible = it },
            onFocusRouteChange = {
                focusRoute = it
                viewportInitialized = 0f
            },
            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { headerHeightPx = it.height.toFloat() },
        )
        if (!listVisible) CandyTrailZoomControls(
            onZoomOut = {
                zoomAroundViewportCenter(1f / 1.2f)
                onHaptic(CandyTrailHaptic.VirtualKey)
            },
            onReset = {
                viewportInitialized = 0f
                onHaptic(CandyTrailHaptic.VirtualKey)
            },
            onCurrent = {
                trail.currentNodeId?.let(::focusNode)
                onHaptic(CandyTrailHaptic.VirtualKey)
            },
            onZoomIn = {
                zoomAroundViewportCenter(1.2f)
                onHaptic(CandyTrailHaptic.VirtualKey)
            },
            strings = strings,
            modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { controlsHeightPx = it.height.toFloat() },
        )
        CandyTrailNodeActionsSheet(
            node = actionNodeId?.let { nodeId -> trail.nodes.firstOrNull { it.id == nodeId } },
            onFork = { nodeId ->
                actionNodeId = null
                if (commitInFlight) return@CandyTrailNodeActionsSheet
                commitInFlight = true
                try {
                    onForkNode(nodeId)?.let { destinationId ->
                        onHaptic(CandyTrailHaptic.Confirm)
                        onForkCreationFinished(destinationId)
                    }
                } finally {
                    commitInFlight = false
                }
            },
            onDismiss = { actionNodeId = null },
            canCollapse = actionNodeId?.let { CandyTrailPresentationRules.hiddenCount(trail, it) > 0 } == true,
            isCollapsed = actionNodeId in collapsedNodeIds,
            onToggleCollapse = { nodeId ->
                collapsedNodeIds = if (nodeId in collapsedNodeIds) collapsedNodeIds - nodeId else collapsedNodeIds + nodeId
                actionNodeId = null
            },
            strings = strings,
            containerColor = nodeActionsContainerColor,
        )
    }
}

@Composable
private fun CandyTrailEdges(
    trail: CandyTrail,
    layout: CandyTrailLayout,
    progress: () -> Float,
    revealTargets: Set<CandyTrailGraphTarget>,
    revealProgress: () -> Float,
    pulsePath: List<CandyTrailDirectedEdge>,
    pulseProgress: () -> Float,
    edgeColor: Color,
    currentEdgeColor: Color,
    openForkEdgeColor: Color,
    closedForkEdgeColor: Color,
    pulseColor: Color,
    compactScale: Float?,
) {
    val density = LocalDensity.current
    val positions = remember(layout.positions) { layout.positions.associateBy(CandyTrailNodePosition::nodeId) }
    val currentPath = remember(trail.nodes, trail.currentNodeId) {
        trail.currentNodeId?.let { currentNodeId ->
            CandyTrailGraphMotionRules.pathTargets(
                trail,
                CandyTrailGraphTarget.Node(currentNodeId),
            ).toSet()
        }.orEmpty()
    }
    val halfWidth = compactScale?.let { 24f / CandyTrailPresentationRules.symbolScale(it) }
        ?: (CandyTrailLayoutRules.NODE_WIDTH / 2f)
    val startInset = CandyTrailLayoutRules.NODE_WIDTH / 2f + halfWidth
    val endInset = CandyTrailLayoutRules.NODE_WIDTH / 2f - halfWidth
    val edgePaths = remember(trail.nodes, positions, density.density, halfWidth) {
        trail.nodes.mapIndexedNotNull { index, node ->
            val parent = node.parentId?.let(positions::get) ?: return@mapIndexedNotNull null
            val child = positions[node.id] ?: return@mapIndexedNotNull null
            val start = Offset(
                x = (parent.x + startInset) * density.density,
                y = (parent.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f) * density.density,
            )
            val end = Offset(
                x = (child.x + endInset) * density.density,
                y = (child.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f) * density.density,
            )
            val geometry = CandyTrailGraphMotionRules.edgeGeometry(
                startX = start.x,
                startY = start.y,
                endX = end.x,
                endY = end.y,
            )
            val drawable = CandyTrailDrawableEdge(
                path = Path().apply {
                    moveTo(geometry.startX, geometry.startY)
                    cubicTo(
                        geometry.firstControlX,
                        geometry.firstControlY,
                        geometry.secondControlX,
                        geometry.secondControlY,
                        geometry.endX,
                        geometry.endY,
                    )
                },
            )
            CandyTrailEdgePath(
                nodeId = node.id,
                index = index,
                drawable = drawable,
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
    val forkEdgePaths = remember(trail.forks, positions, forkPositions, density.density, halfWidth) {
        trail.forks.mapIndexedNotNull { index, fork ->
            val parent = positions[fork.originNodeId] ?: return@mapIndexedNotNull null
            val child = forkPositions[fork.id] ?: return@mapIndexedNotNull null
            val start = Offset(
                x = (parent.x + startInset) * density.density,
                y = (parent.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f) * density.density,
            )
            val end = Offset(
                x = (child.x + endInset) * density.density,
                y = (child.y + CandyTrailLayoutRules.NODE_HEIGHT / 2f) * density.density,
            )
            val geometry = CandyTrailGraphMotionRules.edgeGeometry(
                startX = start.x,
                startY = start.y,
                endX = end.x,
                endY = end.y,
            )
            val drawable = CandyTrailDrawableEdge(
                path = Path().apply {
                    moveTo(geometry.startX, geometry.startY)
                    cubicTo(
                        geometry.firstControlX,
                        geometry.firstControlY,
                        geometry.secondControlX,
                        geometry.secondControlY,
                        geometry.endX,
                        geometry.endY,
                    )
                },
            )
            CandyTrailForkEdgePath(
                forkId = fork.id,
                index = index,
                lifecycle = fork.lifecycle,
                drawable = drawable,
                arrow = Path().apply {
                    moveTo(end.x, end.y)
                    lineTo(end.x - 12f, end.y - 7f)
                    lineTo(end.x - 12f, end.y + 7f)
                    close()
                },
            )
        }
    }
    val edgePathsByNode = remember(edgePaths) { edgePaths.associateBy(CandyTrailEdgePath::nodeId) }
    val forkEdgePathsById = remember(forkEdgePaths) {
        forkEdgePaths.associateBy(CandyTrailForkEdgePath::forkId)
    }
    Canvas(Modifier.fillMaxSize()) {
        edgePaths.forEach { edge ->
            val alpha = CandyTrailMotionRules.staggeredProgress(
                progress = progress(),
                index = edge.index,
                count = trail.nodes.size,
            )
            if (alpha <= 0f) return@forEach
            val target = CandyTrailGraphTarget.Node(edge.nodeId)
            val edgeOnCurrentPath = target in currentPath
            val targetProgress = if (target in revealTargets) {
                CandyTrailGraphMotionRules.revealProgress(revealProgress())
            } else {
                1f
            }
            val color = (if (edgeOnCurrentPath) currentEdgeColor else edgeColor).copy(alpha = alpha)
            drawCandyTrailEdgeSegment(
                edge = edge.drawable,
                startFraction = 0f,
                endFraction = targetProgress,
                color = color,
                style = Stroke(width = if (edgeOnCurrentPath) 4f else 2.5f, cap = StrokeCap.Round),
            )
            drawPath(
                edge.arrow,
                color = (if (edgeOnCurrentPath) currentEdgeColor else edgeColor).copy(alpha = alpha),
                alpha = CandyTrailGraphMotionRules.arrowAlpha(targetProgress),
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
            val target = CandyTrailGraphTarget.Fork(edge.forkId)
            val targetProgress = if (target in revealTargets) {
                CandyTrailGraphMotionRules.revealProgress(revealProgress())
            } else {
                1f
            }
            drawCandyTrailEdgeSegment(
                edge = edge.drawable,
                startFraction = 0f,
                endFraction = targetProgress,
                color = color,
                style = Stroke(
                    width = 3.5f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f)),
                ),
            )
            drawPath(
                edge.arrow,
                color,
                alpha = CandyTrailGraphMotionRules.arrowAlpha(targetProgress),
            )
        }

        val orderedPulseEdges = pulsePath.mapNotNull { directedEdge ->
            val edge = when (val target = directedEdge.target) {
                is CandyTrailGraphTarget.Node -> edgePathsByNode[target.id]?.drawable
                is CandyTrailGraphTarget.Fork -> forkEdgePathsById[target.id]?.drawable
            }
            edge?.let { drawable -> directedEdge to drawable }
        }
        val totalPulseLength = orderedPulseEdges
            .sumOf { (_, edge) -> edge.length.toDouble() }
            .toFloat()
        val pulseAlpha = CandyTrailGraphMotionRules.pulseAlpha(pulseProgress())
        if (pulseAlpha > 0f && totalPulseLength > 0f) {
            var edgeStartDistance = 0f
            orderedPulseEdges.forEach { (directedEdge, edge) ->
                val segment = CandyTrailGraphMotionRules.pulseSegment(
                    progress = pulseProgress(),
                    edgeStartDistance = edgeStartDistance,
                    edgeLength = edge.length,
                    totalLength = totalPulseLength,
                )
                if (segment != null) {
                    val directedSegment = CandyTrailGraphMotionRules.directedSegment(
                        segment = segment,
                        reversed = directedEdge.reversed,
                    )
                    drawCandyTrailEdgeSegment(
                        edge = edge,
                        startFraction = directedSegment.startFraction,
                        endFraction = directedSegment.endFraction,
                        color = pulseColor.copy(alpha = pulseAlpha * 0.24f),
                        style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawCandyTrailEdgeSegment(
                        edge = edge,
                        startFraction = directedSegment.startFraction,
                        endFraction = directedSegment.endFraction,
                        color = pulseColor.copy(alpha = pulseAlpha),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                edgeStartDistance += edge.length
            }
        }
    }
}

private class CandyTrailDrawableEdge(val path: Path) {
    val measure = PathMeasure().apply { setPath(path, forceClosed = false) }
    val length: Float = measure.length
}

private data class CandyTrailEdgePath(
    val nodeId: String,
    val index: Int,
    val drawable: CandyTrailDrawableEdge,
    val arrow: Path,
)

private data class CandyTrailForkEdgePath(
    val forkId: String,
    val index: Int,
    val lifecycle: CandyTrailForkLifecycle,
    val drawable: CandyTrailDrawableEdge,
    val arrow: Path,
)

private fun DrawScope.drawCandyTrailEdgeSegment(
    edge: CandyTrailDrawableEdge,
    startFraction: Float,
    endFraction: Float,
    color: Color,
    style: Stroke,
) {
    val safeStart = startFraction.coerceIn(0f, 1f)
    val safeEnd = endFraction.coerceIn(safeStart, 1f)
    if (safeEnd <= safeStart || edge.length <= 0f) return
    if (safeStart == 0f && safeEnd == 1f) {
        drawPath(edge.path, color, style = style)
        return
    }
    val segment = Path()
    edge.measure.getSegment(
        startDistance = edge.length * safeStart,
        stopDistance = edge.length * safeEnd,
        destination = segment,
        startWithMoveTo = true,
    )
    drawPath(segment, color, style = style)
}

@Composable
private fun CandyTrailPageIcon(
    favicon: ImageBitmap?,
    host: String,
    modifier: Modifier,
) {
    if (favicon != null) {
        Image(favicon, null, modifier, contentScale = ContentScale.Fit)
    } else {
        Box(
            modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                host.take(1).uppercase().ifBlank { "•" },
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CandyTrailCompactNode(
    title: String,
    description: String,
    favicon: ImageBitmap?,
    current: Boolean,
    fork: Boolean,
    scale: Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // Keep symbols readable until the dense overview, where tapping the surrounding graph zooms in.
    val safeScale = CandyTrailPresentationRules.symbolScale(scale)
    Box(
        modifier = modifier.size(CandyTrailLayoutRules.NODE_WIDTH.dp, CandyTrailLayoutRules.NODE_HEIGHT.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (current && scale < CandyTrailPresentationRules.OVERVIEW_SCALE) {
            Box(
                Modifier
                    .size(18.dp)
                    .graphicsLayer { scaleX = 1f / scale; scaleY = scaleX }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Surface(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = 1f / safeScale; scaleY = scaleX }
                .semantics { contentDescription = description; selected = current }
                .clickable(role = Role.Button, onClick = onClick),
            shape = if (fork) RoundedCornerShape(16.dp, 8.dp, 16.dp, 8.dp) else CircleShape,
            color = when {
                current -> MaterialTheme.colorScheme.primaryContainer
                fork -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            },
            border = if (current) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            shadowElevation = if (current) 4.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (favicon != null) {
                    Image(favicon, null, Modifier.size(28.dp), contentScale = ContentScale.Fit)
                } else {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CandyTrailList(
    trail: CandyTrail,
    favicon: ImageBitmap?,
    pageFavicons: Map<String, ImageBitmap>,
    forkFavicons: Map<String, ImageBitmap>,
    strings: CandyTrailStrings,
    onSelectNode: (String) -> Unit,
    onSelectFork: (String) -> Unit,
    onMore: (String) -> Unit,
    hiddenCounts: Map<String, Int>,
    headerHeight: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = headerHeight + 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(trail.nodes.sortedByDescending { it.visitedAt }, key = { "node:${it.id}" }) { node ->
            val current = node.id == trail.currentNodeId
            CandyTrailNodeCard(
                node = node,
                isCurrent = current,
                favicon = pageFavicons[node.url] ?: favicon.takeIf { current },
                strings = strings,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelectNode(node.id) },
                onMore = { onMore(node.id) },
                hiddenCount = hiddenCounts[node.id] ?: 0,
            )
        }
        items(trail.forks.sortedByDescending { it.createdAt }, key = { "fork:${it.id}" }) { fork ->
            CandyTrailForkCard(
                fork = fork,
                favicon = fork.destinationTabId?.let(forkFavicons::get),
                strings = strings,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelectFork(fork.id) },
            )
        }
    }
}

@Composable
private fun CandyTrailNodeCard(
    node: CandyTrailNode,
    isCurrent: Boolean,
    favicon: ImageBitmap?,
    strings: CandyTrailStrings,
    modifier: Modifier,
    onClick: () -> Unit,
    onMore: () -> Unit,
    compactScale: Float? = null,
    onInspect: () -> Unit = onClick,
    hiddenCount: Int = 0,
) {
    val host = remember(node.url) { node.url.browserHost() }
    val title = node.title.ifBlank { host.ifBlank { node.url } }
    val description = strings.nodeDescription(isCurrent, title, host)
    if (compactScale != null) {
        CandyTrailCompactNode(
            title = host.take(1).uppercase().ifBlank { "•" },
            description = description,
            favicon = favicon,
            current = isCurrent,
            fork = false,
            scale = compactScale,
            modifier = modifier,
            onClick = onInspect,
        )
        return
    }
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
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CandyTrailPageIcon(favicon, host, Modifier.size(32.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isCurrent) strings.currentPage else host,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = onMore) {
                    Icon(Icons.Default.MoreVert, strings.nodeActionsDescription(title))
                }
            }
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            )
            if (hiddenCount > 0) {
                Text(
                    strings.hiddenPages(hiddenCount),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun CandyTrailForkCard(
    fork: CandyTrailFork,
    favicon: ImageBitmap?,
    strings: CandyTrailStrings,
    modifier: Modifier,
    onClick: () -> Unit,
    compactScale: Float? = null,
    onInspect: () -> Unit = onClick,
) {
    val host = remember(fork.url) { fork.url.browserHost() }
    val title = fork.title.ifBlank { host.ifBlank { fork.url } }
    val isOpen = fork.lifecycle == CandyTrailForkLifecycle.Open
    val status = strings.forkStatus(isOpen)
    val description = strings.forkDescription(isOpen, title, host)
    if (compactScale != null) {
        CandyTrailCompactNode(
            title = "↗",
            description = description,
            favicon = favicon,
            current = false,
            fork = true,
            scale = compactScale,
            modifier = modifier,
            onClick = onInspect,
        )
        return
    }
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
            if (favicon != null) {
                Image(
                    bitmap = favicon,
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
    canCollapse: Boolean,
    isCollapsed: Boolean,
    onToggleCollapse: (String) -> Unit,
    strings: CandyTrailStrings,
    containerColor: Color,
) {
    if (node == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(
                node.title.ifBlank { node.url },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                node.url,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                strings.forkUrlOnlyDisclaimer,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (canCollapse || isCollapsed) {
                FilledTonalButton(
                    onClick = { onToggleCollapse(node.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isCollapsed) strings.expandBranch else strings.collapseBranch)
                }
            }
            TextButton(
                onClick = { onFork(node.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.forkFromHere)
            }
        }
    }
}

@Composable
private fun CandyTrailTopBar(
    tab: BrowserTab,
    onBack: () -> Unit,
    strings: CandyTrailStrings,
    listVisible: Boolean,
    focusRoute: Boolean,
    onListVisibleChange: (Boolean) -> Unit,
    onFocusRouteChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
    ) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, strings.close)
                }
                Column(Modifier.weight(1f)) {
                    Text(strings.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        tab.title.ifBlank { strings.newTabTitle },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CandyTrailViewButton(
                    selected = !listVisible,
                    onClick = { onListVisibleChange(false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.AccountTree, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.graphView)
                }
                CandyTrailViewButton(
                    selected = listVisible,
                    onClick = { onListVisibleChange(true) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.listView)
                }
            }
            FilterChip(
                selected = focusRoute,
                onClick = { onFocusRouteChange(!focusRoute) },
                label = { Text(if (focusRoute) strings.allBranches else strings.focusRoute) },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun CandyTrailViewButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val semantics = modifier.semantics { this.selected = selected }
    if (selected) FilledTonalButton(onClick, semantics, content = content)
    else OutlinedButton(onClick, semantics, content = content)
}

@Composable
private fun CandyTrailZoomControls(
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onCurrent: () -> Unit,
    onZoomIn: () -> Unit,
    strings: CandyTrailStrings,
    modifier: Modifier,
) {
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
                    contentDescription = strings.zoomOut
                },
            ) {
                Text("−", fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onReset) {
                Icon(Icons.Default.FitScreen, strings.fitAll)
            }
            FilledIconButton(onClick = onCurrent) {
                Icon(Icons.Default.MyLocation, strings.currentPage)
            }
            IconButton(onClick = onZoomIn) {
                Icon(Icons.Default.Add, strings.zoomIn)
            }
        }
    }
}

private fun String.browserHost(): String {
    val authority = substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
    if (authority.startsWith('[')) {
        return authority.substringBefore(']', missingDelimiterValue = "")
            .takeIf(String::isNotEmpty)
            ?.plus(']')
            .orEmpty()
    }
    return authority.substringBefore(':')
}
