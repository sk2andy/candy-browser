@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.data.AddressBarDockEdge
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.reader.ReaderStudioSessionRules
import dev.sk2andy.materialbrowser.shared.ui.OverviewAddressBarContent
import dev.sk2andy.materialbrowser.shared.ui.TabOverviewChromeTestTags
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.LocalCandyMotionScheme
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
internal fun BrowserBottomBar(
    tab: BrowserTab,
    pageTranslationProvider: PageTranslationProvider,
    compact: Boolean,
    dockState: AddressBarDockState,
    dockTargetEdge: AddressBarDockEdge,
    editing: Boolean,
    actionLayout: AddressBarActionLayout,
    showCastButton: Boolean,
    showQrScanner: Boolean,
    tabCount: Int,
    userScriptMenuCommands: List<UserScriptMenuCommand>,
    onUserScriptMenuCommand: (UserScriptMenuCommand) -> Unit,
    commandFeedback: AddressCommandFeedback?,
    backdropSource: CandyChromeBackdropSource?,
    blurSourceVisible: Boolean,
    feedbackGesturesEnabled: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddress: () -> Unit,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    ghostCompletion: String?,
    onAcceptGhostCompletion: () -> Unit,
    addressFocusNonce: Int,
    onMoveAddressSuggestion: (Int) -> Unit,
    onActivateAddressSuggestion: () -> Unit,
    onDismissEditor: () -> Unit,
    onSubmitAddress: (String) -> Unit,
    showAiModeToggle: Boolean,
    aiModeSelected: Boolean,
    onAiModeSelectedChange: (Boolean) -> Unit,
    onScanQrCode: () -> Unit,
    onExpand: () -> Unit,
    onDock: () -> Unit,
    onParkRight: () -> Unit,
    onDockPlacementChanged: (AddressBarDockPlacement) -> Unit,
    onRestoreDock: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNewTab: () -> Unit,
    onDuplicateTab: () -> Unit,
    onFindInPage: () -> Unit,
    onCloseTab: () -> Unit,
    onToggleIncognito: () -> Unit,
    blankTabModeProgress: Float,
    onIncognitoControlCenterChanged: (Offset) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isPinned: Boolean,
    onTogglePinned: () -> Unit,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    onDomainMutedChange: (Boolean) -> Unit,
    canToggleAlwaysBlockPopups: Boolean,
    isAlwaysBlockPopupsEnabled: Boolean,
    onAlwaysBlockPopupsChange: (Boolean) -> Unit,
    canToggleDesktopView: Boolean,
    isDesktopView: Boolean,
    onDesktopViewChange: (Boolean) -> Unit,
    canToggleCookieBannerRemoval: Boolean,
    isCookieBannerRemovalEnabled: Boolean,
    canToggleForceVerticalScrolling: Boolean,
    isForceVerticalScrollingEnabled: Boolean,
    canToggleForcePageZooming: Boolean,
    isForcePageZoomingEnabled: Boolean,
    canToggleForceSafeArea: Boolean,
    isForceSafeAreaEnabled: Boolean,
    onCookieBannerRemovalEnabledChange: (Boolean) -> Unit,
    onForceVerticalScrollingChange: (Boolean) -> Unit,
    onForcePageZoomingChange: (Boolean) -> Unit,
    onForceSafeAreaChange: (Boolean) -> Unit,
    snoozedTabCount: Int,
    onSnoozedTabs: () -> Unit,
    onFavorites: () -> Unit,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)?,
    onSettings: () -> Unit,
    onPrivacyXRay: () -> Unit,
    permissionActivityVisible: Boolean,
    onPermissionRadar: () -> Unit,
    addressBarPulseNonce: Int,
    newTabPulseNonce: Int,
    onNewTabButtonBounds: (Rect?) -> Unit,
    onOpenExternal: () -> Unit,
    onSummarizeWithAssistant: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onTranslate: () -> Unit,
    onReaderStudio: () -> Unit,
    onOpenCandyTrail: () -> Unit,
    onSnooze: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    supportsPageContentActions: Boolean = true,
    overviewGestureEnabled: Boolean,
    overviewGestureProgress: FloatState,
    showingTabOverview: Boolean,
    visualOnly: Boolean,
    rootBottomInWindowPx: Int,
    onOverviewGestureProgress: (Float) -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
    onBarPositioned: (topInRootPx: Float, topInWindowPx: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val docked = dockState.placement != null
    val dockingEnabled = dockState.enabled
    var menuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val presentation = AddressBarPresentationRules.resolve(
        docked = docked,
        compact = compact,
        editing = editing,
        showingCommandFeedback = commandFeedback != null,
        showingTabOverview = showingTabOverview,
    )
    val tabDragState = rememberDraggableState(onTabDrag)
    val pulseScale = remember { Animatable(1f) }
    val newTabPulseScale = remember { Animatable(1f) }
    val domain = AddressResolver.displayText(tab.url)
    val readerSupported = ReaderStudioSessionRules.isSupportedSource(tab.url)
    val readerOpenLabel = stringResource(R.string.reader_open_action)
    val feedbackText = commandFeedback?.localizedText().orEmpty()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fullWindowHeightPx = LocalContext.current
        .getSystemService(WindowManager::class.java)
        .currentWindowMetrics
        .bounds
        .height()
    val chromeTokens = browserChromeSurfaceTokens(BrowserChromeSurfaceRole.AddressBar)
    val motionScheme = LocalCandyMotionScheme.current
    LaunchedEffect(addressBarPulseNonce, motionScheme) {
        if (addressBarPulseNonce == 0) return@LaunchedEffect
        pulseScale.snapTo(1f)
        pulseScale.animateTo(
            targetValue = motionScheme.addressBarPulseScale,
            animationSpec = spring(
                dampingRatio = motionScheme.addressBarPulseOutDampingRatio,
                stiffness = motionScheme.addressBarPulseOutStiffness,
            ),
        )
        pulseScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = motionScheme.addressBarPulseBackDampingRatio,
                stiffness = motionScheme.addressBarPulseBackStiffness,
            ),
        )
    }
    LaunchedEffect(newTabPulseNonce, motionScheme) {
        if (newTabPulseNonce == 0) return@LaunchedEffect
        newTabPulseScale.snapTo(1f)
        newTabPulseScale.animateTo(
            targetValue = motionScheme.newTabPulseScale,
            animationSpec = spring(
                dampingRatio = motionScheme.newTabPulseOutDampingRatio,
                stiffness = motionScheme.newTabPulseOutStiffness,
            ),
        )
        newTabPulseScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = motionScheme.newTabPulseBackDampingRatio,
                stiffness = motionScheme.newTabPulseBackStiffness,
            ),
        )
    }
    val compactWidth = with(density) {
        textMeasurer.measure(
            text = domain,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        ).size.width.toDp() +
            AddressBarDockingRules.compactAddressSlackDp(dockingEnabled).dp +
            (if (dockingEnabled) 48.dp else 0.dp) +
            (if (showCastButton) 48.dp else 0.dp)
    }
    val feedbackWidth = with(density) {
        textMeasurer.measure(
            text = feedbackText,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        ).size.width.toDp() + 64.dp
    }
    val barColor by animateColorAsState(
        targetValue = when (commandFeedback?.tone) {
            AddressCommandFeedbackTone.Confirm -> MaterialTheme.colorScheme.primaryContainer
            AddressCommandFeedbackTone.Reject -> MaterialTheme.colorScheme.errorContainer
            null -> chromeTokens.containerColor
        },
        animationSpec = tween(motionScheme.addressBarFeedbackColorMillis),
        label = "Address command feedback color",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .addressBarWindowInsetsPadding(
                fullWindowHeightPx = fullWindowHeightPx,
                rootBottomInWindowPx = rootBottomInWindowPx,
                imeInsets = WindowInsets.ime,
                navigationBarInsets = WindowInsets.navigationBars,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(if (visualOnly) Modifier.clearAndSetSemantics { } else Modifier),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val edgeTabWidth = 52.dp
        val edgeTabHeight = 48.dp
        val verticalTravel = (maxHeight - edgeTabHeight).coerceAtLeast(0.dp)
        val horizontalTravelPx = with(density) {
            AddressBarMotion.dockOffsetForPosition(
                position = Offset(1f, 0f),
                maxWidth = maxWidth,
                barWidth = edgeTabWidth,
                verticalTravel = verticalTravel,
            ).x.toPx().absoluteValue
        }
        val dockInteraction = rememberAddressBarDockInteractionState(
            presentation = presentation,
            placement = dockState.placement,
            enabled = dockingEnabled,
            horizontalTravelPx = horizontalTravelPx,
            verticalTravelPx = with(density) { verticalTravel.toPx() },
            density = density,
            onPlacementChanged = onDockPlacementChanged,
            onRestore = {
                onRestoreDock()
                onExpand()
            },
        )
        val dockStretchProgress by animateFloatAsState(
            targetValue = dockInteraction.normalAnchorResistanceProgress,
            animationSpec = if (dockInteraction.normalAnchorResistanceProgress == 0f) {
                spring(
                    dampingRatio = motionScheme.addressBarResistanceDampingRatio,
                    stiffness = motionScheme.addressBarResistanceStiffness,
                )
            } else {
                tween(durationMillis = motionScheme.addressBarResistanceFollowMillis)
            },
            label = "Adresspille Widerstand",
        )
        val motion = rememberAddressBarMotionState(
            presentation = presentation,
            compactWidth = compactWidth,
            maxWidth = maxWidth,
            feedbackWidth = feedbackWidth,
            edgeTabWidth = edgeTabWidth,
            verticalTravel = verticalTravel,
            dockPosition = dockInteraction.position,
        )
        val animatedBarWidth = motion.width
        val animatedBarHeight = motion.height
        val dockOffset = motion.dockOffset
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            CandyChromeSurface(
                backdropSource = backdropSource,
                tokens = chromeTokens,
                modifier = Modifier
                    .offset(x = dockOffset.x, y = dockOffset.y)
                    .width(animatedBarWidth)
                    .height(animatedBarHeight)
                    .then(
                        if (visualOnly) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    down.consume()
                                    while (true) {
                                        val event = awaitPointerEvent(
                                            pass = PointerEventPass.Initial,
                                        )
                                        event.changes.forEach { it.consume() }
                                        if (event.changes.none { it.pressed }) break
                                    }
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .onGloballyPositioned { coordinates ->
                        onBarPositioned(
                            coordinates.boundsInRoot().top,
                            coordinates.boundsInWindow().top.roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        val stretch = dockStretchProgress.coerceIn(-0.12f, 1f)
                        scaleX = pulseScale.value * (1f - stretch * 0.04f)
                        scaleY = pulseScale.value * (1f + stretch * 0.18f)
                        transformOrigin = if (stretch == 0f) {
                            TransformOrigin.Center
                        } else {
                            TransformOrigin(0.5f, 1f)
                        }
                    },
                containerColor = barColor,
                backdropBlurEnabled = commandFeedback == null &&
                    blurSourceVisible &&
                    !menuExpanded &&
                    chromeTokens.backdropBlurEnabled,
            ) {
                Box {
                    AddressBarPresentationTransition(
                        presentation = presentation,
                    ) { targetPresentation ->
                        when (targetPresentation) {
                            AddressBarPresentation.Docked -> AddressBarEdgeTab(
                                edge = if (dockInteraction.position.x < 0f) {
                                    AddressBarDockEdge.Left
                                } else {
                                    AddressBarDockEdge.Right
                                },
                                onRestore = dockInteraction.onRestoreClick,
                                dockDragEnabled = dockingEnabled &&
                                    presentation == AddressBarPresentation.Docked &&
                                    !visualOnly,
                                onDockDragStarted = dockInteraction.onDragStarted,
                                onDockDrag = dockInteraction.onDrag,
                                onDockDragStopped = dockInteraction.onDragStopped,
                                onDockDragCancelled = dockInteraction.onDragCancelled,
                            )
                            AddressBarPresentation.Compact -> {
                                Surface(
                                    modifier = Modifier
                                        .addressBarVerticalGesture(
                                            enabled = overviewGestureEnabled,
                                            initialProgress = overviewGestureProgress,
                                            onProgress = onOverviewGestureProgress,
                                            onStarted = onOverviewGestureStarted,
                                            onCancelled = onOverviewGestureCancelled,
                                            onSwipeUp = onTabs,
                                        )
                                        .draggable(
                                            state = tabDragState,
                                            orientation = Orientation.Horizontal,
                                            enabled = !editing,
                                            onDragStopped = { velocity ->
                                                onTabDragStopped(velocity)
                                            },
                                        )
                                        .addressBarReaderActions(
                                            readerEnabled = readerSupported,
                                            onClick = onExpand,
                                            onReaderStudio = onReaderStudio,
                                            readerLabel = readerOpenLabel,
                                        ),
                                    color = Color.Transparent,
                                ) {
                                    AddressBarCompactContent(
                                        domain = domain,
                                        showCastButton = showCastButton,
                                        dockingEnabled = dockingEnabled,
                                        dockTargetEdge = dockTargetEdge,
                                        onDock = onDock,
                                    )
                                }
                            }
                            AddressBarPresentation.Expanded -> ExpandedBottomBarContent(
                                tab = tab,
                                pageTranslationProvider = pageTranslationProvider,
                                backdropSource = backdropSource.takeIf { blurSourceVisible },
                                actionLayout = actionLayout,
                                showCastButton = showCastButton,
                                showQrScanner = showQrScanner,
                                tabCount = tabCount,
                                userScriptMenuCommands = userScriptMenuCommands,
                                onUserScriptMenuCommand = onUserScriptMenuCommand,
                                menuExpanded = menuExpanded,
                                onMenuExpandedChange = { menuExpanded = it },
                                onBack = onBack,
                                onForward = onForward,
                                onAddress = onAddress,
                                editing = editing,
                                editValue = editValue,
                                onEditValueChange = onEditValueChange,
                                ghostCompletion = ghostCompletion,
                                onAcceptGhostCompletion = onAcceptGhostCompletion,
                                focusRequester = focusRequester,
                                addressFocusNonce = addressFocusNonce,
                                requestAddressFocus = editing && commandFeedback == null,
                                onMoveAddressSuggestion = onMoveAddressSuggestion,
                                onActivateAddressSuggestion = onActivateAddressSuggestion,
                                onDismissEditor = onDismissEditor,
                                onSubmitAddress = onSubmitAddress,
                                showAiModeToggle = showAiModeToggle,
                                aiModeSelected = aiModeSelected,
                                onAiModeSelectedChange = onAiModeSelectedChange,
                                onScanQrCode = onScanQrCode,
                                onTabDrag = onTabDrag,
                                onTabDragStopped = onTabDragStopped,
                                onTabs = onTabs,
                                onReload = onReload,
                                onStop = onStop,
                                onNewTab = onNewTab,
                                onDuplicateTab = onDuplicateTab,
                                onFindInPage = onFindInPage,
                                onCloseTab = onCloseTab,
                                newTabPulseScale = newTabPulseScale.value,
                                onNewTabButtonBounds = onNewTabButtonBounds,
                                onToggleIncognito = onToggleIncognito,
                                blankTabModeProgress = blankTabModeProgress,
                                onIncognitoControlCenterChanged =
                                    onIncognitoControlCenterChanged,
                                isFavorite = isFavorite,
                                onToggleFavorite = onToggleFavorite,
                                isPinned = isPinned,
                                onTogglePinned = onTogglePinned,
                                canToggleDomainMute = canToggleDomainMute,
                                isDomainMuted = isDomainMuted,
                                onDomainMutedChange = onDomainMutedChange,
                                canToggleAlwaysBlockPopups = canToggleAlwaysBlockPopups,
                                isAlwaysBlockPopupsEnabled = isAlwaysBlockPopupsEnabled,
                                onAlwaysBlockPopupsChange = onAlwaysBlockPopupsChange,
                                canToggleDesktopView = canToggleDesktopView,
                                isDesktopView = isDesktopView,
                                onDesktopViewChange = onDesktopViewChange,
                                canToggleCookieBannerRemoval =
                                    canToggleCookieBannerRemoval,
                                isCookieBannerRemovalEnabled =
                                    isCookieBannerRemovalEnabled,
                                canToggleForceVerticalScrolling =
                                    canToggleForceVerticalScrolling,
                                isForceVerticalScrollingEnabled =
                                    isForceVerticalScrollingEnabled,
                                canToggleForcePageZooming = canToggleForcePageZooming,
                                isForcePageZoomingEnabled = isForcePageZoomingEnabled,
                                canToggleForceSafeArea = canToggleForceSafeArea,
                                isForceSafeAreaEnabled = isForceSafeAreaEnabled,
                                onCookieBannerRemovalEnabledChange =
                                    onCookieBannerRemovalEnabledChange,
                                onForceVerticalScrollingChange =
                                    onForceVerticalScrollingChange,
                                onForcePageZoomingChange = onForcePageZoomingChange,
                                onForceSafeAreaChange = onForceSafeAreaChange,
                                snoozedTabCount = snoozedTabCount,
                                onSnoozedTabs = onSnoozedTabs,
                                onFavorites = onFavorites,
                                onDownloads = onDownloads,
                                onHistory = onHistory,
                                onOpenFirefoxExtensions = onOpenFirefoxExtensions,
                                onSettings = onSettings,
                                onPrivacyXRay = onPrivacyXRay,
                                permissionActivityVisible = permissionActivityVisible,
                                onPermissionRadar = onPermissionRadar,
                                onOpenExternal = onOpenExternal,
                                onSummarizeWithAssistant = onSummarizeWithAssistant,
                                onShare = onShare,
                                onPrint = onPrint,
                                onTranslate = onTranslate,
                                onReaderStudio = onReaderStudio,
                                onOpenCandyTrail = onOpenCandyTrail,
                                onSnooze = onSnooze,
                                onAddSiteCapsule = onAddSiteCapsule,
                                supportsPageContentActions = supportsPageContentActions,
                                canDock = dockingEnabled,
                                onDock = onDock,
                                onParkRight = onParkRight,
                                overviewGestureEnabled = overviewGestureEnabled,
                                overviewGestureProgress = overviewGestureProgress,
                                onOverviewGestureProgress = onOverviewGestureProgress,
                                onOverviewGestureStarted = onOverviewGestureStarted,
                                onOverviewGestureCancelled = onOverviewGestureCancelled,
                            )
                            AddressBarPresentation.Overview -> OverviewAddressBarContent(
                                onNewTab = onNewTab,
                                onMore = {},
                                newTabIcon = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = stringResource(R.string.cd_new_tab),
                                    )
                                },
                                moreIcon = {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(
                                            R.string.cd_more_options,
                                        ),
                                    )
                                },
                            )
                            AddressBarPresentation.CommandFeedback -> {
                                commandFeedback?.let { feedback ->
                                    AddressCommandFeedbackContent(
                                        feedback = feedback,
                                        text = feedbackText,
                                        gesturesEnabled = feedbackGesturesEnabled,
                                        onAddress = if (compact) onExpand else onAddress,
                                        onTabDrag = onTabDrag,
                                        onTabDragStopped = onTabDragStopped,
                                        onTabs = onTabs,
                                    )
                                }
                            }
                        }
                    }
                    if (commandFeedback == null && !showingTabOverview) {
                        AddressLoadCapsuleFeedback(
                            tabId = tab.id,
                            isLoading = tab.isLoading,
                            progressPercent = tab.progress,
                            morphProgress = 0f,
                            morphTargetSizePx = with(density) { 56.dp.toPx() },
                            sourceCornerRadiusPx = with(density) {
                                chromeTokens.cornerRadius.toPx()
                            },
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }
    }
}

internal object AddressBarDockTestTags {
    const val EdgeTab = "address_bar_edge_tab"
    const val ParkAction = "address_bar_park_action"
    const val ParkIcon = "address_bar_park_icon"
    const val CompactAddress = "address_bar_compact_address"
    const val CompactContent = "address_bar_compact_content"
}

@Composable
internal fun AddressBarCompactContent(
    domain: String,
    showCastButton: Boolean,
    dockingEnabled: Boolean,
    dockTargetEdge: AddressBarDockEdge,
    onDock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .testTag(AddressBarDockTestTags.CompactContent),
        horizontalArrangement = when {
            !dockingEnabled -> Arrangement.Center
            AddressBarDockingRules.parkActionPrecedesAddress(dockTargetEdge) -> Arrangement.Start
            else -> Arrangement.End
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (
            dockingEnabled &&
            AddressBarDockingRules.parkActionPrecedesAddress(dockTargetEdge)
        ) {
            AddressBarParkAction(
                edge = dockTargetEdge,
                onDock = onDock,
            )
        }
        Text(
            text = domain,
            modifier = Modifier
                .weight(weight = 1f, fill = false)
                .offset(
                    x = AddressBarDockingRules
                        .compactAddressContentOffsetDp(dockTargetEdge).dp,
                )
                .testTag(AddressBarDockTestTags.CompactAddress),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
        )
        if (showCastButton) {
            CastRouteButton()
        }
        if (
            dockingEnabled &&
            !AddressBarDockingRules.parkActionPrecedesAddress(dockTargetEdge)
        ) {
            AddressBarParkAction(
                edge = dockTargetEdge,
                onDock = onDock,
            )
        }
    }
}

@Composable
private fun AddressBarParkAction(
    edge: AddressBarDockEdge,
    onDock: () -> Unit,
) {
    IconButton(
        onClick = onDock,
        modifier = Modifier.testTag(AddressBarDockTestTags.ParkAction),
    ) {
        AddressBarParkIcon(
            edge = edge,
            contentDescription = stringResource(R.string.action_dock_address_bar),
        )
    }
}

@Composable
private fun AddressBarParkIcon(
    edge: AddressBarDockEdge,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .testTag(AddressBarDockTestTags.ParkIcon)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp)
                .graphicsLayer {
                    rotationZ = AddressBarDockingRules.parkChevronRotationDegrees(edge)
                },
        )
    }
}

