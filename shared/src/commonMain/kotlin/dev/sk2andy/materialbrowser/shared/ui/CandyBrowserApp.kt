package dev.sk2andy.materialbrowser.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.PushPin
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItem
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuItemKind
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuLabelKey
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuSection
import dev.sk2andy.materialbrowser.shared.ui.settings.CandySettingsHome
import dev.sk2andy.materialbrowser.shared.ui.settings.SyncSettingsActionSink
import dev.sk2andy.materialbrowser.shared.ui.settings.SyncSettingsUiState
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BrowserViewportTab(
    val id: String,
    val title: String,
    val address: String,
    val isSelected: Boolean,
    val isFavorite: Boolean,
    val isPinned: Boolean,
)

data class BrowserViewportTopping(
    val id: String,
    val name: String,
    val enabled: Boolean,
)

data class BrowserReaderSnapshot(
    val result: ReaderExtractionResult?,
    val sourceUrl: String,
    val isPrivate: Boolean,
)

data class BrowserCandyTrailSnapshot(
    val tabId: String,
    val trail: CandyTrail,
)

data class BrowserViewportSnapshot(
    val address: String,
    val pageTitle: String,
    val tabCountLabel: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val isLoading: Boolean,
    val menuItems: List<BrowserFeatureMenuItem>,
    val tabs: List<BrowserViewportTab>,
    val profiles: List<BrowserViewportProfile> = emptyList(),
    val profileIconEmojis: List<String> = emptyList(),
    val profileIsolationSupported: Boolean = true,
    val activeProfileId: String = "",
    val isTabOverviewVisible: Boolean,
    val isSettingsVisible: Boolean = false,
    val tabOverviewMode: TabOverviewMode,
    val addressFocusRequest: Long,
    val searchEngine: SearchEngine = SearchEngine.Google,
    val searxngInstanceUrl: String = "",
    val translationProvider: PageTranslationProvider = PageTranslationProvider.Yandex,
    val toppings: List<BrowserViewportTopping> = emptyList(),
    val reader: BrowserReaderSnapshot? = null,
    val candyTrail: BrowserCandyTrailSnapshot? = null,
    val syncSettings: SyncSettingsUiState = SyncSettingsUiState(),
)

enum class CandyBrowserUiAction {
    Navigate,
    Back,
    Forward,
    Reload,
    Stop,
    NewTab,
    ShowTabs,
}

interface BrowserViewportActionSink : SyncSettingsActionSink {
    fun addressChanged(value: String)

    fun perform(action: CandyBrowserUiAction)

    fun performMenu(item: BrowserFeatureMenuItem)

    fun performTabAction(
        tabId: String,
        action: BrowserFeatureMenuAction,
    )

    fun addressDragged(
        horizontal: Double,
        vertical: Double,
        velocityX: Double,
        viewportWidth: Double,
        isAddressEditing: Boolean,
    )

    fun selectTab(tabId: String)

    fun selectTabInOverview(tabId: String)

    fun closeTab(tabId: String)

    fun selectProfile(profileId: String)

    fun createProfile(
        emoji: String,
        isolationEnabled: Boolean,
    )

    fun updateProfileEmoji(
        profileId: String,
        emoji: String,
    )

    fun setProfileIsolation(
        profileId: String,
        enabled: Boolean,
    )

    fun hideTabOverview()

    fun showSettings()

    fun dismissSettings()

    fun changeTabOverviewMode(mode: TabOverviewMode)

    fun changeSearchEngine(searchEngine: SearchEngine)

    fun changeSearxngInstanceUrl(value: String)

    fun changeTranslationProvider(provider: PageTranslationProvider)

    fun saveTopping(
        id: String?,
        source: String,
    )

    fun toppingSource(id: String): String?

    fun setToppingEnabled(
        id: String,
        enabled: Boolean,
    )

    fun deleteTopping(id: String)

    fun retryReader()

    fun dismissReader()

    fun openReaderOriginal(url: String)

    fun openReaderLink(url: String)

    fun dismissCandyTrail()

    fun selectCandyTrailNode(
        tabId: String,
        nodeId: String,
    ): Boolean

    fun forkCandyTrailNode(
        tabId: String,
        nodeId: String,
    ): String?

    fun activateCandyTrailFork(
        tabId: String,
        forkId: String,
    ): String?
}

/**
 * Strangler seam between the shared browser chrome and a platform engine view.
 * The viewport stays native; all chrome anatomy and its action routing live here.
 */
