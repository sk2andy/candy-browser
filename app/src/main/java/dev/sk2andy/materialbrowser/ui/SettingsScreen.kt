package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState

internal enum class SettingsDestination {
    Home,
    Search,
    TabsAndGestures,
    AddressBarActions,
    Appearance,
    Browser,
    Downloads,
    Userscripts,
    ToppingCatalog,
    SiteCapsules,
    Sync,
    ProtectionAndData,
    AboutLegal,
}

@Composable
internal fun SettingsScreen(
    destination: SettingsDestination,
    appearanceSettings: AppearanceSettings,
    downloadSettings: BrowserDownloadSettings,
    externalDownloadManagers: List<ExternalDownloadManagerApp>,
    blockerSettings: BlockerSettings,
    inactiveTabLifetime: InactiveTabLifetime,
    residentTabLimit: Int,
    searchEngine: SearchEngine,
    pageTranslationProvider: PageTranslationProvider,
    searxngSettings: SearxngSettings,
    isAiModeToggleVisible: Boolean,
    searchSuggestionProvider: SearchSuggestionProvider,
    isHistorySuggestionsEnabled: Boolean,
    isRecallEnabled: Boolean,
    tabOverviewMode: TabOverviewMode,
    tabListStartsAtBottom: Boolean,
    automaticTabSortingEnabled: Boolean,
    dismissResistancePercent: Int,
    profilesEnabled: Boolean,
    profiles: List<BrowserProfile>,
    activeProfileId: String,
    tabCount: Int,
    addressBarActionLayout: AddressBarActionLayout,
    isAddressBarDockingEnabled: Boolean,
    isExternalLinkPreviewEnabled: Boolean = false,
    isFullImmersiveModeEnabled: Boolean,
    isStartupAnimationEnabled: Boolean,
    isOpenHomeOnStartupEnabled: Boolean = false,
    isScrollBarEnabled: Boolean,
    isVideoAutoplayBlocked: Boolean,
    isVideoAutoplayBlockingSupported: Boolean,
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
    onAppearanceSettingsChanged: (AppearanceSettings) -> Unit,
    onDownloadSettingsChanged: (BrowserDownloadSettings) -> Unit,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onResidentTabLimitChanged: (Int) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onPageTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    onSearxngSettingsChanged: (SearxngSettings) -> Unit,
    onAiModeToggleVisibleChanged: (Boolean) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onHistorySuggestionsEnabledChanged: (Boolean) -> Unit,
    onRecallEnabledChanged: (Boolean) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onTabListStartsAtBottomChanged: (Boolean) -> Unit,
    onAutomaticTabSortingEnabledChanged: (Boolean) -> Unit,
    onDismissResistancePercentChanged: (Int) -> Unit,
    onProfilesEnabledChanged: (Boolean) -> Unit,
    onAddressBarActionLayoutChanged: (AddressBarActionLayout) -> Unit,
    onAddressBarDockingEnabledChanged: (Boolean) -> Unit,
    onExternalLinkPreviewEnabledChanged: (Boolean) -> Unit = {},
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
    onStartupAnimationEnabledChanged: (Boolean) -> Unit,
    onOpenHomeOnStartupEnabledChanged: (Boolean) -> Unit = {},
    onScrollBarEnabledChanged: (Boolean) -> Unit,
    onVideoAutoplayBlockedChanged: (Boolean) -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit,
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onEditCapsule: (SiteCapsule) -> Unit,
    onDeleteCapsule: (SiteCapsule) -> Unit,
    onToggleUserScript: (id: String, enabled: Boolean, onResult: (String?) -> Unit) -> Unit,
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
    onDismiss: () -> Unit,
    onOpenFirefoxExtensions: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedContent(
            targetState = destination,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (
                    targetState == SettingsDestination.Home ||
                    initialState == SettingsDestination.ToppingCatalog &&
                    targetState == SettingsDestination.Userscripts ||
                    initialState == SettingsDestination.AddressBarActions &&
                    targetState == SettingsDestination.TabsAndGestures
                ) {
                    (slideInHorizontally { width -> -width / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> width } + fadeOut())
                } else {
                    (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                        (slideOutHorizontally { width -> -width / 3 } + fadeOut())
                }
            },
            label = "Settings destination",
        ) { currentDestination ->
            when (currentDestination) {
                SettingsDestination.Home -> SettingsHomePage(
                    downloadSummary = downloadSettings.displayName(externalDownloadManagers),
                    onDestinationChanged = onDestinationChanged,
                    onDismiss = onDismiss,
                    onOpenFirefoxExtensions = onOpenFirefoxExtensions,
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
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.TabsAndGestures -> TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = inactiveTabLifetime,
                    residentTabLimit = residentTabLimit,
                    tabOverviewMode = tabOverviewMode,
                    tabListStartsAtBottom = tabListStartsAtBottom,
                    automaticTabSortingEnabled = automaticTabSortingEnabled,
                    dismissResistancePercent = dismissResistancePercent,
                    profilesEnabled = profilesEnabled,
                    isAddressBarDockingEnabled = isAddressBarDockingEnabled,
                    onInactiveTabLifetimeChanged = onInactiveTabLifetimeChanged,
                    onResidentTabLimitChanged = onResidentTabLimitChanged,
                    onTabOverviewModeChanged = onTabOverviewModeChanged,
                    onTabListStartsAtBottomChanged = onTabListStartsAtBottomChanged,
                    onAutomaticTabSortingEnabledChanged =
                        onAutomaticTabSortingEnabledChanged,
                    onDismissResistancePercentChanged = onDismissResistancePercentChanged,
                    onProfilesEnabledChanged = onProfilesEnabledChanged,
                    onAddressBarDockingEnabledChanged = onAddressBarDockingEnabledChanged,
                    onAddressBarActions = {
                        onDestinationChanged(SettingsDestination.AddressBarActions)
                    },
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.AddressBarActions -> {
                    val actionLabels = AddressBarAction.entries.associateWith { action ->
                        stringResource(action.labelRes())
                    }
                    AddressBarActionEditorPage(
                        layout = addressBarActionLayout,
                        tabCount = tabCount,
                        onLayoutChanged = onAddressBarActionLayoutChanged,
                        onBack = {
                            onDestinationChanged(SettingsDestination.TabsAndGestures)
                        },
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

                SettingsDestination.Appearance -> AppearanceSettingsPage(
                    settings = appearanceSettings,
                    onSettingsChanged = onAppearanceSettingsChanged,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.Browser -> BrowserSettingsPage(
                    pageTranslationProvider = pageTranslationProvider,
                    isExternalLinkPreviewEnabled = isExternalLinkPreviewEnabled,
                    isFullImmersiveModeEnabled = isFullImmersiveModeEnabled,
                    isStartupAnimationEnabled = isStartupAnimationEnabled,
                    isOpenHomeOnStartupEnabled = isOpenHomeOnStartupEnabled,
                    isScrollBarEnabled = isScrollBarEnabled,
                    isVideoAutoplayBlocked = isVideoAutoplayBlocked,
                    isVideoAutoplayBlockingSupported = isVideoAutoplayBlockingSupported,
                    isDefaultBrowser = isDefaultBrowser,
                    onExternalLinkPreviewEnabledChanged =
                        onExternalLinkPreviewEnabledChanged,
                    onFullImmersiveModeEnabledChanged = onFullImmersiveModeEnabledChanged,
                    onStartupAnimationEnabledChanged = onStartupAnimationEnabledChanged,
                    onOpenHomeOnStartupEnabledChanged =
                        onOpenHomeOnStartupEnabledChanged,
                    onScrollBarEnabledChanged = onScrollBarEnabledChanged,
                    onVideoAutoplayBlockedChanged = onVideoAutoplayBlockedChanged,
                    onPageTranslationProviderChanged = onPageTranslationProviderChanged,
                    onOpenDefaultBrowserSettings = onOpenDefaultBrowserSettings,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.Downloads -> DownloadsSettingsPage(
                    settings = downloadSettings,
                    externalManagers = externalDownloadManagers,
                    onSettingsChanged = onDownloadSettingsChanged,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.Userscripts -> UserscriptManagementScreen(
                    scripts = userScripts,
                    isRuntimeSupported = isUserScriptSupported,
                    onToggle = onToggleUserScript,
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
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.Sync -> SyncSettingsPage(
                    state = syncState,
                    iconCatalog = syncIconCatalog,
                    localProfiles = profiles.filterNot(BrowserProfile::isSynced),
                    activeProfileId = activeProfileId,
                    onConfigure = onConfigureSync,
                    onEnroll = onEnrollSync,
                    onRefresh = onRefreshSync,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.ProtectionAndData -> ProtectionAndDataSettingsPage(
                    blockerSettings = blockerSettings,
                    blockedCount = blockedCount,
                    isRecallEnabled = isRecallEnabled,
                    trustsUserCertificates = trustsUserCertificates,
                    onBlockerSettingsChanged = onBlockerSettingsChanged,
                    onRecallEnabledChanged = onRecallEnabledChanged,
                    onPrivacyXRay = onPrivacyXRay,
                    onPermissionRadar = onPermissionRadar,
                    onFilterStudio = onFilterStudio,
                    onExportAppData = onExportAppData,
                    onImportAppData = onImportAppData,
                    onClearData = onClearData,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.AboutLegal -> SettingsPage(
                    title = stringResource(R.string.settings_section_about_legal),
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
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
