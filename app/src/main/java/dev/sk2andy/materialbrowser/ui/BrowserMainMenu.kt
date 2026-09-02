package dev.sk2andy.materialbrowser.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserInputDiagnostics
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget

internal object BrowserMainMenuMotion {
    const val EXIT_DURATION_MILLIS = 160
    const val EXIT_SCALE = 0.9f
}

private data class BrowserMainMenuPresentation(
    val pageSubtitle: String,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val isLoading: Boolean,
    val canToggleFavorite: Boolean,
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val canUsePageActions: Boolean,
    val canOpenReader: Boolean,
    val canTranslatePage: Boolean,
    val canToggleDomainMute: Boolean,
    val isDomainMuted: Boolean,
    val canToggleDesktopView: Boolean,
    val isDesktopView: Boolean,
    val canToggleCookieBannerRemoval: Boolean,
    val isCookieBannerRemovalEnabled: Boolean,
    val canToggleForceVerticalScrolling: Boolean,
    val isForceVerticalScrollingEnabled: Boolean,
    val canToggleForcePageZooming: Boolean,
    val isForcePageZoomingEnabled: Boolean,
    val canToggleForceSafeArea: Boolean,
    val isForceSafeAreaEnabled: Boolean,
    val canAddSiteCapsule: Boolean,
    val canSnooze: Boolean,
)

