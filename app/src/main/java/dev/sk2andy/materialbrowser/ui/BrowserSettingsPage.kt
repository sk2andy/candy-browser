package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsFeatureIllustration
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsIllustrationKind
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.settings.settingsSearchTarget
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.browser.FavoriteAnimationSpeed
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.shared.ui.settings.TranslationProviderSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.TranslationProviderSettingsStrings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor
import java.util.Locale

internal object BrowserSettingsTestTags {
    const val AppLanguage = "browser_settings_app_language"
    const val StartupAnimation = "browser_settings_startup_animation"
    const val StartupAddressFocus = "browser_settings_startup_address_focus"
    const val FavoriteLaunchAnimation = "browser_settings_favorite_launch_animation"
    const val FavoriteAnimationSpeed = "browser_settings_favorite_animation_speed"
    const val OpenHomeOnStartup = "browser_settings_open_home_on_startup"
    const val ScrollBar = "browser_settings_scroll_bar"
    const val TranslationProvider = "browser_settings_translation_provider"
    const val ExternalLinkPreview = "browser_settings_external_link_preview"
    const val ExternalLinkPreviewProfile = "browser_settings_external_link_preview_profile"
    const val ExternalAppLinks = "browser_settings_external_app_links"
    const val BrowserEngine = "browser_settings_engine"
}

internal enum class BrowserSettingsSection { All, Overview, Startup, Favorites, Websites, Links }

