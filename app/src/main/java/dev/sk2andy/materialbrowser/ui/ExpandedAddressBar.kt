@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.PageTranslationRules
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.AddressBarActionLayoutRules
import dev.sk2andy.materialbrowser.data.TabDeletionRules
import dev.sk2andy.materialbrowser.reader.ReaderStudioSessionRules
import dev.sk2andy.materialbrowser.shared.ui.AddressBarFieldContent
import dev.sk2andy.materialbrowser.ui.theme.BrowserChromeSurfaceRole
import dev.sk2andy.materialbrowser.ui.theme.LocalCandyMotionScheme
import dev.sk2andy.materialbrowser.ui.theme.addressFieldContainerColor
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens

@Composable
internal fun ExpandedBottomBarContent(
    tab: BrowserTab,
    pageTranslationProvider: PageTranslationProvider,
    backdropSource: CandyChromeBackdropSource?,
    actionLayout: AddressBarActionLayout,
    showCastButton: Boolean,
    showQrScanner: Boolean,
    tabCount: Int,
    userScriptMenuCommands: List<UserScriptMenuCommand>,
    onUserScriptMenuCommand: (UserScriptMenuCommand) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onAddress: () -> Unit,
    editing: Boolean,
    editValue: TextFieldValue,
    onEditValueChange: (TextFieldValue) -> Unit,
    ghostCompletion: String?,
    onAcceptGhostCompletion: () -> Unit,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    addressFocusNonce: Int,
    requestAddressFocus: Boolean,
    onMoveAddressSuggestion: (Int) -> Unit,
    onActivateAddressSuggestion: () -> Unit,
    onDismissEditor: () -> Unit,
    onSubmitAddress: (String) -> Unit,
    showAiModeToggle: Boolean,
    aiModeSelected: Boolean,
    onAiModeSelectedChange: (Boolean) -> Unit,
    onScanQrCode: () -> Unit,
    onTabDrag: (Float) -> Unit,
    onTabDragStopped: suspend (Float) -> Unit,
    onTabs: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onNewTab: () -> Unit,
    onDuplicateTab: () -> Unit,
    onFindInPage: () -> Unit,
    onCloseTab: () -> Unit,
    newTabPulseScale: Float,
    onNewTabButtonBounds: (Rect?) -> Unit,
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
    onHistory: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)?,
    onSettings: () -> Unit,
    onPrivacyXRay: () -> Unit,
    permissionActivityVisible: Boolean,
    onPermissionRadar: () -> Unit,
    onOpenExternal: () -> Unit,
    onSummarizeWithAssistant: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onTranslate: () -> Unit,
    onReaderStudio: () -> Unit,
    onOpenCandyTrail: () -> Unit,
    onSnooze: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    supportsPageContentActions: Boolean,
    canDock: Boolean,
    onDock: () -> Unit,
    onParkRight: () -> Unit,
    overviewGestureEnabled: Boolean,
    overviewGestureProgress: FloatState,
    onOverviewGestureProgress: (Float) -> Unit,
    onOverviewGestureStarted: () -> Unit,
    onOverviewGestureCancelled: () -> Unit,
) {
    val motionScheme = LocalCandyMotionScheme.current
    val addressChromeTokens = browserChromeSurfaceTokens(BrowserChromeSurfaceRole.AddressBar)
    val tabDragState = rememberDraggableState(onTabDrag)
    val keyboard = LocalSoftwareKeyboardController.current
    val windowInfo = LocalWindowInfo.current
    var addressFieldFocused by remember(tab.id) { mutableStateOf(false) }
    val editorUsesFullWidth = AddressBarControlRules.editorUsesFullWidth(
        editing = editing,
        addressFieldFocused = addressFieldFocused,
        imeVisible = WindowInsets.isImeVisible,
    )
    val dynamicSlotCount = maxOf(
        if (showCastButton) 1 else 0,
        if (editing && tab.url == BLANK_URL) 1 else 0,
    )
    val visibleActionLayout = AddressBarActionLayoutRules.reserveDynamicSlots(
        layout = actionLayout,
        dynamicSlotCount = dynamicSlotCount,
    )
    val overflowAddressBarActions = if (showCastButton) {
        AddressBarActionLayoutRules.temporarilyHiddenActions(
            layout = actionLayout,
            visibleLayout = visibleActionLayout,
        ).filter { action ->
            action == AddressBarAction.Tabs ||
                action == AddressBarAction.NewTab ||
                action == AddressBarAction.CloseTab ||
                action == AddressBarAction.ParkRight
        }
    } else {
        emptyList()
    }
    val actionState = AddressBarActionState(
        tabCount = tabCount,
        isLoading = tab.isLoading,
        canGoBack = tab.canGoBack,
        canGoForward = tab.canGoForward,
        canToggleFavorite = tab.url != BLANK_URL && !tab.isIncognito,
        isFavorite = isFavorite,
        isPinned = isPinned,
        canToggleDesktopView = canToggleDesktopView,
        isDesktopView = isDesktopView,
        canToggleForceVerticalScrolling = canToggleForceVerticalScrolling,
        isForceVerticalScrollingEnabled = isForceVerticalScrollingEnabled,
        canUsePageActions = tab.url != BLANK_URL,
        canOpenReader = ReaderStudioSessionRules.isSupportedSource(tab.url),
        canCloseTab = TabDeletionRules.canDelete(tab),
        canParkRight = canDock,
        newTabPulseScale = newTabPulseScale,
    )
    val actionCallbacks = AddressBarActionCallbacks(
        onTabs = onTabs,
        onToggleFavorite = onToggleFavorite,
        onTogglePinned = onTogglePinned,
        onDesktopViewChange = onDesktopViewChange,
        onForceVerticalScrollingChange = onForceVerticalScrollingChange,
        onReaderStudio = onReaderStudio,
        onFindInPage = onFindInPage,
        onShare = onShare,
        onPrint = onPrint,
        onNewTab = onNewTab,
        onReloadOrStop = { if (tab.isLoading) onStop() else onReload() },
        onCloseTab = onCloseTab,
        onBack = onBack,
        onForward = onForward,
        onParkRight = onParkRight,
        onNewTabButtonBounds = onNewTabButtonBounds,
    )
    LaunchedEffect(editorUsesFullWidth) {
        if (editorUsesFullWidth) onMenuExpandedChange(false)
    }
    LaunchedEffect(
        requestAddressFocus,
        tab.id,
        addressFocusNonce,
        windowInfo.isWindowFocused,
    ) {
        if (requestAddressFocus && windowInfo.isWindowFocused) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Column {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            AnimatedVisibility(
                visible = visibleActionLayout.beforeAddress.isNotEmpty() && !editorUsesFullWidth,
                enter = fadeIn(tween(motionScheme.addressBarActionFadeInMillis)) +
                    expandHorizontally(tween(motionScheme.addressBarActionExpandMillis)),
                exit = fadeOut(tween(motionScheme.addressBarActionFadeOutMillis)) +
                    shrinkHorizontally(tween(motionScheme.addressBarActionExpandMillis)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    visibleActionLayout.beforeAddress.forEach { action ->
                        AddressBarActionButton(
                            action = action,
                            state = actionState,
                            callbacks = actionCallbacks,
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .addressBarVerticalGesture(
                        enabled = !editing && overviewGestureEnabled,
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
                        onDragStopped = { velocity -> onTabDragStopped(velocity) },
                    ),
                shape = RoundedCornerShape(addressChromeTokens.cornerRadius),
                color = addressFieldContainerColor(),
            ) {
                AddressBarFieldContent(
                    editing = editing,
                    editValue = editValue,
                    onEditValueChange = onEditValueChange,
                    ghostCompletion = ghostCompletion,
                    placeholder = stringResource(R.string.search_or_enter_url),
                    displayText = if (tab.url == BLANK_URL) {
                        stringResource(R.string.address_empty_hint)
                    } else {
                        AddressResolver.displayText(tab.url)
                    },
                    onSubmitAddress = onSubmitAddress,
                    submissionText = AddressEditorCompletionRules::submissionText,
                    editorModifier = Modifier
                        .testTag(AddressBarTestTags.Editor)
                        .onPreviewKeyEvent { event ->
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (event.type == KeyEventType.KeyDown) {
                                                onMoveAddressSuggestion(1)
                                            }
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            if (event.type == KeyEventType.KeyDown) {
                                                onMoveAddressSuggestion(-1)
                                            }
                                            true
                                        }
                                        Key.Enter,
                                        Key.NumPadEnter,
                                        Key.DirectionCenter,
                                        -> {
                                            if (event.type == KeyEventType.KeyUp) {
                                                onActivateAddressSuggestion()
                                            }
                                            true
                                        }
                                        Key.DirectionRight,
                                        Key.Tab,
                                        -> if (
                                            event.type == KeyEventType.KeyDown &&
                                            ghostCompletion != null &&
                                            editValue.selection.start == editValue.text.length &&
                                            editValue.selection.end == editValue.text.length
                                        ) {
                                            onAcceptGhostCompletion()
                                            true
                                        } else {
                                            false
                                        }
                                        else -> false
                                    }
                                }
                        .focusRequester(focusRequester)
                        .onFocusChanged { addressFieldFocused = it.isFocused },
                    displayTextModifier = Modifier.addressBarReaderActions(
                        readerEnabled = ReaderStudioSessionRules.isSupportedSource(tab.url),
                        onClick = onAddress,
                        onReaderStudio = onReaderStudio,
                        readerLabel = stringResource(R.string.reader_open_action),
                    ),
                    editorTrailingContent = {
                        if (tab.url == BLANK_URL) {
                            BlankTabIncognitoModeButton(
                                enabled = tab.isIncognito,
                                progress = blankTabModeProgress,
                                onCenterChanged = onIncognitoControlCenterChanged,
                                onClick = onToggleIncognito,
                            )
                        }
                        if (showAiModeToggle) {
                            AddressAiModeToggle(
                                selected = aiModeSelected,
                                onSelectedChange = onAiModeSelectedChange,
                            )
                        }
                        IconButton(onClick = onDismissEditor) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close_address_input),
                            )
                        }
                    },
                    displayTrailingContent = {
                            PrivacyXRayBadge(
                                blockedCount = tab.blockedCount,
                                onClick = onPrivacyXRay,
                                modifier = Modifier
                                    .zIndex(2f)
                                    .padding(end = 2.dp),
                                tabId = tab.id,
                            )
                            PermissionRadarBadge(
                                visible = permissionActivityVisible,
                                onClick = onPermissionRadar,
                                modifier = Modifier.padding(end = 2.dp),
                            )
                    },
                )
            }
            AnimatedVisibility(
                visible = !editorUsesFullWidth,
                enter = fadeIn(tween(motionScheme.addressBarActionFadeInMillis)) +
                    expandHorizontally(tween(motionScheme.addressBarActionExpandMillis)),
                exit = fadeOut(tween(motionScheme.addressBarActionFadeOutMillis)) +
                    shrinkHorizontally(tween(motionScheme.addressBarActionExpandMillis)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!editing || tab.url != BLANK_URL) {
                        visibleActionLayout.afterAddress.forEach { action ->
                            AddressBarActionButton(
                                action = action,
                                state = actionState,
                                callbacks = actionCallbacks,
                            )
                        }
                        if (showCastButton) CastRouteButton()
                    }
                    if (editing && tab.url == BLANK_URL && showQrScanner) {
                        IconButton(
                            onClick = onScanQrCode,
                            modifier = Modifier.testTag(AddressBarTestTags.QrScanner),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_qr_code_scanner),
                                contentDescription = stringResource(R.string.cd_scan_qr_code),
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { onMenuExpandedChange(true) }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                )
                            }
                            BrowserMainMenu(
                                expanded = menuExpanded,
                                backdropSource = backdropSource,
                                onDismissRequest = { onMenuExpandedChange(false) },
                                pageSubtitle = if (tab.url == BLANK_URL) {
                                    stringResource(R.string.new_tab_title)
                                } else {
                                    AddressResolver.displayText(tab.url)
                                },
                                canGoBack = tab.canGoBack,
                                canGoForward = tab.canGoForward,
                                isLoading = tab.isLoading,
                                canToggleFavorite = tab.url != BLANK_URL && !tab.isIncognito,
                                isFavorite = isFavorite,
                                isPinned = isPinned,
                                canUsePageActions = tab.url != BLANK_URL,
                                canUseDocumentActions = tab.url != BLANK_URL,
                                canOpenReader = ReaderStudioSessionRules.isSupportedSource(tab.url),
                                canTranslatePage = PageTranslationRules.canTranslate(
                                    provider = pageTranslationProvider,
                                    sourceUrl = tab.url,
                                ),
                                canToggleDomainMute = canToggleDomainMute,
                                isDomainMuted = isDomainMuted,
                                canToggleAlwaysBlockPopups = canToggleAlwaysBlockPopups,
                                isAlwaysBlockPopupsEnabled = isAlwaysBlockPopupsEnabled,
                                canToggleDesktopView = canToggleDesktopView,
                                isDesktopView = isDesktopView,
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
                                canAddSiteCapsule = tab.url != BLANK_URL &&
                                    !tab.isIncognito &&
                                    (
                                        tab.url.startsWith("https://") ||
                                            tab.url.startsWith("http://")
                                        ),
                                canSnooze = !tab.isIncognito,
                                snoozedTabCount = snoozedTabCount,
                                overflowAddressBarActions = overflowAddressBarActions,
                                canCloseTab = actionState.canCloseTab,
                                userScriptMenuCommands = userScriptMenuCommands,
                                onUserScriptMenuCommand = onUserScriptMenuCommand,
                                onTabs = onTabs,
                                onNewTab = onNewTab,
                                onDuplicateTab = onDuplicateTab,
                                onCloseTab = onCloseTab,
                                onBack = onBack,
                                onForward = onForward,
                                onReloadOrStop = { if (tab.isLoading) onStop() else onReload() },
                                onToggleFavorite = onToggleFavorite,
                                onTogglePinned = onTogglePinned,
                                onShare = onShare,
                                onOpenExternal = onOpenExternal,
                                onPrint = onPrint,
                                onOpenReader = onReaderStudio,
                                onTranslate = onTranslate,
                                onFindInPage = onFindInPage,
                                onDomainMutedChange = onDomainMutedChange,
                                onAlwaysBlockPopupsChange = onAlwaysBlockPopupsChange,
                                onDesktopViewChange = onDesktopViewChange,
                                onCookieBannerRemovalEnabledChange =
                                    onCookieBannerRemovalEnabledChange,
                                onForceVerticalScrollingChange =
                                    onForceVerticalScrollingChange,
                                onForcePageZoomingChange = onForcePageZoomingChange,
                                onForceSafeAreaChange = onForceSafeAreaChange,
                                onOpenCandyTrail = onOpenCandyTrail,
                                onAddSiteCapsule = onAddSiteCapsule,
                                onSummarize = onSummarizeWithAssistant,
                                onSnooze = onSnooze,
                                onSnoozedTabs = onSnoozedTabs,
                                canDockAddressBar = canDock,
                                onDockAddressBar = onDock,
                                onParkAddressBarRight = onParkRight,
                                onHistory = onHistory,
                                onOpenFirefoxExtensions = onOpenFirefoxExtensions,
                                onSettings = onSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddressAiModeToggle(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = LocalCandyMotionScheme.current
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = tween(motionScheme.addressBarToggleColorMillis),
        label = "Address AI mode container color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(motionScheme.addressBarToggleColorMillis),
        label = "Address AI mode content color",
    )
    IconToggleButton(
        checked = selected,
        onCheckedChange = onSelectedChange,
        modifier = modifier
            .size(48.dp)
            .shadow(
                elevation = if (selected) 6.dp else 0.dp,
                shape = CircleShape,
            )
            .background(containerColor, CircleShape)
            .testTag(AddressBarTestTags.AiModeToggle),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_symbol_auto_awesome),
            contentDescription = stringResource(
                if (selected) {
                    R.string.cd_disable_google_ai_mode
                } else {
                    R.string.cd_enable_google_ai_mode
                },
            ),
            tint = contentColor,
        )
    }
}