@Composable
fun CandyBrowserApp(
    snapshot: BrowserViewportSnapshot,
    actionSink: BrowserViewportActionSink,
    browserViewport: @Composable () -> Unit,
    chromeEffects: CandyBrowserChromeEffects = DefaultCandyBrowserChromeEffects,
    showBrowserChrome: Boolean = true,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit = { tab, modifier ->
        CandyTabPreviewFallback(tab = tab, modifier = modifier)
    },
) {
    val readerRepository = remember { SessionReaderLibraryDataSource() }
    chromeEffects.browserTheme {
        val reader = snapshot.reader
        val candyTrail = snapshot.candyTrail
        val candyTrailTab = candyTrail?.let { trail ->
            snapshot.tabs.firstOrNull { tab -> tab.id == trail.tabId }
        }
        if (candyTrail != null && candyTrailTab != null) {
            CandyTrailScreenContent(
                tab = candyTrailTab.toBrowserTab(),
                trail = candyTrail.trail,
                favicon = null,
                forkFavicons = emptyMap(),
                strings = candyBrowserTrailStrings,
                nodeActionsContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                onSelectNode = { nodeId ->
                    actionSink.selectCandyTrailNode(candyTrail.tabId, nodeId)
                },
                onNodeSelectionFinished = actionSink::dismissCandyTrail,
                onForkNode = { nodeId ->
                    actionSink.forkCandyTrailNode(candyTrail.tabId, nodeId)
                },
                onForkCreationFinished = { actionSink.dismissCandyTrail() },
                onSelectFork = { forkId ->
                    actionSink.activateCandyTrailFork(candyTrail.tabId, forkId)
                },
                onForkSelectionFinished = { actionSink.dismissCandyTrail() },
                onDismiss = actionSink::dismissCandyTrail,
                onHaptic = {},
            )
        } else if (reader != null) {
            ReaderStudioScreen(
                result = reader.result,
                sourceUrl = reader.sourceUrl,
                isPrivate = reader.isPrivate,
                repository = readerRepository,
                resources = DefaultReaderStudioResources,
                speechFactory = ::UnavailableReaderSpeech,
                onRetry = actionSink::retryReader,
                onDismiss = actionSink::dismissReader,
                onOpenOriginal = actionSink::openReaderOriginal,
                onOpenLink = actionSink::openReaderLink,
            )
        } else if (snapshot.isSettingsVisible) {
            CandySettingsHome(
                searchEngine = snapshot.searchEngine,
                searxngInstanceUrl = snapshot.searxngInstanceUrl,
                onSearchEngineChanged = actionSink::changeSearchEngine,
                onSearxngInstanceUrlChanged = actionSink::changeSearxngInstanceUrl,
                tabOverviewMode = snapshot.tabOverviewMode,
                onTabOverviewModeChanged = actionSink::changeTabOverviewMode,
                translationProvider = snapshot.translationProvider,
                onTranslationProviderChanged = actionSink::changeTranslationProvider,
                toppings = snapshot.toppings,
                onSaveTopping = actionSink::saveTopping,
                toppingSource = actionSink::toppingSource,
                onSetToppingEnabled = actionSink::setToppingEnabled,
                onDeleteTopping = actionSink::deleteTopping,
                onDismiss = actionSink::dismissSettings,
                syncState = snapshot.syncSettings,
                syncActions = actionSink,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (snapshot.isTabOverviewVisible) {
                    CandyTabOverview(
                        snapshot = snapshot,
                        actionSink = actionSink,
                        tabPreview = tabPreview,
                    )
                } else if (snapshot.address.isBlank()) {
                    CandyBlankTab()
                } else {
                    browserViewport()
                }
                if (showBrowserChrome && !snapshot.isTabOverviewVisible) {
                    CandyBrowserChrome(
                        snapshot = snapshot,
                        actionSink = actionSink,
                        onOpenSettings = actionSink::showSettings,
                        chromeEffects = chromeEffects,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CandyBlankTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFC8D4FB),
                        Color(0xFFD9E1FF),
                        Color(0xFFE5E8FA),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(108.dp)
                .clip(RoundedCornerShape(54.dp))
                .background(Color(0xFF53689A)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(62.dp)) {
                val stroke = Stroke(width = size.minDimension * 0.24f, cap = StrokeCap.Round)
                drawArc(
                    color = Color(0xFFFF2F78),
                    startAngle = 205f,
                    sweepAngle = 86f,
                    useCenter = false,
                    style = stroke,
                )
                drawArc(
                    color = Color(0xFFFF2F78),
                    startAngle = 325f,
                    sweepAngle = 86f,
                    useCenter = false,
                    style = stroke,
                )
                drawArc(
                    color = Color(0xFF7457D7),
                    startAngle = 85f,
                    sweepAngle = 86f,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
    }
}

@Composable
private fun CandyTabOverview(
    snapshot: BrowserViewportSnapshot,
    actionSink: BrowserViewportActionSink,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val screenSize = with(density) {
        DpSize(windowSize.width.toDp(), windowSize.height.toDp())
    }
    val overviewContentTopPadding =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
    val overviewContentBottomPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabContentTopPadding = if (snapshot.profiles.isEmpty()) {
        overviewContentTopPadding
    } else {
        4.dp
    }
    var tabActionsTabId by remember { mutableStateOf<String?>(null) }
    var profileActionsProfileId by remember { mutableStateOf<String?>(null) }
    var profileEmojiTargetId by remember { mutableStateOf<String?>(null) }
    var profileIsolationChange by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var overviewActionTargetId by remember {
        mutableStateOf(snapshot.tabs.firstOrNull(BrowserViewportTab::isSelected)?.id)
    }
    var dismissingTabId by remember { mutableStateOf<String?>(null) }
    val overviewScope = rememberCoroutineScope()
    val initialTab = remember {
        snapshot.tabs.firstOrNull(BrowserViewportTab::isSelected) ?: snapshot.tabs.firstOrNull()
    }
    val initialTabId = initialTab?.id.orEmpty()
    val heroProgress = remember { Animatable(0f) }
    val exitHeroProgress = remember { Animatable(0f) }
    var heroTargetBounds by remember { mutableStateOf<Rect?>(null) }
    var heroTargetMode by remember { mutableStateOf<TabOverviewMode?>(null) }
    var heroTargetTabId by remember { mutableStateOf<String?>(null) }
    var heroCompleted by remember { mutableStateOf(false) }
    var heroVisible by remember { mutableStateOf(true) }
    var exitHero by remember { mutableStateOf<CandyTabExitHero?>(null) }
    val bottomBarTopPx = remember { mutableFloatStateOf(windowSize.height.toFloat()) }

    LaunchedEffect(snapshot.tabOverviewMode, initialTabId) {
        heroProgress.snapTo(0f)
        heroCompleted = false
        heroVisible = true
        var waitMillis = 0L
        while (
            waitMillis < HERO_TARGET_WAIT_MILLIS &&
            !TabOverviewHeroRules.canStart(
                heroTargetBounds != null &&
                    heroTargetMode == snapshot.tabOverviewMode &&
                    heroTargetTabId == initialTabId,
            )
        ) {
            delay(HERO_TARGET_POLL_MILLIS)
            waitMillis += HERO_TARGET_POLL_MILLIS
        }
        if (
            TabOverviewHeroRules.canStart(
                heroTargetBounds != null &&
                    heroTargetMode == snapshot.tabOverviewMode &&
                    heroTargetTabId == initialTabId,
            )
        ) {
            heroProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TabOverviewHeroRules.ENTRY_DURATION_MILLIS,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            heroProgress.snapTo(1f)
        }
        heroCompleted = true
        withFrameNanos { }
        heroVisible = false
    }

    fun startExitHero(
        tab: BrowserTab,
        bounds: Rect,
        mode: TabOverviewMode,
    ) {
        if (
            !heroCompleted ||
            dismissingTabId != null ||
            tabActionsTabId != null ||
            exitHero != null
        ) {
            return
        }
        val sourceTab = snapshot.tabs.firstOrNull { source -> source.id == tab.id } ?: return
        exitHero = CandyTabExitHero(
            tab = sourceTab,
            startBounds = bounds,
            mode = mode,
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
                actionSink.selectTab(tab.id)
                actionSink.hideTabOverview()
            } finally {
                exitHero = null
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TabOverviewChromeTestTags.Root),
    ) {
        val rootWidthPx = with(density) { maxWidth.toPx() }
        val rootHeightPx = with(density) { maxHeight.toPx() }
        val isExiting = exitHero != null
        val heroTarget = heroTargetBounds?.takeIf {
            heroTargetMode == snapshot.tabOverviewMode && heroTargetTabId == initialTabId
        }
        val entryHeroVisible = heroTarget != null && heroVisible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .graphicsLayer {
                    alpha = TabOverviewHeroRules.backgroundAlpha(
                        entryProgress = heroProgress.value,
                        isExiting = isExiting,
                    )
                }
                .testTag(TabOverviewChromeTestTags.Background),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = TabOverviewHeroRules.contentAlpha(
                        exitProgress = exitHeroProgress.value,
                        isExiting = isExiting,
                    )
                },
        ) {
        if (snapshot.profiles.isNotEmpty()) {
            Spacer(Modifier.height(overviewContentTopPadding))
            ProfileSwitcher(
                profiles = snapshot.profiles,
                activeProfileId = snapshot.activeProfileId,
                enabled = heroCompleted &&
                    dismissingTabId == null &&
                    tabActionsTabId == null &&
                    exitHero == null,
                onSelect = actionSink::selectProfile,
                onLongClick = { profileId ->
                    snapshot.profiles
                        .firstOrNull { it.id == profileId && !it.isSyncLinked }
                        ?.let { profileActionsProfileId = profileId }
                },
                onAdd = { profileEmojiTargetId = NEW_PROFILE_TARGET },
                modifier = Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        val chromeProgress =
                            ((heroProgress.value - 0.34f) / 0.66f).coerceIn(0f, 1f)
                        alpha = chromeProgress
                        translationY = (1f - chromeProgress) * -18f
                    },
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (snapshot.tabOverviewMode) {
                TabOverviewMode.Hero -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val tabs = snapshot.tabs.map { tab ->
                    BrowserTab(
                        id = tab.id,
                        lastAccessedAt = 0L,
                        title = tab.title,
                        url = tab.address.ifBlank { BLANK_URL },
                        isPinned = tab.isPinned,
                    )
                }
                val selectedTabId = snapshot.tabs
                    .firstOrNull(BrowserViewportTab::isSelected)
                    ?.id
                    ?: tabs.firstOrNull()?.id
                    .orEmpty()
                val initialPage = tabs.indexOfFirst { it.id == selectedTabId }.coerceAtLeast(0)
                val pagerState = rememberPagerState(
                    initialPage = initialPage,
                    pageCount = tabs::size,
                )
                LaunchedEffect(selectedTabId, tabs.map(BrowserTab::id)) {
                    val targetPage = tabs.indexOfFirst { it.id == selectedTabId }
                    if (targetPage >= 0 && pagerState.currentPage != targetPage) {
                        pagerState.scrollToPage(targetPage)
                    }
                }
                LaunchedEffect(pagerState.currentPage, tabs.map(BrowserTab::id)) {
                    overviewActionTargetId = tabs.getOrNull(pagerState.currentPage)?.id
                }
                val layout = TabOverviewHeroRules.coverflowCardLayout(
                    viewportWidth = maxWidth.value,
                    viewportHeight = maxHeight.value,
                )
                val cardWidth = layout.width.dp
                val pageSlotWidth = cardWidth + 12.dp
                val pageHorizontalPadding = ((maxWidth - pageSlotWidth) / 2)
                    .coerceAtLeast(0.dp)
                val rootHeightPx = with(density) { maxHeight.toPx() }
                TabOverviewHeroPager(
                    pagerState = pagerState,
                    tabs = tabs,
                    initialTabId = initialTabId,
                    tabCardWidth = cardWidth,
                    cardAspectRatio = layout.aspectRatio,
                    pageSlotWidth = pageSlotWidth,
                    pageHorizontalPadding = pageHorizontalPadding,
                    topPadding = 4.dp,
                    bottomPadding = 4.dp,
                    heroProgress = { heroProgress.value },
                    heroCompleted = heroCompleted,
                    heroVisible = entryHeroVisible,
                    rootHeightPx = rootHeightPx,
                    dismissResistanceFraction =
                        TabDismissPhysics.DEFAULT_RESISTANCE_FRACTION,
                    dismissingTabId = dismissingTabId,
                    movingTabId = null,
                    movingProgress = { 0f },
                    exitHeroTabId = exitHero?.tab?.id,
                    tabActionsTabId = tabActionsTabId,
                    reorder = TabOverviewHeroPagerReorder(),
                    operationScope = overviewScope,
                    currentTabs = {
                        tabs.filterNot { it.id == dismissingTabId }
                    },
                    selectedTabId = { selectedTabId },
                    onDismissingTabChanged = { dismissingTabId = it },
                    onSelectDismissAnchor = actionSink::selectTabInOverview,
                    onSelectTab = actionSink::selectTab,
                    onCloseTab = { tabId ->
                        if (tabs.firstOrNull { it.id == tabId }?.isPinned == false) {
                            actionSink.closeTab(tabId)
                        }
                    },
                    onCloseOverview = actionSink::hideTabOverview,
                    onStartExitHero = { tab, bounds ->
                        startExitHero(tab, bounds, TabOverviewMode.Hero)
                    },
                    onCardBounds = { tab, bounds, isInitialCard ->
                        if (isInitialCard && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.Hero
                            heroTargetTabId = tab.id
                        }
                    },
                    onCardBoundsDisposed = { tab, bounds ->
                        if (heroTargetTabId == tab.id && heroTargetBounds == bounds) {
                            heroTargetBounds = null
                        }
                    },
                    canDelete = { tab -> !tab.isPinned },
                    haptics = TabOverviewHeroPagerHaptics(),
                    previewContent = { tab ->
                        snapshot.tabs.firstOrNull { it.id == tab.id }?.let { sourceTab ->
                            tabPreview(sourceTab, Modifier.fillMaxSize())
                        }
                    },
                    titleContent = { tab, isInitialCard, modifier ->
                        val sourceTab = snapshot.tabs.firstOrNull { it.id == tab.id }
                        val visuals = TabOverviewHeroVisuals(
                            title = tab.title.ifBlank { "Neuer Tab" },
                            subtitle = sourceTab?.address.orEmpty().ifBlank {
                                "Adresse eingeben"
                            },
                            hasFavicon = false,
                            chromeContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            pinnedContentDescription = "Angeheftet",
                            closeContentDescription = "Tab schließen",
                            titleTestTag = "tab-overview-title-${tab.id}",
                            closeTestTag = "tab-overview-close-${tab.id}",
                            favicon = { size ->
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(size),
                                )
                            },
                            incognitoIcon = { iconModifier, tint ->
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = iconModifier,
                                    tint = tint,
                                )
                            },
                            blankIcon = { iconModifier ->
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = iconModifier,
                                )
                            },
                            pinnedIcon = { iconModifier, tint ->
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Angeheftet",
                                    modifier = iconModifier,
                                    tint = tint,
                                )
                            },
                        )
                        TabTitleRow(
                            tab = tab,
                            visuals = visuals,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            alpha = {
                                if (isInitialCard) {
                                    TabOverviewHeroRules.titleAlpha(heroProgress.value)
                                } else {
                                    1f
                                }
                            },
                            modifier = modifier,
                        )
                    },
                    cardModifier = { tab ->
                        Modifier.testTag("tab-overview-card-${tab.id}")
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(TabOverviewChromeTestTags.HeroPager),
                )
            }
                TabOverviewMode.Grid -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val tabs = snapshot.tabs.map(BrowserViewportTab::toBrowserTab)
                val selectedTabId = snapshot.tabs
                    .firstOrNull(BrowserViewportTab::isSelected)
                    ?.id
                    ?: tabs.firstOrNull()?.id.orEmpty()
                val gridState = rememberLazyGridState(
                    initialFirstVisibleItemIndex = tabs
                        .indexOfFirst { it.id == selectedTabId }
                        .coerceAtLeast(0),
                )
                var viewportSize by remember { mutableStateOf(IntSize.Zero) }
                overviewActionTargetId = selectedTabId
                CompactTabGrid(
                    gridState = gridState,
                    layout = TabOverviewGridRules.layout(
                        viewportWidth = maxWidth.value,
                        viewportHeight = maxHeight.value,
                    ),
                    tabs = tabs,
                    startsAtBottom = false,
                    visible = true,
                    selectedTabId = selectedTabId,
                    initialTabId = initialTabId,
                    visuals = { tab ->
                        candyTabOverviewVisuals(
                            tab = tab,
                            sourceTab = snapshot.tabs.firstOrNull { it.id == tab.id },
                        )
                    },
                    previewContent = { tab ->
                        snapshot.tabs.firstOrNull { it.id == tab.id }?.let { sourceTab ->
                            tabPreview(sourceTab, Modifier.fillMaxSize())
                        }
                    },
                    heroProgress = { heroProgress.value },
                    heroCompleted = heroCompleted,
                    heroVisible = entryHeroVisible,
                    exitHeroTabId = exitHero?.tab?.id,
                    dismissResistanceFraction = TabDismissPhysics.DEFAULT_RESISTANCE_FRACTION,
                    interactionsEnabled = heroCompleted &&
                        dismissingTabId == null &&
                        tabActionsTabId == null &&
                        exitHero == null,
                    reorderSessionId = null,
                    reorderDraggedTabId = null,
                    reorderModifier = { Modifier },
                    reorderAutoScroll = {},
                    viewportSize = { viewportSize },
                    canDelete = { tab -> !tab.isPinned },
                    haptics = TabOverviewHeroPagerHaptics(),
                    onReorderBounds = { _, _ -> },
                    onReorderBoundsDisposed = { _, _ -> },
                    onPreviewBounds = { tab, bounds ->
                        if (tab.id == initialTabId && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.Grid
                            heroTargetTabId = tab.id
                        }
                    },
                    onPreviewBoundsDisposed = { tab, bounds ->
                        if (heroTargetTabId == tab.id && heroTargetBounds == bounds) {
                            heroTargetBounds = null
                        }
                    },
                    onSelect = { tab, bounds ->
                        startExitHero(tab, bounds, TabOverviewMode.Grid)
                    },
                    onCloseTab = { tab ->
                        if (!tab.isPinned) {
                            actionSink.closeTab(tab.id)
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
                        if (!tab.isPinned) {
                            actionSink.closeTab(tab.id)
                        }
                    },
                    gridTestTag = TabOverviewChromeTestTags.Grid,
                    tabTestTag = { tab -> "overview_tab:${tab.id}" },
                    topPadding = tabContentTopPadding,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportSize = it },
                )
            }
                TabOverviewMode.List -> {
                val tabs = snapshot.tabs.map(BrowserViewportTab::toBrowserTab)
                val selectedTabId = snapshot.tabs
                    .firstOrNull(BrowserViewportTab::isSelected)
                    ?.id
                    ?: tabs.firstOrNull()?.id.orEmpty()
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = tabs
                        .indexOfFirst { it.id == selectedTabId }
                        .coerceAtLeast(0),
                )
                overviewActionTargetId = selectedTabId
                CompactTabList(
                    listState = listState,
                    tabs = tabs,
                    startsAtBottom = false,
                    visible = true,
                    selectedTabId = selectedTabId,
                    initialTabId = initialTabId,
                    visuals = { tab ->
                        candyTabOverviewVisuals(
                            tab = tab,
                            sourceTab = snapshot.tabs.firstOrNull { it.id == tab.id },
                        )
                    },
                    heroProgress = { heroProgress.value },
                    heroCompleted = heroCompleted,
                    heroVisible = entryHeroVisible,
                    exitHeroTabId = exitHero?.tab?.id,
                    interactionsEnabled = heroCompleted &&
                        dismissingTabId == null &&
                        tabActionsTabId == null &&
                        exitHero == null,
                    reorderSessionId = null,
                    reorderDraggedTabId = null,
                    reorderModifier = { Modifier },
                    reorderAutoScroll = {},
                    onRowBounds = { tab, bounds ->
                        if (tab.id == initialTabId && !heroCompleted) {
                            heroTargetBounds = bounds
                            heroTargetMode = TabOverviewMode.List
                            heroTargetTabId = tab.id
                        }
                    },
                    onRowBoundsDisposed = { tab, bounds ->
                        if (heroTargetTabId == tab.id && heroTargetBounds == bounds) {
                            heroTargetBounds = null
                        }
                    },
                    onSelect = { tab, bounds ->
                        startExitHero(tab, bounds, TabOverviewMode.List)
                    },
                    onCloseTab = { tab ->
                        if (!tab.isPinned) {
                            actionSink.closeTab(tab.id)
                        }
                    },
                    listTestTag = TabOverviewChromeTestTags.List,
                    tabTestTag = { tab -> "overview_tab:${tab.id}" },
                    topPadding = tabContentTopPadding,
                    modifier = Modifier.fillMaxSize(),
                )
                }
            }
        }
        TabOverviewBottomChrome(
            visible = true,
            enabled = heroCompleted &&
                dismissingTabId == null &&
                tabActionsTabId == null &&
                exitHero == null,
            onNewTab = { actionSink.perform(CandyBrowserUiAction.NewTab) },
            onMore = { tabActionsTabId = overviewActionTargetId },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            bottomInset = overviewContentBottomPadding,
            newTabIcon = {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Tab")
            },
            moreIcon = {
                Icon(Icons.Filled.MoreVert, contentDescription = "Mehr")
            },
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    bottomBarTopPx.floatValue = coordinates.boundsInRoot().top
                }
                .graphicsLayer {
                    alpha = TabOverviewHeroRules.chromeAlpha(
                        entryProgress = heroProgress.value,
                        exitProgress = exitHeroProgress.value,
                        isExiting = isExiting,
                    )
                }
                .then(
                    if (heroCompleted && !isExiting) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { }
                    },
                ),
        )
        }

        if (entryHeroVisible && initialTab != null) {
            TabHeroLayer(
                targetBounds = heroTarget,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
                targetCornerRadius = if (snapshot.tabOverviewMode == TabOverviewMode.Hero) {
                    28.dp
                } else {
                    22.dp
                },
                targetFraction = { heroProgress.value },
                modifier = Modifier.zIndex(20f),
            ) {
                CandyTabHeroContent(
                    tab = initialTab,
                    mode = snapshot.tabOverviewMode,
                    targetBounds = heroTarget,
                    rootWidthPx = rootWidthPx,
                    rootHeightPx = rootHeightPx,
                    bottomBarTopPx = bottomBarTopPx,
                    targetFraction = { heroProgress.value },
                    tabPreview = tabPreview,
                )
            }
        }
        exitHero?.let { hero ->
            val targetFraction = {
                TabOverviewHeroRules.targetFraction(
                    entryProgress = 1f,
                    exitProgress = exitHeroProgress.value,
                    isExiting = true,
                )
            }
            TabHeroLayer(
                targetBounds = hero.startBounds,
                rootWidthPx = rootWidthPx,
                rootHeightPx = rootHeightPx,
                targetCornerRadius = if (hero.mode == TabOverviewMode.Hero) 28.dp else 22.dp,
                targetFraction = targetFraction,
                modifier = Modifier.zIndex(20f),
            ) {
                CandyTabHeroContent(
                    tab = hero.tab,
                    mode = hero.mode,
                    targetBounds = hero.startBounds,
                    rootWidthPx = rootWidthPx,
                    rootHeightPx = rootHeightPx,
                    bottomBarTopPx = bottomBarTopPx,
                    targetFraction = targetFraction,
                    tabPreview = tabPreview,
                )
            }
        }
        if (entryHeroVisible || isExiting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(30f)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { change -> change.consume() }
                            }
                        }
                    },
            )
        }

        val actionTab = tabActionsTabId?.let { tabId ->
            snapshot.tabs.firstOrNull { tab -> tab.id == tabId }
        }
        TabActionsFloatingMenu(
        state = actionTab?.let { tab ->
            val hasPage = tab.address.isNotBlank()
            TabActionsMenuState(
                tabId = tab.id,
                pageSubtitle = tab.address.ifBlank { "Neuer Tab" },
                canToggleFavorite = hasPage,
                isFavorite = tab.isFavorite,
                isPinned = tab.isPinned,
                canUsePageActions = hasPage,
                canToggleDomainMute = false,
                isDomainMuted = false,
                canOpenCandyTrail = hasPage,
                canAddSiteCapsule = false,
                canSummarize = false,
                canSnooze = false,
                canCloseAllTabs = false,
                hasPinnedTabs = snapshot.tabs.any(BrowserViewportTab::isPinned),
            )
        },
        screenSize = screenSize,
        resources = CandyTabActionsMenuResources,
        onToggleFavorite = { tabId ->
            tabActionsTabId = null
            actionSink.performTabAction(tabId, BrowserFeatureMenuAction.ToggleFavorite)
        },
        onTogglePinned = { tabId ->
            tabActionsTabId = null
            actionSink.performTabAction(tabId, BrowserFeatureMenuAction.TogglePinned)
        },
        onMoveToProfile = { _, _ -> },
        onAction = { tabId, action ->
            tabActionsTabId = null
            actionSink.performTabAction(tabId, action)
        },
        onDomainMutedChange = { tabId, _ ->
            actionSink.performTabAction(tabId, BrowserFeatureMenuAction.ToggleDomainMute)
        },
        onCloseAllTabs = {},
        onDismiss = { tabActionsTabId = null },
        )

        val actionProfile = profileActionsProfileId?.let { profileId ->
            snapshot.profiles.firstOrNull { profile ->
                profile.id == profileId && !profile.isSyncLinked
            }
        }
        SharedProfileActionsSheet(
            profile = actionProfile,
            copy = CandyProfileSheetCopy,
            canDelete = false,
            isolationSupported = snapshot.profileIsolationSupported,
            onChangeEmoji = {
                val target = actionProfile ?: return@SharedProfileActionsSheet
                profileActionsProfileId = null
                profileEmojiTargetId = target.id
            },
            onCustomizeWallpaper = null,
            onDelete = null,
            onIsolationChange = { enabled ->
                val target = actionProfile ?: return@SharedProfileActionsSheet
                profileActionsProfileId = null
                profileIsolationChange = target.id to enabled
            },
            onDismiss = { profileActionsProfileId = null },
        )

        profileIsolationChange?.let { (profileId, enabled) ->
            AlertDialog(
                onDismissRequest = { profileIsolationChange = null },
                title = { Text("Profilspeicher wechseln?") },
                text = {
                    Text(
                        if (enabled) {
                            "Tabs in diesem Profil werden mit getrenntem Speicher neu geladen. " +
                                "Bestehende gemeinsame Anmeldungen werden nicht übernommen."
                        } else {
                            "Tabs in diesem Profil werden mit gemeinsamem Speicher neu geladen. " +
                                "Anmeldungen aus dem isolierten Speicher sind dann nicht mehr aktiv."
                        },
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            actionSink.setProfileIsolation(profileId, enabled)
                            profileIsolationChange = null
                        },
                    ) {
                        Text("Speicher wechseln")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { profileIsolationChange = null }) {
                        Text("Abbrechen")
                    }
                },
            )
        }

        val profileEmojiTarget = profileEmojiTargetId
        SharedProfileEmojiPickerSheet(
            visible = profileEmojiTarget != null,
            creatingProfile = profileEmojiTarget == NEW_PROFILE_TARGET,
            isolationSupported = snapshot.profileIsolationSupported,
            emojis = snapshot.profileIconEmojis,
            selectedEmoji = snapshot.profiles
                .firstOrNull { it.id == profileEmojiTarget }
                ?.emoji,
            copy = CandyProfileSheetCopy,
            onCreate = { emoji, isolationEnabled ->
                if (profileEmojiTarget != NEW_PROFILE_TARGET) {
                    return@SharedProfileEmojiPickerSheet
                }
                actionSink.createProfile(emoji, isolationEnabled)
                profileEmojiTargetId = null
            },
            onSelect = { emoji ->
                val target = profileEmojiTarget ?: return@SharedProfileEmojiPickerSheet
                if (target == NEW_PROFILE_TARGET) return@SharedProfileEmojiPickerSheet
                actionSink.updateProfileEmoji(target, emoji)
                profileEmojiTargetId = null
            },
            onDismiss = { profileEmojiTargetId = null },
        )
    }
}

