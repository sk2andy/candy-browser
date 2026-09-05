@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
internal fun TabCard(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    cardWidth: Dp,
    cardAspectRatio: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(cardAspectRatio)
            .then(modifier)
            .clickable(
                onClick = onClick,
                role = Role.Button,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
            )
        }
    }
}

internal class TabBoundsHolder {
    var bounds: Rect? = null
}

@Composable
internal fun CompactTabGrid(
    gridState: LazyGridState,
    layout: TabOverviewGridRules.Layout,
    tabs: List<BrowserTab>,
    visible: Boolean,
    selectedTabId: String,
    initialTabId: String,
    previews: Map<String, Bitmap>,
    favicons: Map<String, Bitmap>,
    favorites: List<FavoriteEntry>,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitHeroTabId: String?,
    dismissResistanceFraction: Float,
    interactionsEnabled: Boolean,
    reorderSessionId: String?,
    reorderDraggedTabId: String?,
    reorderTranslation: (String) -> Offset,
    reorderPointerInRoot: Offset?,
    reorderCanScrollBackward: Boolean,
    reorderCanScrollForward: Boolean,
    onReorderAutoScroll: (Float) -> Unit,
    onReorderBounds: (BrowserTab, Rect) -> Unit,
    onReorderBoundsDisposed: (BrowserTab, Rect?) -> Unit,
    onPreviewBounds: (BrowserTab, Rect) -> Unit,
    onPreviewBoundsDisposed: (BrowserTab, Rect?) -> Unit,
    onSelect: (BrowserTab, Rect) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    onSwipeDismissStart: (BrowserTab) -> Boolean,
    onSwipeDismissEnd: (BrowserTab) -> Unit,
    onSwipeDismiss: (BrowserTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
    LaunchedEffect(visible, initialTabId, selectedTabId, tabs.size) {
        if (!visible || tabs.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == selectedIndex }) {
            gridState.scrollToItem(selectedIndex)
        }
    }
    val topFadeAlpha by animateFloatAsState(
        targetValue = if (gridState.canScrollBackward) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "gridTopFade",
    )
    val bottomFadeAlpha by animateFloatAsState(
        targetValue = if (gridState.canScrollForward) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "gridBottomFade",
    )
    val rootView = LocalView.current
    var gridBounds by remember { mutableStateOf<Rect?>(null) }
    TabReorderEdgeAutoScroll(
        sessionId = reorderSessionId,
        pointerInRoot = reorderPointerInRoot,
        viewportBounds = gridBounds,
        orientation = Orientation.Vertical,
        canScrollBackward = gridState.canScrollBackward && reorderCanScrollBackward,
        canScrollForward = gridState.canScrollForward && reorderCanScrollForward,
        scrollBy = { delta -> gridState.scrollBy(delta) },
        onScrolled = onReorderAutoScroll,
    )
    val overviewBackgroundColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surface,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { gridBounds = it.boundsInRoot() },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.columnCount),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(TabOverviewChromeTestTags.Grid),
            userScrollEnabled = interactionsEnabled,
            contentPadding = PaddingValues(
                horizontal = layout.contentPadding.dp,
                vertical = 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(layout.itemSpacing.dp),
            verticalArrangement = Arrangement.spacedBy(layout.itemSpacing.dp),
        ) {
            gridItemsIndexed(
                items = tabs,
                key = { _, tab -> tab.id },
                contentType = { _, _ -> "tab-grid-card" },
            ) { _, tab ->
                CompactGridTabItem(
                    tab = tab,
                    preview = previews[tab.id],
                    favicon = favicons[tab.id],
                    favorites = favorites,
                    selected = tab.id == selectedTabId,
                    initial = tab.id == initialTabId,
                    heroProgress = heroProgress,
                    heroCompleted = heroCompleted,
                    heroVisible = heroVisible,
                    exitTarget = tab.id == exitHeroTabId,
                    dismissResistanceFraction = dismissResistanceFraction,
                    interactionsEnabled = interactionsEnabled,
                    previewAspectRatio = layout.previewAspectRatio,
                    onReorderBounds = { bounds -> onReorderBounds(tab, bounds) },
                    onReorderBoundsDisposed = { bounds ->
                        onReorderBoundsDisposed(tab, bounds)
                    },
                    onPreviewBounds = { bounds -> onPreviewBounds(tab, bounds) },
                    onPreviewBoundsDisposed = { bounds ->
                        onPreviewBoundsDisposed(tab, bounds)
                    },
                    onSelect = { bounds -> onSelect(tab, bounds) },
                    onClose = { onCloseTab(tab) },
                    onSwipeDismissStart = { onSwipeDismissStart(tab) },
                    onSwipeDismissEnd = { onSwipeDismissEnd(tab) },
                    onSwipeDismiss = { onSwipeDismiss(tab) },
                    modifier = Modifier
                        .then(
                            if (reorderSessionId == null) Modifier.animateItem() else Modifier,
                        )
                        .zIndex(if (reorderDraggedTabId == tab.id) 6f else 0f)
                        .tabReorderVisualMotion(
                            sessionId = reorderSessionId,
                            isDragged = reorderDraggedTabId == tab.id,
                            targetOffset = reorderTranslation(tab.id),
                        )
                        .testTag(SnoozeTestTags.overviewTab(tab.id)),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(28.dp)
                .graphicsLayer {
                    alpha = topFadeAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    val topInRoot = gridBounds?.top ?: 0f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = overviewBackgroundColors,
                            start = Offset(0f, -topInRoot),
                            end = Offset(
                                rootView.width.toFloat(),
                                rootView.height.toFloat() - topInRoot,
                            ),
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .graphicsLayer {
                    alpha = bottomFadeAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    val bottomInRoot = gridBounds?.bottom ?: rootView.height.toFloat()
                    val topInRoot = bottomInRoot - size.height
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = overviewBackgroundColors,
                            start = Offset(0f, -topInRoot),
                            end = Offset(
                                rootView.width.toFloat(),
                                rootView.height.toFloat() - topInRoot,
                            ),
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        )
    }
}

@Composable
private fun CompactGridTabItem(
    tab: BrowserTab,
    preview: Bitmap?,
    favicon: Bitmap?,
    favorites: List<FavoriteEntry>,
    selected: Boolean,
    initial: Boolean,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    exitTarget: Boolean,
    dismissResistanceFraction: Float,
    interactionsEnabled: Boolean,
    previewAspectRatio: Float,
    onReorderBounds: (Rect) -> Unit,
    onReorderBoundsDisposed: (Rect?) -> Unit,
    onPreviewBounds: (Rect) -> Unit,
    onPreviewBoundsDisposed: (Rect?) -> Unit,
    onSelect: (Rect) -> Unit,
    onClose: () -> Unit,
    onSwipeDismissStart: () -> Boolean,
    onSwipeDismissEnd: () -> Unit,
    onSwipeDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rootView = LocalView.current
    val gestureScope = rememberCoroutineScope()
    val boundsHolder = remember(tab.id) { TabBoundsHolder() }
    val reorderBoundsHolder = remember(tab.id) { TabBoundsHolder() }
    val breakFreeProgress = remember(tab.id) { Animatable(0f) }
    var breakFreeJob by remember(tab.id) { mutableStateOf<Job?>(null) }
    var rawDismissOffset by remember(tab.id) { mutableFloatStateOf(0f) }
    var dismissOffset by remember(tab.id) { mutableFloatStateOf(0f) }
    var cardWidthPx by remember(tab.id) { mutableFloatStateOf(1f) }
    var dragActive by remember(tab.id) { mutableStateOf(false) }
    var resistanceCleared by remember(tab.id) { mutableStateOf(false) }
    var rubberbandHapticActive by remember(tab.id) { mutableStateOf(false) }
    var dismissHapticPlayed by remember(tab.id) { mutableStateOf(false) }
    var gestureRaised by remember(tab.id) { mutableStateOf(false) }
    var dismissInProgress by remember(tab.id) { mutableStateOf(false) }
    DisposableEffect(tab.id, rootView) {
        onDispose {
            breakFreeJob?.cancel()
            onPreviewBoundsDisposed(boundsHolder.bounds)
            onReorderBoundsDisposed(reorderBoundsHolder.bounds)
            if (rubberbandHapticActive) {
                rootView.stopRubberbandHaptic()
            }
            if (dismissInProgress) {
                onSwipeDismissEnd()
            }
        }
    }
    val shape = RoundedCornerShape(22.dp)
    val realCardVisible = TabOverviewHeroRules.isCardVisible(
        isInitialCard = initial,
        progress = if (heroCompleted) 1f else 0f,
        isExitTarget = exitTarget,
    )
    val dismissThreshold = cardWidthPx * TabDismissPhysics.CARD_DISMISS_THRESHOLD_FRACTION
    val dragState = rememberDraggableState { delta ->
        rawDismissOffset += delta
        val rawDistance = rawDismissOffset.absoluteValue
        val hasClearedResistance = TabDismissPhysics.hasClearedResistance(
            rawDistance = rawDistance,
            dismissThreshold = dismissThreshold,
            resistanceFraction = dismissResistanceFraction,
        )
        val shouldVibrate = TabDismissPhysics.isInResistancePhase(
            rawDistance = rawDistance,
            dismissThreshold = dismissThreshold,
            resistanceFraction = dismissResistanceFraction,
        )
        if (shouldVibrate && !rubberbandHapticActive) {
            rootView.startRubberbandHaptic()
            rubberbandHapticActive = true
        } else if (!shouldVibrate && rubberbandHapticActive) {
            rootView.stopRubberbandHaptic()
            rubberbandHapticActive = false
        }
        if (hasClearedResistance != resistanceCleared) {
            resistanceCleared = hasClearedResistance
            breakFreeJob?.cancel()
            breakFreeJob = gestureScope.launch {
                breakFreeProgress.animateTo(
                    targetValue = if (hasClearedResistance) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.72f,
                        stiffness = 800f,
                    ),
                )
            }
        }
        if (hasClearedResistance && !dismissHapticPlayed) {
            rootView.performConfirmHaptic()
            dismissHapticPlayed = true
        }
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                reorderBoundsHolder.bounds = bounds
                onReorderBounds(bounds)
            }
            .onSizeChanged { cardWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .zIndex(if (gestureRaised) 2f else 0f)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.ModulateAlpha
                val currentDismissOffset = if (dragActive) {
                    TabDismissPhysics.signedVisualDistance(
                        rawDistance = rawDismissOffset,
                        releaseProgress = breakFreeProgress.value,
                    )
                } else {
                    dismissOffset
                }
                translationX = currentDismissOffset
                val dismissProgress =
                    (currentDismissOffset.absoluteValue / (dismissThreshold * 1.7f))
                        .coerceIn(0f, 1f)
                alpha = (when {
                    initial -> TabOverviewHeroRules.compactChromeAlpha(heroProgress())
                    else -> TabOverviewHeroRules.neighborAlpha(heroProgress())
                }) * (1f - dismissProgress * 0.72f)
                val dismissScale = 1f - dismissProgress * 0.05f
                scaleX = dismissScale
                scaleY = dismissScale
                rotationZ = (currentDismissOffset / cardWidthPx).coerceIn(-1f, 1f) * 2f
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = interactionsEnabled &&
                    heroCompleted &&
                    !heroVisible &&
                    TabDeletionRules.canDelete(tab),
                onDragStarted = {
                    breakFreeJob?.cancel()
                    breakFreeProgress.snapTo(0f)
                    rootView.stopRubberbandHaptic()
                    rawDismissOffset = 0f
                    dismissOffset = 0f
                    dragActive = true
                    gestureRaised = true
                    resistanceCleared = false
                    rubberbandHapticActive = false
                    dismissHapticPlayed = false
                },
                onDragStopped = {
                    rootView.stopRubberbandHaptic()
                    rubberbandHapticActive = false
                    breakFreeJob?.cancel()
                    breakFreeProgress.stop()
                    dismissOffset = TabDismissPhysics.signedVisualDistance(
                        rawDistance = rawDismissOffset,
                        releaseProgress = breakFreeProgress.value,
                    )
                    dragActive = false
                    val farEnough = TabDismissPhysics.hasClearedResistance(
                        rawDistance = rawDismissOffset.absoluteValue,
                        dismissThreshold = dismissThreshold,
                        resistanceFraction = dismissResistanceFraction,
                    )
                    if (farEnough && onSwipeDismissStart()) {
                        dismissInProgress = true
                        gestureScope.launch {
                            try {
                                val direction = if (rawDismissOffset < 0f) -1f else 1f
                                Animatable(dismissOffset).animateTo(
                                    targetValue = direction * rootView.width * 1.1f,
                                    animationSpec = tween(
                                        durationMillis = 180,
                                        easing = FastOutSlowInEasing,
                                    ),
                                ) { dismissOffset = value }
                                onSwipeDismiss()
                            } finally {
                                dismissInProgress = false
                                gestureRaised = false
                                onSwipeDismissEnd()
                            }
                        }
                    } else {
                        gestureScope.launch {
                            Animatable(dismissOffset).animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.78f,
                                    stiffness = 520f,
                                ),
                            ) { dismissOffset = value }
                            rawDismissOffset = 0f
                            breakFreeProgress.snapTo(0f)
                            resistanceCleared = false
                            dismissHapticPlayed = false
                            gestureRaised = false
                        }
                    }
                },
            )
            .semantics { this.selected = selected }
            .clickable(
                enabled = interactionsEnabled,
                role = Role.Button,
                onClick = { boundsHolder.bounds?.let(onSelect) },
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(previewAspectRatio)
                .graphicsLayer {
                    alpha = if (
                        TabOverviewHeroRules.isGridPreviewVisible(
                            isInitialCard = initial,
                            isCardVisible = realCardVisible,
                            isHeroVisible = heroVisible,
                        )
                    ) {
                        1f
                    } else {
                        0f
                    }
                }
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    boundsHolder.bounds = bounds
                    onPreviewBounds(bounds)
                },
        ) {
            TabPreviewContent(
                tab = tab,
                preview = preview,
                favicon = favicon,
                favorites = favorites,
            )
            GridTabPreviewChrome(
                tab = tab,
                favicon = favicon,
                interactionsEnabled = interactionsEnabled,
                onClose = onClose,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