@Composable
internal fun BrowserMainMenu(
    expanded: Boolean,
    blurTarget: BlurTarget?,
    onDismissRequest: () -> Unit,
    pageSubtitle: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    isLoading: Boolean,
    canToggleFavorite: Boolean,
    isFavorite: Boolean,
    isPinned: Boolean,
    canUsePageActions: Boolean,
    canOpenReader: Boolean,
    canTranslatePage: Boolean,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    canToggleDesktopView: Boolean,
    isDesktopView: Boolean,
    canToggleCookieBannerRemoval: Boolean,
    isCookieBannerRemovalEnabled: Boolean,
    canToggleForceVerticalScrolling: Boolean,
    isForceVerticalScrollingEnabled: Boolean,
    canToggleForcePageZooming: Boolean,
    isForcePageZoomingEnabled: Boolean,
    canToggleForceSafeArea: Boolean,
    isForceSafeAreaEnabled: Boolean,
    canAddSiteCapsule: Boolean,
    canSnooze: Boolean,
    snoozedTabCount: Int,
    overflowAddressBarActions: List<AddressBarAction> = emptyList(),
    canCloseTab: Boolean = true,
    userScriptMenuCommands: List<UserScriptMenuCommand> = emptyList(),
    onTabs: () -> Unit = {},
    onNewTab: () -> Unit = {},
    onCloseTab: () -> Unit = {},
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onPrint: () -> Unit,
    onOpenReader: () -> Unit,
    onTranslate: () -> Unit,
    onFindInPage: () -> Unit = {},
    onDomainMutedChange: (Boolean) -> Unit,
    onDesktopViewChange: (Boolean) -> Unit,
    onCookieBannerRemovalEnabledChange: (Boolean) -> Unit,
    onForceVerticalScrollingChange: (Boolean) -> Unit,
    onForcePageZoomingChange: (Boolean) -> Unit,
    onForceSafeAreaChange: (Boolean) -> Unit,
    onOpenCandyTrail: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    onSummarize: () -> Unit,
    onSnooze: () -> Unit,
    onSnoozedTabs: () -> Unit,
    onUserScriptMenuCommand: (UserScriptMenuCommand) -> Unit = {},
    canDockAddressBar: Boolean = true,
    onDockAddressBar: () -> Unit,
    onParkAddressBarRight: () -> Unit = {},
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val chromeTokens = browserChromeSurfaceTokens()
    val menuShape = MaterialTheme.shapes.large
    val outerCorners = MaterialTheme.shapes.medium
    val innerCorners = MaterialTheme.shapes.extraSmall
    val firstItemShape = RoundedCornerShape(
        topStart = outerCorners.topStart,
        topEnd = outerCorners.topEnd,
        bottomEnd = innerCorners.bottomEnd,
        bottomStart = innerCorners.bottomStart,
    )
    val lastItemShape = RoundedCornerShape(
        topStart = innerCorners.topStart,
        topEnd = innerCorners.topEnd,
        bottomEnd = outerCorners.bottomEnd,
        bottomStart = outerCorners.bottomStart,
    )
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val menuWidth = minOf(400.dp, screenWidth - 24.dp)
    val compactToolbar = menuWidth < 340.dp
    val menuMaxHeight = screenHeight * BROWSER_MAIN_MENU_MAX_HEIGHT_FRACTION
    val menuScrollState = rememberScrollState()
    val popupOffset = with(LocalDensity.current) { IntOffset(0, (-10).dp.roundToPx()) }
    val requestedPresentation = BrowserMainMenuPresentation(
        pageSubtitle = pageSubtitle,
        canGoBack = canGoBack,
        canGoForward = canGoForward,
        isLoading = isLoading,
        canToggleFavorite = canToggleFavorite,
        isFavorite = isFavorite,
        isPinned = isPinned,
        canUsePageActions = canUsePageActions,
        canOpenReader = canOpenReader,
        canTranslatePage = canTranslatePage,
        canToggleDomainMute = canToggleDomainMute,
        isDomainMuted = isDomainMuted,
        canToggleDesktopView = canToggleDesktopView,
        isDesktopView = isDesktopView,
        canToggleCookieBannerRemoval = canToggleCookieBannerRemoval,
        isCookieBannerRemovalEnabled = isCookieBannerRemovalEnabled,
        canToggleForceVerticalScrolling = canToggleForceVerticalScrolling,
        isForceVerticalScrollingEnabled = isForceVerticalScrollingEnabled,
        canToggleForcePageZooming = canToggleForcePageZooming,
        isForcePageZoomingEnabled = isForcePageZoomingEnabled,
        canToggleForceSafeArea = canToggleForceSafeArea,
        isForceSafeAreaEnabled = isForceSafeAreaEnabled,
        canAddSiteCapsule = canAddSiteCapsule,
        canSnooze = canSnooze,
    )
    var presentation by remember { mutableStateOf(requestedPresentation) }
    if (expanded && requestedPresentation != presentation) {
        presentation = requestedPresentation
    }
    var popupVisible by remember { mutableStateOf(expanded) }
    var actionCommitted by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(if (expanded) 1f else 0f) }
    val menuTransformOrigin = if (LocalLayoutDirection.current == LayoutDirection.Ltr) {
        TransformOrigin(1f, 1f)
    } else {
        TransformOrigin(0f, 1f)
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            actionCommitted = false
            val reversingExit = popupVisible
            popupVisible = true
            if (reversingExit) {
                exitProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = BrowserMainMenuMotion.EXIT_DURATION_MILLIS,
                        easing = LinearOutSlowInEasing,
                    ),
                )
            } else {
                menuScrollState.scrollTo(0)
                exitProgress.snapTo(1f)
            }
        } else if (popupVisible) {
            exitProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = BrowserMainMenuMotion.EXIT_DURATION_MILLIS,
                    easing = FastOutLinearInEasing,
                ),
            )
            popupVisible = false
        }
    }
    LaunchedEffect(expanded, popupVisible) {
        BrowserInputDiagnostics.popupState(expanded, popupVisible)
    }
    fun dismissThen(action: () -> Unit) {
        if (!expanded || actionCommitted) return
        actionCommitted = true
        onDismissRequest()
        action()
    }

    if (popupVisible) Popup(
        alignment = Alignment.BottomEnd,
        offset = popupOffset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        BrowserChromeSurface(
            blurTarget = blurTarget,
            tokens = chromeTokens,
            modifier = Modifier
                .width(menuWidth)
                .height(menuMaxHeight)
                .graphicsLayer {
                    alpha = exitProgress.value
                    val scale = BrowserMainMenuMotion.EXIT_SCALE +
                        (1f - BrowserMainMenuMotion.EXIT_SCALE) * exitProgress.value
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = menuTransformOrigin
                }
                .clip(menuShape)
                .testTag(BrowserMainMenuTestTags.Menu),
            shape = menuShape,
            blurCornerRadius = chromeTokens.largeCornerRadius,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(menuScrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
            Text(
                text = stringResource(R.string.browser_menu_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = presentation.pageSubtitle,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BrowserMainMenuTestTags.Toolbar),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MenuToolbarAction(
                        label = stringResource(R.string.action_back),
                        iconRes = R.drawable.ic_symbol_arrow_back,
                        enabled = presentation.canGoBack,
                        onClick = { dismissThen(onBack) },
                        modifier = Modifier.weight(1f),
                    )
                    MenuToolbarAction(
                        label = stringResource(R.string.action_forward),
                        iconRes = R.drawable.ic_symbol_arrow_forward,
                        enabled = presentation.canGoForward,
                        onClick = { dismissThen(onForward) },
                        modifier = Modifier.weight(1f),
                    )
                    MenuToolbarAction(
                        label = stringResource(
                            if (presentation.isLoading) {
                                R.string.action_stop_loading
                            } else {
                                R.string.action_reload
                            },
                        ),
                        iconRes = if (presentation.isLoading) {
                            R.drawable.ic_symbol_close
                        } else {
                            R.drawable.ic_symbol_refresh
                        },
                        onClick = { dismissThen(onReloadOrStop) },
                        modifier = Modifier.weight(1f),
                    )
                    if (!compactToolbar) {
                        BrowserMainMenuFavoriteAction(
                            presentation = presentation,
                            onClick = { dismissThen(onToggleFavorite) },
                            modifier = Modifier.weight(1f),
                        )
                        BrowserMainMenuPinAction(
                            isPinned = presentation.isPinned,
                            onClick = { dismissThen(onTogglePinned) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (compactToolbar) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BrowserMainMenuFavoriteAction(
                            presentation = presentation,
                            onClick = { dismissThen(onToggleFavorite) },
                            modifier = Modifier.weight(1f),
                        )
                        BrowserMainMenuPinAction(
                            isPinned = presentation.isPinned,
                            onClick = { dismissThen(onTogglePinned) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.browser_menu_page_group),
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                modifier = Modifier.testTag(BrowserMainMenuTestTags.PageGroup),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                overflowAddressBarActions.forEachIndexed { index, action ->
                    val iconRes = when (action) {
                        AddressBarAction.Tabs -> R.drawable.ic_switch_to_tab
                        AddressBarAction.NewTab -> R.drawable.ic_symbol_add
                        AddressBarAction.CloseTab -> R.drawable.ic_symbol_close
                        AddressBarAction.ParkRight ->
                            R.drawable.ic_symbol_chevron_physical_right
                        else -> return@forEachIndexed
                    }
                    val enabled = when (action) {
                        AddressBarAction.CloseTab -> canCloseTab
                        AddressBarAction.ParkRight -> canDockAddressBar
                        else -> true
                    }
                    val callback = when (action) {
                        AddressBarAction.Tabs -> onTabs
                        AddressBarAction.NewTab -> onNewTab
                        AddressBarAction.CloseTab -> onCloseTab
                        AddressBarAction.ParkRight -> onParkAddressBarRight
                        else -> return@forEachIndexed
                    }
                    MenuRow(
                        label = stringResource(action.labelRes()),
                        iconRes = iconRes,
                        enabled = enabled,
                        shape = if (index == 0) firstItemShape else innerCorners,
                        onClick = { dismissThen(callback) },
                    )
                }
                MenuRow(
                    label = stringResource(R.string.reader_open_action),
                    iconRes = R.drawable.ic_reader_align_start,
                    enabled = presentation.canOpenReader,
                    shape = if (overflowAddressBarActions.isEmpty()) {
                        firstItemShape
                    } else {
                        innerCorners
                    },
                    onClick = { dismissThen(onOpenReader) },
                )
                MenuRow(
                    label = stringResource(R.string.action_translate_page),
                    iconRes = R.drawable.ic_symbol_translate,
                    enabled = presentation.canTranslatePage,
                    shape = innerCorners,
                    modifier = Modifier.testTag(BrowserMainMenuTestTags.Translate),
                    onClick = { dismissThen(onTranslate) },
                )
                MenuRow(
                    label = stringResource(R.string.action_find_in_page),
                    iconRes = R.drawable.ic_symbol_find_in_page,
                    enabled = presentation.canUsePageActions,
                    shape = innerCorners,
                    modifier = Modifier.testTag(BrowserMainMenuTestTags.FindInPage),
                    onClick = { dismissThen(onFindInPage) },
                )
                MenuRow(
                    label = stringResource(R.string.action_share),
                    iconRes = R.drawable.ic_symbol_share,
                    enabled = presentation.canUsePageActions,
                    shape = innerCorners,
                    onClick = { dismissThen(onShare) },
                )
                MenuRow(
                    label = stringResource(R.string.action_open_in_app),
                    iconRes = R.drawable.ic_symbol_open_in_new,
                    enabled = presentation.canUsePageActions,
                    shape = innerCorners,
                    onClick = { dismissThen(onOpenExternal) },
                )
                MenuRow(
                    label = stringResource(R.string.action_print),
                    iconRes = R.drawable.ic_symbol_print,
                    enabled = presentation.canUsePageActions,
                    shape = innerCorners,
                    onClick = { dismissThen(onPrint) },
                )
                if (
                    presentation.canToggleForceVerticalScrolling ||
                    presentation.canToggleForcePageZooming ||
                    presentation.canToggleForceSafeArea
                ) {
                    BrowserMenuToggleItem(
                        label = stringResource(R.string.privacy_cookie_banner_remove),
                        supportingText = stringResource(
                            if (presentation.canToggleCookieBannerRemoval) {
                                R.string.privacy_cookie_banner_remove_description
                            } else {
                                R.string.privacy_cookie_banner_remove_unavailable
                            },
                        ),
                        checked = presentation.isCookieBannerRemovalEnabled,
                        enabled = presentation.canToggleCookieBannerRemoval,
                        onCheckedChange = onCookieBannerRemovalEnabledChange,
                        modifier = Modifier.testTag(
                            BrowserMainMenuTestTags.CookieBannerRemoval,
                        ),
                        shape = innerCorners,
                    )
                    if (presentation.canToggleForceVerticalScrolling) {
                        BrowserMenuToggleItem(
                            label = stringResource(R.string.privacy_force_vertical_scrolling),
                            supportingText = stringResource(
                                R.string.privacy_force_vertical_scrolling_description,
                            ),
                            checked = presentation.isForceVerticalScrollingEnabled,
                            enabled = true,
                            onCheckedChange = onForceVerticalScrollingChange,
                            modifier = Modifier.testTag(
                                BrowserMainMenuTestTags.ForceVerticalScrolling,
                            ),
                            shape = innerCorners,
                        )
                    }
                    if (presentation.canToggleForcePageZooming) {
                        BrowserMenuToggleItem(
                            label = stringResource(R.string.privacy_force_page_zooming),
                            supportingText = stringResource(
                                R.string.privacy_force_page_zooming_description,
                            ),
                            checked = presentation.isForcePageZoomingEnabled,
                            enabled = true,
                            onCheckedChange = onForcePageZoomingChange,
                            modifier = Modifier.testTag(
                                BrowserMainMenuTestTags.ForcePageZooming,
                            ),
                            shape = innerCorners,
                        )
                    }
                    if (presentation.canToggleForceSafeArea) {
                        BrowserMenuToggleItem(
                            label = stringResource(R.string.compatibility_force_safe_area),
                            supportingText = stringResource(
                                R.string.compatibility_force_safe_area_description,
                            ),
                            checked = presentation.isForceSafeAreaEnabled,
                            enabled = true,
                            onCheckedChange = onForceSafeAreaChange,
                            modifier = Modifier.testTag(
                                BrowserMainMenuTestTags.ForceSafeArea,
                            ),
                            shape = innerCorners,
                        )
                    }
                }
                BrowserMenuToggleItem(
                    label = stringResource(R.string.action_desktop_view),
                    supportingText = stringResource(R.string.action_desktop_view_description),
                    checked = presentation.isDesktopView,
                    enabled = presentation.canToggleDesktopView,
                    onCheckedChange = onDesktopViewChange,
                    modifier = Modifier.testTag(BrowserMainMenuTestTags.DesktopView),
                    shape = innerCorners,
                )
                DomainMuteMenuItem(
                    enabled = presentation.canToggleDomainMute,
                    muted = presentation.isDomainMuted,
                    onMutedChange = onDomainMutedChange,
                    shape = lastItemShape,
                )
            }

            if (userScriptMenuCommands.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.browser_menu_toppings_group),
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    modifier = Modifier.testTag(BrowserMainMenuTestTags.ToppingsGroup),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    userScriptMenuCommands.forEachIndexed { index, command ->
                        val shape = when {
                            userScriptMenuCommands.size == 1 -> outerCorners
                            index == 0 -> firstItemShape
                            index == userScriptMenuCommands.lastIndex -> lastItemShape
                            else -> innerCorners
                        }
                        MenuRow(
                            label = command.caption,
                            iconRes = R.drawable.ic_symbol_extension,
                            shape = shape,
                            supportingText = command.scriptName,
                            modifier = Modifier.testTag(
                                BrowserMainMenuTestTags.userScriptCommand(command.commandId),
                            ),
                            onClick = { dismissThen { onUserScriptMenuCommand(command) } },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.testTag(BrowserMainMenuTestTags.CandyGroup),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MenuRow(
                    label = stringResource(R.string.action_open_candy_trail),
                    iconRes = R.drawable.ic_symbol_route,
                    enabled = presentation.canUsePageActions,
                    shape = firstItemShape,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    onClick = { dismissThen(onOpenCandyTrail) },
                )
                MenuRow(
                    label = stringResource(R.string.action_add_site_capsule),
                    iconRes = R.drawable.ic_symbol_add_to_home_screen,
                    enabled = presentation.canAddSiteCapsule,
                    shape = innerCorners,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    onClick = { dismissThen(onAddSiteCapsule) },
                )
                MenuRow(
                    label = stringResource(R.string.action_summarize),
                    iconRes = R.drawable.ic_symbol_auto_awesome,
                    enabled = presentation.canUsePageActions,
                    shape = innerCorners,
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    onClick = { dismissThen(onSummarize) },
                )
                MenuRow(
                    label = stringResource(R.string.action_snooze_tab),
                    iconRes = R.drawable.ic_snooze,
                    enabled = presentation.canSnooze,
                    shape = lastItemShape,
                    modifier = Modifier.testTag(BrowserMainMenuTestTags.Snooze),
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    supportingText = if (presentation.canSnooze) {
                        null
                    } else {
                        stringResource(R.string.snooze_unavailable_private)
                    },
                    onClick = { dismissThen(onSnooze) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.browser_menu_browser_group),
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Column(
                modifier = Modifier.testTag(BrowserMainMenuTestTags.BrowserGroup),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (
                    canDockAddressBar &&
                    AddressBarAction.ParkRight !in overflowAddressBarActions
                ) {
                    MenuRow(
                        label = stringResource(R.string.action_dock_address_bar),
                        iconRes = R.drawable.ic_symbol_chevron_right,
                        shape = firstItemShape,
                        modifier = Modifier.testTag(BrowserMainMenuTestTags.DockAddressBar),
                        onClick = { dismissThen(onDockAddressBar) },
                    )
                }
                MenuRow(
                    label = stringResource(R.string.snoozed_tabs_title),
                    iconRes = R.drawable.ic_snooze,
                    shape = if (
                        canDockAddressBar &&
                        AddressBarAction.ParkRight !in overflowAddressBarActions
                    ) {
                        innerCorners
                    } else {
                        firstItemShape
                    },
                    modifier = Modifier.testTag(BrowserMainMenuTestTags.SnoozedTabs),
                    supportingText = if (snoozedTabCount == 0) {
                        stringResource(R.string.snoozed_tabs_settings_summary)
                    } else {
                        pluralStringResource(
                            R.plurals.snoozed_tabs_settings_count,
                            snoozedTabCount,
                            snoozedTabCount,
                        )
                    },
                    onClick = { dismissThen(onSnoozedTabs) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                MenuRow(
                    label = stringResource(R.string.action_history),
                    iconRes = R.drawable.ic_history,
                    shape = innerCorners,
                    modifier = Modifier.testTag(BrowserMainMenuTestTags.History),
                    onClick = { dismissThen(onHistory) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                MenuRow(
                    label = stringResource(R.string.action_settings),
                    iconRes = R.drawable.ic_symbol_settings,
                    shape = lastItemShape,
                    modifier = Modifier.testTag(BrowserMainMenuTestTags.Settings),
                    onClick = { dismissThen(onSettings) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
            }
        }
    }
}

@Composable
private fun BrowserMainMenuFavoriteAction(
    presentation: BrowserMainMenuPresentation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuToolbarAction(
        label = stringResource(R.string.action_favorite),
        iconRes = if (presentation.isFavorite) {
            R.drawable.ic_symbol_favorite_filled
        } else {
            R.drawable.ic_symbol_favorite
        },
        accessibilityLabel = stringResource(
            if (presentation.isFavorite) {
                R.string.action_remove_favorite
            } else {
                R.string.action_add_favorite
            },
        ),
        enabled = presentation.canToggleFavorite,
        selected = presentation.isFavorite,
        onClick = onClick,
        modifier = modifier.testTag(BrowserMainMenuTestTags.Favorite),
    )
}

@Composable
private fun BrowserMainMenuPinAction(
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MenuToolbarAction(
        label = stringResource(
            if (isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
        ),
        iconRes = R.drawable.ic_push_pin,
        accessibilityLabel = stringResource(
            if (isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
        ),
        selected = isPinned,
        onClick = onClick,
        modifier = modifier.testTag(BrowserMainMenuTestTags.Pin),
    )
}

@Composable
internal fun TabActionsMenuContent(
    pageSubtitle: String,
    canToggleFavorite: Boolean,
    isFavorite: Boolean,
    isPinned: Boolean,
    canUsePageActions: Boolean,
    canToggleDomainMute: Boolean,
    isDomainMuted: Boolean,
    canAddSiteCapsule: Boolean,
    canSnooze: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onPrint: () -> Unit,
    onDomainMutedChange: (Boolean) -> Unit,
    onOpenCandyTrail: () -> Unit,
    onAddSiteCapsule: () -> Unit,
    onSummarize: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
    compactToolbar: Boolean = false,
    profileContent: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val outerCorners = MaterialTheme.shapes.medium
    val innerCorners = MaterialTheme.shapes.extraSmall
    val firstItemShape = RoundedCornerShape(
        topStart = outerCorners.topStart,
        topEnd = outerCorners.topEnd,
        bottomEnd = innerCorners.bottomEnd,
        bottomStart = innerCorners.bottomStart,
    )
    val lastItemShape = RoundedCornerShape(
        topStart = innerCorners.topStart,
        topEnd = innerCorners.topEnd,
        bottomEnd = outerCorners.bottomEnd,
        bottomStart = outerCorners.bottomStart,
    )
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.tab_actions_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = pageSubtitle,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TabActionsMenuTestTags.Toolbar),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MenuToolbarAction(
                label = stringResource(R.string.action_favorite),
                iconRes = if (isFavorite) {
                    R.drawable.ic_symbol_favorite_filled
                } else {
                    R.drawable.ic_symbol_favorite
                },
                accessibilityLabel = stringResource(
                    if (isFavorite) R.string.action_remove_favorite
                    else R.string.action_add_favorite,
                ),
                enabled = canToggleFavorite,
                selected = isFavorite,
                horizontalContent = !compactToolbar,
                onClick = onToggleFavorite,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TabActionsMenuTestTags.Favorite),
            )
            MenuToolbarAction(
                label = stringResource(
                    if (isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
                ),
                iconRes = R.drawable.ic_push_pin,
                accessibilityLabel = stringResource(
                    if (isPinned) R.string.action_remove_pin else R.string.action_pin_tab,
                ),
                selected = isPinned,
                horizontalContent = !compactToolbar,
                onClick = onTogglePinned,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TabActionsMenuTestTags.Pin),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.browser_menu_page_group),
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.testTag(TabActionsMenuTestTags.PageGroup),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(
                label = stringResource(R.string.action_share),
                iconRes = R.drawable.ic_symbol_share,
                enabled = canUsePageActions,
                shape = firstItemShape,
                onClick = onShare,
            )
            MenuRow(
                label = stringResource(R.string.action_open_in_app),
                iconRes = R.drawable.ic_symbol_open_in_new,
                enabled = canUsePageActions,
                shape = innerCorners,
                onClick = onOpenExternal,
            )
            MenuRow(
                label = stringResource(R.string.action_print),
                iconRes = R.drawable.ic_symbol_print,
                enabled = canUsePageActions,
                shape = innerCorners,
                onClick = onPrint,
            )
            DomainMuteMenuItem(
                enabled = canToggleDomainMute,
                muted = isDomainMuted,
                onMutedChange = onDomainMutedChange,
                shape = lastItemShape,
            )
        }

        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.testTag(TabActionsMenuTestTags.CandyGroup),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MenuRow(
                label = stringResource(R.string.action_open_candy_trail),
                iconRes = R.drawable.ic_symbol_route,
                enabled = canUsePageActions,
                shape = firstItemShape,
                modifier = Modifier.testTag(TabActionsMenuTestTags.Trail),
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                onClick = onOpenCandyTrail,
            )
            MenuRow(
                label = stringResource(R.string.action_add_site_capsule),
                iconRes = R.drawable.ic_symbol_add_to_home_screen,
                enabled = canAddSiteCapsule,
                shape = innerCorners,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                onClick = onAddSiteCapsule,
            )
            MenuRow(
                label = stringResource(R.string.action_summarize),
                iconRes = R.drawable.ic_symbol_auto_awesome,
                enabled = canUsePageActions,
                shape = innerCorners,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                onClick = onSummarize,
            )
            MenuRow(
                label = stringResource(R.string.action_snooze_tab),
                iconRes = R.drawable.ic_snooze,
                enabled = canSnooze,
                shape = lastItemShape,
                modifier = Modifier.testTag(SnoozeTestTags.TabActionsSnooze),
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                supportingText = if (canSnooze) {
                    null
                } else {
                    stringResource(R.string.snooze_unavailable_private)
                },
                onClick = onSnooze,
            )
        }
        profileContent()
    }
}

@Composable
internal fun MenuToolbarAction(
    label: String,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accessibilityLabel: String? = null,
    horizontalContent: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (selected) colors.primaryContainer else colors.surfaceContainerHighest
    val accessibilityModifier = if (accessibilityLabel != null) {
        Modifier.semantics(mergeDescendants = true) {
            this.selected = selected
            contentDescription = accessibilityLabel
        }
    } else {
        Modifier
    }
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 64.dp)
            .then(accessibilityModifier),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(containerColor),
        contentColor = contentColor,
    ) {
        if (horizontalContent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
internal fun MenuRow(
    label: String,
    @DrawableRes iconRes: Int,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        enabled = enabled,
        shape = shape,
        color = browserChromeColor(containerColor, frostedAlpha = 0.68f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f),
                    )
                }
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                trailingContent()
            }
        }
    }
}

@Composable
internal fun BrowserMenuToggleItem(
    label: String,
    supportingText: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        shape = shape,
        color = browserChromeColor(colors.surfaceContainer),
        contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = supportingText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
                enabled = enabled,
            )
        }
    }
}

@Composable
internal fun DomainMuteMenuItem(
    enabled: Boolean,
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(DomainMuteMenuTestTags.Item)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = muted,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onMutedChange,
            ),
        shape = shape,
        color = browserChromeColor(colors.surfaceContainer),
        contentColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_symbol_volume_off),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.action_mute_domain),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = muted,
                onCheckedChange = null,
                modifier = Modifier.clearAndSetSemantics {},
                enabled = enabled,
            )
        }
    }
}