@Composable
internal fun AddressBarEdgeTab(
    edge: AddressBarDockEdge,
    onRestore: () -> Unit,
    dockDragEnabled: Boolean,
    onDockDragStarted: () -> Unit,
    onDockDrag: (Offset) -> Unit,
    onDockDragStopped: () -> Unit,
    onDockDragCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val restoreDescription = stringResource(R.string.cd_restore_address_bar)
    var dragCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var previousPointerInRoot by remember { mutableStateOf<Offset?>(null) }
    Surface(
        onClick = onRestore,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(AddressBarDockTestTags.EdgeTab)
            .onGloballyPositioned { dragCoordinates = it }
            .semantics { contentDescription = restoreDescription }
            .pointerInput(dockDragEnabled) {
                if (!dockDragEnabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { localPosition ->
                        previousPointerInRoot = dragCoordinates?.localToRoot(localPosition)
                        onDockDragStarted()
                    },
                    onDragEnd = {
                        previousPointerInRoot = null
                        onDockDragStopped()
                    },
                    onDragCancel = {
                        previousPointerInRoot = null
                        onDockDragCancelled()
                    },
                    onDrag = { change, dragAmount ->
                        val pointerInRoot = dragCoordinates?.localToRoot(change.position)
                        val rootDragAmount = previousPointerInRoot
                            ?.let { previous -> pointerInRoot?.minus(previous) }
                            ?: dragAmount
                        previousPointerInRoot = pointerInRoot
                        change.consume()
                        onDockDrag(rootDragAmount)
                    },
                )
            },
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        rotationZ = when (edge) {
                            AddressBarDockEdge.Left -> -90f
                            AddressBarDockEdge.Right -> 90f
                        }
                    },
            )
        }
    }
}