private val CandyProfileSheetCopy = BrowserProfileSheetCopy(
    actionsTitle = "Profiloptionen",
    changeIcon = "Icon ändern",
    newTabWallpaper = "Hintergrund für neuen Tab",
    tabSwitcherWallpaper = "Hintergrund für Tab-Switcher",
    deleteProfile = "Profil löschen · Tabs behalten",
    addProfileTitle = "Icon für das neue Profil wählen",
    changeIconTitle = "Neues Icon wählen",
    createProfile = "Profil erstellen",
    isolationTitle = "Eigener Speicher · Isolationsmodus",
    isolationSubtitle =
        "Trennt Cookies, Anmeldungen, Websitedaten, Cache und Service Worker für dieses Profil",
    isolationUnsupported = "Separate Profilspeicher werden auf diesem Gerät nicht unterstützt",
)

private const val NEW_PROFILE_TARGET = "__new_profile__"

private data class CandyTabExitHero(
    val tab: BrowserViewportTab,
    val startBounds: Rect,
    val mode: TabOverviewMode,
)

@Composable
private fun CandyTabHeroContent(
    tab: BrowserViewportTab,
    mode: TabOverviewMode,
    targetBounds: Rect,
    rootWidthPx: Float,
    rootHeightPx: Float,
    bottomBarTopPx: FloatState,
    targetFraction: () -> Float,
    tabPreview: @Composable (BrowserViewportTab, Modifier) -> Unit,
) {
    val browserTab = tab.toBrowserTab()
    if (mode == TabOverviewMode.List) {
        TabListHeroContent(
            tab = browserTab,
            visuals = candyTabOverviewVisuals(browserTab, tab),
            targetBounds = targetBounds,
            rootWidthPx = rootWidthPx,
            rootHeightPx = rootHeightPx,
            targetFraction = targetFraction,
            fullscreenPreviewContent = {
                tabPreview(tab, Modifier.fillMaxSize())
            },
        )
    } else {
        TabCardHeroContent(
            tab = browserTab,
            previewSize = null,
            targetBounds = targetBounds,
            rootWidthPx = rootWidthPx,
            rootHeightPx = rootHeightPx,
            previewTopInsetPx = 0,
            bottomBarTopPx = bottomBarTopPx,
            targetFraction = targetFraction,
            fullscreenPreviewContent = { blankFavoritesAlpha ->
                tabPreview(
                    tab,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = blankFavoritesAlpha?.invoke() ?: 1f
                        },
                )
            },
            previewContent = {
                tabPreview(tab, Modifier.fillMaxSize())
            },
        )
    }
}

