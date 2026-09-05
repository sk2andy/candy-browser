package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

internal object BrowserSettingsTestTags {
    const val StartupAnimation = "browser_settings_startup_animation"
    const val OpenHomeOnStartup = "browser_settings_open_home_on_startup"
    const val ScrollBar = "browser_settings_scroll_bar"
    const val TranslationProvider = "browser_settings_translation_provider"
    const val ExternalLinkPreview = "browser_settings_external_link_preview"
}

@Composable
internal fun BrowserSettingsPage(
    pageTranslationProvider: PageTranslationProvider,
    isExternalLinkPreviewEnabled: Boolean = false,
    isFullImmersiveModeEnabled: Boolean,
    isStartupAnimationEnabled: Boolean,
    isOpenHomeOnStartupEnabled: Boolean = false,
    isScrollBarEnabled: Boolean,
    isVideoAutoplayBlocked: Boolean,
    isVideoAutoplayBlockingSupported: Boolean,
    isDefaultBrowser: Boolean,
    onExternalLinkPreviewEnabledChanged: (Boolean) -> Unit = {},
    onFullImmersiveModeEnabledChanged: (Boolean) -> Unit,
    onStartupAnimationEnabledChanged: (Boolean) -> Unit,
    onOpenHomeOnStartupEnabledChanged: (Boolean) -> Unit = {},
    onScrollBarEnabledChanged: (Boolean) -> Unit,
    onVideoAutoplayBlockedChanged: (Boolean) -> Unit,
    onPageTranslationProviderChanged: (PageTranslationProvider) -> Unit,
    onOpenDefaultBrowserSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var translationProviderMenuExpanded by remember { mutableStateOf(false) }
    SettingsPage(
        title = stringResource(R.string.settings_section_browser),
        onBack = onBack,
    ) {
        SettingsSwitch(
            title = stringResource(R.string.settings_startup_animation_title),
            subtitle = stringResource(R.string.settings_startup_animation_subtitle),
            checked = isStartupAnimationEnabled,
            onCheckedChange = onStartupAnimationEnabledChanged,
            modifier = Modifier.testTag(BrowserSettingsTestTags.StartupAnimation),
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
        Box {
            SettingsChoice(
                title = stringResource(R.string.settings_translation_provider),
                value = pageTranslationProvider.displayName,
                expanded = translationProviderMenuExpanded,
                onClick = { translationProviderMenuExpanded = true },
                modifier = Modifier.testTag(BrowserSettingsTestTags.TranslationProvider),
            )
            SettingsDropdown(
                expanded = translationProviderMenuExpanded,
                onDismissRequest = { translationProviderMenuExpanded = false },
            ) {
                PageTranslationProvider.entries.forEach { provider ->
                    SettingsDropdownItem(
                        label = provider.displayName,
                        selected = provider == pageTranslationProvider,
                        onClick = {
                            translationProviderMenuExpanded = false
                            onPageTranslationProviderChanged(provider)
                        },
                    )
                }
            }
        }
        Text(
            stringResource(
                when (pageTranslationProvider) {
                    PageTranslationProvider.Google ->
                        R.string.settings_translation_provider_google_summary
                    PageTranslationProvider.Yandex ->
                        R.string.settings_translation_provider_summary
                    PageTranslationProvider.Kagi ->
                        R.string.settings_translation_provider_kagi_summary
                },
            ),
            modifier = Modifier.padding(start = 18.dp, top = 6.dp, end = 18.dp),
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