@Composable
private fun AddressCommandFeedbackContent(
    feedback: AddressCommandFeedback,
    text: String,
    gesturesEnabled: Boolean,
    onAddress: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
) {
    val tabDragState = rememberDraggableState(onTabDrag)
    val contentColor = when (feedback.tone) {
        AddressCommandFeedbackTone.Confirm -> MaterialTheme.colorScheme.onPrimaryContainer
        AddressCommandFeedbackTone.Reject -> MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("address_command_feedback")
            .clickable(enabled = gesturesEnabled, onClick = onAddress)
            .addressBarVerticalGesture(
                enabled = gesturesEnabled,
                onSwipeUp = onTabs,
            )
            .draggable(
                state = tabDragState,
                orientation = Orientation.Horizontal,
                enabled = gesturesEnabled,
                onDragStopped = { velocity -> onTabDragStopped(velocity) },
            )
            .semantics(mergeDescendants = true) {
                liveRegion = if (feedback.tone == AddressCommandFeedbackTone.Reject) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (feedback.tone == AddressCommandFeedbackTone.Confirm) {
                Icons.Default.Check
            } else {
                Icons.Default.Close
            },
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AddressCommandFeedback.localizedText(): String = when (message) {
    AddressCommandFeedbackMessage.CacheCleared ->
        stringResource(R.string.command_feedback_cache_cleared)
    AddressCommandFeedbackMessage.CookiesCleared ->
        stringResource(R.string.command_feedback_cookies_cleared)
    AddressCommandFeedbackMessage.Reloaded ->
        stringResource(R.string.command_feedback_reloaded)
    AddressCommandFeedbackMessage.LoadingStopped ->
        stringResource(R.string.command_feedback_loading_stopped)
    AddressCommandFeedbackMessage.TabPinned ->
        stringResource(R.string.command_feedback_tab_pinned)
    AddressCommandFeedbackMessage.TabUnpinned ->
        stringResource(R.string.command_feedback_tab_unpinned)
    AddressCommandFeedbackMessage.DuplicateTabsClosed -> pluralStringResource(
        R.plurals.command_feedback_duplicates_closed,
        count,
        count,
    )
    AddressCommandFeedbackMessage.TabMoved -> stringResource(
        R.string.command_feedback_tab_moved,
        targetProfileLabel.orEmpty(),
    )
    AddressCommandFeedbackMessage.ProfileSwitched -> stringResource(
        R.string.command_feedback_profile_switched,
        targetProfileLabel.orEmpty(),
    )
    AddressCommandFeedbackMessage.RegularTabCreated ->
        stringResource(R.string.command_feedback_regular_tab_created)
    AddressCommandFeedbackMessage.IncognitoTabCreated ->
        stringResource(R.string.command_feedback_incognito_tab_created)
    AddressCommandFeedbackMessage.SettingsOpened ->
        stringResource(R.string.command_feedback_settings_opened)
    AddressCommandFeedbackMessage.Rejected ->
        stringResource(R.string.command_feedback_rejected)
}

internal fun Modifier.addressBarReaderActions(
    readerEnabled: Boolean,
    onClick: () -> Unit,
    onReaderStudio: () -> Unit,
    readerLabel: String,
): Modifier = combinedClickable(
    role = Role.Button,
    onClick = onClick,
    onLongClick = onReaderStudio.takeIf { readerEnabled },
    onLongClickLabel = readerLabel.takeIf { readerEnabled },
)

@Composable
internal fun Modifier.addressBarVerticalGesture(
    enabled: Boolean = true,
    initialProgress: FloatState? = null,
    onProgress: (Float) -> Unit = {},
    onStarted: () -> Unit = {},
    onCancelled: () -> Unit = {},
    onSwipeUp: () -> Unit,
): Modifier {
    val currentInitialProgress by rememberUpdatedState(initialProgress)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnStarted by rememberUpdatedState(onStarted)
    val currentOnCancelled by rememberUpdatedState(onCancelled)
    val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
    val gestureView = LocalView.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    if (!enabled) return this
    return pointerInput(enabled, touchSlop) {
        val threshold = AddressBarGestureRules.OPEN_TABS_THRESHOLD_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var lastY = down.position.y
            var accumulatedX = 0f
            var accumulatedY = 0f
            var gestureActive = false
            var state = AddressBarOverviewGestureRules.stateForProgress(
                progress = currentInitialProgress?.floatValue ?: 0f,
                threshold = threshold,
            )
            var committed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.position - change.previousPosition
                    if (!gestureActive) {
                        accumulatedX += delta.x
                        accumulatedY += delta.y
                        when (
                            AddressBarOverviewGestureRules.direction(
                                dragX = accumulatedX,
                                dragY = accumulatedY,
                                touchSlop = touchSlop,
                            )
                        ) {
                            AddressBarOverviewGestureDirection.Pending -> {
                                lastY = change.position.y
                                if (!change.pressed) break
                                continue
                            }
                            AddressBarOverviewGestureDirection.Rejected -> break
                            AddressBarOverviewGestureDirection.Upward -> {
                                gestureActive = true
                                currentOnStarted()
                                lastY = down.position.y
                            }
                        }
                    }
                    val update = AddressBarOverviewGestureRules.update(
                        state = state,
                        deltaY = change.position.y - lastY,
                        threshold = threshold,
                    )
                    state = update.state
                    lastY = change.position.y
                    change.consume()
                    currentOnProgress(update.progress)
                    if (!change.pressed) {
                        val release = AddressBarOverviewGestureRules.release(state)
                        if (release.shouldCommit) {
                            committed = true
                            currentOnProgress(release.progress)
                            gestureView.performHapticFeedback(
                                HapticFeedbackConstants.VIRTUAL_KEY,
                            )
                            currentOnSwipeUp()
                        }
                        break
                    }
                }
            } finally {
                if (gestureActive && !committed) currentOnCancelled()
            }
        }
    }
}