@Composable
internal fun BrowserSettingsPage(
    section: BrowserSettingsSection = BrowserSettingsSection.All,
    navigationContent: @Composable ColumnScope.() -> Unit = {},
    appLanguageTag: String = "",
    supportedAppLocales: List<Locale> = emptyList(),
    onAppLanguageChanged: (String) -> Unit = {},
    browserEngineKind: AndroidBrowserEngineKind = AndroidBrowserEngineKind.GeckoView,
    pageTranslationProvider: PageTranslationProvider,
    isExternalLinkPreviewEnabled: Boolean = false,
    externalLinkPreviewProfileId: String? = null,
    externalLinkPreviewProfiles: List<BrowserProfile> = emptyList(),
    externalAppLinkHandling: ExternalAppLinkHandling = ExternalAppLinkHandling.Default,
    isFullImmersiveModeEnabled: Boolean,
    isStartupAnimationEnabled: Boolean,
    startupAddressFocusMode: StartupAddressFocusMode = StartupAddressFocusMode.Default,
    isFavoriteLaunchAnimationEnabled: Boolean = true,
    favoriteAnimationSpeed: FavoriteAnimationSpeed = FavoriteAnimationSpeed.Default,
    isOpenHomeOnStartupEnabled: Boolean = false,
    isScrollBarEnabled: Boolean,
    isDefaultBrowser: Boolean,
    onBrowserEngineKindChanged: (AndroidBrowserEngineKind) -> Unit = {},
    onExternalLinkPreviewEnabledChanged: (Boolean) -> Unit = {},
    onExternalLinkPreviewProfileChanged: (String?) -> Unit = {},
    onExternalAppLinkHandlingChanged: (ExternalAppLinkHandling) -> Unit = {},
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
    onStartupAnimationEnabledChanged: (Boolean) -> Unit,
    onStartupAddressFocusModeChanged: (StartupAddressFocusMode) -> Unit = {},
    onFavoriteLaunchAnimationEnabledChanged: (Boolean) -> Unit = {},
    onFavoriteAnimationSpeedChanged: (FavoriteAnimationSpeed) -> Unit = {},
    onImportFavoriteBookmarks: () -> Unit = {},
    onOpenHomeOnStartupEnabledChanged: (Boolean) -> Unit = {},
    onScrollBarEnabledChanged: (Boolean) -> Unit,
    onPageTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit,
    onBack: () -> Unit,
    onLinkRouting: () -> Unit = {},
    onDeviceSearch: () -> Unit = {},
    onExternalLinkPreview: () -> Unit = {},
) {
    var engineMenuExpanded by remember { mutableStateOf(false) }
    var startupAddressFocusMenuExpanded by remember { mutableStateOf(false) }
    var favoriteSpeedMenuExpanded by remember { mutableStateOf(false) }
    var externalAppLinksMenuExpanded by remember { mutableStateOf(false) }
    SettingsPage(
        title = stringResource(when (section) {
            BrowserSettingsSection.Startup -> R.string.settings_browser_group_startup
            BrowserSettingsSection.Favorites -> R.string.settings_browser_group_favorites
            BrowserSettingsSection.Websites -> R.string.settings_browser_group_websites
            BrowserSettingsSection.Links -> R.string.settings_browser_group_links
            else -> R.string.settings_section_browser
        }),
        onBack = onBack,
    ) {
        if (section == BrowserSettingsSection.All || section == BrowserSettingsSection.Overview) {
            SettingsSectionTitle(stringResource(R.string.settings_browser_group_general))
            SettingsPreferenceGroup {
                if (supportedAppLocales.isNotEmpty()) {
                    AppLanguageSettings(
                        languageTag = appLanguageTag,
                        supportedLocales = supportedAppLocales,
                        onLanguageChanged = onAppLanguageChanged,
                    )
                    SettingsPreferenceDivider()
                }
                if (!BuildConfig.SYSTEM_WEBVIEW_ONLY) {
                    Box {
                        SettingsChoice(
                            title = stringResource(R.string.settings_browser_engine_title),
                            value = browserEngineKind.displayName(),
                            expanded = engineMenuExpanded,
                            onClick = { engineMenuExpanded = true },
                            modifier = Modifier.testTag(BrowserSettingsTestTags.BrowserEngine),
                        )
                        SettingsDropdown(
                            expanded = engineMenuExpanded,
                            onDismissRequest = { engineMenuExpanded = false },
                        ) {
                            AndroidBrowserEngineKind.entries.forEach { kind ->
                                SettingsDropdownItem(
                                    label = kind.displayName(),
                                    selected = kind == browserEngineKind,
                                    onClick = {
                                        engineMenuExpanded = false
                                        if (kind != browserEngineKind) onBrowserEngineKindChanged(kind)
                                    },
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(
                            when (browserEngineKind) {
                                AndroidBrowserEngineKind.GeckoView ->
                                    R.string.settings_browser_engine_gecko_summary
                                AndroidBrowserEngineKind.SystemWebView ->
                                    R.string.settings_browser_engine_system_summary
                            },
                        ),
                        modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_browser_engine_restart_warning),
                        modifier = Modifier.padding(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    SettingsPreferenceDivider()
                }
                SettingsLink(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.settings_default_browser),
                    subtitle = stringResource(
                        if (isDefaultBrowser) R.string.settings_default_browser_active
                        else R.string.settings_make_default_browser,
                    ),
                    onClick = onOpenDefaultBrowserSettings,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        if (section == BrowserSettingsSection.All || section == BrowserSettingsSection.Startup) {
            SettingsSectionTitle(stringResource(R.string.settings_browser_group_startup))
            SettingsPreferenceGroup {
                SettingsSwitch(
                    title = stringResource(R.string.settings_startup_animation_title),
                    subtitle = stringResource(R.string.settings_startup_animation_subtitle),
                    checked = isStartupAnimationEnabled,
                    onCheckedChange = onStartupAnimationEnabledChanged,
                    modifier = Modifier.testTag(BrowserSettingsTestTags.StartupAnimation),
                )
                SettingsPreferenceDivider()
                Box {
                    SettingsChoice(
                        title = stringResource(R.string.settings_startup_address_focus_title),
                        value = startupAddressFocusMode.displayName(),
                        expanded = startupAddressFocusMenuExpanded,
                        onClick = { startupAddressFocusMenuExpanded = true },
                        modifier = Modifier.testTag(BrowserSettingsTestTags.StartupAddressFocus),
                    )
                    SettingsDropdown(
                        expanded = startupAddressFocusMenuExpanded,
                        onDismissRequest = { startupAddressFocusMenuExpanded = false },
                    ) {
                        StartupAddressFocusMode.entries.forEach { mode ->
                            SettingsDropdownItem(
                                label = mode.displayName(),
                                selected = mode == startupAddressFocusMode,
                                onClick = {
                                    startupAddressFocusMenuExpanded = false
                                    if (mode != startupAddressFocusMode) {
                                        onStartupAddressFocusModeChanged(mode)
                                    }
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_startup_address_focus_summary),
                    modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPreferenceDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_open_home_on_startup_title),
                    subtitle = stringResource(R.string.settings_open_home_on_startup_subtitle),
                    checked = isOpenHomeOnStartupEnabled,
                    onCheckedChange = onOpenHomeOnStartupEnabledChanged,
                    modifier = Modifier.testTag(BrowserSettingsTestTags.OpenHomeOnStartup),
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        if (section == BrowserSettingsSection.All || section == BrowserSettingsSection.Favorites) {
            SettingsSectionTitle(stringResource(R.string.settings_browser_group_favorites))
            SettingsPreferenceGroup {
                SettingsSwitch(
                    title = stringResource(R.string.settings_favorite_launch_animation_title),
                    subtitle = stringResource(R.string.settings_favorite_launch_animation_subtitle),
                    checked = isFavoriteLaunchAnimationEnabled,
                    onCheckedChange = onFavoriteLaunchAnimationEnabledChanged,
                    modifier = Modifier.testTag(BrowserSettingsTestTags.FavoriteLaunchAnimation),
                )
                SettingsPreferenceDivider()
                Box {
                    SettingsChoice(
                        title = stringResource(R.string.settings_favorite_animation_speed_title),
                        value = favoriteAnimationSpeed.displayName(),
                        expanded = isFavoriteLaunchAnimationEnabled && favoriteSpeedMenuExpanded,
                        onClick = { favoriteSpeedMenuExpanded = true },
                        modifier = Modifier.testTag(BrowserSettingsTestTags.FavoriteAnimationSpeed),
                        enabled = isFavoriteLaunchAnimationEnabled,
                    )
                    SettingsDropdown(
                        expanded = isFavoriteLaunchAnimationEnabled && favoriteSpeedMenuExpanded,
                        onDismissRequest = { favoriteSpeedMenuExpanded = false },
                    ) {
                        FavoriteAnimationSpeed.entries.forEach { speed ->
                            SettingsDropdownItem(
                                label = speed.displayName(),
                                selected = speed == favoriteAnimationSpeed,
                                onClick = {
                                    favoriteSpeedMenuExpanded = false
                                    if (speed != favoriteAnimationSpeed) {
                                        onFavoriteAnimationSpeedChanged(speed)
                                    }
                                },
                            )
                        }
                    }
                }
                SettingsPreferenceDivider()
                SettingsLink(
                    icon = Icons.Filled.Favorite,
                    title = stringResource(R.string.settings_favorite_bookmark_import_title),
                    subtitle = stringResource(R.string.settings_favorite_bookmark_import_summary),
                    onClick = onImportFavoriteBookmarks,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        if (section == BrowserSettingsSection.All || section == BrowserSettingsSection.Websites) {
            SettingsSectionTitle(stringResource(R.string.settings_browser_group_websites))
            SettingsPreferenceGroup {
                Box(Modifier.settingsSearchTarget(stringResource(R.string.device_search_title))) {
                    SettingsLink(
                        icon = Icons.Default.Search,
                        title = stringResource(R.string.device_search_title),
                        subtitle = stringResource(R.string.device_search_settings_summary),
                        onClick = onDeviceSearch,
                    )
                }
                SettingsPreferenceDivider()
                SettingsLink(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.link_routing_title),
                    subtitle = stringResource(R.string.link_routing_summary),
                    onClick = onLinkRouting,
                )
                SettingsPreferenceDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_full_immersive_mode_title),
                    subtitle = stringResource(R.string.settings_full_immersive_mode_subtitle),
                    checked = isFullImmersiveModeEnabled,
                    onCheckedChange = onFullImmersiveModeEnabledChanged,
                )
                SettingsPreferenceDivider()
                SettingsSwitch(
                    title = stringResource(R.string.settings_scroll_bar_title),
                    subtitle = stringResource(R.string.settings_scroll_bar_subtitle),
                    checked = isScrollBarEnabled,
                    onCheckedChange = onScrollBarEnabledChanged,
                    modifier = Modifier.testTag(BrowserSettingsTestTags.ScrollBar),
                )
                SettingsPreferenceDivider()
                TranslationProviderSettings(
                    provider = pageTranslationProvider,
                    strings = TranslationProviderSettingsStrings(
                        title = stringResource(R.string.settings_translation_provider),
                        providerNames = PageTranslationProvider.entries.associateWith { it.displayName },
                        providerSummaries = mapOf(
                            PageTranslationProvider.Google to stringResource(
                                R.string.settings_translation_provider_google_summary,
                            ),
                            PageTranslationProvider.Yandex to stringResource(
                                R.string.settings_translation_provider_summary,
                            ),
                            PageTranslationProvider.Kagi to stringResource(
                                R.string.settings_translation_provider_kagi_summary,
                            ),
                        ),
                    ),
                    containerColor = browserChromeColor(MaterialTheme.colorScheme.surfaceContainerHigh),
                    enabled = true,
                    onProviderChanged = onPageTranslationProviderChanged,
                    modifier = Modifier.testTag(BrowserSettingsTestTags.TranslationProvider),
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        if (section == BrowserSettingsSection.All || section == BrowserSettingsSection.Links) {
            SettingsSectionTitle(stringResource(R.string.settings_browser_group_links))
            SettingsPreferenceGroup {
                Box {
                    SettingsChoice(
                        title = stringResource(R.string.settings_external_app_links_title),
                        value = externalAppLinkHandling.displayName(),
                        expanded = externalAppLinksMenuExpanded,
                        onClick = { externalAppLinksMenuExpanded = true },
                        modifier = Modifier.testTag(BrowserSettingsTestTags.ExternalAppLinks),
                    )
                    SettingsDropdown(
                        expanded = externalAppLinksMenuExpanded,
                        onDismissRequest = { externalAppLinksMenuExpanded = false },
                    ) {
                        ExternalAppLinkHandling.entries.forEach { handling ->
                            SettingsDropdownItem(
                                label = handling.displayName(),
                                selected = handling == externalAppLinkHandling,
                                onClick = {
                                    externalAppLinksMenuExpanded = false
                                    if (handling != externalAppLinkHandling) {
                                        onExternalAppLinkHandlingChanged(handling)
                                    }
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_external_app_links_summary),
                    modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsPreferenceDivider()
                dev.sk2andy.materialbrowser.shared.ui.settings.SettingsSelectionLink(
                    title = stringResource(R.string.settings_external_link_preview_title),
                    value = stringResource(R.string.settings_external_link_preview_subtitle),
                    onClick = onExternalLinkPreview,
                    modifier = Modifier.testTag(BrowserSettingsTestTags.ExternalLinkPreview),
                )
            }
        }
        navigationContent()
    }
}

@Composable
private fun AndroidBrowserEngineKind.displayName(): String = when (this) {
    AndroidBrowserEngineKind.GeckoView ->
        stringResource(R.string.settings_browser_engine_gecko)
    AndroidBrowserEngineKind.SystemWebView ->
        stringResource(R.string.settings_browser_engine_system)
}

@Composable
private fun FavoriteAnimationSpeed.displayName(): String = when (this) {
    FavoriteAnimationSpeed.Relaxed ->
        stringResource(R.string.settings_favorite_animation_speed_relaxed)
    FavoriteAnimationSpeed.Normal ->
        stringResource(R.string.settings_favorite_animation_speed_normal)
    FavoriteAnimationSpeed.Fast ->
        stringResource(R.string.settings_favorite_animation_speed_fast)
}

@Composable
private fun StartupAddressFocusMode.displayName(): String = when (this) {
    StartupAddressFocusMode.WhenStartupAnimationDisabled ->
        stringResource(R.string.settings_startup_address_focus_current)
    StartupAddressFocusMode.Always ->
        stringResource(R.string.settings_startup_address_focus_always)
    StartupAddressFocusMode.Never ->
        stringResource(R.string.settings_startup_address_focus_never)
}

@Composable
private fun ExternalAppLinkHandling.displayName(): String = when (this) {
    ExternalAppLinkHandling.Automatic ->
        stringResource(R.string.settings_external_app_links_automatic)
    ExternalAppLinkHandling.AskEveryTime ->
        stringResource(R.string.settings_external_app_links_ask_every_time)
}

@Composable
internal fun ExternalLinkPreviewSettingsPage(
    isExternalLinkPreviewEnabled: Boolean,
    externalLinkPreviewProfileId: String?,
    externalLinkPreviewProfiles: List<BrowserProfile>,
    onExternalLinkPreviewEnabledChanged: (Boolean) -> Unit,
    onExternalLinkPreviewProfileChanged: (String?) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPage(title = stringResource(R.string.settings_external_link_preview_title), onBack = onBack) {
        Text(stringResource(R.string.settings_external_link_preview_subtitle), style = MaterialTheme.typography.bodyMedium)
        SettingsPageSpacer()
        SettingsFeatureIllustration(
            SettingsIllustrationKind.LinkPreview,
            active = isExternalLinkPreviewEnabled,
        )
        SettingsPageSpacer()
        var previewProfileMenuExpanded by remember { mutableStateOf(false) }
        val selectedPreviewProfile = externalLinkPreviewProfiles.firstOrNull {
            it.id == externalLinkPreviewProfileId
        }
        val previewProfileUnavailable =
            externalLinkPreviewProfileId != null && selectedPreviewProfile == null
        SettingsPreferenceGroup {
            SettingsSwitch(
                title = stringResource(R.string.settings_external_link_preview_title),
                subtitle = stringResource(R.string.settings_external_link_preview_subtitle),
                checked = isExternalLinkPreviewEnabled,
                prominent = true,
                onCheckedChange = onExternalLinkPreviewEnabledChanged,
                modifier = Modifier.testTag(BrowserSettingsTestTags.ExternalLinkPreview),
            )
            SettingsPreferenceDivider()
            Box {
                SettingsChoice(
                    title = stringResource(R.string.settings_external_link_preview_profile_title),
                    value = selectedPreviewProfile?.emoji
                        ?: stringResource(
                            if (previewProfileUnavailable) {
                                R.string.settings_external_link_preview_profile_unavailable
                            } else {
                                R.string.settings_external_link_preview_profile_last_used
                            },
                        ),
                    expanded = isExternalLinkPreviewEnabled && previewProfileMenuExpanded,
                    onClick = { if (isExternalLinkPreviewEnabled) previewProfileMenuExpanded = true },
                    modifier = Modifier.testTag(BrowserSettingsTestTags.ExternalLinkPreviewProfile),
                    enabled = isExternalLinkPreviewEnabled,
                    inPreferenceGroup = true,
                )
                SettingsDropdown(
                    expanded = isExternalLinkPreviewEnabled && previewProfileMenuExpanded,
                    onDismissRequest = { previewProfileMenuExpanded = false },
                ) {
                    SettingsDropdownItem(
                        label = stringResource(R.string.settings_external_link_preview_profile_last_used),
                        selected = externalLinkPreviewProfileId == null || previewProfileUnavailable,
                        onClick = {
                            previewProfileMenuExpanded = false
                            onExternalLinkPreviewProfileChanged(null)
                        },
                    )
                    externalLinkPreviewProfiles.forEach { profile ->
                        SettingsDropdownItem(
                            label = profile.emoji,
                            selected = profile.id == externalLinkPreviewProfileId,
                            onClick = {
                                previewProfileMenuExpanded = false
                                onExternalLinkPreviewProfileChanged(profile.id)
                            },
                        )
                    }
                }
            }
        }
    }
}