private const val HERO_TARGET_WAIT_MILLIS = 250L
private const val HERO_TARGET_POLL_MILLIS = 16L

private fun BrowserViewportTab.toBrowserTab(): BrowserTab = BrowserTab(
    id = id,
    lastAccessedAt = 0L,
    title = title,
    url = address.ifBlank { BLANK_URL },
    isPinned = isPinned,
)

@Composable
private fun candyTabOverviewVisuals(
    tab: BrowserTab,
    sourceTab: BrowserViewportTab?,
): TabOverviewHeroVisuals = TabOverviewHeroVisuals(
    title = tab.title.ifBlank { "Neuer Tab" },
    subtitle = sourceTab?.address.orEmpty().ifBlank { "Adresse eingeben" },
    hasFavicon = false,
    chromeContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    pinnedContentDescription = "Angeheftet",
    closeContentDescription = "Tab schließen",
    titleTestTag = "tab-overview-title-${tab.id}",
    closeTestTag = "tab-overview-close-${tab.id}",
    favicon = { size ->
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    },
    incognitoIcon = { iconModifier, tint ->
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            modifier = iconModifier,
            tint = tint,
        )
    },
    blankIcon = { iconModifier ->
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = iconModifier,
        )
    },
    pinnedIcon = { iconModifier, tint ->
        Icon(
            imageVector = Icons.Filled.PushPin,
            contentDescription = "Angeheftet",
            modifier = iconModifier,
            tint = tint,
        )
    },
)

