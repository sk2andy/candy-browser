@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.shared.ui.TabOverviewGridRules
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewHeroRules
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewEdgeAction
import dev.sk2andy.materialbrowser.shared.ui.TabHeroLayer
import dev.sk2andy.materialbrowser.shared.ui.TabDismissPhysics
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewHeroPager
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewHeroPagerHaptics
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewHeroPagerReorder
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewBottomChrome
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewChromeTestTags
import dev.sk2andy.materialbrowser.shared.ui.TabTitleRow
import dev.sk2andy.materialbrowser.shared.ui.CompactTabGrid
import dev.sk2andy.materialbrowser.shared.ui.CompactTabList
import dev.sk2andy.materialbrowser.ui.ProfileSwitcher
import dev.sk2andy.materialbrowser.ui.allowTopOverflow

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.key
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import dev.sk2andy.materialbrowser.data.TabAutoSortingRules
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.data.TabPinningRules
import dev.sk2andy.materialbrowser.data.TabReorderingRules
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal data class HeroPagerSnapshotObservation(
    val pageCount: Int,
    val tabIds: List<String>,
    val currentPage: Int,
)

@Composable
internal fun TabOverview(
    controller: BrowserController,
    backgroundWallpaper: ProfileWallpaperRuntime? = null,
    visible: Boolean,
    bottomBarTopPx: FloatState,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onNewTab: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncSettings: () -> Unit = onOpenSettings,
    onEditProfileWallpaper: (String, ProfileWallpaperTarget) -> Unit = { _, _ -> },
    destinationChromeVisible: Boolean,
    onEntryHeroStarted: (Boolean) -> Unit,
    onEntryHeroCompleted: () -> Unit,
    onExitHeroVisibilityChanged: (Boolean) -> Unit,
    candyTrailTabId: String?,
    candyTrailSourceBounds: Rect?,
    candyTrailBackProgress: Float,
    candyTrailBackEdgeSign: Int,
    candyTrailPredictiveBackCommitted: Boolean,
    onOpenCandyTrail: (String, Rect?) -> Unit,
    onCloseCandyTrail: () -> Unit,
    onToggleFavoriteTab: (String) -> Unit,
    onAddSiteCapsule: (String) -> Unit,
    onSnoozeTab: (String) -> Unit,
    onHeroPagerSnapshotObserved: ((HeroPagerSnapshotObservation) -> Unit)? = null,
) {
    val rootView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Frosted content moves into a nested ComposeView whose parent already consumed these insets.
    val statusBarInsets = WindowInsets.statusBars
    val navigationBarInsets = WindowInsets.navigationBars
    val overviewWallpaper = backgroundWallpaper.takeUnless {
        controller.selectedTab.isIncognito
    }
    var dismissingTabId by remember { mutableStateOf<String?>(null) }
    val overviewTabs = controller.activeTabs
    var frozenHeroPagerTabs by remember { mutableStateOf<List<BrowserTab>?>(null) }
    val pagerTabs = frozenHeroPagerTabs ?: overviewTabs
    val initialPage = remember {
        overviewTabs.indexOfFirst { it.id == controller.selectedTabId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pagerTabs.size },
    )
    // Test hook for the count/content invariant behind the restored-session flicker regression.
    if (onHeroPagerSnapshotObserved != null) {
        SideEffect {
            onHeroPagerSnapshotObserved(
                HeroPagerSnapshotObservation(
                    pageCount = pagerState.pageCount,
                    tabIds = pagerTabs.map(BrowserTab::id),
                    currentPage = pagerState.currentPage,
                ),
            )
        }
    }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialPage)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (controller.tabListStartsAtBottom) {
            overviewTabs.lastIndex.coerceAtLeast(0)
        } else {
            initialPage
        },
    )
    val initialTabId = remember(visible) { controller.selectedTabId }
    val initialTab = remember(initialTabId, overviewTabs) {
        overviewTabs.firstOrNull { it.id == initialTabId } ?: controller.selectedTab
    }
    val heroPreview = initialTabId
        .takeUnless { initialTab.isIncognito }
        ?.let(controller.previews::get)
        ?.takeIf { !it.isRecycled }
    val heroFavicon = initialTabId
        .let(controller.favicons::get)
        ?.takeIf { !it.isRecycled }
    val initialPreviewTopInsetPx = remember(initialTabId, visible) {
        controller.previewTopInsetPx(initialTabId)
    }
    val heroProgress = remember { Animatable(0f) }
    val overviewScope = rememberCoroutineScope()
    var heroTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var heroTargetMode by remember { mutableStateOf<TabOverviewMode?>(null) }
    var heroTargetTabId by remember { mutableStateOf<String?>(null) }
    var heroStarted by remember { mutableStateOf(false) }
    var heroCompleted by remember { mutableStateOf(false) }
    var heroVisible by remember { mutableStateOf(true) }
    var exitHero by remember { mutableStateOf<TabExitHero?>(null) }
    val currentOnExitHeroVisibilityChanged by rememberUpdatedState(
        onExitHeroVisibilityChanged,
    )
    fun updateExitHero(hero: TabExitHero?) {
        exitHero = hero
        currentOnExitHeroVisibilityChanged(hero != null)
    }
    var userPagerGestureActive by remember { mutableStateOf(false) }
    var lastHapticPage by remember { mutableStateOf<Int?>(null) }
    var pagerSessionEndJob by remember { mutableStateOf<Job?>(null) }
    var tabActionsTabId by remember { mutableStateOf<String?>(null) }
    var overviewBlurTarget by remember { mutableStateOf<BlurTarget?>(null) }
    var profileActionsProfileId by remember { mutableStateOf<String?>(null) }
    var profileIsolationChange by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var emojiPickerTargetId by remember { mutableStateOf<String?>(null) }
    var movingTabId by remember { mutableStateOf<String?>(null) }
    var profileSwitching by remember { mutableStateOf(false) }
    var reorderAnimation by remember { mutableStateOf<TabReorderAnimation?>(null) }
    var reorderLayoutReady by remember { mutableStateOf(false) }
    var activeTabReorder by remember { mutableStateOf<ActiveTabReorder?>(null) }
    var heroReorderDropAnimating by remember { mutableStateOf(false) }
    var tabReorderSettleJob by remember { mutableStateOf<Job?>(null) }
    val reorderProgress = remember { Animatable(1f) }
    val moveProgress = remember { Animatable(0f) }
    val tabCardBounds = remember { mutableStateMapOf<String, Rect>() }
    val tabReorderBounds = remember { mutableStateMapOf<String, Rect>() }
    var overviewRootBounds by remember { mutableStateOf<Rect?>(null) }
    val profileSwitchProgress = remember { Animatable(1f) }
    val tabFocusHapticEvents = remember {
        Channel<Unit>(
            capacity = 8,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    val exitHeroProgress = remember { Animatable(0f) }
    val pinnedTabsVisible by remember(controller.tabOverviewMode, controller.activeProfileId) {
        derivedStateOf {
            val activeTabs = controller.activeTabs
            when (controller.tabOverviewMode) {
                TabOverviewMode.Hero -> pagerState.layoutInfo.visiblePagesInfo.any { page ->
                    activeTabs.getOrNull(page.index)?.isPinned == true
                }
                TabOverviewMode.Grid -> gridState.layoutInfo.visibleItemsInfo.any { item ->
                    activeTabs.any { tab -> tab.isPinned && item.key == tab.id }
                }
                TabOverviewMode.List -> listState.layoutInfo.visibleItemsInfo.any { item ->
                    activeTabs.getOrNull(item.index)?.isPinned == true
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { currentOnExitHeroVisibilityChanged(false) }
    }
    fun startExitHero(
        tab: BrowserTab,
        bounds: Rect,
        cornerRadius: Dp = 28.dp,
    ) {
        if (
            dismissingTabId != null ||
            movingTabId != null ||
            exitHero != null ||
            reorderAnimation != null ||
            activeTabReorder != null ||
            tabActionsTabId != null
        ) {
            return
        }
        val mode = controller.tabOverviewMode
        val preview = controller.previews[tab.id]
            ?.takeIf { !tab.isIncognito && !it.isRecycled }
        updateExitHero(
            TabExitHero(
                tabId = tab.id,
                preview = preview,
                startBounds = bounds,
                isIncognito = tab.isIncognito,
                startCornerRadius = cornerRadius,
                previewTopInsetPx = controller.previewTopInsetPx(tab.id),
                mode = mode,
            ),
        )
        overviewScope.launch {
            try {
                exitHeroProgress.snapTo(0f)
                withFrameNanos { }
                exitHeroProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = TabOverviewHeroRules.EXIT_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )
                onSelect(tab.id)
                onClose()
            } finally {
                updateExitHero(null)
            }
        }
    }

    fun emitTabFocusHaptics(targetPage: Int) {
        val previousPage = lastHapticPage ?: targetPage
        lastHapticPage = targetPage
        repeat(TabFocusHapticRules.crossedEntryCount(previousPage, targetPage)) {
            tabFocusHapticEvents.trySend(Unit)
        }
    }

    LaunchedEffect(rootView, tabFocusHapticEvents) {
        for (event in tabFocusHapticEvents) {
            rootView.performTabFocusHaptic()
            delay(24)
        }
    }

    DisposableEffect(lifecycleOwner, rootView) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                rootView.stopRubberbandHaptic()
                while (tabFocusHapticEvents.tryReceive().isSuccess) {
                    // Drain queued haptics while overview is backgrounded.
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            pagerSessionEndJob?.cancel()
            tabReorderSettleJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
            rootView.stopRubberbandHaptic()
            tabFocusHapticEvents.close()
            activeTabReorder = null
            heroReorderDropAnimating = false
        }
    }

    LaunchedEffect(pagerState.interactionSource, controller.tabOverviewMode, visible) {
        if (controller.tabOverviewMode != TabOverviewMode.Hero || !visible) {
            return@LaunchedEffect
        }
        pagerState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    pagerSessionEndJob?.cancel()
                    while (tabFocusHapticEvents.tryReceive().isSuccess) {
                        // A new drag owns focus feedback from this point onward.
                    }
                    userPagerGestureActive = true
                    lastHapticPage = pagerState.currentPage
                }
                is DragInteraction.Stop,
                is DragInteraction.Cancel,
                -> {
                    pagerSessionEndJob?.cancel()
                    pagerSessionEndJob = overviewScope.launch {
                        delay(32)
                        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                        emitTabFocusHaptics(pagerState.currentPage)
                        userPagerGestureActive = false
                        lastHapticPage = null
                    }
                }
            }
        }
    }
    LaunchedEffect(pagerState, controller.tabOverviewMode, visible) {
        if (controller.tabOverviewMode != TabOverviewMode.Hero || !visible) {
            return@LaunchedEffect
        }
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { focusedPage ->
                if (userPagerGestureActive) {
                    emitTabFocusHaptics(focusedPage)
                }
            }
    }
    LaunchedEffect(
        controller.activeTabs.size,
        controller.activeProfileId,
        controller.selectedTabId,
        dismissingTabId,
        profileSwitching,
        controller.tabOverviewMode,
        visible,
    ) {
        if (
            controller.tabOverviewMode != TabOverviewMode.Hero ||
            !visible ||
            dismissingTabId != null ||
            profileSwitching ||
            activeTabReorder != null
        ) {
            return@LaunchedEffect
        }
        val selectedIndex = controller.activeTabs.indexOfFirst { it.id == controller.selectedTabId }
            .coerceAtLeast(0)
        if (
            controller.activeTabs.isNotEmpty() &&
            pagerState.currentPage != selectedIndex
        ) {
            pagerState.scrollToPage(selectedIndex)
        }
    }
    LaunchedEffect(controller.activeProfileId) {
        if (!visible) {
            profileSwitchProgress.snapTo(1f)
            return@LaunchedEffect
        }
        if (profileSwitching) return@LaunchedEffect
        profileSwitchProgress.snapTo(0f)
        val selectedIndex = controller.activeTabs
            .indexOfFirst { it.id == controller.selectedTabId }
            .coerceAtLeast(0)
        if (
            controller.tabOverviewMode == TabOverviewMode.Hero &&
            pagerState.currentPage != selectedIndex
        ) {
            pagerState.scrollToPage(selectedIndex)
        }
        profileSwitchProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        )
    }

    val candyTrailTransition = updateTransition(
        targetState = candyTrailTabId,
        label = "Candy-Trail-Navigation",
    )
    val layerVisible = CandyTrailLayerRules.isVisible(
        tabOverviewVisible = visible,
        currentCandyTrailTabId = candyTrailTransition.currentState,
        targetCandyTrailTabId = candyTrailTransition.targetState,
    )
    BrowserContentBlurTargetWithConstraints(
        enabled = layerVisible,
        onTargetAttached = { target -> overviewBlurTarget = target },
        onTargetReleased = { target ->
            if (overviewBlurTarget === target) overviewBlurTarget = null
        },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (layerVisible) 10f else -1f)
            .graphicsLayer { alpha = if (layerVisible) 1f else 0f },
        contentModifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overviewRootBounds = it.boundsInRoot() },
    ) {
        val density = LocalDensity.current
        val rootWidthPx = with(density) { maxWidth.toPx() }
        val rootHeightPx = with(density) { maxHeight.toPx() }
        val heroTarget = heroTargetBounds?.takeIf {
            heroTargetMode == controller.tabOverviewMode && heroTargetTabId == initialTabId
        }
        val entryHeroVisible = heroTarget != null && heroVisible
        val isExiting = exitHero != null
        val coverflowCardLayout = TabOverviewHeroRules.coverflowCardLayout(
            viewportWidth = maxWidth.value,
            viewportHeight = maxHeight.value,
        )
        val tabCardWidth = coverflowCardLayout.width.dp
        val pageSlotWidth = tabCardWidth + 12.dp
        val pageSlotWidthPx = with(density) { pageSlotWidth.toPx() }
        val pageHorizontalPadding = ((maxWidth - pageSlotWidth) / 2).coerceAtLeast(0.dp)
        val gridLayout = TabOverviewGridRules.layout(
            viewportWidth = maxWidth.value,
            viewportHeight = maxHeight.value,
        )
        val gridColumnPitchPx = with(density) { gridLayout.columnPitch.dp.toPx() }
        val gridRowPitchPx = with(density) { gridLayout.rowPitch.dp.toPx() }
        val gridLeadingEmptyCellCount = TabOverviewGridRules.leadingEmptyCellCount(
            tabCount = controller.activeTabs.size,
            columnCount = gridLayout.columnCount,
            startsAtBottom = controller.tabListStartsAtBottom,
        )
        val listRowPitchPx = with(density) { 72.dp.toPx() }
        val heroPagerTopOverflow = TAB_OVERVIEW_TOP_SPACING +
            if (controller.profilesEnabled) {
                PROFILE_SWITCHER_LAYOUT_HEIGHT + TAB_OVERVIEW_PROFILE_SPACING
            } else {
                0.dp
            }

        fun reorderSlotOffset(
            reorder: ActiveTabReorder,
            sourceIndex: Int,
            destinationIndex: Int,
        ): Offset {
            val slotOffset = when (reorder.mode) {
                TabOverviewMode.Hero -> Offset(
                    x = (destinationIndex - sourceIndex) * pageSlotWidthPx,
                    y = 0f,
                )
                TabOverviewMode.Grid -> {
                    val sourceGridIndex = sourceIndex + gridLeadingEmptyCellCount
                    val destinationGridIndex = destinationIndex + gridLeadingEmptyCellCount
                    val sourceRow = sourceGridIndex / gridLayout.columnCount
                    val sourceColumn = sourceGridIndex % gridLayout.columnCount
                    val destinationRow = destinationGridIndex / gridLayout.columnCount
                    val destinationColumn = destinationGridIndex % gridLayout.columnCount
                    Offset(
                        x = (destinationColumn - sourceColumn) * gridColumnPitchPx,
                        y = (destinationRow - sourceRow) * gridRowPitchPx,
                    )
                }
                TabOverviewMode.List -> Offset(
                    x = 0f,
                    y = (destinationIndex - sourceIndex) * listRowPitchPx,
                )
            }
            return if (sourceIndex == reorder.sourceIndex) {
                slotOffset - reorder.autoScrollOffset
            } else {
                slotOffset
            }
        }

        fun reorderTranslation(tabId: String): Offset {
            val reorder = activeTabReorder ?: return Offset.Zero
            val index = reorder.orderIds.indexOf(tabId)
            if (index < 0) return Offset.Zero
            if (index == reorder.sourceIndex) return Offset.Zero
            val shiftedIndex = TabReorderMotion.shiftedIndex(
                index = index,
                sourceIndex = reorder.sourceIndex,
                destinationIndex = reorder.destinationIndex,
            )
            return reorderSlotOffset(reorder, index, shiftedIndex)
        }

        fun startTabReorder(tab: BrowserTab, bounds: Rect) {
            if (
                activeTabReorder != null ||
                dismissingTabId != null ||
                movingTabId != null ||
                exitHero != null ||
                reorderAnimation != null ||
                heroReorderDropAnimating ||
                tabActionsTabId != null
            ) {
                return
            }
            val tabs = controller.activeTabs
            val sourceIndex = tabs.indexOfFirst { it.id == tab.id }
            val allowedRange = TabReorderingRules.destinationRange(tabs, tab.id) ?: return
            if (sourceIndex < 0 || allowedRange.first == allowedRange.last) return
            tabReorderSettleJob?.cancel()
            activeTabReorder = ActiveTabReorder(
                tabId = tab.id,
                mode = controller.tabOverviewMode,
                orderIds = tabs.map(BrowserTab::id),
                sourceIndex = sourceIndex,
                destinationIndex = sourceIndex,
                allowedRange = allowedRange,
                sourceBounds = bounds,
                slotBounds = tabReorderBounds.toMap() + (tab.id to bounds),
            )
            rootView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        fun requestedReorderDestination(
            reorder: ActiveTabReorder,
            dragOffset: Offset,
        ): Int {
            if (reorder.mode == TabOverviewMode.Hero) {
                return TabReorderMotion.heroDestinationIndexForDrag(
                    sourceIndex = reorder.sourceIndex,
                    currentDestinationIndex = reorder.destinationIndex,
                    edgeStepping = reorder.heroEdgeStepping,
                    dragOffsetPx = dragOffset.x,
                    viewportOffsetPx = reorder.autoScrollOffset.x,
                    slotWidthPx = pageSlotWidthPx,
                    allowedRange = reorder.allowedRange,
                )
            }
            if (reorder.autoScrollOffset != Offset.Zero) {
                val contentOffset = dragOffset + reorder.autoScrollOffset
                return when (reorder.mode) {
                    TabOverviewMode.Grid -> TabReorderMotion.gridDestinationIndex(
                        sourceIndex = reorder.sourceIndex,
                        dragOffsetPx = contentOffset,
                        columnPitchPx = gridColumnPitchPx,
                        rowPitchPx = gridRowPitchPx,
                        columnCount = gridLayout.columnCount,
                        allowedRange = reorder.allowedRange,
                        leadingEmptyCellCount = gridLeadingEmptyCellCount,
                    )
                    TabOverviewMode.List -> TabReorderMotion.horizontalDestinationIndex(
                        sourceIndex = reorder.sourceIndex,
                        dragOffsetPx = contentOffset.y,
                        slotWidthPx = listRowPitchPx,
                        allowedRange = reorder.allowedRange,
                    )
                    TabOverviewMode.Hero -> reorder.destinationIndex
                }
            }
            val projectedCenter = reorder.sourceBounds.center + dragOffset
            val visibleCandidate = reorder.allowedRange
                .mapNotNull { index ->
                    val candidateId = reorder.orderIds.getOrNull(index)
                    val candidateCenter = candidateId
                        ?.let { tabId -> tabReorderBounds[tabId] ?: reorder.slotBounds[tabId] }
                        ?.center
                        ?: return@mapNotNull null
                    val delta = candidateCenter - projectedCenter
                    val distanceSquared = delta.x * delta.x + delta.y * delta.y
                    index to distanceSquared
                }
                .minByOrNull { (_, distanceSquared) -> distanceSquared }
                ?.first
            return visibleCandidate ?: reorder.destinationIndex
        }

        fun updateReorderDestination(reorder: ActiveTabReorder, dragOffset: Offset) {
            val requestedIndex = requestedReorderDestination(reorder, dragOffset)
            if (requestedIndex != reorder.destinationIndex) {
                rootView.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            }
            activeTabReorder = reorder.copy(
                destinationIndex = requestedIndex,
                dragOffset = dragOffset,
            )
        }

        fun updateTabReorder(dragAmount: Offset) {
            val reorder = activeTabReorder?.takeUnless(ActiveTabReorder::settling) ?: return
            updateReorderDestination(reorder, reorder.dragOffset + dragAmount)
        }

        fun advanceVerticalTabReorderAutoScroll(consumed: Float) {
            val reorder = activeTabReorder?.takeUnless(ActiveTabReorder::settling) ?: return
            updateReorderDestination(
                reorder = reorder.copy(
                    autoScrollOffset = reorder.autoScrollOffset + Offset(0f, consumed),
                ),
                dragOffset = reorder.dragOffset,
            )
        }

        fun finishTabReorder(commit: Boolean) {
            val reorder = activeTabReorder?.takeUnless(ActiveTabReorder::settling) ?: return
            val destinationIndex = if (commit) {
                reorder.destinationIndex
            } else {
                reorder.sourceIndex
            }
            val heroAnchorIndex = if (reorder.mode == TabOverviewMode.Hero) {
                TabReorderMotion.heroPagerAnchorIndex(
                    sourceIndex = reorder.sourceIndex,
                    destinationIndex = destinationIndex,
                )
            } else {
                null
            }
            val settling = reorder.copy(
                destinationIndex = destinationIndex,
                autoScrollOffset = heroAnchorIndex?.let { anchorIndex ->
                    Offset(
                        x = (anchorIndex - reorder.sourceIndex) * pageSlotWidthPx,
                        y = 0f,
                    )
                } ?: reorder.autoScrollOffset,
                lifted = false,
                settling = true,
            )
            activeTabReorder = settling
            tabReorderSettleJob?.cancel()
            tabReorderSettleJob = overviewScope.launch {
                try {
                    if (
                        heroAnchorIndex != null &&
                        (
                            pagerState.currentPage != heroAnchorIndex ||
                                pagerState.currentPageOffsetFraction.absoluteValue > 0.001f
                        )
                    ) {
                        pagerState.animateScrollToPage(
                            page = heroAnchorIndex,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 900f),
                        )
                    }
                    val targetOffset = reorderSlotOffset(
                        reorder = settling,
                        sourceIndex = settling.sourceIndex,
                        destinationIndex = destinationIndex,
                    )
                    Animatable(settling.dragOffset, Offset.VectorConverter).animateTo(
                        targetValue = targetOffset,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 680f),
                    ) {
                        activeTabReorder = activeTabReorder
                            ?.takeIf { it.tabId == settling.tabId }
                            ?.copy(dragOffset = value)
                    }
                    val orderUnchanged = controller.activeTabs.map(BrowserTab::id) == settling.orderIds
                    val animateHeroDrop = commit &&
                        orderUnchanged &&
                        settling.mode == TabOverviewMode.Hero &&
                        destinationIndex != settling.sourceIndex
                    if (animateHeroDrop) {
                        // Switch from stable tab keys to position keys before mutating the list.
                        // Otherwise Pager follows the moved key to its new index in one frame.
                        heroReorderDropAnimating = true
                        withFrameNanos { }
                    }
                    val changed = commit && orderUnchanged && controller.reorderTab(
                        tabId = settling.tabId,
                        destinationIndex = destinationIndex,
                    )
                    activeTabReorder = null
                    if (changed && animateHeroDrop) {
                        withFrameNanos { }
                        pagerState.animateScrollToPage(
                            page = destinationIndex,
                            animationSpec = spring(
                                dampingRatio = 0.84f,
                                stiffness = 430f,
                            ),
                        )
                    }
                    if (changed) rootView.performConfirmHaptic()
                } finally {
                    if (activeTabReorder?.tabId == settling.tabId) {
                        activeTabReorder = null
                    }
                    heroReorderDropAnimating = false
                }
            }
        }

        val heroReorder = activeTabReorder?.takeIf {
            it.mode == TabOverviewMode.Hero && !it.settling
        }
        LaunchedEffect(activeTabReorder?.tabId) {
            val reorder = activeTabReorder?.takeUnless(ActiveTabReorder::settling)
                ?: return@LaunchedEffect
            withFrameNanos { }
            activeTabReorder
                ?.takeIf { it.tabId == reorder.tabId && !it.settling }
                ?.let { current -> activeTabReorder = current.copy(lifted = true) }
        }
        HeroTabReorderEdgeAutoScroll(
            sessionId = heroReorder?.tabId,
            pointerInRoot = heroReorder?.let { it.sourceBounds.center + it.dragOffset },
            viewportBounds = overviewRootBounds,
            canStepBackward = (heroReorder?.destinationIndex ?: 0) >
                (heroReorder?.allowedRange?.first ?: 0),
            canStepForward = (heroReorder?.destinationIndex ?: 0) <
                (heroReorder?.allowedRange?.last ?: 0),
            onStep = { direction ->
                val current = activeTabReorder
                    ?.takeIf { it.mode == TabOverviewMode.Hero && !it.settling }
                    ?: return@HeroTabReorderEdgeAutoScroll false
                val destinationIndex = (current.destinationIndex + direction)
                    .coerceIn(current.allowedRange.first, current.allowedRange.last)
                if (destinationIndex == current.destinationIndex) {
                    return@HeroTabReorderEdgeAutoScroll false
                }
                val anchorIndex = TabReorderMotion.heroPagerAnchorIndex(
                    sourceIndex = current.sourceIndex,
                    destinationIndex = destinationIndex,
                )
                activeTabReorder = current.copy(
                    destinationIndex = destinationIndex,
                    heroEdgeStepping = true,
                    autoScrollOffset = Offset(
                        x = (anchorIndex - current.sourceIndex) * pageSlotWidthPx,
                        y = 0f,
                    ),
                )
                rootView.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                pagerState.animateScrollToPage(
                    page = anchorIndex,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 1_350f),
                )
                true
            },
        )

        LaunchedEffect(visible, controller.tabOverviewMode, initialTabId) {
            if (!visible) {
                heroProgress.snapTo(0f)
                exitHeroProgress.snapTo(0f)
                heroStarted = false
                heroCompleted = false
                heroVisible = true
                updateExitHero(null)
                tabReorderSettleJob?.cancel()
                activeTabReorder = null
                heroReorderDropAnimating = false
                return@LaunchedEffect
            }

            heroProgress.snapTo(0f)
            heroStarted = false
            heroCompleted = false
            heroVisible = true

            var waitMillis = 0L
            while (
                waitMillis < 250L &&
                !TabOverviewHeroRules.canStart(
                    heroTargetBounds != null &&
                        heroTargetMode == controller.tabOverviewMode &&
                        heroTargetTabId == initialTabId,
                )
            ) {
                delay(16)
                waitMillis += 16
            }

            val hasStableTarget = TabOverviewHeroRules.canStart(
                heroTargetBounds != null &&
                    heroTargetMode == controller.tabOverviewMode &&
                    heroTargetTabId == initialTabId,
            )
            heroStarted = true
            onEntryHeroStarted(hasStableTarget)
            if (hasStableTarget) {
                heroProgress.animateTo(
                    1f,
                    tween(
                        durationMillis = TabOverviewHeroRules.ENTRY_DURATION_MILLIS,
                        easing = FastOutSlowInEasing,
                    ),
                )
            } else {
                heroProgress.snapTo(1f)
            }
            heroCompleted = true
            onEntryHeroCompleted()
            withFrameNanos { }
            heroVisible = false
        }

        TabOverviewBackground(
            wallpaper = overviewWallpaper,
            statusBarInsets = statusBarInsets,
            navigationBarInsets = navigationBarInsets,
            modifier = Modifier
                .fillMaxSize()
                .then(if (visible) Modifier.testTag(TabOverviewChromeTestTags.Root) else Modifier)
                .graphicsLayer {
                    alpha = if (visible) {
                        TabOverviewHeroRules.backgroundAlpha(
                            entryProgress = heroProgress.value,
                            isExiting = isExiting,
                        )
                    } else {
                        0f
                    }
                },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .longPressTabOverviewReorder(
                    enabled = visible &&
                        !controller.automaticTabSortingEnabled &&
                        heroCompleted &&
                        !heroVisible &&
                        dismissingTabId == null &&
                        movingTabId == null &&
                        !profileSwitching &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        !heroReorderDropAnimating &&
                        tabActionsTabId == null,
                    sessionKey = Triple(
                        controller.activeProfileId,
                        controller.tabOverviewMode,
                        controller.activeTabs.map(BrowserTab::id),
                    ),
                    onDragStart = { position ->
                        val positionInRoot = position +
                            (overviewRootBounds?.topLeft ?: Offset.Zero)
                        val target = controller.activeTabs
                            .asSequence()
                            .filter { tab -> TabReorderingRules.canMove(controller.activeTabs, tab.id) }
                            .mapNotNull { tab ->
                                tabReorderBounds[tab.id]
                                    ?.takeIf { bounds -> bounds.contains(positionInRoot) }
                                    ?.let { bounds -> tab to bounds }
                            }
                            .minByOrNull { (_, bounds) ->
                                val delta = bounds.center - positionInRoot
                                delta.x * delta.x + delta.y * delta.y
                            }
                        if (target == null) {
                            false
                        } else {
                            startTabReorder(target.first, target.second)
                            activeTabReorder?.tabId == target.first.id
                        }
                    },
                    onDrag = ::updateTabReorder,
                    onDragEnd = { finishTabReorder(commit = true) },
                    onDragCancel = { finishTabReorder(commit = false) },
                )
                .graphicsLayer {
                    alpha = if (visible) {
                        TabOverviewHeroRules.contentAlpha(
                            exitProgress = exitHeroProgress.value,
                            isExiting = isExiting,
                        )
                    } else {
                        0f
                    }
                }
                .windowInsetsPadding(statusBarInsets)
                .windowInsetsPadding(navigationBarInsets)
                .then(
                    if (
                        candyTrailTransition.currentState != null ||
                        candyTrailTransition.targetState != null ||
                        tabActionsTabId != null
                    ) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Spacer(Modifier.height(TAB_OVERVIEW_TOP_SPACING))
            if (controller.profilesEnabled) {
                ProfileSwitcher(
                    profiles = controller.profiles,
                    activeProfileId = controller.activeProfileId,
                    enabled = dismissingTabId == null &&
                        movingTabId == null &&
                        !profileSwitching &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        !heroReorderDropAnimating &&
                        activeTabReorder == null &&
                        tabActionsTabId == null &&
                        profileActionsProfileId == null &&
                        profileIsolationChange == null &&
                        emojiPickerTargetId == null,
                    onSelect = { profileId ->
                        if (profileId == controller.activeProfileId) return@ProfileSwitcher
                        overviewScope.launch {
                            profileSwitching = true
                            try {
                                profileSwitchProgress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 120,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                                if (
                                    controller.tabOverviewMode == TabOverviewMode.Hero &&
                                    pagerState.currentPage != 0
                                ) {
                                    pagerState.scrollToPage(0)
                                }
                                if (controller.selectProfile(profileId)) {
                                    controller.loadActiveProfileTabSwitcherWallpaper()
                                    val selectedIndex = controller.activeTabs
                                        .indexOfFirst { it.id == controller.selectedTabId }
                                        .coerceAtLeast(0)
                                    if (
                                        controller.tabOverviewMode == TabOverviewMode.Hero &&
                                        pagerState.currentPage != selectedIndex
                                    ) {
                                        pagerState.scrollToPage(selectedIndex)
                                    }
                                    withFrameNanos { }
                                    rootView.performConfirmHaptic()
                                }
                                profileSwitchProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = 0.78f,
                                        stiffness = 460f,
                                    ),
                                )
                            } finally {
                                withContext(NonCancellable) {
                                    profileSwitchProgress.snapTo(1f)
                                    profileSwitching = false
                                }
                            }
                        }
                    },
                    onLongClick = { profileId ->
                        rootView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        val profile = controller.profiles.firstOrNull { it.id == profileId }
                        if (profile?.isSynced == true) {
                            onClose()
                            onOpenSyncSettings()
                        } else {
                            profileActionsProfileId = profileId
                        }
                    },
                    onAdd = {
                        rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        emojiPickerTargetId = NEW_PROFILE_TARGET
                    },
                    modifier = Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            val chromeProgress =
                                ((heroProgress.value - 0.34f) / 0.66f).coerceIn(0f, 1f)
                            alpha = chromeProgress
                            translationY = (1f - chromeProgress) * -18f
                        },
                )
                Spacer(Modifier.height(TAB_OVERVIEW_PROFILE_SPACING))
            }
            when (controller.tabOverviewMode) {
                TabOverviewMode.Hero -> TabOverviewHeroPager(
                    pagerState = pagerState,
                    tabs = pagerTabs,
                    initialTabId = initialTabId,
                    tabCardWidth = tabCardWidth,
                    cardAspectRatio = coverflowCardLayout.aspectRatio,
                    pageSlotWidth = pageSlotWidth,
                    pageHorizontalPadding = pageHorizontalPadding,
                    topPadding = heroPagerTopOverflow + HERO_PAGER_VERTICAL_PADDING,
                    bottomPadding = HERO_PAGER_VERTICAL_PADDING,
                    heroProgress = { heroProgress.value },
                    heroCompleted = heroCompleted,
                    heroVisible = heroVisible,
                    rootHeightPx = rootHeightPx,
                    dismissResistanceFraction = controller.dismissResistancePercent / 100f,
                    dismissingTabId = dismissingTabId,
                    movingTabId = movingTabId,
                    movingProgress = { moveProgress.value },
                    exitHeroTabId = exitHero?.tabId,
                    tabActionsTabId = tabActionsTabId,
                    reorder = TabOverviewHeroPagerReorder(
                        animationTabId = reorderAnimation?.tabId,
                        layoutReady = reorderLayoutReady,
                        pageSlotWidthPx = pageSlotWidthPx,
                        progress = { reorderProgress.value },
                        indexDelta = { tabId ->
                            reorderAnimation?.indexDeltas?.get(tabId) ?: 0
                        },
                        activeTabId = activeTabReorder?.tabId,
                        visualModifier = { tab ->
                            Modifier.tabReorderVisualMotion(
                                sessionId = activeTabReorder?.tabId,
                                isDragged = activeTabReorder?.tabId == tab.id,
                                targetOffset = reorderTranslation(tab.id),
                            )
                        },
                        dropAnimating = heroReorderDropAnimating,
                    ),
                    operationScope = overviewScope,
                    currentTabs = { controller.activeTabs },
                    selectedTabId = { controller.selectedTabId },
                    onDismissingTabChanged = { tabId ->
                        if (tabId == null) {
                            frozenHeroPagerTabs = null
                            dismissingTabId = null
                        } else {
                            frozenHeroPagerTabs = pagerTabs
                            dismissingTabId = tabId
                        }
                    },
                    onSelectDismissAnchor = onSelect,
                    onSelectTab = controller::selectTab,
                    onCloseTab = controller::closeTab,
                    onCloseOverview = onClose,
                    onStartExitHero = ::startExitHero,
                    onCardBounds = { tab, bounds, isInitialCard ->
                        tabCardBounds[tab.id] = bounds
                        tabReorderBounds[tab.id] = bounds
                        if (isInitialCard) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.Hero
                            heroTargetTabId = tab.id
                        }
                    },
                    onCardBoundsDisposed = { tab, bounds ->
                        if (tabCardBounds[tab.id] == bounds) {
                            tabCardBounds.remove(tab.id)
                        }
                        if (tabReorderBounds[tab.id] == bounds) {
                            tabReorderBounds.remove(tab.id)
                        }
                    },
                    canDelete = TabDeletionRules::canDelete,
                    haptics = TabOverviewHeroPagerHaptics(
                        startRubberband = rootView::startRubberbandHaptic,
                        stopRubberband = rootView::stopRubberbandHaptic,
                        confirm = rootView::performConfirmHaptic,
                    ),
                    previewContent = { tab ->
                        TabPreviewContent(
                            tab = tab,
                            preview = controller.previews[tab.id],
                            favicon = controller.favicons[tab.id],
                            favorites = controller.favorites,
                            favoriteFavicons = controller.favoriteFavicons,
                        )
                    },
                    titleContent = { tab, isInitialCard, modifier ->
                        TabTitleRow(
                            tab = tab,
                            visuals = tabOverviewHeroVisuals(
                                tab = tab,
                                favicon = controller.favicons[tab.id],
                            ),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            alpha = {
                                if (isInitialCard) {
                                    ((heroProgress.value - 0.72f) / 0.28f)
                                        .coerceIn(0f, 1f)
                                } else {
                                    1f
                                }
                            },
                            modifier = modifier,
                        )
                    },
                    cardModifier = { tab ->
                        Modifier.testTag(SnoozeTestTags.overviewTab(tab.id))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val progress = profileSwitchProgress.value
                            alpha = progress
                            translationY = (1f - progress) * 14f
                            val scale = 0.97f + progress * 0.03f
                            scaleX = scale
                            scaleY = scale
                        }
                        .allowTopOverflow(heroPagerTopOverflow)
                        .testTag(TabOverviewChromeTestTags.HeroPager),
                )
                TabOverviewMode.Grid -> CompactTabGrid(
                    gridState = gridState,
                    layout = gridLayout,
                    tabs = controller.activeTabs,
                    startsAtBottom = controller.tabListStartsAtBottom,
                    visible = visible,
                    selectedTabId = controller.selectedTabId,
                    initialTabId = initialTabId,
                    visuals = { tab ->
                        tabOverviewHeroVisuals(tab, controller.favicons[tab.id])
                    },
                    previewContent = { tab ->
                        TabPreviewContent(
                            tab = tab,
                            preview = controller.previews[tab.id],
                            favicon = controller.favicons[tab.id],
                            favorites = controller.favorites,
                            favoriteFavicons = controller.favoriteFavicons,
                        )
                    },
                    heroProgress = { heroProgress.value },
                    heroCompleted = heroCompleted,
                    heroVisible = entryHeroVisible,
                    exitHeroTabId = exitHero?.tabId,
                    dismissResistanceFraction = controller.dismissResistancePercent / 100f,
                    interactionsEnabled = dismissingTabId == null &&
                        movingTabId == null &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        activeTabReorder == null &&
                        tabActionsTabId == null,
                    reorderSessionId = activeTabReorder?.tabId,
                    reorderDraggedTabId = activeTabReorder?.tabId,
                    reorderModifier = { tab ->
                        Modifier.tabReorderVisualMotion(
                            sessionId = activeTabReorder?.tabId,
                            isDragged = activeTabReorder?.tabId == tab.id,
                            targetOffset = reorderTranslation(tab.id),
                        )
                    },
                    reorderAutoScroll = { gridBounds ->
                        TabReorderEdgeAutoScroll(
                            sessionId = activeTabReorder?.tabId,
                            pointerInRoot = activeTabReorder
                                ?.takeUnless(ActiveTabReorder::settling)
                                ?.let { it.sourceBounds.center + it.dragOffset },
                            viewportBounds = gridBounds,
                            orientation = Orientation.Vertical,
                            canScrollBackward = gridState.canScrollBackward &&
                                (activeTabReorder?.let {
                                    it.destinationIndex > it.allowedRange.first
                                } ?: false),
                            canScrollForward = gridState.canScrollForward &&
                                (activeTabReorder?.let {
                                    it.destinationIndex < it.allowedRange.last
                                } ?: false),
                            scrollBy = { delta -> gridState.scrollBy(delta) },
                            onScrolled = ::advanceVerticalTabReorderAutoScroll,
                        )
                    },
                    viewportSize = { IntSize(rootView.width, rootView.height) },
                    canDelete = TabDeletionRules::canDelete,
                    haptics = TabOverviewHeroPagerHaptics(
                        startRubberband = rootView::startRubberbandHaptic,
                        stopRubberband = rootView::stopRubberbandHaptic,
                        confirm = rootView::performConfirmHaptic,
                    ),
                    onReorderBounds = { tab, bounds -> tabReorderBounds[tab.id] = bounds },
                    onReorderBoundsDisposed = { tab, bounds ->
                        if (tabReorderBounds[tab.id] == bounds) tabReorderBounds.remove(tab.id)
                    },
                    onPreviewBounds = { tab, bounds ->
                        tabCardBounds[tab.id] = bounds
                        if (tab.id == initialTabId && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.Grid
                            heroTargetTabId = tab.id
                        }
                    },
                    onPreviewBoundsDisposed = { tab, bounds ->
                        if (tabCardBounds[tab.id] == bounds) tabCardBounds.remove(tab.id)
                    },
                    onSelect = { tab, bounds -> startExitHero(tab, bounds, 22.dp) },
                    onCloseTab = { tab ->
                        if (TabDeletionRules.canDelete(tab)) {
                            rootView.performConfirmHaptic()
                            controller.closeTab(tab.id)
                        }
                    },
                    onSwipeDismissStart = { tab ->
                        if (dismissingTabId == null) {
                            dismissingTabId = tab.id
                            true
                        } else {
                            false
                        }
                    },
                    onSwipeDismissEnd = { tab ->
                        if (dismissingTabId == tab.id) {
                            dismissingTabId = null
                        }
                    },
                    onSwipeDismiss = { tab ->
                        if (TabDeletionRules.canDelete(tab)) {
                            controller.closeTab(tab.id)
                        }
                    },
                    gridTestTag = TabOverviewChromeTestTags.Grid,
                    tabTestTag = { tab -> SnoozeTestTags.overviewTab(tab.id) },
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val progress = profileSwitchProgress.value
                            alpha = progress
                            translationY = (1f - progress) * 14f
                        },
                )
                TabOverviewMode.List -> CompactTabList(
                    listState = listState,
                    tabs = controller.activeTabs,
                    startsAtBottom = controller.tabListStartsAtBottom,
                    visible = visible,
                    selectedTabId = controller.selectedTabId,
                    initialTabId = initialTabId,
                    visuals = { tab ->
                        tabOverviewHeroVisuals(tab, controller.favicons[tab.id])
                    },
                    heroProgress = { heroProgress.value },
                    heroCompleted = heroCompleted,
                    heroVisible = entryHeroVisible,
                    exitHeroTabId = exitHero?.tabId,
                    interactionsEnabled = dismissingTabId == null &&
                        movingTabId == null &&
                        exitHero == null &&
                        reorderAnimation == null &&
                        activeTabReorder == null &&
                        tabActionsTabId == null,
                    reorderSessionId = activeTabReorder?.tabId,
                    reorderDraggedTabId = activeTabReorder?.tabId,
                    reorderModifier = { tab ->
                        Modifier.tabReorderVisualMotion(
                            sessionId = activeTabReorder?.tabId,
                            isDragged = activeTabReorder?.tabId == tab.id,
                            targetOffset = reorderTranslation(tab.id),
                        )
                    },
                    reorderAutoScroll = { listBounds ->
                        TabReorderEdgeAutoScroll(
                            sessionId = activeTabReorder?.tabId,
                            pointerInRoot = activeTabReorder
                                ?.takeUnless(ActiveTabReorder::settling)
                                ?.let { it.sourceBounds.center + it.dragOffset },
                            viewportBounds = listBounds,
                            orientation = Orientation.Vertical,
                            canScrollBackward = activeTabReorder?.let {
                                it.destinationIndex > it.allowedRange.first
                            } ?: false,
                            canScrollForward = activeTabReorder?.let {
                                it.destinationIndex < it.allowedRange.last
                            } ?: false,
                            scrollBy = { delta -> listState.scrollBy(delta) },
                            onScrolled = ::advanceVerticalTabReorderAutoScroll,
                        )
                    },
                    onRowBounds = { tab, bounds ->
                        tabCardBounds[tab.id] = bounds
                        tabReorderBounds[tab.id] = bounds
                        if (tab.id == initialTabId && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.List
                            heroTargetTabId = tab.id
                        }
                    },
                    onRowBoundsDisposed = { tab, bounds ->
                        if (tabCardBounds[tab.id] == bounds) tabCardBounds.remove(tab.id)
                        if (tabReorderBounds[tab.id] == bounds) tabReorderBounds.remove(tab.id)
                    },
                    onSelect = { tab, bounds -> startExitHero(tab, bounds, 22.dp) },
                    onCloseTab = { tab ->
                        if (TabDeletionRules.canDelete(tab)) {
                            rootView.performConfirmHaptic()
                            controller.closeTab(tab.id)
                        }
                    },
                    listTestTag = TabOverviewChromeTestTags.List,
                    tabTestTag = { tab -> SnoozeTestTags.overviewTab(tab.id) },
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            val progress = profileSwitchProgress.value
                            alpha = progress
                            translationY = (1f - progress) * 14f
                        },
                )
            }
            val actionTargetId = if (controller.tabOverviewMode == TabOverviewMode.Hero) {
                controller.activeTabs.getOrNull(pagerState.currentPage)?.id
            } else {
                controller.selectedTabId
            }
            val chromeEnabled = destinationChromeVisible &&
                dismissingTabId == null &&
                movingTabId == null &&
                exitHero == null &&
                reorderAnimation == null &&
                !heroReorderDropAnimating &&
                activeTabReorder == null &&
                tabActionsTabId == null
            val overviewChromeTokens = browserChromeSurfaceTokens(
                BrowserChromeSurfaceRole.AddressBar,
            )
            val pinnedTabsJumpVisible = destinationChromeVisible &&
                controller.activeTabs.any(BrowserTab::isPinned) &&
                !pinnedTabsVisible
            TabOverviewBottomChrome(
                visible = destinationChromeVisible,
                enabled = chromeEnabled,
                onNewTab = onNewTab,
                onMore = {
                    actionTargetId?.let { tabId ->
                        rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        tabActionsTabId = tabId
                    }
                },
                containerColor = overviewChromeTokens.containerColor,
                tonalElevation = overviewChromeTokens.tonalElevation,
                shadowElevation = overviewChromeTokens.shadowElevation,
                newTabIcon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.cd_new_tab),
                    )
                },
                moreIcon = {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                    )
                },
                leadingAction = {
                    TabOverviewEdgeAction(
                        visible = pinnedTabsJumpVisible,
                        enabled = chromeEnabled,
                        contentDescription = stringResource(R.string.cd_scroll_to_pinned_tabs),
                        testTag = TabOverviewChromeTestTags.PinnedTabsJump,
                        animationLabel = "pinned-tabs-jump-alpha",
                        onHaptic = {
                            rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        onClick = {
                            overviewScope.launch {
                                when (controller.tabOverviewMode) {
                                    TabOverviewMode.Hero ->
                                        pagerState.animateScrollToPage(
                                            page = 0,
                                            animationSpec = spring(
                                                dampingRatio = 0.86f,
                                                stiffness = 720f,
                                            ),
                                        )
                                    TabOverviewMode.Grid -> gridState.animateScrollToItem(0)
                                    TabOverviewMode.List -> listState.animateScrollToItem(0)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .zIndex(1f),
                    ) {
                        Icon(
                            imageVector = if (controller.tabOverviewMode == TabOverviewMode.Hero) {
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft
                            } else {
                                Icons.Default.KeyboardArrowUp
                            },
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_push_pin),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                        )
                    }
                },
                trailingAction = {
                    TabOverviewEdgeAction(
                        visible = destinationChromeVisible,
                        enabled = chromeEnabled,
                        contentDescription = stringResource(R.string.action_settings),
                        testTag = TabOverviewChromeTestTags.Settings,
                        animationLabel = "tab-overview-settings-alpha",
                        onHaptic = {
                            rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .zIndex(1f),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                        )
                    }
                },
            )
        }

        activeTabReorder?.let { reorder ->
            controller.activeTabs.firstOrNull { it.id == reorder.tabId }?.let { tab ->
                DraggedTabReorderOverlay(
                    reorder = reorder,
                    tab = tab,
                    preview = controller.previews[tab.id],
                    favicon = controller.favicons[tab.id],
                    favorites = controller.favorites,
                    selected = tab.id == controller.selectedTabId,
                    rootTopLeft = overviewRootBounds?.topLeft ?: Offset.Zero,
                )
            }
        }

        if (heroTarget != null && heroVisible) {
            TabHeroLayer(
                targetBounds = heroTarget,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
                targetCornerRadius = if (controller.tabOverviewMode == TabOverviewMode.Hero) {
                    28.dp
                } else {
                    22.dp
                },
                targetFraction = { heroProgress.value },
                modifier = if (initialTab.isIncognito) {
                    Modifier.graphicsLayer {
                        alpha = TabOverviewHeroRules.incognitoVeilAlpha(
                            heroProgress.value,
                        )
                    }
                } else {
                    Modifier
                },
            ) {
                when (controller.tabOverviewMode) {
                    TabOverviewMode.List -> AndroidTabListHeroContent(
                        tab = initialTab,
                        preview = heroPreview,
                        favicon = heroFavicon,
                        favorites = controller.favorites,
                        favoriteFavicons = controller.favoriteFavicons,
                        targetBounds = heroTarget,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = initialPreviewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { heroProgress.value },
                    )
                    TabOverviewMode.Hero -> AndroidTabCardHeroContent(
                        tab = initialTab,
                        preview = heroPreview,
                        favicon = heroFavicon,
                        favorites = controller.favorites,
                        favoriteFavicons = controller.favoriteFavicons,
                        targetBounds = heroTarget,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = initialPreviewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { heroProgress.value },
                    )
                    TabOverviewMode.Grid -> AndroidTabCardHeroContent(
                        tab = initialTab,
                        preview = heroPreview,
                        favicon = heroFavicon,
                        favorites = controller.favorites,
                        favoriteFavicons = controller.favoriteFavicons,
                        targetBounds = heroTarget,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = initialPreviewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { heroProgress.value },
                    )
                }
            }
        }
        if (visible && entryHeroVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(statusBarInsets)
                    .padding(
                        top = TAB_OVERVIEW_TOP_SPACING +
                            PROFILE_SWITCHER_LAYOUT_HEIGHT +
                            TAB_OVERVIEW_PROFILE_SPACING,
                    )
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
        exitHero?.let { hero ->
            TabHeroLayer(
                targetBounds = hero.startBounds,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
                targetCornerRadius = hero.startCornerRadius,
                targetFraction = { 1f - exitHeroProgress.value },
                modifier = Modifier.zIndex(20f),
            ) {
                val preview = hero.preview
                val heroTab = controller.activeTabs.firstOrNull { it.id == hero.tabId }
                if (hero.mode == TabOverviewMode.List && heroTab != null) {
                    AndroidTabListHeroContent(
                        tab = heroTab,
                        preview = preview,
                        favicon = controller.favicons[hero.tabId],
                        favorites = controller.favorites,
                        favoriteFavicons = controller.favoriteFavicons,
                        targetBounds = hero.startBounds,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { 1f - exitHeroProgress.value },
                    )
                } else if (
                    heroTab != null &&
                    (hero.mode == TabOverviewMode.Hero || hero.mode == TabOverviewMode.Grid)
                ) {
                    AndroidTabCardHeroContent(
                        tab = heroTab,
                        preview = preview,
                        favicon = controller.favicons[hero.tabId],
                        favorites = controller.favorites,
                        favoriteFavicons = controller.favoriteFavicons,
                        targetBounds = hero.startBounds,
                        rootWidthPx = rootWidthPx,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                        targetFraction = { 1f - exitHeroProgress.value },
                    )
                } else if (heroTab?.url == BLANK_URL) {
                    FullscreenTabPreviewContent(
                        tab = heroTab,
                        preview = null,
                        favicon = null,
                        favorites = controller.favorites,
                        favoriteFavicons = controller.favoriteFavicons,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                    )
                } else if (preview != null && !preview.isRecycled && heroTab != null) {
                    FullscreenTabPreviewContent(
                        tab = heroTab,
                        preview = preview,
                        favicon = controller.favicons[hero.tabId],
                        favorites = controller.favorites,
                        favoriteFavicons = controller.favoriteFavicons,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                    )
                } else if (hero.isIncognito && heroTab != null) {
                    FullscreenTabPreviewContent(
                        tab = heroTab,
                        preview = null,
                        favicon = null,
                        favorites = controller.favorites,
                        favoriteFavicons = controller.favoriteFavicons,
                        rootHeightPx = rootHeightPx,
                        previewTopInsetPx = hero.previewTopInsetPx,
                        bottomBarTopPx = bottomBarTopPx,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surface,
                                    ),
                                    radius = 1100f,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground_art),
                            contentDescription = null,
                            modifier = Modifier.size(88.dp),
                            tint = Color.Unspecified,
                        )
                    }
                }
            }
        }

        candyTrailTransition.AnimatedContent(
            transitionSpec = {
                val transform = if (targetState != null) {
                    slideInHorizontally(
                        initialOffsetX = { width ->
                            PredictiveBackMotion.entryTranslation(
                                progress = 0f,
                                width = width.toFloat(),
                            ).roundToInt()
                        },
                        animationSpec = tween(
                            durationMillis = PredictiveBackMotion.ENTRY_DURATION_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith ExitTransition.None
                } else if (candyTrailPredictiveBackCommitted) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    EnterTransition.None togetherWith slideOutHorizontally(
                        targetOffsetX = { width -> width },
                        animationSpec = tween(
                            durationMillis = PredictiveBackMotion.EXIT_DURATION_MILLIS,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
                transform.using(SizeTransform(clip = false))
            },
            contentKey = { it ?: "closed" },
        ) { presentedTabId ->
            val candyTrailTab = presentedTabId?.let { tabId ->
                controller.activeTabs.firstOrNull { it.id == tabId }
            }
            if (candyTrailTab != null) {
                val candyTrail = controller.candyTrail(candyTrailTab.id)
                CandyTrailScreen(
                    tab = candyTrailTab,
                    trail = candyTrail,
                    favicon = controller.favicons[candyTrailTab.id],
                    forkFavicons = candyTrail.forks.mapNotNull { fork ->
                        val destinationId = fork.destinationTabId ?: return@mapNotNull null
                        controller.favicons[destinationId]?.let { destinationId to it }
                    }.toMap(),
                    predictiveBackProgress = candyTrailBackProgress,
                    predictiveBackEdgeSign = candyTrailBackEdgeSign,
                    onSelectNode = { nodeId ->
                        controller.navigateToCandyTrailNode(candyTrailTab.id, nodeId)
                    },
                    onNodeSelectionFinished = {
                        onCloseCandyTrail()
                        val bounds = tabCardBounds[candyTrailTab.id] ?: candyTrailSourceBounds
                        if (bounds == null) {
                            onSelect(candyTrailTab.id)
                            onClose()
                        } else {
                            val preview = controller.previews[candyTrailTab.id]
                                ?.takeIf { !candyTrailTab.isIncognito && !it.isRecycled }
                            updateExitHero(
                                TabExitHero(
                                    candyTrailTab.id,
                                    preview,
                                    bounds,
                                    candyTrailTab.isIncognito,
                                    previewTopInsetPx = controller.previewTopInsetPx(
                                        candyTrailTab.id,
                                    ),
                                    mode = controller.tabOverviewMode,
                                ),
                            )
                            overviewScope.launch {
                                try {
                                    exitHeroProgress.snapTo(0f)
                                    withFrameNanos { }
                                    exitHeroProgress.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(
                                            durationMillis = TabOverviewHeroRules.EXIT_DURATION_MILLIS,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                    onSelect(candyTrailTab.id)
                                    onClose()
                                } finally {
                                    updateExitHero(null)
                                }
                            }
                        }
                    },
                    onForkNode = { nodeId ->
                        controller.forkCandyTrailNode(candyTrailTab.id, nodeId)
                    },
                    onForkCreationFinished = { destinationId ->
                        onCloseCandyTrail()
                        onSelect(destinationId)
                        onClose()
                    },
                    onSelectFork = { forkId ->
                        controller.activateCandyTrailFork(candyTrailTab.id, forkId)
                    },
                    onForkSelectionFinished = { destinationId ->
                        onCloseCandyTrail()
                        onSelect(destinationId)
                        onClose()
                    },
                    onDismiss = onCloseCandyTrail,
                )
            }
        }

    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (layerVisible) 11f else -1f),
    ) {
        val actionTab = tabActionsTabId?.let { tabId ->
            controller.activeTabs.firstOrNull { it.id == tabId }
        }
        TabActionsFloatingMenu(
            tab = actionTab,
            backdropSource = overviewBlurTarget.asCandyChromeBackdropSource(),
            profiles = if (controller.profilesEnabled) {
                controller.profiles
            } else {
                controller.profiles.take(1)
            },
            isFavorite = actionTab?.let { tab -> controller.isFavorite(tab.url) } == true,
            canToggleDomainMute = actionTab?.let { tab ->
                controller.canToggleDomainMute(tab.id)
            } == true,
            isDomainMuted = actionTab?.let { tab ->
                controller.isDomainMuted(tab.id)
            } == true,
            canCloseAllTabs = controller.activeTabs.any(TabDeletionRules::canDelete),
            hasPinnedTabs = controller.activeTabs.any(BrowserTab::isPinned),
            onToggleFavorite = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                onToggleFavoriteTab(target.id)
            },
            onOpenCandyTrail = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                val bounds = tabCardBounds[target.id]
                tabActionsTabId = null
                onOpenCandyTrail(target.id, bounds)
            },
            onTogglePinned = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                overviewScope.launch {
                    val oldOrder = controller.activeTabs.map(BrowserTab::id)
                    val tabsWithUpdatedPin = TabPinningRules.withPinnedState(
                        tabs = controller.activeTabs,
                        tabId = target.id,
                        isPinned = !target.isPinned,
                    )
                    val newOrder = if (controller.automaticTabSortingEnabled) {
                        TabAutoSortingRules.orderedTabs(
                            tabs = tabsWithUpdatedPin,
                            selectedTabId = controller.selectedTabId,
                        )
                    } else {
                        tabsWithUpdatedPin
                    }.map(BrowserTab::id)
                    if (controller.tabOverviewMode != TabOverviewMode.Hero) {
                        if (controller.setTabPinned(target.id, !target.isPinned)) {
                            rootView.performConfirmHaptic()
                        }
                        return@launch
                    }
                    if (oldOrder == newOrder) {
                        if (controller.setTabPinned(target.id, !target.isPinned)) {
                            rootView.performConfirmHaptic()
                        }
                        return@launch
                    }
                    val animation = TabReorderAnimation(
                        tabId = target.id,
                        targetIndex = newOrder.indexOf(target.id).coerceAtLeast(0),
                        indexDeltas = TabReorderMotion.indexDeltas(oldOrder, newOrder),
                    )
                    try {
                        reorderProgress.snapTo(0f)
                        reorderAnimation = animation
                        // Switch Pager to temporary position keys before list mutation. Stable tab
                        // keys would move viewport anchor with target and break FLIP start positions.
                        withFrameNanos { }
                        if (!controller.setTabPinned(target.id, !target.isPinned)) return@launch
                        reorderLayoutReady = true
                        withFrameNanos { }
                        rootView.performConfirmHaptic()
                        reorderProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 360,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                        if (pagerState.currentPage != animation.targetIndex) {
                            pagerState.animateScrollToPage(
                                page = animation.targetIndex,
                                animationSpec = tween(
                                    durationMillis = 240,
                                    easing = FastOutSlowInEasing,
                                ),
                            )
                        }
                    } finally {
                        reorderProgress.snapTo(1f)
                        reorderLayoutReady = false
                        reorderAnimation = null
                    }
                }
            },
            onMoveToProfile = { profileId ->
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                overviewScope.launch {
                    try {
                        movingTabId = target.id
                        moveProgress.snapTo(0f)
                        moveProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                        )
                        if (controller.moveTabToProfile(target.id, profileId)) {
                            rootView.performConfirmHaptic()
                        }
                    } finally {
                        moveProgress.snapTo(0f)
                        movingTabId = null
                    }
                }
            },
            onShare = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                controller.sharePage(target.id)
            },
            onOpenExternal = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                controller.openPageExternally(target.id)
            },
            onPrint = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                controller.printPage(target.id)
            },
            onDomainMutedChange = { muted ->
                val target = actionTab ?: return@TabActionsFloatingMenu
                if (controller.setDomainMuted(target.id, muted)) {
                    rootView.performConfirmHaptic()
                }
            },
            onAddSiteCapsule = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                onAddSiteCapsule(target.id)
            },
            onSummarize = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                controller.summarizePageWithAssistant(target.id)
            },
            onSnooze = {
                val target = actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                onSnoozeTab(target.id)
            },
            onCloseAllTabs = {
                actionTab ?: return@TabActionsFloatingMenu
                tabActionsTabId = null
                if (controller.closeAllTabs() > 0) rootView.performConfirmHaptic()
            },
            onDismiss = { tabActionsTabId = null },
            extensionActions = if (actionTab?.id == controller.selectedTabId) {
                controller.firefoxExtensionActions
            } else {
                emptyList()
            },
            onExtensionAction = { actionKey ->
                tabActionsTabId = null
                controller.clickFirefoxExtensionAction(actionKey)
            },
        )

        val actionProfile = profileActionsProfileId?.let { profileId ->
            controller.localBrowserProfiles.firstOrNull { it.id == profileId }
        }
        ProfileActionsSheet(
            profile = actionProfile,
            canDelete = controller.localBrowserProfiles.size > 1,
            isolationSupported = controller.isProfileIsolationSupported,
            onChangeEmoji = {
                val target = actionProfile ?: return@ProfileActionsSheet
                profileActionsProfileId = null
                emojiPickerTargetId = target.id
            },
            onCustomizeWallpaper = { wallpaperTarget ->
                val target = actionProfile ?: return@ProfileActionsSheet
                profileActionsProfileId = null
                onEditProfileWallpaper(target.id, wallpaperTarget)
            },
            onDelete = {
                val target = actionProfile ?: return@ProfileActionsSheet
                profileActionsProfileId = null
                controller.deleteProfileAsync(target.id) { deleted ->
                    if (deleted) rootView.performConfirmHaptic()
                }
            },
            onIsolationChange = { enabled ->
                val target = actionProfile ?: return@ProfileActionsSheet
                profileActionsProfileId = null
                profileIsolationChange = target.id to enabled
            },
            onDismiss = { profileActionsProfileId = null },
        )

        profileIsolationChange?.let { (profileId, enabled) ->
            AlertDialog(
                onDismissRequest = { profileIsolationChange = null },
                title = { Text(stringResource(R.string.profile_isolation_confirm_title)) },
                text = {
                    Text(
                        stringResource(
                            if (enabled) R.string.profile_isolation_enable_message
                            else R.string.profile_isolation_disable_message,
                        ),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (controller.setProfileIsolation(profileId, enabled)) {
                                rootView.performConfirmHaptic()
                            }
                            profileIsolationChange = null
                        },
                    ) {
                        Text(stringResource(R.string.action_switch_storage))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { profileIsolationChange = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        val emojiPickerTarget = emojiPickerTargetId
        EmojiPickerSheet(
            visible = emojiPickerTarget != null,
            creatingProfile = emojiPickerTarget == NEW_PROFILE_TARGET,
            isolationSupported = controller.isProfileIsolationSupported,
            emojis = controller.syncIconCatalog.icons.map { it.emoji },
            selectedEmoji = controller.localBrowserProfiles
                .firstOrNull { it.id == emojiPickerTarget }
                ?.emoji,
            onCreate = { emoji, isolationEnabled ->
                if (emojiPickerTarget != NEW_PROFILE_TARGET) return@EmojiPickerSheet
                val changed = controller.createProfile(emoji, isolationEnabled) != null
                if (changed) {
                    emojiPickerTargetId = null
                    rootView.performConfirmHaptic()
                }
            },
            onSelect = { emoji ->
                val target = emojiPickerTarget ?: return@EmojiPickerSheet
                if (target == NEW_PROFILE_TARGET) return@EmojiPickerSheet
                emojiPickerTargetId = null
                val changed = controller.updateProfileEmoji(target, emoji)
                if (changed) rootView.performConfirmHaptic()
            },
            onDismiss = { emojiPickerTargetId = null },
        )
    }

}

@Composable
internal fun TabOverviewBackground(
    wallpaper: ProfileWallpaperRuntime?,
    statusBarInsets: WindowInsets = WindowInsets.statusBars,
    navigationBarInsets: WindowInsets = WindowInsets.navigationBars,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val backgroundModifier = if (wallpaper == null) {
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    colors.primaryContainer,
                    colors.tertiaryContainer,
                    colors.surface,
                ),
            ),
        )
    } else {
        Modifier.drawBehind {
            drawProfileWallpaper(
                bitmap = wallpaper.bitmap,
                wallpaper = wallpaper.wallpaper,
                scrimAlpha = 0.54f,
            )
        }
    }
    Box(
        modifier = modifier
            .testTag(TabOverviewChromeTestTags.Background)
            .then(backgroundModifier),
    ) {
        if (wallpaper != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(statusBarInsets)
                    .background(colors.surface.copy(alpha = 0.92f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(navigationBarInsets)
                    .background(colors.surface.copy(alpha = 0.92f)),
            )
        }
    }
}
