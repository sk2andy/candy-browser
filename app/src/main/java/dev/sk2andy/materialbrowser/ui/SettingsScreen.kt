package dev.sk2andy.materialbrowser.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.DownloadManagerMode
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import kotlin.math.roundToInt

internal enum class SettingsDestination {
    Home,
    Search,
    TabsAndGestures,
    Browser,
    Downloads,
    SiteCapsules,
    ProtectionAndData,
    AboutLegal,
}

@Composable
internal fun SettingsScreen(
    destination: SettingsDestination,
    downloadSettings: BrowserDownloadSettings,
    externalDownloadManagers: List<ExternalDownloadManagerApp>,
    blockerSettings: BlockerSettings,
    inactiveTabLifetime: InactiveTabLifetime,
    searchEngine: SearchEngine,
    searchSuggestionProvider: SearchSuggestionProvider,
    tabOverviewMode: TabOverviewMode,
    dismissResistancePercent: Int,
    profilesEnabled: Boolean,
    isTabButtonVisible: Boolean,
    isFullImmersiveModeEnabled: Boolean,
    isVideoAutoplayBlocked: Boolean,
    isVideoAutoplayBlockingSupported: Boolean,
    blockedCount: Int,
    isDefaultBrowser: Boolean,
    siteCapsules: List<SiteCapsule>,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onDownloadSettingsChanged: (BrowserDownloadSettings) -> Unit,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onDismissResistancePercentChanged: (Int) -> Unit,
    onProfilesEnabledChanged: (Boolean) -> Unit,
    onTabButtonVisibleChanged: (Boolean) -> Unit,
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
    onVideoAutoplayBlockedChanged: (Boolean) -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit,
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onEditCapsule: (SiteCapsule) -> Unit,
    onDeleteCapsule: (SiteCapsule) -> Unit,
    onFilterStudio: () -> Unit,
    onClearData: () -> Unit,
    onOpenLegalUrl: (String) -> Unit,
    onDismiss: () -> Unit,
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
                if (targetState == SettingsDestination.Home) {
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
                )

                SettingsDestination.Search -> SearchSettingsPage(
                    searchEngine = searchEngine,
                    searchSuggestionProvider = searchSuggestionProvider,
                    onSearchEngineChanged = onSearchEngineChanged,
                    onSearchSuggestionProviderChanged = onSearchSuggestionProviderChanged,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.TabsAndGestures -> TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = inactiveTabLifetime,
                    tabOverviewMode = tabOverviewMode,
                    dismissResistancePercent = dismissResistancePercent,
                    profilesEnabled = profilesEnabled,
                    isTabButtonVisible = isTabButtonVisible,
                    onInactiveTabLifetimeChanged = onInactiveTabLifetimeChanged,
                    onTabOverviewModeChanged = onTabOverviewModeChanged,
                    onDismissResistancePercentChanged = onDismissResistancePercentChanged,
                    onProfilesEnabledChanged = onProfilesEnabledChanged,
                    onTabButtonVisibleChanged = onTabButtonVisibleChanged,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.Browser -> BrowserSettingsPage(
                    isFullImmersiveModeEnabled = isFullImmersiveModeEnabled,
                    isVideoAutoplayBlocked = isVideoAutoplayBlocked,
                    isVideoAutoplayBlockingSupported = isVideoAutoplayBlockingSupported,
                    isDefaultBrowser = isDefaultBrowser,
                    onFullImmersiveModeEnabledChanged = onFullImmersiveModeEnabledChanged,
                    onVideoAutoplayBlockedChanged = onVideoAutoplayBlockedChanged,
                    onOpenDefaultBrowserSettings = onOpenDefaultBrowserSettings,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.Downloads -> DownloadsSettingsPage(
                    settings = downloadSettings,
                    externalManagers = externalDownloadManagers,
                    onSettingsChanged = onDownloadSettingsChanged,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.SiteCapsules -> SiteCapsulesSettingsPage(
                    siteCapsules = siteCapsules,
                    onEditCapsule = onEditCapsule,
                    onDeleteCapsule = onDeleteCapsule,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.ProtectionAndData -> ProtectionAndDataSettingsPage(
                    blockerSettings = blockerSettings,
                    blockedCount = blockedCount,
                    onBlockerSettingsChanged = onBlockerSettingsChanged,
                    onPrivacyXRay = onPrivacyXRay,
                    onPermissionRadar = onPermissionRadar,
                    onFilterStudio = onFilterStudio,
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

@Composable
private fun SettingsHomePage(
    downloadSummary: String,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_title),
        onBack = onDismiss,
    ) {
        SettingsLink(
            title = stringResource(R.string.settings_section_search),
            subtitle = stringResource(R.string.settings_home_search_summary),
            onClick = { onDestinationChanged(SettingsDestination.Search) },
        )
        SettingsPageSpacer()
        SettingsLink(
            title = stringResource(R.string.settings_tabs_gestures_title),
            subtitle = stringResource(R.string.settings_home_tabs_gestures_summary),
            onClick = { onDestinationChanged(SettingsDestination.TabsAndGestures) },
        )
        SettingsPageSpacer()
        SettingsLink(
            title = stringResource(R.string.settings_section_browser),
            subtitle = stringResource(R.string.settings_home_browser_summary),
            onClick = { onDestinationChanged(SettingsDestination.Browser) },
        )
        SettingsPageSpacer()
        SettingsLink(
            title = stringResource(R.string.settings_downloads_title),
            subtitle = downloadSummary,
            onClick = { onDestinationChanged(SettingsDestination.Downloads) },
        )
        SettingsPageSpacer()
        SettingsLink(
            title = stringResource(R.string.capsule_settings_title),
            subtitle = stringResource(R.string.settings_home_capsules_summary),
            onClick = { onDestinationChanged(SettingsDestination.SiteCapsules) },
        )
        SettingsPageSpacer()
        SettingsLink(
            title = stringResource(R.string.settings_protection_data_title),
            subtitle = stringResource(R.string.settings_home_protection_summary),
            onClick = { onDestinationChanged(SettingsDestination.ProtectionAndData) },
        )
        SettingsPageSpacer()
        SettingsLink(
            title = stringResource(R.string.settings_section_about_legal),
            subtitle = stringResource(R.string.settings_home_about_summary),
            onClick = { onDestinationChanged(SettingsDestination.AboutLegal) },
        )
    }
}

@Composable
private fun SearchSettingsPage(
    searchEngine: SearchEngine,
    searchSuggestionProvider: SearchSuggestionProvider,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onBack: () -> Unit,
) {
    var searchEngineMenuExpanded by remember { mutableStateOf(false) }
    var searchSuggestionMenuExpanded by remember { mutableStateOf(false) }
    SettingsPage(
        title = stringResource(R.string.settings_section_search),
        onBack = onBack,
    ) {
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_search_engine),
                value = searchEngine.displayName,
                expanded = searchEngineMenuExpanded,
                onClick = { searchEngineMenuExpanded = true },
            )
            SettingsDropdown(
                expanded = searchEngineMenuExpanded,
                onDismissRequest = { searchEngineMenuExpanded = false },
            ) {
                SearchEngine.entries.forEach { engine ->
                    SettingsDropdownItem(
                        label = engine.displayName,
                        selected = engine == searchEngine,
                        onClick = {
                            searchEngineMenuExpanded = false
                            onSearchEngineChanged(engine)
                        },
                    )
                }
            }
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_search_suggestions),
                value = searchSuggestionProvider.displayName(),
                expanded = searchSuggestionMenuExpanded,
                onClick = { searchSuggestionMenuExpanded = true },
            )
            SettingsDropdown(
                expanded = searchSuggestionMenuExpanded,
                onDismissRequest = { searchSuggestionMenuExpanded = false },
            ) {
                SearchSuggestionProvider.entries.forEach { provider ->
                    SettingsDropdownItem(
                        label = provider.displayName(),
                        selected = provider == searchSuggestionProvider,
                        onClick = {
                            searchSuggestionMenuExpanded = false
                            onSearchSuggestionProviderChanged(provider)
                        },
                    )
                }
            }
        }
        Text(
            stringResource(
                if (searchSuggestionProvider == SearchSuggestionProvider.None) {
                    R.string.settings_search_suggestions_none_summary
                } else {
                    R.string.settings_search_suggestions_summary
                },
            ),
            modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TabsAndGesturesSettingsPage(
    inactiveTabLifetime: InactiveTabLifetime,
    tabOverviewMode: TabOverviewMode,
    dismissResistancePercent: Int,
    profilesEnabled: Boolean,
    isTabButtonVisible: Boolean,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onDismissResistancePercentChanged: (Int) -> Unit,
    onProfilesEnabledChanged: (Boolean) -> Unit,
    onTabButtonVisibleChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var lifetimeMenuExpanded by remember { mutableStateOf(false) }
    var overviewModeMenuExpanded by remember { mutableStateOf(false) }
    var resistancePercent by remember(dismissResistancePercent) {
        mutableFloatStateOf(dismissResistancePercent.toFloat())
    }
    SettingsPage(
        title = stringResource(R.string.settings_tabs_gestures_title),
        onBack = onBack,
    ) {
        SettingsSectionTitle(stringResource(R.string.settings_section_tabs))
        Spacer(Modifier.height(8.dp))
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_tab_overview_mode),
                value = tabOverviewMode.displayName(),
                expanded = overviewModeMenuExpanded,
                onClick = { overviewModeMenuExpanded = true },
            )
            SettingsDropdown(
                expanded = overviewModeMenuExpanded,
                onDismissRequest = { overviewModeMenuExpanded = false },
            ) {
                TabOverviewMode.entries.forEach { mode ->
                    SettingsDropdownItem(
                        label = mode.displayName(),
                        selected = mode == tabOverviewMode,
                        onClick = {
                            overviewModeMenuExpanded = false
                            onTabOverviewModeChanged(mode)
                        },
                    )
                }
            }
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_auto_close_tabs),
                value = inactiveTabLifetime.displayName(),
                expanded = lifetimeMenuExpanded,
                onClick = { lifetimeMenuExpanded = true },
            )
            SettingsDropdown(
                expanded = lifetimeMenuExpanded,
                onDismissRequest = { lifetimeMenuExpanded = false },
            ) {
                InactiveTabLifetime.entries.forEach { lifetime ->
                    SettingsDropdownItem(
                        label = lifetime.displayName(),
                        selected = lifetime == inactiveTabLifetime,
                        onClick = {
                            lifetimeMenuExpanded = false
                            onInactiveTabLifetimeChanged(lifetime)
                        },
                    )
                }
            }
        }
        SettingsPageSpacer()
        SettingsSwitch(
            title = stringResource(R.string.settings_profiles_title),
            subtitle = stringResource(R.string.settings_profiles_subtitle),
            checked = profilesEnabled,
            onCheckedChange = onProfilesEnabledChanged,
        )
        Spacer(Modifier.height(14.dp))
        SettingsSectionTitle(stringResource(R.string.settings_section_gestures))
        Spacer(Modifier.height(2.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_tab_button_title),
            subtitle = stringResource(R.string.settings_tab_button_subtitle),
            checked = isTabButtonVisible,
            onCheckedChange = onTabButtonVisibleChanged,
        )
        Spacer(Modifier.height(2.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    stringResource(R.string.settings_tab_dismiss_resistance),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        R.string.settings_tab_dismiss_resistance_summary,
                        resistancePercent.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = resistancePercent,
                    onValueChange = { resistancePercent = it },
                    onValueChangeFinished = {
                        onDismissResistancePercentChanged(resistancePercent.roundToInt())
                    },
                    valueRange = 10f..90f,
                    steps = 7,
                )
            }
        }
    }
}