@Composable
internal fun AddressBarTabCounterButton(
    tabCount: Int,
    onClick: () -> Unit,
) {
    val description = pluralStringResource(
        R.plurals.cd_open_tab_overview_count,
        tabCount,
        tabCount,
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .testTag(AddressBarTestTags.TabButton)
            .semantics { contentDescription = description },
    ) {
        AddressBarTabCounterGlyph(tabCount = tabCount)
    }
}

internal object AddressBarTestTags {
    const val AiModeToggle = "address_bar_ai_mode_toggle"
    const val Editor = "address_bar_editor"
    const val IncognitoToggle = "address_bar_incognito_toggle"
    const val QrScanner = "address_bar_qr_scanner"
    const val TabButton = "address_bar_tab_button"
}

@Composable
internal fun AddressEditorBackdrop(
    showStartContent: Boolean,
    modeProgress: Float,
    revealOriginInRoot: Offset,
    wallpaper: ProfileWallpaperRuntime?,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val boundedProgress = BlankTabModeMorphRules.bounded(modeProgress)
    val regularIconAlpha = BlankTabModeMorphRules.regularIconAlpha(boundedProgress)
    val incognitoIconAlpha = BlankTabModeMorphRules.incognitoIconAlpha(boundedProgress)
    val backgroundModifier = if (showStartContent) {
        Modifier.blankTabModeBackground(
            progress = boundedProgress,
            revealOriginInRoot = revealOriginInRoot,
            regularCenterColor = colors.primaryContainer,
            incognitoCenterColor = colors.inverseSurface,
            edgeColor = colors.surface,
            wallpaper = wallpaper,
        )
    } else {
        Modifier.background(
            Brush.linearGradient(
                listOf(
                    colors.scrim.copy(alpha = 0.08f),
                    colors.scrim.copy(alpha = 0.08f),
                ),
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .clickable(onClick = onDismiss)
            .statusBarsPadding(),
    ) {
        if (showStartContent) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp),
                shape = RoundedCornerShape(
                    BlankTabModeMorphRules.heroCornerRadiusDp(boundedProgress).dp,
                ),
                color = lerp(colors.primary, colors.inverseSurface, boundedProgress),
                shadowElevation = 14.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground_art),
                        contentDescription = null,
                        modifier = Modifier
                            .size(68.dp)
                            .graphicsLayer {
                                alpha = regularIconAlpha
                                scaleX = BlankTabModeMorphRules.iconScale(regularIconAlpha)
                                scaleY = scaleX
                            },
                        tint = Color.Unspecified,
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_incognito_filled),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                alpha = incognitoIconAlpha
                                scaleX = BlankTabModeMorphRules.iconScale(incognitoIconAlpha)
                                scaleY = scaleX
                            },
                        tint = colors.inverseOnSurface,
                    )
                }
            }
        }
    }
}