internal object BrowserMainMenuTestTags {
    const val Menu = "browser_main_menu"
    const val Toolbar = "browser_main_menu_toolbar"
    const val Favorite = "browser_main_menu_favorite"
    const val Pin = "browser_main_menu_pin"
    const val PageGroup = "browser_main_menu_page_group"
    const val Translate = "browser_main_menu_translate"
    const val CandyGroup = "browser_main_menu_candy_group"
    const val ToppingsGroup = "browser_main_menu_toppings_group"
    const val BrowserGroup = "browser_main_menu_browser_group"
    const val History = "browser_main_menu_history"
    const val Settings = "browser_main_menu_settings"
    const val Snooze = "browser_main_menu_snooze"
    const val SnoozedTabs = "browser_main_menu_snoozed_tabs"
    const val DockAddressBar = "browser_main_menu_dock_address_bar"
    const val CookieBannerRemoval = "browser_main_menu_cookie_banner_removal"
    const val ForceVerticalScrolling = "browser_main_menu_force_vertical_scrolling"
    const val DesktopView = "browser_main_menu_desktop_view"
    const val FindInPage = "browser_main_menu_find_in_page"
    const val ForcePageZooming = "browser_main_menu_force_page_zooming"
    const val ForceSafeArea = "browser_main_menu_force_safe_area"

    fun userScriptCommand(commandId: String): String =
        "browser_main_menu_topping_command_$commandId"
}

internal object TabActionsMenuTestTags {
    const val Toolbar = "tab_actions_menu_toolbar"
    const val Favorite = "tab_actions_menu_favorite"
    const val Pin = "tab_actions_menu_pin"
    const val PageGroup = "tab_actions_menu_page_group"
    const val CandyGroup = "tab_actions_menu_candy_group"
    const val Trail = "tab_actions_menu_trail"
}

internal object DomainMuteMenuTestTags {
    const val Item = "domain_mute_menu_item"
}

private const val BROWSER_MAIN_MENU_MAX_HEIGHT_FRACTION = 0.8f