@Composable
private fun BrowserSettingsPage(
    isFullImmersiveModeEnabled: Boolean,
    isVideoAutoplayBlocked: Boolean,
    isVideoAutoplayBlockingSupported: Boolean,
    isDefaultBrowser: Boolean,
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
    onVideoAutoplayBlockedChanged: (Boolean) -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_section_browser),
        onBack = onBack,
    ) {
        SettingsSwitch(
            title = stringResource(R.string.settings_full_immersive_mode_title),
            subtitle = stringResource(R.string.settings_full_immersive_mode_subtitle),
            checked = isFullImmersiveModeEnabled,
            onCheckedChange = onFullImmersiveModeEnabledChanged,
        )
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_video_autoplay_title),
            subtitle = stringResource(
                if (isVideoAutoplayBlockingSupported) {
                    R.string.settings_video_autoplay_subtitle
                } else {
                    R.string.settings_video_autoplay_unsupported
                },
            ),
            checked = isVideoAutoplayBlocked,
            enabled = isVideoAutoplayBlockingSupported,
            onCheckedChange = onVideoAutoplayBlockedChanged,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onOpenDefaultBrowserSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    stringResource(R.string.settings_default_browser),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        if (isDefaultBrowser) {
                            R.string.settings_default_browser_active
                        } else {
                            R.string.settings_make_default_browser
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDefaultBrowser) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadsSettingsPage(
    settings: BrowserDownloadSettings,
    externalManagers: List<ExternalDownloadManagerApp>,
    onSettingsChanged: (BrowserDownloadSettings) -> Unit,
    onBack: () -> Unit,
) {
    var managerMenuExpanded by remember { mutableStateOf(false) }
    val selectedExternalManager = externalManagers.firstOrNull {
        it.id == settings.externalManagerId
    }
    val oneDmRelevant = when (settings.managerMode) {
        DownloadManagerMode.BuiltIn -> false
        DownloadManagerMode.AskEveryTime -> externalManagers.any(ExternalDownloadManagerApp::isOneDm)
        DownloadManagerMode.External -> selectedExternalManager?.isOneDm == true
    }
    SettingsPage(
        title = stringResource(R.string.settings_downloads_title),
        onBack = onBack,
    ) {
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_download_manager_title),
                value = settings.displayName(externalManagers),
                expanded = managerMenuExpanded,
                onClick = { managerMenuExpanded = true },
            )
            SettingsDropdown(
                expanded = managerMenuExpanded,
                onDismissRequest = { managerMenuExpanded = false },
            ) {
                SettingsDropdownItem(
                    label = stringResource(R.string.settings_download_manager_builtin),
                    selected = settings.managerMode == DownloadManagerMode.BuiltIn,
                    onClick = {
                        managerMenuExpanded = false
                        onSettingsChanged(
                            settings.copy(
                                managerMode = DownloadManagerMode.BuiltIn,
                                externalManagerId = null,
                            ),
                        )
                    },
                )
                SettingsDropdownItem(
                    label = stringResource(R.string.settings_download_manager_ask),
                    selected = settings.managerMode == DownloadManagerMode.AskEveryTime,
                    onClick = {
                        managerMenuExpanded = false
                        onSettingsChanged(
                            settings.copy(
                                managerMode = DownloadManagerMode.AskEveryTime,
                                externalManagerId = null,
                            ),
                        )
                    },
                )
                externalManagers.forEach { manager ->
                    SettingsDropdownItem(
                        label = manager.label,
                        selected = settings.managerMode == DownloadManagerMode.External &&
                            settings.externalManagerId == manager.id,
                        onClick = {
                            managerMenuExpanded = false
                            onSettingsChanged(
                                settings.copy(
                                    managerMode = DownloadManagerMode.External,
                                    externalManagerId = manager.id,
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (externalManagers.isEmpty()) {
            Text(
                stringResource(R.string.settings_download_no_external_managers),
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (oneDmRelevant) {
            Spacer(Modifier.height(18.dp))
            SettingsSwitch(
                title = stringResource(R.string.settings_download_one_dm_session_title),
                subtitle = stringResource(R.string.settings_download_one_dm_session_summary),
                checked = settings.shareSessionDataWithOneDm,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(shareSessionDataWithOneDm = it))
                },
            )
        }
    }
}

@Composable
private fun SiteCapsulesSettingsPage(
    siteCapsules: List<SiteCapsule>,
    onEditCapsule: (SiteCapsule) -> Unit,
    onDeleteCapsule: (SiteCapsule) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.capsule_settings_title),
        onBack = onBack,
    ) {
        Text(
            stringResource(R.string.capsule_settings_launcher_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (siteCapsules.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    stringResource(R.string.capsule_settings_empty),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            siteCapsules.forEach { capsule ->
                Surface(
                    onClick = { onEditCapsule(capsule) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(capsule.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                AddressResolver.displayText(capsule.startUrl),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteCapsule(capsule) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.capsule_delete_title),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionAndDataSettingsPage(
    blockerSettings: BlockerSettings,
    blockedCount: Int,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onFilterStudio: () -> Unit,
    onClearData: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(
        title = stringResource(R.string.settings_protection_data_title),
        onBack = onBack,
    ) {
        Surface(
            onClick = onPermissionRadar,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.permission_radar_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.permission_radar_settings_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Text(
                    "◉",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        PrivacyXRaySettingsCounter(
            blockedCount = blockedCount,
            onClick = onPrivacyXRay,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onFilterStudio,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
                Text(
                    stringResource(R.string.filter_studio_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.filter_studio_settings_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_block_ads_title),
            subtitle = stringResource(R.string.settings_block_ads_subtitle),
            checked = blockerSettings.blockAdsAndTrackers,
            onCheckedChange = {
                onBlockerSettingsChanged(blockerSettings.copy(blockAdsAndTrackers = it))
            },
        )
        SettingsSwitch(
            title = stringResource(R.string.settings_hide_cookie_banners_title),
            subtitle = stringResource(R.string.settings_hide_cookie_banners_subtitle),
            checked = blockerSettings.hideCookieConsent,
            onCheckedChange = {
                onBlockerSettingsChanged(blockerSettings.copy(hideCookieConsent = it))
            },
        )
        SettingsSwitch(
            title = stringResource(R.string.settings_block_third_party_cookies_title),
            subtitle = stringResource(R.string.settings_block_third_party_cookies_subtitle),
            checked = blockerSettings.blockThirdPartyCookies,
            onCheckedChange = {
                onBlockerSettingsChanged(blockerSettings.copy(blockThirdPartyCookies = it))
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_protection_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = onClearData,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Text(
                stringResource(R.string.action_clear_browsing_data),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        content()
    }
}

@Composable
private fun BrowserDownloadSettings.displayName(
    externalManagers: List<ExternalDownloadManagerApp>,
): String = when (managerMode) {
    DownloadManagerMode.BuiltIn -> stringResource(R.string.settings_download_manager_builtin)
    DownloadManagerMode.AskEveryTime -> stringResource(R.string.settings_download_manager_ask)
    DownloadManagerMode.External -> externalManagers
        .firstOrNull { it.id == externalManagerId }
        ?.label
        ?: stringResource(R.string.settings_download_manager_external_unavailable)
}

@Composable
internal fun PrivacyXRaySettingsCounter(
    blockedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .testTag(PrivacyXRayTestTags.SettingsCounter),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(
                    R.plurals.blocked_requests_count,
                    blockedCount,
                    blockedCount,
                ),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "◈",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun InactiveTabLifetime.displayName(): String = when (this) {
    InactiveTabLifetime.Never -> stringResource(R.string.tab_lifetime_never)
    InactiveTabLifetime.SixHours -> pluralStringResource(R.plurals.tab_lifetime_hours, 6, 6)
    InactiveTabLifetime.OneDay -> pluralStringResource(R.plurals.tab_lifetime_days, 1, 1)
    InactiveTabLifetime.ThreeDays -> pluralStringResource(R.plurals.tab_lifetime_days, 3, 3)
    InactiveTabLifetime.SevenDays -> pluralStringResource(R.plurals.tab_lifetime_days, 7, 7)
    InactiveTabLifetime.ThirtyDays -> pluralStringResource(R.plurals.tab_lifetime_days, 30, 30)
}

@Composable
private fun TabOverviewMode.displayName(): String = when (this) {
    TabOverviewMode.Hero -> stringResource(R.string.tab_overview_mode_hero)
    TabOverviewMode.Grid -> stringResource(R.string.tab_overview_mode_grid)
    TabOverviewMode.List -> stringResource(R.string.tab_overview_mode_list)
}

@Composable
private fun SearchSuggestionProvider.displayName(): String = when (this) {
    SearchSuggestionProvider.None -> stringResource(R.string.search_suggestion_provider_none)
    SearchSuggestionProvider.DuckDuckGo -> "DuckDuckGo"
    SearchSuggestionProvider.Brave -> "Brave Search"
    SearchSuggestionProvider.Ecosia -> "Ecosia"
    SearchSuggestionProvider.Qwant -> "Qwant"
    SearchSuggestionProvider.Startpage -> "Startpage"
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsChoice(
    title: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "Selection indicator",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        content = content,
    )
}

@Composable
private fun SettingsDropdownItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        },
    )
}

@Composable
private fun SettingsLink(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.6f,
                ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsPageSpacer() {
    Spacer(Modifier.height(12.dp))
}