@Composable
internal fun CandyTabPreviewFallback(
    tab: BrowserViewportTab,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.surfaceContainer,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun CandyBrowserChrome(
    snapshot: BrowserViewportSnapshot,
    actionSink: BrowserViewportActionSink,
    onOpenSettings: () -> Unit,
    chromeEffects: CandyBrowserChromeEffects,
    modifier: Modifier = Modifier,
) {
    val chromeMetrics = chromeEffects.metrics
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    val windowSize = windowInfo.containerSize
    val screenSize = with(density) {
        DpSize(windowSize.width.toDp(), windowSize.height.toDp())
    }
    var address by remember { mutableStateOf(TextFieldValue(snapshot.address)) }
    var menuExpanded by remember { mutableStateOf(false) }
    val menuMorphProgress = remember { Animatable(0f) }
    var isAddressEditing by remember { mutableStateOf(false) }
    var addressFieldHasReceivedFocus by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportWidth by remember { mutableStateOf(1f) }
    val addressFocusRequester = remember { FocusRequester() }
    val velocityTracker = remember { VelocityTracker() }
    LaunchedEffect(snapshot.address) {
        if (!isAddressEditing) {
            address = TextFieldValue(snapshot.address)
        }
    }
    LaunchedEffect(menuExpanded, chromeEffects.reducesMotion()) {
        val target = if (menuExpanded) 1f else 0f
        if (chromeEffects.reducesMotion()) {
            menuMorphProgress.snapTo(target)
        } else {
            menuMorphProgress.animateTo(
                targetValue = target,
                animationSpec = tween(
                    durationMillis = AddressMenuMorphRules.DURATION_MILLIS,
                    easing = if (menuExpanded) LinearOutSlowInEasing else FastOutLinearInEasing,
                ),
            )
        }
    }
    val menuMorphFrame = AddressMenuMorphRules.frame(
        animatedProgress = menuMorphProgress.value,
        expanded = menuExpanded,
        reduceMotion = chromeEffects.reducesMotion(),
    )
    LaunchedEffect(snapshot.addressFocusRequest) {
        if (
            AddressBarFieldFocusRules.shouldApplyFocusRequest(
                request = snapshot.addressFocusRequest,
                address = snapshot.address,
            )
        ) {
            isAddressEditing = true
        }
    }
    LaunchedEffect(
        isAddressEditing,
        snapshot.addressFocusRequest,
        windowInfo.isWindowFocused,
    ) {
        if (isAddressEditing && windowInfo.isWindowFocused) {
            withFrameNanos { }
            addressFocusRequester.requestFocus()
            keyboard?.show()
        } else if (!isAddressEditing) {
            addressFieldHasReceivedFocus = false
        }
    }
    chromeEffects.addressChromeSurface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = menuMorphFrame.addressSurfaceScale
                scaleY = menuMorphFrame.addressSurfaceScale
            },
        shape = RoundedCornerShape(chromeMetrics.addressCornerRadius),
        color = Color.White.copy(alpha = 0.92f),
        morphProgress = menuMorphFrame.menuExpansionProgress,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = chromeMetrics.addressMinHeight)
                .graphicsLayer {
                    alpha = menuMorphFrame.addressContentAlpha
                }
                .onSizeChanged { viewportWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(isAddressEditing, viewportWidth) {
                    detectDragGestures(
                        onDragStart = {
                            dragOffset = Offset.Zero
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            actionSink.addressDragged(
                                horizontal = dragOffset.x.toDouble(),
                                vertical = dragOffset.y.toDouble(),
                                velocityX = velocityTracker.calculateVelocity().x.toDouble(),
                                viewportWidth = viewportWidth.toDouble(),
                                isAddressEditing = isAddressEditing,
                            )
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = { dragOffset = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragOffset += amount
                        },
                    )
                }
                .padding(
                    horizontal = chromeMetrics.addressHorizontalPadding,
                    vertical = chromeMetrics.addressVerticalPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(chromeMetrics.actionSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tabSwitcherShape = RoundedCornerShape(chromeMetrics.tabSwitcherCornerRadius)
            chromeEffects.tabSwitcherSurface(
                modifier = Modifier.size(
                    width = chromeMetrics.tabSwitcherWidth,
                    height = chromeMetrics.tabSwitcherHeight,
                ),
                shape = tabSwitcherShape,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = { actionSink.perform(CandyBrowserUiAction.ShowTabs) }) {
                        Text(snapshot.tabCountLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            chromeEffects.addressFieldSurface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(chromeMetrics.addressFieldCornerRadius))
                    .clickable { isAddressEditing = true },
                shape = RoundedCornerShape(chromeMetrics.addressFieldCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                morphProgress = menuMorphFrame.menuExpansionProgress,
            ) {
                Box {
                    AddressBarFieldContent(
                        editing = isAddressEditing,
                        editValue = address,
                        onEditValueChange = { value ->
                            address = value
                            actionSink.addressChanged(value.text)
                        },
                        ghostCompletion = null,
                        placeholder = "Adresse oder Suche",
                        displayText = snapshot.address.displayAddressText().ifBlank {
                            "Adresse oder Suche"
                        },
                        onSubmitAddress = {
                            actionSink.perform(CandyBrowserUiAction.Navigate)
                            isAddressEditing = false
                        },
                        submissionText = { input, _ -> input },
                        editorModifier = Modifier
                            .focusRequester(addressFocusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    addressFieldHasReceivedFocus = true
                                } else if (
                                    AddressBarFieldFocusRules.shouldDismissEditor(
                                        hasReceivedFocus = addressFieldHasReceivedFocus,
                                        isFocused = false,
                                    )
                                ) {
                                    addressFieldHasReceivedFocus = false
                                    isAddressEditing = false
                                }
                            },
                        editorTrailingContent = {
                            IconButton(onClick = { isAddressEditing = false }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Adresseingabe schließen",
                                )
                            }
                        },
                    )
                    AddressLoadCapsuleFeedback(
                        tabId = snapshot.tabs.firstOrNull { it.isSelected }?.id.orEmpty(),
                        isLoading = snapshot.isLoading,
                        progressPercent = if (snapshot.isLoading) 0 else 100,
                        morphProgress = menuMorphFrame.addressCornerMorphProgress,
                        morphTargetSizePx = with(density) { 48.dp.toPx() },
                        sourceCornerRadiusPx = with(density) {
                            chromeMetrics.addressFieldCornerRadius.toPx()
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
            IconButton(
                onClick = { actionSink.perform(CandyBrowserUiAction.NewTab) },
                modifier = Modifier.size(chromeMetrics.actionSize),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Neuer Tab")
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(chromeMetrics.actionSize),
                ) {
                    Icon(
                        if (chromeEffects.usesHorizontalOverflowIcon()) {
                            Icons.Filled.MoreHoriz
                        } else {
                            Icons.Filled.MoreVert
                        },
                        contentDescription = "Mehr",
                    )
                }
            }
        }
        BrowserMainMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            pageSubtitle = snapshot.pageTitle,
            items = snapshot.menuItems,
            snoozedTabCount = 0,
            screenSize = screenSize,
            resources = CandyBrowserMainMenuResources,
            effects = chromeEffects.mainMenu,
            morphAnchorSize = DpSize(chromeMetrics.actionSize, chromeMetrics.actionSize),
            morphProgress = menuMorphFrame.menuExpansionProgress,
            onAction = { item ->
                if (item.action == BrowserFeatureMenuAction.OpenSettings) {
                    onOpenSettings()
                } else {
                    actionSink.performMenu(item)
                }
            },
        )
    }
}

private object CandyBrowserMainMenuResources : BrowserMainMenuResources {
    @Composable
    override fun title(): String = "Menü"

    @Composable
    override fun sectionTitle(section: BrowserFeatureMenuSection): String = section.localizedTitle()

    @Composable
    override fun label(item: BrowserFeatureMenuItem, toolbar: Boolean): String =
        if (toolbar && item.action == BrowserFeatureMenuAction.ToggleFavorite) {
            "Favorit"
        } else {
            item.localizedLabel()
        }

    @Composable
    override fun supportingText(item: BrowserFeatureMenuItem, snoozedTabCount: Int): String? =
        item.supportingText ?: when (item.action) {
            BrowserFeatureMenuAction.ToggleCookieBannerRemoval ->
                "Bekannte Einwilligungsdialoge auf dieser Domain ausblenden"
            BrowserFeatureMenuAction.ToggleForceVerticalScrolling ->
                "Scroll-Sperren der Seite auf dieser Domain überschreiben"
            BrowserFeatureMenuAction.ToggleForcePageZooming ->
                "Zoom-Sperren der Seite auf dieser Domain überschreiben"
            BrowserFeatureMenuAction.ToggleForceSafeArea ->
                "Diese Domain auch beim Scrollen unterhalb der Statusleiste halten"
            BrowserFeatureMenuAction.ToggleAlwaysBlockPopups ->
                "Alle von dieser Domain geöffneten Pop-ups ohne Nachfrage blockieren"
            BrowserFeatureMenuAction.ToggleDesktopView ->
                "Desktop-Version für diese Domain anfordern"
            BrowserFeatureMenuAction.SnoozeTab ->
                if (item.enabled) null else "Im Inkognitomodus nicht verfügbar"
            BrowserFeatureMenuAction.OpenSnoozedTabs -> "Tabs verwalten, die später zurückkehren"
            BrowserFeatureMenuAction.DuplicateTab ->
                "Nur die Adresse wird in den neuen Tab übernommen."
            else -> null
        }

    @Composable
    override fun icon(item: BrowserFeatureMenuItem, modifier: Modifier) {
        BrowserMenuActionIcon(
            action = item.action,
            selected = item.checked == true,
            modifier = modifier,
        )
    }

    @Composable
    override fun trailingIcon(modifier: Modifier) {
        Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = modifier)
    }
}

private fun String.displayAddressText(): String =
    removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .removePrefix("www.")

private object CandyTabActionsMenuResources : TabActionsMenuResources {
    @Composable
    override fun text(label: TabActionsMenuLabel): String = when (label) {
        TabActionsMenuLabel.Title -> "Tab-Aktionen"
        TabActionsMenuLabel.Favorite -> "Favorit"
        TabActionsMenuLabel.AddFavorite -> "Favorit hinzufügen"
        TabActionsMenuLabel.RemoveFavorite -> "Favorit entfernen"
        TabActionsMenuLabel.PinTab -> "Tab anheften"
        TabActionsMenuLabel.UnpinTab -> "Tab lösen"
        TabActionsMenuLabel.PageGroup -> "Seite"
        TabActionsMenuLabel.Share -> "Teilen"
        TabActionsMenuLabel.OpenExternal -> "Extern öffnen"
        TabActionsMenuLabel.Print -> "Drucken"
        TabActionsMenuLabel.MuteDomain -> "Domain stummschalten"
        TabActionsMenuLabel.CandyTrail -> "Candy Trail"
        TabActionsMenuLabel.AddSiteCapsule -> "Site Capsule hinzufügen"
        TabActionsMenuLabel.Summarize -> "Zusammenfassen"
        TabActionsMenuLabel.Snooze -> "Tab schlummern"
        TabActionsMenuLabel.SnoozeUnavailablePrivate -> "Auf iOS noch nicht verfügbar"
        TabActionsMenuLabel.MoveToProfile -> "Tab in Profil verschieben"
        TabActionsMenuLabel.CloseAllTabs -> "Alle Tabs schließen"
        TabActionsMenuLabel.CloseAllTabsPinnedSupportingText ->
            "Angeheftete Tabs bleiben geöffnet"
    }

    @Composable
    override fun icon(
        action: BrowserFeatureMenuAction,
        selected: Boolean,
        modifier: Modifier,
    ) {
        if (action == BrowserFeatureMenuAction.CloseTab) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = modifier)
        } else {
            BrowserMenuActionIcon(
                action = action,
                selected = selected,
                modifier = modifier,
            )
        }
    }
}

