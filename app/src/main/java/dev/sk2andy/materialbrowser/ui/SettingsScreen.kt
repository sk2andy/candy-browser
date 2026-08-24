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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.blocking.BlockerSettings
import dev.sk2andy.materialbrowser.browser.AddressResolver
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngRules
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.actions.ExternalDownloadManagerApp
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.DownloadManagerMode
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import kotlin.math.roundToInt

internal enum class SettingsDestination {
    Home,
    Search,
    TabsAndGestures,
    Appearance,
    Browser,
    Downloads,
    Userscripts,
    ToppingCatalog,
    SiteCapsules,
    ProtectionAndData,
    AboutLegal,
}

internal object AppearanceSettingsTestTags {
    const val AppearanceMode = "appearance_settings_mode"
    const val ColorPalette = "appearance_settings_palette"
    const val SurfaceStyle = "appearance_settings_surface"
    const val ShapeStyle = "appearance_settings_shape"
    const val FrostedTransparency = "appearance_settings_frosted_transparency"
    const val FrostedAddressBarTransparency =
        "appearance_settings_frosted_address_bar_transparency"
    const val FrostedBlur = "appearance_settings_frosted_blur"
}

internal object ProtectionSettingsTestTags {
    const val UserCaWarning = "protection_settings_user_ca_warning"
    const val ExportAppData = "protection_settings_export_app_data"
    const val ImportAppData = "protection_settings_import_app_data"
}

internal object BrowserSettingsTestTags {
    const val ScrollBar = "browser_settings_scroll_bar"
}

internal object SearchSettingsTestTags {
    const val SearxngInstanceUrl = "search_settings_searxng_instance_url"
    const val SearxngFallback = "search_settings_searxng_fallback"
}

