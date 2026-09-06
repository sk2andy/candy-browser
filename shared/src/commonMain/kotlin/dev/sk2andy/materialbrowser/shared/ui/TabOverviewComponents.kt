package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TabOverviewHeroVisuals(
    val title: String,
    val subtitle: String,
    val hasFavicon: Boolean,
    val chromeContainerColor: Color,
    val pinnedContentDescription: String,
    val closeContentDescription: String,
    val titleTestTag: String,
    val closeTestTag: String,
    val favicon: @Composable (Dp) -> Unit,
    val incognitoIcon: @Composable (Modifier, Color) -> Unit,
    val blankIcon: @Composable (Modifier) -> Unit,
    val pinnedIcon: @Composable (Modifier, Color) -> Unit,
)

class TabOverviewHeroPagerReorder(
    val animationTabId: String? = null,
    val layoutReady: Boolean = true,
    val pageSlotWidthPx: Float = 0f,
    val progress: () -> Float = { 0f },
    val indexDelta: (String) -> Int = { 0 },
    val activeTabId: String? = null,
    val visualModifier: @Composable (BrowserTab) -> Modifier = { Modifier },
    val dropAnimating: Boolean = false,
)

class TabOverviewHeroPagerHaptics(
    val startRubberband: () -> Unit = {},
    val stopRubberband: () -> Unit = {},
    val confirm: () -> Unit = {},
)

object TabOverviewPagerRules {
    fun key(tabs: List<BrowserTab>, page: Int): String =
        tabs.getOrNull(page)?.id ?: "tab-page-$page"

    fun tabAt(tabs: List<BrowserTab>, page: Int): BrowserTab? = tabs.getOrNull(page)
}

@Composable
fun TabCard(
    cardWidth: Dp,
    cardAspectRatio: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    previewContent: @Composable () -> Unit,
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
            previewContent()
        }
    }
}

