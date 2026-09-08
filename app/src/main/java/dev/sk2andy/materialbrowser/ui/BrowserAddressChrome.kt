@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.cast.CastUiState
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import eightbitlab.com.blurview.BlurTarget
import kotlin.math.absoluteValue

@Composable
internal fun BoxScope.BrowserAddressChrome(
    controller: BrowserController,
    selectedTab: BrowserTab,
    addressEditorVisible: Boolean,
    showInteractiveBlankStart: Boolean,
    blankTabModeProgress: Float,
    blankTabModeRevealOrigin: Offset,
    profileWallpaperRuntime: ProfileWallpaperRuntime?,
    commandFeedback: AddressCommandFeedback?,
    suggestionItems: List<AddressSuggestionItem>,
    highlightedSuggestionIndex: Int,
    browserHeightPx: Float,
    bottomBarTopPx: MutableFloatState,
    browserContentBlurTarget: BlurTarget?,
    linkPeekAddressBarExpanded: Boolean,
    castUiState: CastUiState,
    settingsVisible: Boolean,
    addressValue: TextFieldValue,
    domainCompletion: String?,
    addressFocusNonce: Int,
    showAiModeToggle: Boolean,
    aiModeSelectedState: MutableState<Boolean>,
    browserDragOffset: MutableFloatState,
    browserWidthPx: Float,
    tabSwitchTravelPx: Float,
    tabHandoffAlpha: Animatable<Float, AnimationVector1D>,
    tabOverviewOpening: Boolean,
    tabOverviewVisible: Boolean,
    overviewGestureProgress: MutableFloatState,
    addressBarMorphInFront: Boolean,
    browserRootBottomInWindowPx: Int,
    visibleSnoozedTabCount: Int,
    webViewVideoOnlyPresentation: Boolean,
    onToggleCastPlayback: () -> Unit,
    onSeekCast: (Long) -> Unit,
    onCastVolumeChange: (Float) -> Unit,
    onDisconnectCast: () -> Unit,
    openAddressEditor: () -> Unit,
    onAddressEditorDismiss: () -> Unit,
    onAddressValueChanged: (TextFieldValue) -> Unit,
    onHighlightedSuggestionChanged: (Int) -> Unit,
    selectSuggestion: (AddressSuggestionItem) -> Unit,
    fillAddressFromSuggestion: (AddressSuggestionItem) -> Unit,
    moveSuggestionHighlight: (Int) -> Unit,
    submitAddressOrCommand: (String) -> Unit,
    onLiveFrameCleared: () -> Unit,
    onTabHandoffChanged: (TabHandoff?) -> Unit,
    onTabs: () -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
    openNewTabAndEdit: () -> Unit,
    toggleFavoriteWithFeedback: (String) -> Unit,
    onBlankTabModeRevealOriginChanged: (Offset) -> Unit,
    onSnoozedTabs: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)?,
    onSettings: () -> Unit,
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onNewTabButtonBoundsChanged: (Rect?) -> Unit,
    onReaderStudio: () -> Unit,
    onOpenCandyTrail: () -> Unit,
    onSnooze: () -> Unit,
    onAddSiteCapsule: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val rootView = LocalView.current
    val chromeBackdropSource = browserContentBlurTarget.asCandyChromeBackdropSource()
    val qrScanFailureMessage = stringResource(R.string.toast_qr_scan_failed)
    val qrScanner = rememberQrCodeScanner()
    var qrScanInProgress by remember { mutableStateOf(false) }
    val selectedSiteState = controller.siteProtectionState(selectedTab.id)
    val selectedSiteHasHost = controller.supportsPageContentActions &&
        selectedSiteState.host != null
    val canToggleSelectedCookieBannerRemoval = selectedSiteHasHost &&
        controller.blockerSettings.hideCookieConsent &&
        !selectedSiteState.isPaused
    val permissionActivityVisible = controller.hasPermissionActivity(selectedTab.id)
    if (addressEditorVisible && !showInteractiveBlankStart) {
        AddressEditorBackdrop(
            showStartContent = selectedTab.url == BLANK_URL,
            modeProgress = blankTabModeProgress,
            revealOriginInRoot = blankTabModeRevealOrigin,
            wallpaper = profileWallpaperRuntime.takeUnless { selectedTab.isIncognito },
            onDismiss = onAddressEditorDismiss,
        )
        if (commandFeedback == null) {
            AddressSuggestions(
                suggestions = suggestionItems,
                highlightedIndex = highlightedSuggestionIndex,
                onHighlight = onHighlightedSuggestionChanged,
                onSelect = selectSuggestion,
                onFill = fillAddressFromSuggestion,
                rootHeightPx = browserHeightPx,
                bottomBarTopPx = bottomBarTopPx,
                backdropSource = chromeBackdropSource,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    val addressBarDockingAvailable = AddressBarDockingRules.isAvailable(
        settingEnabled = controller.isAddressBarDockingEnabled,
        isBlankTab = selectedTab.url == BLANK_URL,
    )
    val effectiveAddressBarDockPlacement = controller.addressBarDockPlacement
        .takeIf { addressBarDockingAvailable && !linkPeekAddressBarExpanded }
    BrowserBottomBar(
        tab = selectedTab,
        pageTranslationProvider = controller.pageTranslationProvider,
        compact = controller.isBottomBarCompact && !linkPeekAddressBarExpanded,
        dockState = AddressBarDockState(
            enabled = addressBarDockingAvailable,
            placement = effectiveAddressBarDockPlacement,
        ),
        dockTargetEdge = controller.lastAddressBarDockEdge,
        editing = addressEditorVisible,
        actionLayout = controller.addressBarActionLayout,
        showCastButton = !BuildConfig.FOSS_DISTRIBUTION &&
            (controller.castMediaCandidate != null || castUiState.isConnected),
        showQrScanner = !BuildConfig.FOSS_DISTRIBUTION,
        tabCount = controller.activeTabs.size,
        userScriptMenuCommands = if (controller.isUserScriptSupported) {
            controller.selectedUserScriptMenuCommands
        } else {
            emptyList()
        },
        supportsPageContentActions = controller.supportsPageContentActions,
        onUserScriptMenuCommand = controller::invokeUserScriptMenuCommand,
        commandFeedback = commandFeedback,
        backdropSource = chromeBackdropSource,
        blurSourceVisible = browserContentBlurTarget != null && !tabOverviewVisible,
        feedbackGesturesEnabled = !addressEditorVisible && !settingsVisible,
        onBack = controller::goBack,
        onForward = controller::goForward,
        onAddress = openAddressEditor,
        editValue = addressValue,
        onEditValueChange = onAddressValueChanged,
        ghostCompletion = domainCompletion,
        onAcceptGhostCompletion = {
            domainCompletion?.let { completion ->
                onAddressValueChanged(
                        TextFieldValue(
                            text = completion,
                            selection = TextRange(completion.length),
                        ),
                    )
                rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        },
        addressFocusNonce = addressFocusNonce,
        onMoveAddressSuggestion = moveSuggestionHighlight,
        onActivateAddressSuggestion = {
            val highlighted = suggestionItems.getOrNull(highlightedSuggestionIndex)
            if (highlighted == null) {
                submitAddressOrCommand(
                    AddressEditorCompletionRules.submissionText(
                        input = addressValue.text,
                        ghostCompletion = domainCompletion,
                    ),
                )
            } else {
                selectSuggestion(highlighted)
            }
        },
        onDismissEditor = { onAddressEditorDismiss() },
        onSubmitAddress = submitAddressOrCommand,
        showAiModeToggle = showAiModeToggle,
        aiModeSelected = aiModeSelectedState.value,
        onAiModeSelectedChange = { selected ->
            aiModeSelectedState.value = selected
            rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        },
        onScanQrCode = {
            if (!qrScanInProgress) {
                qrScanInProgress = true
                qrScanner.startScan(
                    onSuccess = { scannedValue ->
                        qrScanInProgress = false
                        if (scannedValue.isEmpty()) {
                            Toast.makeText(
                                context,
                                qrScanFailureMessage,
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            controller.submitAddress(scannedValue)
                            onAddressEditorDismiss()
                        }
                    },
                    onCanceled = { qrScanInProgress = false },
                    onFailure = {
                        qrScanInProgress = false
                        Toast.makeText(
                            context,
                            qrScanFailureMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        },
        onExpand = controller::expandBottomBar,
        onDock = { controller.updateAddressBarDocked(true) },
        onParkRight = controller::parkAddressBarOnRight,
        onDockPlacementChanged = controller::updateAddressBarDockPlacement,
        onRestoreDock = { controller.updateAddressBarDocked(false) },
        onTabDrag = { delta ->
            if (!addressEditorVisible && !tabOverviewVisible) {
                if (browserDragOffset.floatValue == 0f && delta != 0f) {
                    controller.refreshSelectedTabPreviewBeforeDeparture()
                }
                val proposed = browserDragOffset.floatValue + delta
                val tabs = controller.activeTabs
                val currentIndex = tabs.indexOfFirst { it.id == controller.selectedTabId }
                val hasTarget = if (proposed < 0f) {
                    currentIndex in 0 until tabs.lastIndex
                } else {
                    currentIndex > 0
                }
                browserDragOffset.floatValue = if (hasTarget) {
                    proposed.coerceIn(-tabSwitchTravelPx, tabSwitchTravelPx)
                } else {
                    (proposed * 0.24f).coerceIn(
                        -with(density) { 42.dp.toPx() },
                        with(density) { 42.dp.toPx() },
                    )
                }
            }
        },
        onTabDragStopped = { velocity ->
            val direction = browserDragOffset.floatValue.compareTo(0f)
            val tabs = controller.activeTabs
            val currentIndex = tabs.indexOfFirst { it.id == controller.selectedTabId }
            val targetTab = tabs.getOrNull(currentIndex - direction)
            val minTravel = with(density) { 24.dp.toPx() }
            val fastEnough = browserDragOffset.floatValue.absoluteValue >= minTravel &&
                velocity.absoluteValue >= with(density) { 900.dp.toPx() } &&
                velocity.compareTo(0f) == direction
            val shouldSwitch = targetTab != null &&
                (
                    AddressBarTabSwitchRules.hasReachedDistance(
                        dragDistance = browserDragOffset.floatValue.absoluteValue,
                        viewportWidth = browserWidthPx,
                    ) || fastEnough
                )
            val settle = Animatable(browserDragOffset.floatValue)
            settle.animateTo(
                targetValue = if (shouldSwitch) direction * tabSwitchTravelPx else 0f,
                initialVelocity = velocity,
                animationSpec = if (shouldSwitch) {
                    tween(150, easing = FastOutSlowInEasing)
                } else {
                    spring(dampingRatio = 0.82f, stiffness = 520f)
                },
            ) { browserDragOffset.floatValue = value }
            if (shouldSwitch) {
                onLiveFrameCleared()
                tabHandoffAlpha.snapTo(1f)
                onTabHandoffChanged(
                    TabHandoff(
                        tabId = targetTab.id,
                        preview = controller.previews[targetTab.id].takeUnless {
                            targetTab.isIncognito
                        },
                        title = targetTab.title,
                        favicon = controller.favicons[targetTab.id],
                        isIncognito = targetTab.isIncognito,
                        previewTopInsetPx = controller.previewTopInsetPx(targetTab.id),
                    ),
                )
                browserDragOffset.floatValue = 0f
                controller.selectTab(targetTab.id)
                rootView.performConfirmHaptic()
            } else {
                browserDragOffset.floatValue = 0f
            }
        },
        onTabs = onTabs,
        overviewGestureEnabled = !tabOverviewOpening && !tabOverviewVisible,
        overviewGestureProgress = overviewGestureProgress,
        showingTabOverview = tabOverviewVisible,
        visualOnly = addressBarMorphInFront,
        rootBottomInWindowPx = browserRootBottomInWindowPx,
        onOverviewGestureProgress = { progress ->
            onOverviewGestureStarted()
            overviewGestureProgress.floatValue = progress.coerceIn(0f, 1f)
        },
        onOverviewGestureStarted = { onOverviewGestureStarted() },
        onOverviewGestureCancelled = onOverviewGestureCancelled,
        onReload = controller::reload,
        onStop = controller::stopLoading,
        onNewTab = openNewTabAndEdit,
        onDuplicateTab = { controller.duplicateSelectedTab() },
        onFindInPage = {
            onAddressEditorDismiss()
            controller.openFindInPage()
        },
        onCloseTab = { controller.closeTab(selectedTab.id) },
        onToggleIncognito = {
            if (controller.setBlankTabIncognito(enabled = !selectedTab.isIncognito)) {
                rootView.performConfirmHaptic()
            }
        },
        blankTabModeProgress = blankTabModeProgress,
        onIncognitoControlCenterChanged = onBlankTabModeRevealOriginChanged,
        isFavorite = controller.isSelectedTabFavorite,
        onToggleFavorite = { toggleFavoriteWithFeedback(selectedTab.id) },
        isPinned = selectedTab.isPinned,
        onTogglePinned = {
            if (controller.setTabPinned(selectedTab.id, !selectedTab.isPinned)) {
                rootView.performConfirmHaptic()
            }
        },
        canToggleDomainMute = controller.canToggleSelectedDomainMute,
        isDomainMuted = controller.isSelectedDomainMuted,
        onDomainMutedChange = controller::setSelectedDomainMuted,
        canToggleAlwaysBlockPopups = controller.canToggleSelectedAlwaysBlockPopups,
        isAlwaysBlockPopupsEnabled = controller.isSelectedAlwaysBlockPopups,
        onAlwaysBlockPopupsChange = controller::setSelectedAlwaysBlockPopups,
        canToggleDesktopView = controller.canToggleSelectedDesktopView,
        isDesktopView = controller.isSelectedDesktopView,
        onDesktopViewChange = controller::setSelectedDesktopView,
        canToggleCookieBannerRemoval = canToggleSelectedCookieBannerRemoval,
        isCookieBannerRemovalEnabled = canToggleSelectedCookieBannerRemoval &&
            !selectedSiteState.cookieBannerRemovalDisabled,
        canToggleForceVerticalScrolling = selectedSiteHasHost,
        isForceVerticalScrollingEnabled = selectedSiteState.forceVerticalScrolling,
        canToggleForcePageZooming = selectedSiteHasHost,
        isForcePageZoomingEnabled = selectedSiteState.forcePageZooming,
        canToggleForceSafeArea = selectedSiteHasHost,
        isForceSafeAreaEnabled = selectedSiteState.forceSafeArea,
        onCookieBannerRemovalEnabledChange = { enabled ->
            if (controller.setCookieBannerRemovalDisabled(selectedTab.id, !enabled)) {
                rootView.performConfirmHaptic()
            }
        },
        onForceVerticalScrollingChange = { enabled ->
            if (controller.setForceVerticalScrolling(selectedTab.id, enabled)) {
                rootView.performConfirmHaptic()
            }
        },
        onForcePageZoomingChange = { enabled ->
            if (controller.setForcePageZooming(selectedTab.id, enabled)) {
                rootView.performConfirmHaptic()
            }
        },
        onForceSafeAreaChange = { enabled ->
            if (controller.setForceSafeArea(selectedTab.id, enabled)) {
                rootView.performConfirmHaptic()
            }
        },
        snoozedTabCount = visibleSnoozedTabCount,
        onSnoozedTabs = {
            onAddressEditorDismiss()
            onSnoozedTabs()
        },
        onHistory = {
            onAddressEditorDismiss()
            onOpenHistory()
        },
        onOpenFirefoxExtensions = onOpenFirefoxExtensions?.let { openExtensions ->
            {
                onAddressEditorDismiss()
                openExtensions()
            }
        },
        onSettings = {
            onAddressEditorDismiss()
            onSettings()
        },
        onPrivacyXRay = {
            onPrivacyXRay()
            rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        },
        permissionActivityVisible = permissionActivityVisible,
        onPermissionRadar = {
            onPermissionRadar()
            rootView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        },
        addressBarPulseNonce = controller.contentActions.addressBarPulseNonce,
        newTabPulseNonce = controller.contentActions.linkPeekNewTabPulseNonce,
        onNewTabButtonBounds = onNewTabButtonBoundsChanged,
        onOpenExternal = controller::openSelectedPageExternally,
        onSummarizeWithAssistant = controller::summarizeSelectedPageWithAssistant,
        onShare = controller::shareSelectedPage,
        onPrint = controller::printSelectedPage,
        onTranslate = controller::translateSelectedPage,
        onReaderStudio = onReaderStudio,
        onOpenCandyTrail = onOpenCandyTrail,
        onSnooze = onSnooze,
        onAddSiteCapsule = onAddSiteCapsule,
        onBarPositioned = { topInRootPx, topInWindowPx ->
            if (
                effectiveAddressBarDockPlacement != null &&
                !addressEditorVisible &&
                commandFeedback == null
            ) {
                bottomBarTopPx.floatValue = Float.NaN
                controller.setPreviewContentBottomInWindowPx(0)
            } else {
                bottomBarTopPx.floatValue = topInRootPx
                controller.setPreviewContentBottomInWindowPx(topInWindowPx)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .zIndex(
                when {
                    commandFeedback != null && !tabOverviewVisible -> 30f
                    addressBarMorphInFront -> 20f
                    else -> 0f
                },
            ),
    )

    if (
        !selectedTab.isIncognito &&
        !tabOverviewVisible &&
        !addressEditorVisible &&
        !settingsVisible &&
        !webViewVideoOnlyPresentation
    ) {
        CastControls(
            state = castUiState,
            onTogglePlayback = onToggleCastPlayback,
            onSeek = onSeekCast,
            onVolumeChange = onCastVolumeChange,
            onDisconnect = onDisconnectCast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    bottom = if (effectiveAddressBarDockPlacement != null) 12.dp else 88.dp,
                )
                .zIndex(10f),
        )
    }

}