@Composable
internal fun SettingsScreen(
    destination: SettingsDestination,
    appearanceSettings: AppearanceSettings,
    downloadSettings: BrowserDownloadSettings,
    externalDownloadManagers: List<ExternalDownloadManagerApp>,
    blockerSettings: BlockerSettings,
    inactiveTabLifetime: InactiveTabLifetime,
    searchEngine: SearchEngine,
    searxngSettings: SearxngSettings,
    isAiModeToggleVisible: Boolean,
    searchSuggestionProvider: SearchSuggestionProvider,
    tabOverviewMode: TabOverviewMode,
    dismissResistancePercent: Int,
    profilesEnabled: Boolean,
    isTabButtonVisible: Boolean,
    isFullImmersiveModeEnabled: Boolean,
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
    onDestinationChanged: (SettingsDestination) -> Unit,
    onAppearanceSettingsChanged: (AppearanceSettings) -> Unit,
    onDownloadSettingsChanged: (BrowserDownloadSettings) -> Unit,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onInactiveTabLifetimeChanged: (InactiveTabLifetime) -> Unit,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearxngSettingsChanged: (SearxngSettings) -> Unit,
    onAiModeToggleVisibleChanged: (Boolean) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onTabOverviewModeChanged: (TabOverviewMode) -> Unit,
    onDismissResistancePercentChanged: (Int) -> Unit,
    onProfilesEnabledChanged: (Boolean) -> Unit,
    onTabButtonVisibleChanged: (Boolean) -> Unit,
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
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
    onFilterStudio: () -> Unit,
    onExportAppData: () -> Unit,
    onImportAppData: () -> Unit,
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
                if (
                    targetState == SettingsDestination.Home ||
                    initialState == SettingsDestination.ToppingCatalog &&
                    targetState == SettingsDestination.Userscripts
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
                )

                SettingsDestination.Search -> SearchSettingsPage(
                    searchEngine = searchEngine,
                    searxngSettings = searxngSettings,
                    isAiModeToggleVisible = isAiModeToggleVisible,
                    searchSuggestionProvider = searchSuggestionProvider,
                    onSearchEngineChanged = onSearchEngineChanged,
                    onSearxngSettingsChanged = onSearxngSettingsChanged,
                    onAiModeToggleVisibleChanged = onAiModeToggleVisibleChanged,
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

                SettingsDestination.Appearance -> AppearanceSettingsPage(
                    settings = appearanceSettings,
                    onSettingsChanged = onAppearanceSettingsChanged,
                    onBack = { onDestinationChanged(SettingsDestination.Home) },
                )

                SettingsDestination.Browser -> BrowserSettingsPage(
                    isFullImmersiveModeEnabled = isFullImmersiveModeEnabled,
                    isScrollBarEnabled = isScrollBarEnabled,
                    isVideoAutoplayBlocked = isVideoAutoplayBlocked,
                    isVideoAutoplayBlockingSupported = isVideoAutoplayBlockingSupported,
                    isDefaultBrowser = isDefaultBrowser,
                    onFullImmersiveModeEnabledChanged = onFullImmersiveModeEnabledChanged,
                    onScrollBarEnabledChanged = onScrollBarEnabledChanged,
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

                SettingsDestination.ProtectionAndData -> ProtectionAndDataSettingsPage(
                    blockerSettings = blockerSettings,
                    blockedCount = blockedCount,
                    trustsUserCertificates = trustsUserCertificates,
                    onBlockerSettingsChanged = onBlockerSettingsChanged,
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
            icon = Icons.Default.Search,
            title = stringResource(R.string.settings_section_search),
            subtitle = stringResource(R.string.settings_home_search_summary),
            onClick = { onDestinationChanged(SettingsDestination.Search) },
        )
        SettingsPageSpacer()
        SettingsLink(
            icon = Icons.AutoMirrored.Filled.List,
            title = stringResource(R.string.settings_tabs_gestures_title),
            subtitle = stringResource(R.string.settings_home_tabs_gestures_summary),
            onClick = { onDestinationChanged(SettingsDestination.TabsAndGestures) },
        )
        SettingsPageSpacer()
        SettingsLink(
            icon = Icons.Default.Face,
            title = stringResource(R.string.settings_appearance_title),
            subtitle = stringResource(R.string.settings_home_appearance_summary),
            onClick = { onDestinationChanged(SettingsDestination.Appearance) },
        )
        SettingsPageSpacer()
        SettingsLink(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.settings_section_browser),
            subtitle = stringResource(R.string.settings_home_browser_summary),
            onClick = { onDestinationChanged(SettingsDestination.Browser) },
        )
        SettingsPageSpacer()
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_reader_download),
            title = stringResource(R.string.settings_downloads_title),
            subtitle = downloadSummary,
            onClick = { onDestinationChanged(SettingsDestination.Downloads) },
        )
        SettingsPageSpacer()
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_symbol_extension),
            title = stringResource(R.string.userscript_title),
            subtitle = stringResource(R.string.settings_home_userscripts_summary),
            onClick = { onDestinationChanged(SettingsDestination.Userscripts) },
        )
        SettingsPageSpacer()
        SettingsLink(
            icon = Icons.Default.Favorite,
            title = stringResource(R.string.capsule_settings_title),
            subtitle = stringResource(R.string.settings_home_capsules_summary),
            onClick = { onDestinationChanged(SettingsDestination.SiteCapsules) },
        )
        SettingsPageSpacer()
        SettingsLink(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.settings_protection_data_title),
            subtitle = stringResource(R.string.settings_home_protection_summary),
            onClick = { onDestinationChanged(SettingsDestination.ProtectionAndData) },
        )
        SettingsPageSpacer()
        SettingsLink(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_section_about_legal),
            subtitle = stringResource(R.string.settings_home_about_summary),
            onClick = { onDestinationChanged(SettingsDestination.AboutLegal) },
        )
    }
}