@Composable
fun TabOverviewHeroPager(
    pagerState: PagerState,
    tabs: List<BrowserTab>,
    initialTabId: String,
    tabCardWidth: Dp,
    cardAspectRatio: Float,
    pageSlotWidth: Dp,
    pageHorizontalPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    heroProgress: () -> Float,
    heroCompleted: Boolean,
    heroVisible: Boolean,
    rootHeightPx: Float,
    dismissResistanceFraction: Float,
    dismissingTabId: String?,
    movingTabId: String?,
    movingProgress: () -> Float,
    exitHeroTabId: String?,
    tabActionsTabId: String?,
    reorder: TabOverviewHeroPagerReorder,
    operationScope: CoroutineScope,
    currentTabs: () -> List<BrowserTab>,
    selectedTabId: () -> String,
    onDismissingTabChanged: (String?) -> Unit,
    onSelectDismissAnchor: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseOverview: () -> Unit,
    onStartExitHero: (BrowserTab, Rect) -> Unit,
    onCardBounds: (BrowserTab, Rect, Boolean) -> Unit,
    onCardBoundsDisposed: (BrowserTab, Rect?) -> Unit,
    canDelete: (BrowserTab) -> Boolean,
    haptics: TabOverviewHeroPagerHaptics,
    previewContent: @Composable (BrowserTab) -> Unit,
    titleContent: @Composable (BrowserTab, Boolean, Modifier) -> Unit,
    cardModifier: (BrowserTab) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
) {
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(10),
        decayAnimationSpec = exponentialDecay(frictionMultiplier = 0.62f),
        snapAnimationSpec = spring(dampingRatio = 0.95f, stiffness = 1_000f),
        snapPositionalThreshold = 0.11f,
    )
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = pageHorizontalPadding,
            top = topPadding,
            end = pageHorizontalPadding,
            bottom = bottomPadding,
        ),
        pageSpacing = 0.dp,
        pageSize = PageSize.Fixed(pageSlotWidth),
        flingBehavior = pagerFlingBehavior,
        verticalAlignment = Alignment.CenterVertically,
        beyondViewportPageCount = if (reorder.animationTabId == null) {
            1
        } else {
            (tabs.size - 1).coerceAtLeast(0)
        },
        userScrollEnabled = dismissingTabId == null &&
            movingTabId == null &&
            exitHeroTabId == null &&
            reorder.animationTabId == null &&
            !reorder.dropAnimating &&
            reorder.activeTabId == null &&
            tabActionsTabId == null,
        key = { page ->
            if (reorder.animationTabId == null && !reorder.dropAnimating) {
                TabOverviewPagerRules.key(tabs, page)
            } else {
                "tab-reorder-$page"
            }
        },
    ) { page ->
        val tab = TabOverviewPagerRules.tabAt(tabs, page) ?: return@HorizontalPager
        val cardGestureScope = rememberCoroutineScope()
        var dismissOffset by remember(tab.id) { mutableFloatStateOf(0f) }
        var rawDismissOffset by remember(tab.id) { mutableFloatStateOf(0f) }
        val breakFreeProgress = remember(tab.id) { Animatable(0f) }
        var breakFreeJob by remember(tab.id) { mutableStateOf<Job?>(null) }
        var dragActive by remember(tab.id) { mutableStateOf(false) }
        var resistanceCleared by remember(tab.id) { mutableStateOf(false) }
        var rubberbandHapticActive by remember(tab.id) { mutableStateOf(false) }
        var dismissHapticPlayed by remember(tab.id) { mutableStateOf(false) }
        var cardBounds by remember(tab.id) { mutableStateOf<Rect?>(null) }
        DisposableEffect(tab.id) {
            onDispose { onCardBoundsDisposed(tab, cardBounds) }
        }
        val density = LocalDensity.current
        val dismissThreshold = with(density) {
            tabCardWidth.toPx() * TabDismissPhysics.CARD_DISMISS_THRESHOLD_FRACTION
        }
        val dragState = rememberDraggableState { delta ->
            if (delta < 0f || rawDismissOffset < 0f) {
                rawDismissOffset = (rawDismissOffset + delta).coerceAtMost(0f)
                val rawDistance = -rawDismissOffset
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
                    haptics.startRubberband()
                    rubberbandHapticActive = true
                } else if (!shouldVibrate && rubberbandHapticActive) {
                    haptics.stopRubberband()
                    rubberbandHapticActive = false
                }
                if (hasClearedResistance != resistanceCleared) {
                    resistanceCleared = hasClearedResistance
                    breakFreeJob?.cancel()
                    breakFreeJob = cardGestureScope.launch {
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
                    haptics.confirm()
                    dismissHapticPlayed = true
                }
            }
        }
        val isInitialCard = tab.id == initialTabId
        val realCardVisible = TabOverviewHeroRules.isCardVisible(
            isInitialCard = isInitialCard,
            progress = if (heroCompleted) 1f else 0f,
            isExitTarget = exitHeroTabId == tab.id,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(
                    when {
                        reorder.activeTabId == tab.id -> 6f
                        reorder.animationTabId == tab.id -> 4f
                        dragActive || dismissOffset < 0f -> 2f
                        else -> 0f
                    },
                )
                .graphicsLayer {
                    alpha = 1f
                    translationX = if (reorder.layoutReady) {
                        reorder.indexDelta(tab.id) * reorder.pageSlotWidthPx *
                            (1f - reorder.progress().coerceIn(0f, 1f))
                    } else {
                        0f
                    }
                }
                .then(reorder.visualModifier(tab)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .graphicsLayer {
                        clip = false
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val currentDismissOffset = if (dragActive) {
                            -TabDismissPhysics.visualDistance(
                                rawDistance = -rawDismissOffset,
                                releaseProgress = breakFreeProgress.value,
                            )
                        } else {
                            dismissOffset
                        }
                        translationY = currentDismissOffset
                        val dismissProgress =
                            (-currentDismissOffset / (dismissThreshold * 1.7f)).coerceIn(0f, 1f)
                        val entryAlpha = if (isInitialCard) {
                            1f
                        } else {
                            TabOverviewHeroRules.neighborAlpha(heroProgress())
                        }
                        val tabMovingProgress = if (movingTabId == tab.id) {
                            movingProgress()
                        } else {
                            0f
                        }
                        alpha = (1f - dismissProgress * 0.72f) *
                            entryAlpha *
                            (1f - tabMovingProgress * 0.82f)
                        translationX = tabMovingProgress * 48f
                        val scale = (1f - dismissProgress * 0.05f) *
                            (1f - tabMovingProgress * 0.06f)
                        scaleX = scale
                        scaleY = scale
                    }
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        enabled = heroCompleted && !heroVisible &&
                            canDelete(tab) &&
                            dismissingTabId == null &&
                            movingTabId == null &&
                            exitHeroTabId == null &&
                            reorder.animationTabId == null &&
                            reorder.activeTabId == null &&
                            tabActionsTabId == null,
                        onDragStarted = {
                            breakFreeJob?.cancel()
                            breakFreeProgress.snapTo(0f)
                            haptics.stopRubberband()
                            rawDismissOffset = 0f
                            dragActive = true
                            resistanceCleared = false
                            rubberbandHapticActive = false
                            dismissHapticPlayed = false
                        },
                        onDragStopped = {
                            haptics.stopRubberband()
                            rubberbandHapticActive = false
                            breakFreeJob?.cancel()
                            breakFreeProgress.stop()
                            dismissOffset = -TabDismissPhysics.visualDistance(
                                rawDistance = -rawDismissOffset,
                                releaseProgress = breakFreeProgress.value,
                            )
                            dragActive = false
                            val farEnough = TabDismissPhysics.hasClearedResistance(
                                rawDistance = -rawDismissOffset,
                                dismissThreshold = dismissThreshold,
                                resistanceFraction = dismissResistanceFraction,
                            )
                            if (farEnough) {
                                val dismissedId = tab.id
                                val centeredId = tabs.getOrNull(pagerState.currentPage)?.id
                                val anchorId = if (centeredId == dismissedId) {
                                    tabs.getOrNull(page + 1)?.id
                                        ?: tabs.getOrNull(page - 1)?.id
                                } else {
                                    centeredId
                                }
                                onDismissingTabChanged(dismissedId)
                                operationScope.launch {
                                    try {
                                        Animatable(dismissOffset).animateTo(
                                            targetValue = -rootHeightPx,
                                            animationSpec = tween(
                                                durationMillis = 180,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) { dismissOffset = value }
                                        anchorId?.let { stableAnchorId ->
                                            val oldAnchorIndex = tabs.indexOfFirst {
                                                it.id == stableAnchorId
                                            }
                                            if (
                                                oldAnchorIndex >= 0 &&
                                                pagerState.currentPage != oldAnchorIndex
                                            ) {
                                                pagerState.animateScrollToPage(
                                                    page = oldAnchorIndex,
                                                    animationSpec = tween(
                                                        durationMillis = 240,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                                )
                                            }
                                            onSelectDismissAnchor(stableAnchorId)
                                        }
                                        onCloseTab(dismissedId)
                                        val targetId = anchorId ?: selectedTabId()
                                        val newAnchorIndex = currentTabs()
                                            .indexOfFirst { it.id == targetId }
                                            .coerceAtLeast(0)
                                        if (pagerState.currentPage != newAnchorIndex) {
                                            pagerState.scrollToPage(newAnchorIndex)
                                        }
                                    } finally {
                                        onDismissingTabChanged(null)
                                    }
                                }
                            } else {
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
                            }
                        },
                    )
                    .padding(horizontal = 4.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.width(tabCardWidth)) {
                    TabCard(
                        cardWidth = tabCardWidth,
                        cardAspectRatio = cardAspectRatio,
                        modifier = Modifier
                            .graphicsLayer { alpha = if (realCardVisible) 1f else 0f }
                            .then(cardModifier(tab))
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInRoot()
                                cardBounds = bounds
                                onCardBounds(tab, bounds, isInitialCard)
                            },
                        onClick = {
                            val bounds = cardBounds
                            if (bounds == null) {
                                onSelectTab(tab.id)
                                onCloseOverview()
                            } else {
                                onStartExitHero(tab, bounds)
                            }
                        },
                    ) {
                        previewContent(tab)
                    }
                    titleContent(
                        tab,
                        isInitialCard,
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 4.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun TabHeroLayer(
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    targetCornerRadius: Dp = 28.dp,
    targetFraction: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val targetCornerRadiusPx = with(density) { targetCornerRadius.toPx() }
    val heroClipPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val fraction = targetFraction().coerceIn(0f, 1f)
                val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                val scale = width / rootWidthPx
                val clipTop = (rootHeightPx - height / scale) * PREVIEW_CROP_TOP_FRACTION
                translationX = targetBounds.left * fraction
                translationY = targetBounds.top * fraction - clipTop * scale
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .drawWithContent {
                val fraction = targetFraction().coerceIn(0f, 1f)
                val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                val scale = width / rootWidthPx
                val visibleHeight = height / scale
                val clipTop = (rootHeightPx - visibleHeight) * PREVIEW_CROP_TOP_FRACTION
                val cornerRadius = targetCornerRadiusPx * fraction / scale
                heroClipPath.reset()
                heroClipPath.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = clipTop,
                        right = rootWidthPx,
                        bottom = clipTop + visibleHeight,
                        cornerRadius = CornerRadius(cornerRadius),
                    ),
                )
                clipPath(heroClipPath) { this@drawWithContent.drawContent() }
            }
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
fun TabCardHeroContent(
    tab: BrowserTab,
    previewSize: IntSize?,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    previewTopInsetPx: Int,
    bottomBarTopPx: FloatState,
    targetFraction: () -> Float,
    fullscreenPreviewContent: @Composable ((() -> Float)?) -> Unit,
    previewContent: @Composable () -> Unit,
) {
    val previewLayout = TabOverviewHeroRules.cardPreviewLayout(
        rootWidthPx = rootWidthPx,
        rootHeightPx = rootHeightPx,
        targetWidthPx = targetBounds.width,
        targetHeightPx = targetBounds.height,
        cropTopFraction = PREVIEW_CROP_TOP_FRACTION,
    )
    Box(Modifier.fillMaxSize()) {
        if (tab.url == BLANK_URL && !tab.isIncognito) {
            fullscreenPreviewContent {
                TabOverviewHeroRules.blankFavoritesAlpha(targetFraction())
            }
        } else if (tab.isIncognito) {
            fullscreenPreviewContent(null)
        } else {
            Layout(
                content = previewContent,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .clipToBounds(),
            ) { measurables, constraints ->
                val capturedHeightPx = previewSize
                    ?.takeIf { it.width > 0 && it.height > 0 }
                    ?.let { size -> rootWidthPx * size.height / size.width }
                val startLayout = TabSwitchPreviewLayoutRules.resolve(
                    rootHeightPx = rootHeightPx,
                    previewTopInsetPx = previewTopInsetPx,
                    bottomBarTopPx = bottomBarTopPx.floatValue,
                    capturedHeightPx = capturedHeightPx,
                )
                val frame = TabOverviewHeroRules.cardPreviewFrame(
                    startTopPx = startLayout.topInsetPx,
                    startHeightPx = startLayout.visibleHeightPx,
                    targetLayout = previewLayout,
                    targetFraction = targetFraction(),
                )
                val frameHeight = frame.sourceHeightPx.roundToInt().coerceAtLeast(1)
                val previewPlaceable = measurables.single().measure(
                    Constraints.fixed(
                        width = constraints.maxWidth,
                        height = frameHeight,
                    ),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    previewPlaceable.placeRelative(
                        x = 0,
                        y = frame.sourceTopPx.roundToInt(),
                    )
                }
            }
        }
    }
}

@Composable
fun TabListHeroContent(
    tab: BrowserTab,
    visuals: TabOverviewHeroVisuals,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    targetFraction: () -> Float,
    fullscreenPreviewContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val targetScale = (targetBounds.width / rootWidthPx).coerceAtLeast(0.01f)
    val sourceRowHeight = with(density) { (targetBounds.height / targetScale).toDp() }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    alpha = 1f - TabOverviewHeroRules.compactChromeAlpha(targetFraction())
                },
        ) {
            fullscreenPreviewContent()
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(sourceRowHeight)
                .graphicsLayer {
                    val fraction = targetFraction().coerceIn(0f, 1f)
                    val width = rootWidthPx + (targetBounds.width - rootWidthPx) * fraction
                    val height = rootHeightPx + (targetBounds.height - rootHeightPx) * fraction
                    val scale = width / rootWidthPx
                    val visibleHeight = height / scale
                    translationY = (rootHeightPx - visibleHeight) * PREVIEW_CROP_TOP_FRACTION
                    alpha = TabOverviewHeroRules.compactChromeAlpha(fraction)
                },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    visuals.favicon(36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            visuals.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            visuals.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (tab.isPinned) {
                        visuals.pinnedIcon(
                            Modifier
                                .padding(horizontal = 15.dp)
                                .size(20.dp),
                            MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabTitleRow(
    tab: BrowserTab,
    visuals: TabOverviewHeroVisuals,
    contentColor: Color,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.graphicsLayer { this.alpha = alpha() },
        shape = MaterialTheme.shapes.extraLarge,
        color = visuals.chromeContainerColor,
        contentColor = contentColor,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                tab.isIncognito -> visuals.incognitoIcon(Modifier.size(22.dp), contentColor)
                tab.url == BLANK_URL -> visuals.blankIcon(Modifier.size(22.dp))
                visuals.hasFavicon -> visuals.favicon(22.dp)
                else -> Surface(
                    modifier = Modifier.size(22.dp),
                    shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            visuals.title.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                visuals.title,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
            )
            if (tab.isPinned) {
                Spacer(Modifier.width(8.dp))
                visuals.pinnedIcon(Modifier.size(18.dp), contentColor)
            }
        }
    }
}

@Composable
fun GridTabPreviewChrome(
    tab: BrowserTab,
    visuals: TabOverviewHeroVisuals,
    interactionsEnabled: Boolean,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    Box(modifier) {
        TabTitleRow(
            tab = tab,
            visuals = visuals,
            contentColor = contentColor,
            alpha = { 1f },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = 8.dp,
                    top = 8.dp,
                    end = if (tab.isPinned) 8.dp else 60.dp,
                )
                .testTag(visuals.titleTestTag),
        )
        if (!tab.isPinned) {
            IconButton(
                onClick = onClose ?: {},
                enabled = interactionsEnabled && onClose != null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(48.dp)
                    .testTag(visuals.closeTestTag),
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = visuals.chromeContainerColor,
                    contentColor = contentColor,
                    shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = onClose?.let { visuals.closeContentDescription },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabOverviewEdgeAction(
    visible: Boolean,
    enabled: Boolean,
    contentDescription: String,
    testTag: String,
    animationLabel: String,
    onHaptic: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 180 else 140),
        label = animationLabel,
    )
    IconButton(
        onClick = {
            onHaptic()
            onClick()
        },
        enabled = visible && enabled,
        modifier = modifier
            .graphicsLayer { alpha = animatedAlpha }
            .then(
                if (visible) {
                    Modifier
                        .testTag(testTag)
                        .semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            )
            .size(48.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

private const val PREVIEW_CROP_TOP_FRACTION = 0.25f
