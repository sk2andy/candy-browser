package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.ExternalAppLinkHandling
import dev.sk2andy.materialbrowser.browser.FavoriteAnimationSpeed
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.shared.ui.settings.TranslationProviderSettings
import dev.sk2andy.materialbrowser.shared.ui.settings.TranslationProviderSettingsStrings
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object BrowserSettingsTestTags {
    const val StartupAnimation = "browser_settings_startup_animation"
    const val StartupAddressFocus = "browser_settings_startup_address_focus"
    const val FavoriteLaunchAnimation = "browser_settings_favorite_launch_animation"
    const val FavoriteAnimationSpeed = "browser_settings_favorite_animation_speed"
    const val OpenHomeOnStartup = "browser_settings_open_home_on_startup"
    const val ScrollBar = "browser_settings_scroll_bar"
    const val TranslationProvider = "browser_settings_translation_provider"
    const val ExternalLinkPreview = "browser_settings_external_link_preview"
    const val ExternalAppLinks = "browser_settings_external_app_links"
    const val BrowserEngine = "browser_settings_engine"
    const val InlineMediaPlayer = "browser_settings_inline_media_player"
}

@Composable
internal fun BrowserSettingsPage(
    browserEngineKind: AndroidBrowserEngineKind = AndroidBrowserEngineKind.GeckoView,
    pageTranslationProvider: PageTranslationProvider,
    isExternalLinkPreviewEnabled: Boolean = false,
    externalAppLinkHandling: ExternalAppLinkHandling = ExternalAppLinkHandling.Default,
    isFullImmersiveModeEnabled: Boolean,
    isStartupAnimationEnabled: Boolean,
    startupAddressFocusMode: StartupAddressFocusMode = StartupAddressFocusMode.Default,
    isFavoriteLaunchAnimationEnabled: Boolean = true,
    favoriteAnimationSpeed: FavoriteAnimationSpeed = FavoriteAnimationSpeed.Default,
    isOpenHomeOnStartupEnabled: Boolean = false,
    isScrollBarEnabled: Boolean,
    isVideoAutoplayBlocked: Boolean,
    isVideoAutoplayBlockingSupported: Boolean,
    isInlineMediaPlayerEnabled: Boolean = false,
    isInlineMediaPlayerSupported: Boolean = true,
    isDefaultBrowser: Boolean,
    onBrowserEngineKindChanged: (AndroidBrowserEngineKind) -> Unit = {},
    onExternalLinkPreviewEnabledChanged: (Boolean) -> Unit = {},
    onExternalAppLinkHandlingChanged: (ExternalAppLinkHandling) -> Unit = {},
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
    onStartupAnimationEnabledChanged: (Boolean) -> Unit,
    onStartupAddressFocusModeChanged: (StartupAddressFocusMode) -> Unit = {},
    onFavoriteLaunchAnimationEnabledChanged: (Boolean) -> Unit = {},
    onFavoriteAnimationSpeedChanged: (FavoriteAnimationSpeed) -> Unit = {},
    onImportFavoriteBookmarks: () -> Unit = {},
    onOpenHomeOnStartupEnabledChanged: (Boolean) -> Unit = {},
    onScrollBarEnabledChanged: (Boolean) -> Unit,
    onVideoAutoplayBlockedChanged: (Boolean) -> Unit,
    onInlineMediaPlayerEnabledChanged: (Boolean) -> Unit = {},
    onPageTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var engineMenuExpanded by remember { mutableStateOf(false) }
    var startupAddressFocusMenuExpanded by remember { mutableStateOf(false) }
    var favoriteSpeedMenuExpanded by remember { mutableStateOf(false) }
    var externalAppLinksMenuExpanded by remember { mutableStateOf(false) }
    SettingsPage(
        title = stringResource(R.string.settings_section_browser),
        onBack = onBack,
    ) {
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
                modifier = Modifier.padding(start = 18.dp, top = 4.dp, end = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
        SettingsSwitch(
            title = stringResource(R.string.settings_startup_animation_title),
            subtitle = stringResource(R.string.settings_startup_animation_subtitle),
            checked = isStartupAnimationEnabled,
            onCheckedChange = onStartupAnimationEnabledChanged,
            modifier = Modifier.testTag(BrowserSettingsTestTags.StartupAnimation),
        )
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_favorite_launch_animation_title),
            subtitle = stringResource(R.string.settings_favorite_launch_animation_subtitle),
            checked = isFavoriteLaunchAnimationEnabled,
            onCheckedChange = onFavoriteLaunchAnimationEnabledChanged,
            modifier = Modifier.testTag(BrowserSettingsTestTags.FavoriteLaunchAnimation),
        )
        Spacer(Modifier.height(8.dp))
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_favorite_animation_speed_title),
                value = favoriteAnimationSpeed.displayName(),
                expanded = favoriteSpeedMenuExpanded,
                onClick = { favoriteSpeedMenuExpanded = true },
                modifier = Modifier.testTag(BrowserSettingsTestTags.FavoriteAnimationSpeed),
            )
            SettingsDropdown(
                expanded = favoriteSpeedMenuExpanded,
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
        Spacer(Modifier.height(8.dp))
        SettingsLink(
            icon = Icons.Filled.Favorite,
            title = stringResource(R.string.settings_favorite_bookmark_import_title),
            subtitle = stringResource(R.string.settings_favorite_bookmark_import_summary),
            onClick = onImportFavoriteBookmarks,
        )
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_open_home_on_startup_title),
            subtitle = stringResource(R.string.settings_open_home_on_startup_subtitle),
            checked = isOpenHomeOnStartupEnabled,
            onCheckedChange = onOpenHomeOnStartupEnabledChanged,
            modifier = Modifier.testTag(BrowserSettingsTestTags.OpenHomeOnStartup),
        )
        Spacer(Modifier.height(8.dp))
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
        SettingsSwitch(
            title = stringResource(R.string.settings_inline_media_player_title),
            subtitle = stringResource(
                if (isInlineMediaPlayerSupported) {
                    R.string.settings_inline_media_player_subtitle
                } else {
                    R.string.settings_inline_media_player_unsupported
                },
            ),
            checked = isInlineMediaPlayerEnabled,
            enabled = isInlineMediaPlayerSupported,
            onCheckedChange = onInlineMediaPlayerEnabledChanged,
            modifier = Modifier.testTag(BrowserSettingsTestTags.InlineMediaPlayer),
        )
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(8.dp))
        SettingsSwitch(
            title = stringResource(R.string.settings_external_link_preview_title),
            subtitle = stringResource(R.string.settings_external_link_preview_subtitle),
            checked = isExternalLinkPreviewEnabled,
            onCheckedChange = onExternalLinkPreviewEnabledChanged,
            modifier = Modifier.testTag(BrowserSettingsTestTags.ExternalLinkPreview),
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