private val candyBrowserTrailStrings = CandyTrailStrings(
    empty = "Noch keine Seiten in diesem Candy Trail",
    nodeDescription = { isCurrent, title, host ->
        if (isCurrent) {
            "Aktuelle Seite: $title, $host"
        } else {
            "Seite: $title, $host"
        }
    },
    nodeActionsDescription = { title -> "Aktionen für $title" },
    forkStatus = { isOpen -> if (isOpen) "Geöffnet" else "Geschlossen" },
    forkDescription = { isOpen, title, host ->
        val status = if (isOpen) "Geöffnet" else "Geschlossen"
        "$status: $title, $host"
    },
    forkUrlOnlyDisclaimer = "Nur die Adresse wird in den neuen Tab übernommen.",
    forkFromHere = "Ab hier verzweigen",
    close = "Candy Trail schließen",
    title = "Candy Trail",
    newTabTitle = "Neuer Tab",
    zoomOut = "Verkleinern",
    resetZoom = "Zoom zurücksetzen",
    zoomIn = "Vergrößern",
)

@Composable
private fun BrowserMenuActionIcon(
    action: BrowserFeatureMenuAction,
    selected: Boolean = false,
    modifier: Modifier,
) {
    val useFilledVariant = BrowserMenuIconRules.useFilledVariant(
        action = action,
        selected = selected,
    )
    val imageVector = when (action) {
        BrowserFeatureMenuAction.Back -> Icons.Filled.ArrowBack
        BrowserFeatureMenuAction.Forward -> Icons.Filled.ArrowForward
        BrowserFeatureMenuAction.Reload -> Icons.Filled.Refresh
        BrowserFeatureMenuAction.Stop -> Icons.Filled.Close
        BrowserFeatureMenuAction.ToggleFavorite ->
            if (useFilledVariant) Icons.Filled.Star else Icons.Filled.StarBorder
        BrowserFeatureMenuAction.TogglePinned ->
            if (useFilledVariant) Icons.Filled.PushPin else Icons.Outlined.PushPin
        BrowserFeatureMenuAction.ShowTabs -> Icons.Filled.ViewCarousel
        BrowserFeatureMenuAction.NewTab -> Icons.Filled.Add
        BrowserFeatureMenuAction.DuplicateTab -> Icons.Filled.ContentCopy
        BrowserFeatureMenuAction.CloseTab -> Icons.Filled.Close
        BrowserFeatureMenuAction.ParkAddressBarRight,
        BrowserFeatureMenuAction.DockAddressBar,
        -> Icons.Filled.ChevronRight
        BrowserFeatureMenuAction.OpenReader -> Icons.Filled.MenuBook
        BrowserFeatureMenuAction.TranslatePage -> Icons.Filled.Translate
        BrowserFeatureMenuAction.FindInPage -> Icons.Filled.Search
        BrowserFeatureMenuAction.Share -> Icons.Filled.Share
        BrowserFeatureMenuAction.OpenExternal -> Icons.Filled.OpenInNew
        BrowserFeatureMenuAction.Print -> Icons.Filled.Print
        BrowserFeatureMenuAction.ToggleDomainMute -> Icons.Filled.VolumeOff
        BrowserFeatureMenuAction.OpenCandyTrail -> Icons.Filled.Route
        BrowserFeatureMenuAction.AddSiteCapsule -> Icons.Filled.AddToHomeScreen
        BrowserFeatureMenuAction.OpenFavorites -> Icons.Filled.Star
        BrowserFeatureMenuAction.OpenDownloads -> Icons.Filled.Download
        BrowserFeatureMenuAction.OpenHistory -> Icons.Filled.History
        BrowserFeatureMenuAction.OpenSettings -> Icons.Filled.Settings
        BrowserFeatureMenuAction.Summarize -> Icons.Filled.AutoAwesome
        BrowserFeatureMenuAction.SnoozeTab,
        BrowserFeatureMenuAction.OpenSnoozedTabs,
        -> Icons.Filled.Snooze
        BrowserFeatureMenuAction.OpenFirefoxExtensions,
        BrowserFeatureMenuAction.InvokeToppingCommand,
        -> Icons.Filled.Extension
        BrowserFeatureMenuAction.ToggleDesktopView -> Icons.Filled.DesktopWindows
        BrowserFeatureMenuAction.ToggleCookieBannerRemoval,
        BrowserFeatureMenuAction.ToggleForceVerticalScrolling,
        BrowserFeatureMenuAction.ToggleForcePageZooming,
        BrowserFeatureMenuAction.ToggleForceSafeArea,
        -> Icons.Filled.Settings
        BrowserFeatureMenuAction.ToggleAlwaysBlockPopups -> Icons.Filled.Block
    }
    Icon(imageVector = imageVector, contentDescription = null, modifier = modifier)
}

