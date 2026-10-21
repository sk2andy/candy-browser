package dev.sk2andy.materialbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.shared.ui.settings.AppearanceSettingsSection
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoLoggingStatus
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsRules
import dev.sk2andy.materialbrowser.browser.DnsOverHttpsSettings
import dev.sk2andy.materialbrowser.browser.HttpsOnlyMode
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingRule
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.browser.FavoriteAnimationSpeed
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerSeekSettings
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.PrivacySignalSettings
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.browser.StatusBarScrollRules
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.LinkPeekSwipeAction
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.LocalSettingsSearchTarget
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.browser.LinkPeekAction
import dev.sk2andy.materialbrowser.browser.LinkPeekActionLayout
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.topping.ToppingFrameScope
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuBottomSheetContentLayout
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.shared.browser.AddressBarLongPressAction
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState
import java.util.Locale

@Composable
internal fun SettingsScreen(
    appLanguageTag: String = "",
    supportedAppLocales: List<Locale> = emptyList(),
    onAppLanguageChanged: (String) -> Unit = {},
    destination: SettingsDestination,
    browserEngineKind: AndroidBrowserEngineKind = AndroidBrowserEngineKind.GeckoView,
    isDnsOverHttpsSupported: Boolean = browserEngineKind == AndroidBrowserEngineKind.GeckoView,
    isHttpsOnlySupported: Boolean = browserEngineKind == AndroidBrowserEngineKind.GeckoView,
    appearanceSettings: AppearanceSettings,
    downloadSettings: BrowserDownloadSettings,
    externalDownloadManagers: List<ExternalDownloadManagerApp>,
    blockerSettings: BlockerSettings,
    webRtcProtectionMode: WebRtcProtectionMode = WebRtcProtectionMode.Default,
    privacySignalSettings: PrivacySignalSettings = PrivacySignalSettings.Default,
    isAutoDeAmpEnabled: Boolean = true,
    httpsOnlyMode: HttpsOnlyMode = HttpsOnlyMode.Default,
    dnsOverHttpsSettings: DnsOverHttpsSettings = DnsOverHttpsRules.Default,
    inactiveTabLifetime: InactiveTabLifetime,
    residentTabLimit: Int,
    searchEngine: SearchEngine,
    pageTranslationProvider: PageTranslationProvider,
    addressBarLongPressAction: AddressBarLongPressAction = AddressBarLongPressAction.Default,
    linkLongPressAction: LinkLongPressAction = LinkLongPressAction.LinkPeek,
    linkPeekSwipeAction: LinkPeekSwipeAction = LinkPeekSwipeAction.OpenBackground,
    linkPeekActionLayout: LinkPeekActionLayout = LinkPeekActionLayout.Default,
    searxngSettings: SearxngSettings,
    isAiModeToggleVisible: Boolean,
    searchSuggestionProvider: SearchSuggestionProvider,
    isHistorySuggestionsEnabled: Boolean,
    isFormDraftSavingEnabled: Boolean = false,
    isRecallEnabled: Boolean,
    historyRecordingMode: HistoryRecordingMode,
    tabOverviewMode: TabOverviewMode,
    tabStackFolderMode: TabOverviewMode,
    tabListStartsAtBottom: Boolean,
    automaticTabSortingEnabled: Boolean,
    isClosedTabUndoEnabled: Boolean = false,
    dismissResistancePercent: Int,
    profilesEnabled: Boolean,
    profiles: List<BrowserProfile>,
    activeProfileId: String,
    tabCount: Int,
    addressBarActionLayout: AddressBarActionLayout,
    browserMenuLayout: BrowserMenuLayout = BrowserMenuLayout.Default,
    isStatusBarScrollEnabled: Boolean = false,
    statusBarScrollTargetWidthPercent: Int = StatusBarScrollRules.DEFAULT_TARGET_WIDTH_PERCENT,
    isAddressBarDockingEnabled: Boolean,
    isAddressBarSwipeModeEnabled: Boolean = false,
    isExternalLinkPreviewEnabled: Boolean = false,
    externalLinkPreviewProfileId: String? = null,
    externalLinkPreviewProfiles: List<BrowserProfile> = emptyList(),
    externalAppLinkHandling: ExternalAppLinkHandling = ExternalAppLinkHandling.Default,
    externalAppSiteExceptions: List<String> = emptyList(),
    isFullImmersiveModeEnabled: Boolean,
    isStartupAnimationEnabled: Boolean,
    startupAddressFocusMode: StartupAddressFocusMode = StartupAddressFocusMode.Default,
    isHttpPasswordAutofillEnabled: Boolean = false,
    isHttpPasswordAutofillSupported: Boolean = false,
    isFavoriteLaunchAnimationEnabled: Boolean = true,
    favoriteAnimationSpeed: FavoriteAnimationSpeed = FavoriteAnimationSpeed.Default,
    isOpenHomeOnStartupEnabled: Boolean = false,
    isScrollBarEnabled: Boolean,
    isVideoAutoplayBlocked: Boolean,
    isVideoAutoplayBlockingSupported: Boolean,
    inlineMediaPlayerMode: InlineMediaPlayerMode = InlineMediaPlayerMode.Default,
    inlineMediaPlayerSeekSettings: InlineMediaPlayerSeekSettings = InlineMediaPlayerSeekSettings(),
    isInlineMediaPlayerSupported: Boolean = true,
    developerSettings: DeveloperSettings = DeveloperSettings(),
    isDeveloperOptionsUnlocked: Boolean = false,
    isInputDiagnosticsEnabled: Boolean = false,
    isGeckoLoggingSupported: Boolean = false,
    geckoLoggingStatus: GeckoLoggingStatus = GeckoLoggingStatus.Disabled,
    trustsUserCertificates: Boolean = BuildConfig.TRUST_USER_CERTIFICATES,
    blockedCount: Int,
    isDefaultBrowser: Boolean,
    isUserScriptSupported: Boolean,
    siteCapsules: List<SiteCapsule>,
    userScripts: List<UserscriptUiItem>,
    toppingCatalogState: ToppingCatalogUiState,
    syncState: SyncRepositoryState,
    syncIconCatalog: SyncDeviceIconCatalog,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onBrowserEngineKindChanged: (AndroidBrowserEngineKind) -> Unit = {},
    onAppearanceSettingsChanged: (AppearanceSettings) -> Unit,
    onDownloadSettingsChanged: (BrowserDownloadSettings) -> Unit,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onWebRtcProtectionModeChanged: (WebRtcProtectionMode) -> Unit = {},
    onPrivacySignalSettingsChanged: (PrivacySignalSettings) -> Unit = {},
    onAutoDeAmpEnabledChanged: (Boolean) -> Unit = {},
    onHttpsOnlyModeChanged: (HttpsOnlyMode) -> Unit = {},
    onDnsOverHttpsSettingsChanged: (DnsOverHttpsSettings) -> Unit = {},
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onResidentTabLimitChanged: (Int) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onPageTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    onAddressBarLongPressActionChanged: (AddressBarLongPressAction) -> Unit = {},
    onLinkLongPressActionChanged: (LinkLongPressAction) -> Unit = {},
    onLinkPeekSwipeActionChanged: (LinkPeekSwipeAction) -> Unit = {},
    onLinkPeekActionLayoutChanged: (LinkPeekActionLayout) -> Unit = {},
    onSearxngSettingsChanged: (SearxngSettings) -> Unit,
    onAiModeToggleVisibleChanged: (Boolean) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onHistorySuggestionsEnabledChanged: (Boolean) -> Unit,
    onFormDraftSavingEnabledChanged: (Boolean) -> Unit = {},
    onRecallEnabledChanged: (Boolean) -> Unit,
    onHistoryRecordingModeChanged: (HistoryRecordingMode) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onTabStackFolderModeChanged: (TabOverviewMode) -> Unit,
    onTabListStartsAtBottomChanged: (Boolean) -> Unit,
    onAutomaticTabSortingEnabledChanged: (Boolean) -> Unit,
    onClosedTabUndoEnabledChanged: (Boolean) -> Unit = {},
    onDismissResistancePercentChanged: (Int) -> Unit,
    onProfilesEnabledChanged: (Boolean) -> Unit,
    onAddressBarActionLayoutChanged: (AddressBarActionLayout) -> Unit,
    onBrowserMenuLocationChanged: (BrowserMenuEntry, BrowserMenuLocation) -> Unit = { _, _ -> },
    onBrowserMenuPresentationChanged: (BrowserMenuPresentation) -> Unit = {},
    onBrowserMenuBottomSheetContentLayoutChanged: (BrowserMenuBottomSheetContentLayout) -> Unit = {},
    onBrowserMenuBottomSheetToolbarEntriesChanged: (List<BrowserMenuEntry?>) -> Unit = {},
    onStatusBarScrollEnabledChanged: (Boolean) -> Unit = {},
    onStatusBarScrollTargetWidthPercentChanged: (Int) -> Unit = {},
    onStatusBarScrollTargetWidthPreview: (widthPercent: Int, finished: Boolean) -> Unit = { _, _ -> },
    onOpenStatusBarScrollAccessibilitySettings: () -> Unit = {},
    onAddressBarDockingEnabledChanged: (Boolean) -> Unit,
    onAddressBarSwipeModeEnabledChanged: (Boolean) -> Unit = {},
    onExternalLinkPreviewEnabledChanged: (Boolean) -> Unit = {},
    onExternalLinkPreviewProfileChanged: (String?) -> Unit = {},
    onExternalAppLinkHandlingChanged: (ExternalAppLinkHandling) -> Unit = {},
    onResetExternalAppSiteException: (String) -> Unit = {},
    onResetAllExternalAppSiteExceptions: () -> Unit = {},
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
    onStartupAnimationEnabledChanged: (Boolean) -> Unit,
    onStartupAddressFocusModeChanged: (StartupAddressFocusMode) -> Unit = {},
    onHttpPasswordAutofillEnabledChanged: (Boolean) -> Unit = {},
    onFavoriteLaunchAnimationEnabledChanged: (Boolean) -> Unit = {},
    onFavoriteAnimationSpeedChanged: (FavoriteAnimationSpeed) -> Unit = {},
    onImportFavoriteBookmarks: () -> Unit = {},
    onOpenHomeOnStartupEnabledChanged: (Boolean) -> Unit = {},
    onScrollBarEnabledChanged: (Boolean) -> Unit,
    onVideoAutoplayBlockedChanged: (Boolean) -> Unit,
    onInlineMediaPlayerModeChanged: (InlineMediaPlayerMode) -> Unit = {},
    onInlineMediaPlayerSeekSettingsChanged: (InlineMediaPlayerSeekSettings) -> Unit = {},
    onDeveloperSettingsChanged: (DeveloperSettings) -> Unit = {},
    onInputDiagnosticsEnabledChanged: (Boolean) -> Unit = {},
    onCopyDeveloperDiagnostics: () -> Unit = {},
    onExportAppLogs: () -> Unit = {},
    onClearAppLogs: () -> Unit = {},
    onExportGeckoLogs: () -> Unit = {},
    onClearGeckoLogs: () -> Unit = {},
    onShowGestureOnboarding: () -> Unit = {},
    onShowReleaseNotes: () -> Unit = {},
    onUnlockDeveloperOptions: () -> Unit = {},
    onOpenDefaultBrowserSettings: () -> Unit,
    onOpenDeviceSearch: () -> Unit = {},
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onEditCapsule: (SiteCapsule) -> Unit,
    onDeleteCapsule: (SiteCapsule) -> Unit,
    onToggleUserScript: (id: String, enabled: Boolean, onResult: (String?) -> Unit) -> Unit,
    onSetUserScriptFrameScope: (
        id: String,
        scope: ToppingFrameScope,
        onResult: (String?) -> Unit,
    ) -> Unit = { _, _, onResult -> onResult(null) },
    onSaveUserScript: (id: String?, source: String, onResult: (String?) -> Unit) -> Unit,
    onDeleteUserScript: (id: String, onResult: (String?) -> Unit) -> Unit,
    onImportUserScript: () -> Unit,
    onToggleTopping: (id: String, enabled: Boolean) -> Unit,
    onUpdateTopping: (id: String) -> Unit,
    onRefreshToppingCatalog: () -> Unit,
    onConfigureSync: (SyncConnectionSettings) -> Boolean,
    onEnrollSync: (CharArray, CharArray, (SyncEnrollmentOutcome) -> Unit) -> Unit,
    onRefreshSync: () -> Unit,
    onFilterStudio: () -> Unit,
    onExportAppData: () -> Unit,
    onImportAppData: () -> Unit,
    onClearData: () -> Unit,
    onOpenLegalUrl: (String) -> Unit,
    onShareSyncExtensionLink: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)? = null,
    linkRoutingRules: List<LinkRoutingRule> = emptyList(),
    onLinkRoutingRulesChanged: (List<LinkRoutingRule>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchTarget by remember { mutableStateOf<SettingsSearchEntry?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clearSearch = {
        searchQuery = ""
        keyboard?.hide()
        focusManager.clearFocus()
    }
    LaunchedEffect(destination) {
        if (searchTarget?.destination != destination) searchTarget = null
    }
    BackHandler(enabled = destination == SettingsDestination.Home && searchQuery.isNotEmpty()) {
        clearSearch()
    }
    val searchEntries = settingsSearchEntries(
        SettingsSearchAvailability(
            isLinkPeekEnabled = linkLongPressAction == LinkLongPressAction.LinkPeek,
            searchSuggestionProvider = searchSuggestionProvider,
            hasAppLanguages = supportedAppLocales.isNotEmpty(),
            isGeckoEngine = browserEngineKind == AndroidBrowserEngineKind.GeckoView,
            hasBrowserEngineSelection = !BuildConfig.SYSTEM_WEBVIEW_ONLY,
            isDnsOverHttpsSupported = isDnsOverHttpsSupported,
            isHttpsOnlySupported = isHttpsOnlySupported,
            isVideoAutoplayBlockingSupported = isVideoAutoplayBlockingSupported,
            isInlineMediaPlayerSupported = isInlineMediaPlayerSupported,
            isForceDarkWebsitesSupported = browserEngineKind == AndroidBrowserEngineKind.SystemWebView,
            isDeveloperOptionsUnlocked = isDeveloperOptionsUnlocked,
            isHttpPasswordAutofillSupported = isHttpPasswordAutofillSupported,
            isGeckoLoggingSupported = isGeckoLoggingSupported,
            hasFirefoxExtensions = onOpenFirefoxExtensions != null,
        ),
    )
    val settingsParent: (SettingsDestination) -> SettingsDestination = { page ->
        if (searchTarget?.destination == page && searchQuery.isNotBlank()) {
            SettingsDestination.Home
        } else {
            SettingsBackRules.parentDestination(page)
        }
    }
    SettingsBackRouter(
        parentDestination = settingsParent(destination),
        destination = destination,
        onDestinationChanged = onDestinationChanged,
        modifier = modifier,
    ) { currentDestination ->
        CompositionLocalProvider(
            LocalSettingsSearchTarget provides searchTarget?.title.takeIf {
                searchTarget?.destination == currentDestination
            },
        ) {
            when (currentDestination) {
                SettingsDestination.Home -> SettingsHomePage(
                    downloadSummary = downloadSettings.displayName(externalDownloadManagers),
                    onDestinationChanged = onDestinationChanged,
                    onDismiss = {
                        if (searchQuery.isNotEmpty()) clearSearch() else onDismiss()
                    },
                    onOpenFirefoxExtensions = onOpenFirefoxExtensions,
                    developerOptionsUnlocked = isDeveloperOptionsUnlocked,
                    onUnlockDeveloperOptions = onUnlockDeveloperOptions,
                    searchQuery = searchQuery,
                    searchEntries = searchEntries,
                    onSearchQueryChanged = { searchQuery = it },
                    onSearchResultSelected = { entry ->
                        if (entry.destination == null) {
                            onOpenFirefoxExtensions?.invoke()
                        } else {
                            searchTarget = entry
                            onDestinationChanged(entry.destination)
                        }
                    },
                )

                SettingsDestination.Search -> SearchSettingsPage(
                    searchEngine = searchEngine,
                    searxngSettings = searxngSettings,
                    isAiModeToggleVisible = isAiModeToggleVisible,
                    searchSuggestionProvider = searchSuggestionProvider,
                    isHistorySuggestionsEnabled = isHistorySuggestionsEnabled,
                    onSearchEngineChanged = onSearchEngineChanged,
                    onSearxngSettingsChanged = onSearxngSettingsChanged,
                    onAiModeToggleVisibleChanged = onAiModeToggleVisibleChanged,
                    onSearchSuggestionProviderChanged = onSearchSuggestionProviderChanged,
                    onHistorySuggestionsEnabledChanged =
                        onHistorySuggestionsEnabledChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.TabsAndGestures,
                SettingsDestination.Tabs,
                SettingsDestination.TabLifecycle,
                SettingsDestination.ProfileOptions,
                SettingsDestination.Gestures,
                SettingsDestination.GestureLinks,
                SettingsDestination.GestureAddressBar,
                SettingsDestination.GestureControls,
                -> TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = inactiveTabLifetime,
                    residentTabLimit = residentTabLimit,
                    tabOverviewMode = tabOverviewMode,
                    tabStackFolderMode = tabStackFolderMode,
                    tabListStartsAtBottom = tabListStartsAtBottom,
                    automaticTabSortingEnabled = automaticTabSortingEnabled,
                    isClosedTabUndoEnabled = isClosedTabUndoEnabled,
                    dismissResistancePercent = dismissResistancePercent,
                    profilesEnabled = profilesEnabled,
                    isAddressBarDockingEnabled = isAddressBarDockingEnabled,
                    isAddressBarSwipeModeEnabled = isAddressBarSwipeModeEnabled,
                    addressBarLongPressAction = addressBarLongPressAction,
                    linkLongPressAction = linkLongPressAction,
                    linkPeekSwipeAction = linkPeekSwipeAction,
                    isStatusBarScrollEnabled = isStatusBarScrollEnabled,
                    statusBarScrollTargetWidthPercent = statusBarScrollTargetWidthPercent,
                    onInactiveTabLifetimeChanged = onInactiveTabLifetimeChanged,
                    onResidentTabLimitChanged = onResidentTabLimitChanged,
                    onTabOverviewModeChanged = onTabOverviewModeChanged,
                    onTabStackFolderModeChanged = onTabStackFolderModeChanged,
                    onTabListStartsAtBottomChanged = onTabListStartsAtBottomChanged,
                    onAutomaticTabSortingEnabledChanged =
                        onAutomaticTabSortingEnabledChanged,
                    onClosedTabUndoEnabledChanged = onClosedTabUndoEnabledChanged,
                    onDismissResistancePercentChanged = onDismissResistancePercentChanged,
                    onProfilesEnabledChanged = onProfilesEnabledChanged,
                    onAddressBarDockingEnabledChanged = onAddressBarDockingEnabledChanged,
                    onAddressBarSwipeModeEnabledChanged = onAddressBarSwipeModeEnabledChanged,
                    onAddressBarLongPressActions = {
                        onDestinationChanged(SettingsDestination.AddressBarLongPressActions)
                    },
                    onLinkLongPressActionChanged = onLinkLongPressActionChanged,
                    onLinkPeekSwipeActionChanged = onLinkPeekSwipeActionChanged,
                    onStatusBarScrollEnabledChanged = onStatusBarScrollEnabledChanged,
                    onStatusBarScrollTargetWidthPercentChanged = onStatusBarScrollTargetWidthPercentChanged,
                    onStatusBarScrollTargetWidthPreview = onStatusBarScrollTargetWidthPreview,
                    onOpenStatusBarScrollAccessibilitySettings = onOpenStatusBarScrollAccessibilitySettings,
                    onLinkPeekActions = {
                        onDestinationChanged(SettingsDestination.LinkPeekActions)
                    },
                    onAddressBarActions = {
                        onDestinationChanged(SettingsDestination.AddressBarActions)
                    },
                    onMenuActions = {
                        onDestinationChanged(SettingsDestination.MenuActions)
                    },
                    onTabOverview = { onDestinationChanged(SettingsDestination.TabOverview) },
                    onTabLifecycle = { onDestinationChanged(SettingsDestination.TabLifecycle) },
                    onProfileOptions = { onDestinationChanged(SettingsDestination.ProfileOptions) },
                    onLinkInteractions = { onDestinationChanged(SettingsDestination.GestureLinks) },
                    onAddressBarButtons = { onDestinationChanged(SettingsDestination.GestureAddressBar) },
                    onGestureControls = { onDestinationChanged(SettingsDestination.GestureControls) },
                    onTabStackFolder = { onDestinationChanged(SettingsDestination.TabStackFolder) },
                    onClosedTabUndo = { onDestinationChanged(SettingsDestination.ClosedTabUndo) },
                    onLinkLongPress = { onDestinationChanged(SettingsDestination.LinkLongPress) },
                    onLinkPeekSwipe = { onDestinationChanged(SettingsDestination.LinkPeekSwipe) },
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                    section = when (currentDestination) {
                        SettingsDestination.Tabs -> TabSettingsSection.Tabs
                        SettingsDestination.TabLifecycle -> TabSettingsSection.Lifecycle
                        SettingsDestination.ProfileOptions -> TabSettingsSection.Profiles
                        SettingsDestination.Gestures -> TabSettingsSection.Gestures
                        SettingsDestination.GestureLinks -> TabSettingsSection.LinkInteractions
                        SettingsDestination.GestureAddressBar -> TabSettingsSection.AddressBarButtons
                        SettingsDestination.GestureControls -> TabSettingsSection.GestureControls
                        else -> TabSettingsSection.All
                    },
                )

                SettingsDestination.AddressBarLongPressActions ->
                    AddressBarLongPressSettingsPage(
                        selectedAction = addressBarLongPressAction,
                        onActionSelected = onAddressBarLongPressActionChanged,
                        onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                    )

                SettingsDestination.AddressBarActions -> {
                    val actionLabels = AddressBarAction.entries.associateWith { action ->
                        stringResource(action.labelRes())
                    }
                    AddressBarActionEditorPage(
                        layout = addressBarActionLayout,
                        tabCount = tabCount,
                        onLayoutChanged = onAddressBarActionLayoutChanged,
                        onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                        backLabel = stringResource(R.string.action_back),
                        title = stringResource(R.string.settings_address_bar_actions_title),
                        instructions = stringResource(
                            R.string.settings_address_bar_actions_instructions,
                        ),
                        availableTitle = stringResource(
                            R.string.settings_address_bar_actions_available,
                        ),
                        beforeLabel = stringResource(R.string.settings_address_bar_actions_before),
                        afterLabel = stringResource(R.string.settings_address_bar_actions_after),
                        moreLabel = stringResource(R.string.cd_more_options),
                        fullMessage = stringResource(R.string.settings_address_bar_actions_full),
                        actionLabel = actionLabels::getValue,
                    )
                }

                SettingsDestination.MenuActions -> BrowserMenuSettingsPage(
                    layout = browserMenuLayout,
                    statusBarScrollEnabled = isStatusBarScrollEnabled,
                    onLocationChanged = onBrowserMenuLocationChanged,
                    onPresentationChanged = onBrowserMenuPresentationChanged,
                    onBottomSheetContentLayoutChanged = onBrowserMenuBottomSheetContentLayoutChanged,
                    onBottomSheetToolbarEntriesChanged = onBrowserMenuBottomSheetToolbarEntriesChanged,
                    onOpenPresentation = { onDestinationChanged(SettingsDestination.MenuStyle) },
                    onOpenContentLayout = { onDestinationChanged(SettingsDestination.MenuContentLayout) },
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.MenuStyle -> dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuStyleSettingsPage(
                    layout = browserMenuLayout,
                    resources = AndroidBrowserMenuSettingsResources,
                    backContentDescription = stringResource(R.string.action_back),
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                    onPresentationChanged = onBrowserMenuPresentationChanged,
                )

                SettingsDestination.MenuContentLayout -> dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuContentLayoutSettingsPage(
                    layout = browserMenuLayout,
                    resources = AndroidBrowserMenuSettingsResources,
                    backContentDescription = stringResource(R.string.action_back),
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                    onContentLayoutChanged = onBrowserMenuBottomSheetContentLayoutChanged,
                )

                SettingsDestination.TabOverview -> dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSelectionPage(
                    mode = tabOverviewMode,
                    strings = androidTabOverviewSettingsStrings(),
                    backContentDescription = stringResource(R.string.action_back),
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                    onModeChanged = onTabOverviewModeChanged,
                )

                SettingsDestination.TabStackFolder -> dev.sk2andy.materialbrowser.shared.ui.settings.TabOverviewSelectionPage(
                    mode = tabStackFolderMode,
                    strings = androidTabOverviewSettingsStrings().copy(
                        overviewMode = stringResource(R.string.settings_tab_stack_folder_mode),
                    ),
                    backContentDescription = stringResource(R.string.action_back),
                    onModeChanged = onTabStackFolderModeChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                    testTagPrefix = "tab_stack_folder_option",
                )
                SettingsDestination.ClosedTabUndo -> ClosedTabUndoSettingsPage(
                    enabled = isClosedTabUndoEnabled,
                    onEnabledChanged = onClosedTabUndoEnabledChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )
                SettingsDestination.LinkLongPress -> LinkLongPressSettingsPage(
                    action = linkLongPressAction,
                    onActionChanged = onLinkLongPressActionChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )
                SettingsDestination.LinkPeekSwipe -> LinkPeekSwipeSettingsPage(
                    action = linkPeekSwipeAction,
                    enabled = linkLongPressAction == LinkLongPressAction.LinkPeek,
                    onActionChanged = onLinkPeekSwipeActionChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )
                SettingsDestination.LinkPeekActions -> {
                    val actionLabels = LinkPeekAction.entries.associateWith { action ->
                        stringResource(action.labelRes())
                    }
                    LinkPeekActionEditorPage(
                        layout = linkPeekActionLayout,
                        onLayoutChanged = onLinkPeekActionLayoutChanged,
                        onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                        backLabel = stringResource(R.string.action_back),
                        title = stringResource(R.string.settings_link_peek_actions_title),
                        instructions = stringResource(
                            R.string.settings_link_peek_actions_instructions,
                        ),
                        availableTitle = stringResource(
                            R.string.settings_link_peek_actions_available,
                        ),
                        fixedPlusLabel = stringResource(R.string.action_open_in_new_tab),
                        emptySlotLabel = stringResource(
                            R.string.settings_link_peek_actions_empty_slot,
                        ),
                        moveToSlotLabel = stringResource(
                            R.string.settings_link_peek_actions_move_to_slot,
                        ),
                        moveToAvailableLabel = stringResource(
                            R.string.settings_link_peek_actions_move_to_available,
                        ),
                        actionLabel = actionLabels::getValue,
                    )
                }

                SettingsDestination.Appearance,
                SettingsDestination.AppearanceColors,
                SettingsDestination.AppearanceSurfaces,
                SettingsDestination.AppearanceAddressBar,
                SettingsDestination.AppearanceWebsites -> AppearanceSettingsPage(
                    section = when (currentDestination) {
                        SettingsDestination.AppearanceColors -> AppearanceSettingsSection.Colors
                        SettingsDestination.AppearanceSurfaces -> AppearanceSettingsSection.Surfaces
                        SettingsDestination.AppearanceAddressBar -> AppearanceSettingsSection.AddressBar
                        SettingsDestination.AppearanceWebsites -> AppearanceSettingsSection.Websites
                        else -> AppearanceSettingsSection.Theme
                    },
                    navigationContent = {
                        if (currentDestination == SettingsDestination.Appearance) {
                            AppearanceSubpageLinks(onDestinationChanged)
                        }
                    },
                    settings = appearanceSettings,
                    onSettingsChanged = onAppearanceSettingsChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                    forceDarkWebsitesAvailable =
                        browserEngineKind == AndroidBrowserEngineKind.SystemWebView,
                    browserEngineKind = browserEngineKind,
                )

                SettingsDestination.AppIcon -> SettingsPage(
                    title = stringResource(R.string.settings_app_icon),
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                ) {
                    SettingsPreferenceGroup {
                        AppIconSettings()
                    }
                }

                SettingsDestination.Browser,
                SettingsDestination.BrowserStartup,
                SettingsDestination.BrowserFavorites,
                SettingsDestination.BrowserWebsites,
                SettingsDestination.Translations,
                SettingsDestination.BrowserLinks -> BrowserSettingsPage(
                    section = when (currentDestination) {
                        SettingsDestination.BrowserStartup -> BrowserSettingsSection.Startup
                        SettingsDestination.BrowserFavorites -> BrowserSettingsSection.Favorites
                        SettingsDestination.BrowserWebsites -> BrowserSettingsSection.Websites
                        SettingsDestination.Translations -> BrowserSettingsSection.Translations
                        SettingsDestination.BrowserLinks -> BrowserSettingsSection.Links
                        else -> BrowserSettingsSection.Overview
                    },
                    navigationContent = {
                        if (currentDestination == SettingsDestination.Browser) {
                            BrowserSubpageLinks(onDestinationChanged)
                        }
                    },
                    appLanguageTag = appLanguageTag,
                    supportedAppLocales = supportedAppLocales,
                    onAppLanguageChanged = onAppLanguageChanged,
                    browserEngineKind = browserEngineKind,
                    pageTranslationProvider = pageTranslationProvider,
                    isExternalLinkPreviewEnabled = isExternalLinkPreviewEnabled,
                    externalLinkPreviewProfileId = externalLinkPreviewProfileId,
                    externalLinkPreviewProfiles = externalLinkPreviewProfiles,
                    externalAppLinkHandling = externalAppLinkHandling,
                    isFullImmersiveModeEnabled = isFullImmersiveModeEnabled,
                    isStartupAnimationEnabled = isStartupAnimationEnabled,
                    startupAddressFocusMode = startupAddressFocusMode,
                    isFavoriteLaunchAnimationEnabled = isFavoriteLaunchAnimationEnabled,
                    favoriteAnimationSpeed = favoriteAnimationSpeed,
                    isOpenHomeOnStartupEnabled = isOpenHomeOnStartupEnabled,
                    isScrollBarEnabled = isScrollBarEnabled,
                    isDefaultBrowser = isDefaultBrowser,
                    onBrowserEngineKindChanged = onBrowserEngineKindChanged,
                    onExternalLinkPreviewEnabledChanged =
                        onExternalLinkPreviewEnabledChanged,
                    onExternalLinkPreviewProfileChanged = onExternalLinkPreviewProfileChanged,
                    onLinkRouting = { onDestinationChanged(SettingsDestination.LinkRouting) },
                    onDeviceSearch = onOpenDeviceSearch,
                    onExternalLinkPreview = { onDestinationChanged(SettingsDestination.ExternalLinkPreview) },
                    onExternalAppLinks = { onDestinationChanged(SettingsDestination.ExternalAppLinks) },
                    onTranslations = { onDestinationChanged(SettingsDestination.Translations) },
                    onFullImmersiveModeEnabledChanged = onFullImmersiveModeEnabledChanged,
                    onStartupAnimationEnabledChanged = onStartupAnimationEnabledChanged,
                    onStartupAddressFocusModeChanged = onStartupAddressFocusModeChanged,
                    onFavoriteLaunchAnimationEnabledChanged =
                        onFavoriteLaunchAnimationEnabledChanged,
                    onFavoriteAnimationSpeedChanged = onFavoriteAnimationSpeedChanged,
                    onImportFavoriteBookmarks = onImportFavoriteBookmarks,
                    onOpenHomeOnStartupEnabledChanged =
                        onOpenHomeOnStartupEnabledChanged,
                    onScrollBarEnabledChanged = onScrollBarEnabledChanged,
                    onPageTranslationProviderChanged = onPageTranslationProviderChanged,
                    onOpenDefaultBrowserSettings = onOpenDefaultBrowserSettings,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.ReadAloud -> ReaderSpeechSettingsPage(
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.ExternalAppLinks -> ExternalAppLinksSettingsPage(
                    externalAppLinkHandling = externalAppLinkHandling,
                    siteExceptions = externalAppSiteExceptions,
                    onExternalAppLinkHandlingChanged = onExternalAppLinkHandlingChanged,
                    onResetSiteException = onResetExternalAppSiteException,
                    onResetAllSiteExceptions = onResetAllExternalAppSiteExceptions,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.ExternalLinkPreview -> ExternalLinkPreviewSettingsPage(
                    isExternalLinkPreviewEnabled = isExternalLinkPreviewEnabled,
                    externalLinkPreviewProfileId = externalLinkPreviewProfileId,
                    externalLinkPreviewProfiles = externalLinkPreviewProfiles,
                    onExternalLinkPreviewEnabledChanged = onExternalLinkPreviewEnabledChanged,
                    onExternalLinkPreviewProfileChanged = onExternalLinkPreviewProfileChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.LinkRouting -> LinkRoutingSettingsPage(
                    rules = linkRoutingRules,
                    profiles = profiles.filter { profilesEnabled || it.id == activeProfileId },
                    capsules = siteCapsules,
                    onRulesChanged = onLinkRoutingRulesChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.Player -> PlayerSettingsPage(
                    onPlayerMode = { onDestinationChanged(SettingsDestination.PlayerMode) },
                    isVideoAutoplayBlocked = isVideoAutoplayBlocked,
                    isVideoAutoplayBlockingSupported = isVideoAutoplayBlockingSupported,
                    inlineMediaPlayerMode = inlineMediaPlayerMode,
                    inlineMediaPlayerSeekSettings = inlineMediaPlayerSeekSettings,
                    isInlineMediaPlayerSupported = isInlineMediaPlayerSupported,
                    onVideoAutoplayBlockedChanged = onVideoAutoplayBlockedChanged,
                    onInlineMediaPlayerModeChanged = onInlineMediaPlayerModeChanged,
                    onInlineMediaPlayerSeekSettingsChanged = onInlineMediaPlayerSeekSettingsChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.PlayerMode -> PlayerModeSettingsPage(
                    mode = inlineMediaPlayerMode,
                    enabled = isInlineMediaPlayerSupported,
                    onModeChanged = onInlineMediaPlayerModeChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.Downloads -> DownloadsSettingsPage(
                    settings = downloadSettings,
                    externalManagers = externalDownloadManagers,
                    onSettingsChanged = onDownloadSettingsChanged,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.RssFeeds -> RssSettingsPage(
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.Userscripts -> UserscriptManagementScreen(
                    scripts = userScripts,
                    isRuntimeSupported = isUserScriptSupported,
                    onToggle = onToggleUserScript,
                    onSetFrameScope = onSetUserScriptFrameScope,
                    onSave = onSaveUserScript,
                    onDelete = onDeleteUserScript,
                    onImport = onImportUserScript,
                    onDiscover = { onDestinationChanged(SettingsDestination.ToppingCatalog) },
                    onDismiss = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.ToppingCatalog -> ToppingCatalogScreen(
                    state = toppingCatalogState,
                    onToggle = onToggleTopping,
                    onUpdate = onUpdateTopping,
                    onRetry = onRefreshToppingCatalog,
                    onDismiss = { onDestinationChanged(SettingsDestination.Userscripts) },
                )

                SettingsDestination.SiteCapsules -> SiteCapsulesSettingsPage(
                    siteCapsules = siteCapsules,
                    onEditCapsule = onEditCapsule,
                    onDeleteCapsule = onDeleteCapsule,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.Sync -> SyncSettingsPage(
                    state = syncState,
                    iconCatalog = syncIconCatalog,
                    localProfiles = profiles.filterNot(BrowserProfile::isSynced),
                    activeProfileId = activeProfileId,
                    onConfigure = onConfigureSync,
                    onEnroll = onEnrollSync,
                    onRefresh = onRefreshSync,
                    onOpenDocumentation = onOpenLegalUrl,
                    onShareExtensionLink = onShareSyncExtensionLink,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.ProtectionAndData,
                SettingsDestination.HistoryAndData,
                -> ProtectionAndDataSettingsPage(
                    blockerSettings = blockerSettings,
                    blockedCount = blockedCount,
                    browserEngineKind = browserEngineKind,
                    isDnsOverHttpsSupported = isDnsOverHttpsSupported,
                    isHttpsOnlySupported = isHttpsOnlySupported,
                    webRtcProtectionMode = webRtcProtectionMode,
                    privacySignalSettings = privacySignalSettings,
                    isAutoDeAmpEnabled = isAutoDeAmpEnabled,
                    httpsOnlyMode = httpsOnlyMode,
                    dnsOverHttpsSettings = dnsOverHttpsSettings,
                    isFormDraftSavingEnabled = isFormDraftSavingEnabled,
                    isRecallEnabled = isRecallEnabled,
                    historyRecordingMode = historyRecordingMode,
                    trustsUserCertificates = trustsUserCertificates,
                    onBlockerSettingsChanged = onBlockerSettingsChanged,
                    onWebRtcProtectionModeChanged = onWebRtcProtectionModeChanged,
                    onPrivacySignalSettingsChanged = onPrivacySignalSettingsChanged,
                    onAutoDeAmpEnabledChanged = onAutoDeAmpEnabledChanged,
                    onHttpsOnlyModeChanged = onHttpsOnlyModeChanged,
                    onDnsOverHttpsSettingsChanged = onDnsOverHttpsSettingsChanged,
                    onFormDraftSavingEnabledChanged = onFormDraftSavingEnabledChanged,
                    onRecallEnabledChanged = onRecallEnabledChanged,
                    onHistoryRecordingModeChanged = onHistoryRecordingModeChanged,
                    onPrivacyXRay = onPrivacyXRay,
                    onPermissionRadar = onPermissionRadar,
                    onFilterStudio = onFilterStudio,
                    onExportAppData = onExportAppData,
                    onImportAppData = onImportAppData,
                    onClearData = onClearData,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                    section = when (currentDestination) {
                        SettingsDestination.ProtectionAndData ->
                            ProtectionSettingsSection.Protection
                        SettingsDestination.HistoryAndData ->
                            ProtectionSettingsSection.HistoryAndData
                        else -> ProtectionSettingsSection.All
                    },
                )

                SettingsDestination.DeveloperOptions -> DeveloperOptionsSettingsPage(
                    settings = developerSettings,
                    isHttpPasswordAutofillEnabled = isHttpPasswordAutofillEnabled,
                    isHttpPasswordAutofillSupported = isHttpPasswordAutofillSupported,
                    isInputDiagnosticsEnabled = isInputDiagnosticsEnabled,
                    isGeckoLoggingSupported = isGeckoLoggingSupported,
                    geckoLoggingStatus = geckoLoggingStatus,
                    onSettingsChanged = onDeveloperSettingsChanged,
                    onHttpPasswordAutofillEnabledChanged =
                        onHttpPasswordAutofillEnabledChanged,
                    onInputDiagnosticsEnabledChanged = onInputDiagnosticsEnabledChanged,
                    onCopyDiagnostics = onCopyDeveloperDiagnostics,
                    onExportLogs = onExportAppLogs,
                    onClearLogs = onClearAppLogs,
                    onExportGeckoLogs = onExportGeckoLogs,
                    onClearGeckoLogs = onClearGeckoLogs,
                    onShowOnboarding = onShowGestureOnboarding,
                    onShowReleaseNotes = onShowReleaseNotes,
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                )

                SettingsDestination.AboutLegal -> SettingsPage(
                    title = stringResource(R.string.settings_section_about_legal),
                    onBack = { onDestinationChanged(settingsParent(currentDestination)) },
                ) {
                    AboutLegalSection(
                        onOpenUrl = onOpenLegalUrl,
                        showTitle = false,
                    )
                }
            }
        }
    }
}

private fun LinkPeekAction.labelRes(): Int = when (this) {
    LinkPeekAction.ReaderLater -> R.string.reader_save_offline
    LinkPeekAction.OpenPrivate -> R.string.action_open_link_in_private_tab
    LinkPeekAction.Copy -> R.string.external_link_preview_copy_link
    LinkPeekAction.Share -> R.string.action_share
    LinkPeekAction.Favorite -> R.string.action_favorite
    LinkPeekAction.Snooze -> R.string.action_snooze_tab
    LinkPeekAction.OpenForeground -> R.string.action_open_in_new_tab_and_switch
}
