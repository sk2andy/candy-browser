package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.actions.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.data.AddressBarActionLayout
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.LinkPeekAction
import dev.sk2andy.materialbrowser.data.LinkPeekActionLayout
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsRouter
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncRepositoryState

@Composable
internal fun SettingsScreen(
    destination: SettingsDestination,
    browserEngineKind: AndroidBrowserEngineKind = AndroidBrowserEngineKind.GeckoView,
    appearanceSettings: AppearanceSettings,
    downloadSettings: BrowserDownloadSettings,
    externalDownloadManagers: List<ExternalDownloadManagerApp>,
    blockerSettings: BlockerSettings,
    inactiveTabLifetime: InactiveTabLifetime,
    residentTabLimit: Int,
    searchEngine: SearchEngine,
    pageTranslationProvider: PageTranslationProvider,
    linkLongPressAction: LinkLongPressAction = LinkLongPressAction.LinkPeek,
    linkPeekActionLayout: LinkPeekActionLayout = LinkPeekActionLayout.Default,
    searxngSettings: SearxngSettings,
    isAiModeToggleVisible: Boolean,
    searchSuggestionProvider: SearchSuggestionProvider,
    isHistorySuggestionsEnabled: Boolean,
    isRecallEnabled: Boolean,
    tabOverviewMode: TabOverviewMode,
    tabStackFolderMode: TabOverviewMode,
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
    developerSettings: DeveloperSettings = DeveloperSettings(),
    isDeveloperOptionsUnlocked: Boolean = false,
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
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onResidentTabLimitChanged: (Int) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onPageTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    onLinkLongPressActionChanged: (LinkLongPressAction) -> Unit = {},
    onLinkPeekActionLayoutChanged: (LinkPeekActionLayout) -> Unit = {},
    onSearxngSettingsChanged: (SearxngSettings) -> Unit,
    onAiModeToggleVisibleChanged: (Boolean) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onHistorySuggestionsEnabledChanged: (Boolean) -> Unit,
    onRecallEnabledChanged: (Boolean) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onTabStackFolderModeChanged: (TabOverviewMode) -> Unit,
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
    onDeveloperSettingsChanged: (DeveloperSettings) -> Unit = {},
    onUnlockDeveloperOptions: () -> Unit = {},
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
    onOpenFirefoxExtensions: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    SettingsRouter(
        destination = destination,
        modifier = modifier,
    ) { currentDestination ->
            when (currentDestination) {
                SettingsDestination.Home -> SettingsHomePage(
                    downloadSummary = downloadSettings.displayName(externalDownloadManagers),
                    onDestinationChanged = onDestinationChanged,
                    onDismiss = onDismiss,
                    onOpenFirefoxExtensions = onOpenFirefoxExtensions,
                    developerOptionsUnlocked = isDeveloperOptionsUnlocked,
                    onUnlockDeveloperOptions = onUnlockDeveloperOptions,
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
                    tabStackFolderMode = tabStackFolderMode,
                    tabListStartsAtBottom = tabListStartsAtBottom,
                    automaticTabSortingEnabled = automaticTabSortingEnabled,
                    dismissResistancePercent = dismissResistancePercent,
                    profilesEnabled = profilesEnabled,
                    isAddressBarDockingEnabled = isAddressBarDockingEnabled,
                    linkLongPressAction = linkLongPressAction,
                    onInactiveTabLifetimeChanged = onInactiveTabLifetimeChanged,
                    onResidentTabLimitChanged = onResidentTabLimitChanged,
                    onTabOverviewModeChanged = onTabOverviewModeChanged,
                    onTabStackFolderModeChanged = onTabStackFolderModeChanged,
                    onTabListStartsAtBottomChanged = onTabListStartsAtBottomChanged,
                    onAutomaticTabSortingEnabledChanged =
                        onAutomaticTabSortingEnabledChanged,
                    onDismissResistancePercentChanged = onDismissResistancePercentChanged,
                    onProfilesEnabledChanged = onProfilesEnabledChanged,
                    onAddressBarDockingEnabledChanged = onAddressBarDockingEnabledChanged,
                    onLinkLongPressActionChanged = onLinkLongPressActionChanged,
                    onLinkPeekActions = {
                        onDestinationChanged(SettingsDestination.LinkPeekActions)
                    },
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

                SettingsDestination.LinkPeekActions -> {
                    val actionLabels = LinkPeekAction.entries.associateWith { action ->
                        stringResource(action.labelRes())
                    }
                    LinkPeekActionEditorPage(
                        layout = linkPeekActionLayout,
                        onLayoutChanged = onLinkPeekActionLayoutChanged,
                        onBack = {
                            onDestinationChanged(SettingsDestination.TabsAndGestures)
                        },
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

                SettingsDestination.Appearance -> AppearanceSettingsPage(
                    settings = appearanceSettings,
                    onSettingsChanged = onAppearanceSettingsChanged,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.Browser -> BrowserSettingsPage(
                    browserEngineKind = browserEngineKind,
                    pageTranslationProvider = pageTranslationProvider,
                    isExternalLinkPreviewEnabled = isExternalLinkPreviewEnabled,
                    isFullImmersiveModeEnabled = isFullImmersiveModeEnabled,
                    isStartupAnimationEnabled = isStartupAnimationEnabled,
                    isOpenHomeOnStartupEnabled = isOpenHomeOnStartupEnabled,
                    isScrollBarEnabled = isScrollBarEnabled,
                    isVideoAutoplayBlocked = isVideoAutoplayBlocked,
                    isVideoAutoplayBlockingSupported = isVideoAutoplayBlockingSupported,
                    isDefaultBrowser = isDefaultBrowser,
                    onBrowserEngineKindChanged = onBrowserEngineKindChanged,
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

                SettingsDestination.DeveloperOptions -> DeveloperOptionsSettingsPage(
                    settings = developerSettings,
                    onSettingsChanged = onDeveloperSettingsChanged,
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

private fun LinkPeekAction.labelRes(): Int = when (this) {
    LinkPeekAction.ReaderLater -> R.string.reader_save_offline
    LinkPeekAction.OpenPrivate -> R.string.action_open_link_in_private_tab
    LinkPeekAction.Copy -> R.string.external_link_preview_copy_link
    LinkPeekAction.Share -> R.string.action_share
    LinkPeekAction.Favorite -> R.string.action_favorite
    LinkPeekAction.Snooze -> R.string.action_snooze_tab
    LinkPeekAction.OpenForeground -> R.string.action_open_in_new_tab_and_switch
}