private fun BrowserFeatureMenuSection.localizedTitle(): String = when (this) {
    BrowserFeatureMenuSection.Toolbar -> "Werkzeugleiste"
    BrowserFeatureMenuSection.Page -> "Seite"
    BrowserFeatureMenuSection.Toppings -> "Toppings"
    BrowserFeatureMenuSection.Candy -> "Candy"
    BrowserFeatureMenuSection.Browser -> "Browser"
}

private fun BrowserFeatureMenuItem.localizedLabel(): String = dynamicLabel ?: when (labelKey) {
    BrowserFeatureMenuLabelKey.Back -> "Zurück"
    BrowserFeatureMenuLabelKey.Forward -> "Vor"
    BrowserFeatureMenuLabelKey.Reload -> "Neu laden"
    BrowserFeatureMenuLabelKey.StopLoading -> "Stopp"
    BrowserFeatureMenuLabelKey.AddFavorite -> "Favorit hinzufügen"
    BrowserFeatureMenuLabelKey.RemoveFavorite -> "Favorit entfernen"
    BrowserFeatureMenuLabelKey.PinTab -> "Tab anheften"
    BrowserFeatureMenuLabelKey.UnpinTab -> "Tab lösen"
    BrowserFeatureMenuLabelKey.Tabs -> "Tabs"
    BrowserFeatureMenuLabelKey.NewTab -> "Neuer Tab"
    BrowserFeatureMenuLabelKey.DuplicateTab -> "Tab duplizieren"
    BrowserFeatureMenuLabelKey.CloseTab -> "Tab schließen"
    BrowserFeatureMenuLabelKey.ParkAddressBarRight -> "Adressleiste rechts parken"
    BrowserFeatureMenuLabelKey.Reader -> "Lesemodus"
    BrowserFeatureMenuLabelKey.Translate -> "Übersetzen"
    BrowserFeatureMenuLabelKey.FindInPage -> "Auf Seite suchen"
    BrowserFeatureMenuLabelKey.Share -> "Teilen"
    BrowserFeatureMenuLabelKey.OpenExternal -> "Extern öffnen"
    BrowserFeatureMenuLabelKey.Print -> "Drucken"
    BrowserFeatureMenuLabelKey.CookieBannerRemoval -> "Cookie-Banner entfernen"
    BrowserFeatureMenuLabelKey.ForceVerticalScrolling -> "Vertikales Scrollen erzwingen"
    BrowserFeatureMenuLabelKey.ForcePageZooming -> "Seitenzoom erzwingen"
    BrowserFeatureMenuLabelKey.ForceSafeArea -> "Safe Area erzwingen"
    BrowserFeatureMenuLabelKey.AlwaysBlockPopups -> "Pop-ups immer blockieren"
    BrowserFeatureMenuLabelKey.DesktopView -> "Desktop-Ansicht"
    BrowserFeatureMenuLabelKey.MuteDomain -> "Domain stummschalten"
    BrowserFeatureMenuLabelKey.UnmuteDomain -> "Domain aktivieren"
    BrowserFeatureMenuLabelKey.CandyTrail -> "Candy Trail"
    BrowserFeatureMenuLabelKey.AddSiteCapsule -> "Site Capsule hinzufügen"
    BrowserFeatureMenuLabelKey.Summarize -> "Zusammenfassen"
    BrowserFeatureMenuLabelKey.SnoozeTab -> "Tab schlummern"
    BrowserFeatureMenuLabelKey.DockAddressBar -> "Adressleiste andocken"
    BrowserFeatureMenuLabelKey.SnoozedTabs -> "Schlummernde Tabs"
    BrowserFeatureMenuLabelKey.Favorites -> "Favoriten"
    BrowserFeatureMenuLabelKey.Downloads -> "Downloads"
    BrowserFeatureMenuLabelKey.History -> "Verlauf"
    BrowserFeatureMenuLabelKey.Settings -> "Einstellungen"
    BrowserFeatureMenuLabelKey.FirefoxExtensions -> "Firefox-Erweiterungen"
    BrowserFeatureMenuLabelKey.ToppingsCommand -> "Topping"
}