@Composable
internal fun AppearanceSettingsPage(
    settings: AppearanceSettings,
    onSettingsChanged: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
) {
    var appearanceMenuExpanded by remember { mutableStateOf(false) }
    var paletteMenuExpanded by remember { mutableStateOf(false) }
    var surfaceMenuExpanded by remember { mutableStateOf(false) }
    var shapeMenuExpanded by remember { mutableStateOf(false) }

    SettingsPage(
        title = stringResource(R.string.settings_appearance_title),
        onBack = onBack,
    ) {
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_appearance_mode),
                value = settings.appearanceMode.displayName(),
                expanded = appearanceMenuExpanded,
                onClick = { appearanceMenuExpanded = true },
                modifier = Modifier.testTag(AppearanceSettingsTestTags.AppearanceMode),
            )
            SettingsDropdown(
                expanded = appearanceMenuExpanded,
                onDismissRequest = { appearanceMenuExpanded = false },
            ) {
                BrowserAppearanceMode.entries.forEach { mode ->
                    SettingsDropdownItem(
                        label = mode.displayName(),
                        selected = mode == settings.appearanceMode,
                        onClick = {
                            appearanceMenuExpanded = false
                            onSettingsChanged(settings.copy(appearanceMode = mode))
                        },
                    )
                }
            }
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_color_palette),
                value = settings.colorPalette.displayName(),
                expanded = paletteMenuExpanded,
                onClick = { paletteMenuExpanded = true },
                modifier = Modifier.testTag(AppearanceSettingsTestTags.ColorPalette),
            )
            SettingsDropdown(
                expanded = paletteMenuExpanded,
                onDismissRequest = { paletteMenuExpanded = false },
            ) {
                BrowserColorPalette.entries.forEach { palette ->
                    SettingsDropdownItem(
                        label = palette.displayName(),
                        selected = palette == settings.colorPalette,
                        onClick = {
                            paletteMenuExpanded = false
                            onSettingsChanged(settings.copy(colorPalette = palette))
                        },
                    )
                }
            }
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_surface_style),
                value = settings.surfaceStyle.displayName(),
                expanded = surfaceMenuExpanded,
                onClick = { surfaceMenuExpanded = true },
                modifier = Modifier.testTag(AppearanceSettingsTestTags.SurfaceStyle),
            )
            SettingsDropdown(
                expanded = surfaceMenuExpanded,
                onDismissRequest = { surfaceMenuExpanded = false },
            ) {
                BrowserSurfaceStyle.entries.forEach { style ->
                    SettingsDropdownItem(
                        label = style.displayName(),
                        selected = style == settings.surfaceStyle,
                        onClick = {
                            surfaceMenuExpanded = false
                            onSettingsChanged(settings.copy(surfaceStyle = style))
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.settings_surface_style_summary),
            modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.surfaceStyle == BrowserSurfaceStyle.Frosted) {
            SettingsPageSpacer()
            AppearanceSlider(
                title = stringResource(R.string.settings_frosted_transparency),
                value = settings.frostedTransparencyPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT.toFloat(),
                steps = 7,
                onValueChange = { value ->
                    onSettingsChanged(
                        settings.copy(
                            frostedTransparencyPercent = value.roundToInt(),
                        ),
                    )
                },
                testTag = AppearanceSettingsTestTags.FrostedTransparency,
            )
            SettingsPageSpacer()
            AppearanceSlider(
                title = stringResource(R.string.settings_frosted_address_bar_transparency),
                value = settings.frostedAddressBarTransparencyPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_TRANSPARENCY_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_TRANSPARENCY_PERCENT.toFloat(),
                steps = 7,
                onValueChange = { value ->
                    onSettingsChanged(
                        settings.copy(
                            frostedAddressBarTransparencyPercent = value.roundToInt(),
                        ),
                    )
                },
                testTag = AppearanceSettingsTestTags.FrostedAddressBarTransparency,
            )
            SettingsPageSpacer()
            AppearanceSlider(
                title = stringResource(R.string.settings_frosted_blur),
                value = settings.frostedBlurPercent.toFloat(),
                valueRange = AppearanceSettings.MIN_FROSTED_BLUR_PERCENT.toFloat()..
                    AppearanceSettings.MAX_FROSTED_BLUR_PERCENT.toFloat(),
                steps = 9,
                onValueChange = { value ->
                    onSettingsChanged(
                        settings.copy(frostedBlurPercent = value.roundToInt()),
                    )
                },
                testTag = AppearanceSettingsTestTags.FrostedBlur,
            )
        }
        SettingsPageSpacer()
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_shape_style),
                value = settings.shapeStyle.displayName(),
                expanded = shapeMenuExpanded,
                onClick = { shapeMenuExpanded = true },
                modifier = Modifier.testTag(AppearanceSettingsTestTags.ShapeStyle),
            )
            SettingsDropdown(
                expanded = shapeMenuExpanded,
                onDismissRequest = { shapeMenuExpanded = false },
            ) {
                BrowserShapeStyle.entries.forEach { style ->
                    SettingsDropdownItem(
                        label = style.displayName(),
                        selected = style == settings.shapeStyle,
                        onClick = {
                            shapeMenuExpanded = false
                            onSettingsChanged(settings.copy(shapeStyle = style))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    testTag: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${value.roundToInt()} %",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.testTag(testTag),
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}

@Composable
internal fun SearchSettingsPage(
    searchEngine: SearchEngine,
    searxngSettings: SearxngSettings,
    isAiModeToggleVisible: Boolean,
    searchSuggestionProvider: SearchSuggestionProvider,
    onSearchEngineChanged: (SearchEngine) -> Unit,
    onSearxngSettingsChanged: (SearxngSettings) -> Unit,
    onAiModeToggleVisibleChanged: (Boolean) -> Unit,
    onSearchSuggestionProviderChanged: (SearchSuggestionProvider) -> Unit,
    onBack: () -> Unit,
) {
    var searchEngineMenuExpanded by remember { mutableStateOf(false) }
    var searchSuggestionMenuExpanded by remember { mutableStateOf(false) }
    var fallbackMenuExpanded by remember { mutableStateOf(false) }
    var instanceUrlDraft by remember { mutableStateOf(searxngSettings.instanceUrl) }
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
        if (searchEngine.supportsAiSearch) {
            SettingsPageSpacer()
            SettingsSwitch(
                title = stringResource(R.string.settings_ai_mode_toggle_title),
                subtitle = stringResource(R.string.settings_ai_mode_toggle_subtitle),
                checked = isAiModeToggleVisible,
                onCheckedChange = onAiModeToggleVisibleChanged,
            )
        }
        if (
            searchEngine == SearchEngine.SearXNG ||
            searchSuggestionProvider == SearchSuggestionProvider.SearXNG
        ) {
            SettingsPageSpacer()
            val normalizedInstanceUrl = SearxngRules.normalizedInstanceUrl(
                instanceUrlDraft,
            )
            OutlinedTextField(
                value = instanceUrlDraft,
                onValueChange = { value ->
                    val boundedValue = value.take(SearxngRules.MAX_INSTANCE_URL_LENGTH)
                    val normalizedValue = SearxngRules.normalizedInstanceUrl(boundedValue)
                    instanceUrlDraft = boundedValue
                    when {
                        boundedValue.isBlank() -> onSearxngSettingsChanged(
                            searxngSettings.copy(instanceUrl = ""),
                        )
                        normalizedValue != null -> onSearxngSettingsChanged(
                            searxngSettings.copy(instanceUrl = normalizedValue),
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SearchSettingsTestTags.SearxngInstanceUrl),
                label = { Text(stringResource(R.string.settings_searxng_instance_url)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (
                                instanceUrlDraft.isNotBlank() &&
                                normalizedInstanceUrl == null
                            ) {
                                R.string.settings_searxng_instance_url_invalid
                            } else {
                                R.string.settings_searxng_instance_url_summary
                            },
                        ),
                    )
                },
                isError = instanceUrlDraft.isNotBlank() &&
                    normalizedInstanceUrl == null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
            )
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
                } else if (searchSuggestionProvider == SearchSuggestionProvider.SearXNG) {
                    R.string.settings_searxng_search_suggestions_summary
                } else {
                    R.string.settings_search_suggestions_summary
                },
            ),
            modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (searchSuggestionProvider == SearchSuggestionProvider.SearXNG) {
            SettingsPageSpacer()
            Box {
                SettingsChoice(
                    title = stringResource(R.string.settings_searxng_suggestion_fallback),
                    value = searxngSettings.suggestionFallback.displayName(),
                    expanded = fallbackMenuExpanded,
                    onClick = { fallbackMenuExpanded = true },
                    modifier = Modifier.testTag(SearchSettingsTestTags.SearxngFallback),
                )
                SettingsDropdown(
                    expanded = fallbackMenuExpanded,
                    onDismissRequest = { fallbackMenuExpanded = false },
                ) {
                    SearchSuggestionProvider.entries
                        .filterNot { it == SearchSuggestionProvider.SearXNG }
                        .forEach { provider ->
                            SettingsDropdownItem(
                                label = provider.displayName(),
                                selected = provider == searxngSettings.suggestionFallback,
                                onClick = {
                                    fallbackMenuExpanded = false
                                    onSearxngSettingsChanged(
                                        searxngSettings.copy(suggestionFallback = provider),
                                    )
                                },
                            )
                        }
                }
            }
            Text(
                stringResource(R.string.settings_searxng_suggestion_fallback_summary),
                modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            shape = MaterialTheme.shapes.large,
            color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
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
internal fun BrowserSettingsPage(
    isFullImmersiveModeEnabled: Boolean,
    isScrollBarEnabled: Boolean,
    isVideoAutoplayBlocked: Boolean,
    isVideoAutoplayBlockingSupported: Boolean,
    isDefaultBrowser: Boolean,
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
    onScrollBarEnabledChanged: (Boolean) -> Unit,
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
            title = stringResource(R.string.settings_scroll_bar_title),
            subtitle = stringResource(R.string.settings_scroll_bar_subtitle),
            checked = isScrollBarEnabled,
            onCheckedChange = onScrollBarEnabledChanged,
            modifier = Modifier.testTag(BrowserSettingsTestTags.ScrollBar),
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
            shape = MaterialTheme.shapes.large,
            color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                shape = MaterialTheme.shapes.large,
                color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                    shape = MaterialTheme.shapes.large,
                    color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
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
internal fun ProtectionAndDataSettingsPage(
    blockerSettings: BlockerSettings,
    blockedCount: Int,
    trustsUserCertificates: Boolean,
    onBlockerSettingsChanged: (BlockerSettings) -> Unit,
    onPrivacyXRay: () -> Unit,
    onPermissionRadar: () -> Unit,
    onFilterStudio: () -> Unit,
    onExportAppData: () -> Unit = {},
    onImportAppData: () -> Unit = {},
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
            shape = MaterialTheme.shapes.large,
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
            shape = MaterialTheme.shapes.large,
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
        if (trustsUserCertificates) {
            Spacer(Modifier.height(18.dp))
            UserCaTrustWarning()
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
        DataArchiveAction(
            title = stringResource(R.string.data_archive_export_title),
            summary = stringResource(R.string.data_archive_export_summary),
            onClick = onExportAppData,
            modifier = Modifier.testTag(ProtectionSettingsTestTags.ExportAppData),
        )
        Spacer(Modifier.height(8.dp))
        DataArchiveAction(
            title = stringResource(R.string.data_archive_import_title),
            summary = stringResource(R.string.data_archive_import_summary),
            onClick = onImportAppData,
            modifier = Modifier.testTag(ProtectionSettingsTestTags.ImportAppData),
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            onClick = onClearData,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp),
            shape = MaterialTheme.shapes.large,
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
private fun DataArchiveAction(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserCaTrustWarning(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProtectionSettingsTestTags.UserCaWarning),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.settings_user_ca_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.settings_user_ca_summary),
                style = MaterialTheme.typography.bodySmall,
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
        shape = MaterialTheme.shapes.large,
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
    InactiveTabLifetime.Immediately -> stringResource(R.string.tab_lifetime_immediately)
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
    SearchSuggestionProvider.Kagi -> "Kagi"
    SearchSuggestionProvider.SearXNG -> "SearXNG"
}

@Composable
private fun BrowserAppearanceMode.displayName(): String = when (this) {
    BrowserAppearanceMode.System -> stringResource(R.string.appearance_mode_system)
    BrowserAppearanceMode.Light -> stringResource(R.string.appearance_mode_light)
    BrowserAppearanceMode.Dark -> stringResource(R.string.appearance_mode_dark)
    BrowserAppearanceMode.Amoled -> stringResource(R.string.appearance_mode_amoled)
}

@Composable
private fun BrowserColorPalette.displayName(): String = when (this) {
    BrowserColorPalette.Dynamic -> stringResource(R.string.color_palette_dynamic)
    BrowserColorPalette.Candy -> stringResource(R.string.color_palette_candy)
    BrowserColorPalette.Neutral -> stringResource(R.string.color_palette_neutral)
}

@Composable
private fun BrowserSurfaceStyle.displayName(): String = when (this) {
    BrowserSurfaceStyle.Clear -> stringResource(R.string.surface_style_clear)
    BrowserSurfaceStyle.Frosted -> stringResource(R.string.surface_style_frosted)
}

@Composable
private fun BrowserShapeStyle.displayName(): String = when (this) {
    BrowserShapeStyle.Angular -> stringResource(R.string.shape_style_angular)
    BrowserShapeStyle.Rounded -> stringResource(R.string.shape_style_rounded)
    BrowserShapeStyle.ExtraRounded -> stringResource(R.string.shape_style_extra_rounded)
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
    modifier: Modifier = Modifier,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
        label = "Selection indicator",
    )
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                shape = MaterialTheme.shapes.medium,
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
        modifier = Modifier.clip(MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
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
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
