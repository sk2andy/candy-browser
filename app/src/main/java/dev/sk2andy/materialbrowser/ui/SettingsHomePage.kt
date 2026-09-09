package dev.sk2andy.materialbrowser.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHomeIcon
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHomeLabel
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHomeResources
import dev.sk2andy.materialbrowser.shared.ui.settings.SettingsHomePage as SharedSettingsHomePage
import dev.sk2andy.materialbrowser.ui.theme.browserChromeColor

@Composable
internal fun SettingsHomePage(
    downloadSummary: String,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onDismiss: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)? = null,
    developerOptionsUnlocked: Boolean = false,
    onUnlockDeveloperOptions: (() -> Unit)? = null,
) {
    SharedSettingsHomePage(
        downloadSummary = downloadSummary,
        resources = AndroidSettingsHomeResources,
        linkContainerColor = browserChromeColor(
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        icon = { icon, modifier, tint ->
            AndroidSettingsHomeIcon(
                icon = icon,
                modifier = modifier,
                tint = tint,
            )
        },
        onDestinationChanged = onDestinationChanged,
        onDismiss = onDismiss,
        onOpenFirefoxExtensions = onOpenFirefoxExtensions,
        developerOptionsUnlocked = developerOptionsUnlocked,
        onUnlockDeveloperOptions = onUnlockDeveloperOptions,
    )
}

private object AndroidSettingsHomeResources : SettingsHomeResources {
    @Composable
    override fun text(label: SettingsHomeLabel): String = stringResource(
        when (label) {
            SettingsHomeLabel.Title -> R.string.settings_title
            SettingsHomeLabel.Back -> R.string.action_back
            SettingsHomeLabel.SearchTitle -> R.string.settings_section_search
            SettingsHomeLabel.SearchSummary -> R.string.settings_home_search_summary
            SettingsHomeLabel.SyncTitle -> R.string.sync_settings_title
            SettingsHomeLabel.SyncSummary -> R.string.settings_home_sync_summary
            SettingsHomeLabel.TabsAndGesturesTitle -> R.string.settings_tabs_gestures_title
            SettingsHomeLabel.TabsAndGesturesSummary ->
                R.string.settings_home_tabs_gestures_summary
            SettingsHomeLabel.AppearanceTitle -> R.string.settings_appearance_title
            SettingsHomeLabel.AppearanceSummary -> R.string.settings_home_appearance_summary
            SettingsHomeLabel.BrowserTitle -> R.string.settings_section_browser
            SettingsHomeLabel.BrowserSummary -> R.string.settings_home_browser_summary
            SettingsHomeLabel.DownloadsTitle -> R.string.settings_downloads_title
            SettingsHomeLabel.UserscriptsTitle -> R.string.userscript_title
            SettingsHomeLabel.UserscriptsSummary -> R.string.settings_home_userscripts_summary
            SettingsHomeLabel.FirefoxExtensionsTitle -> R.string.gecko_extensions_title
            SettingsHomeLabel.FirefoxExtensionsSummary -> R.string.gecko_extensions_summary
            SettingsHomeLabel.SiteCapsulesTitle -> R.string.capsule_settings_title
            SettingsHomeLabel.SiteCapsulesSummary -> R.string.settings_home_capsules_summary
            SettingsHomeLabel.ProtectionAndDataTitle ->
                R.string.settings_protection_data_title
            SettingsHomeLabel.ProtectionAndDataSummary ->
                R.string.settings_home_protection_summary
            SettingsHomeLabel.DeveloperOptionsTitle -> R.string.developer_options_title
            SettingsHomeLabel.DeveloperOptionsSummary -> R.string.developer_options_summary
            SettingsHomeLabel.UnlockDeveloperOptions ->
                R.string.developer_options_unlock_action
            SettingsHomeLabel.AboutLegalTitle -> R.string.settings_section_about_legal
            SettingsHomeLabel.AboutLegalSummary -> R.string.settings_home_about_summary
        },
    )
}

@Composable
private fun AndroidSettingsHomeIcon(
    icon: SettingsHomeIcon,
    modifier: Modifier,
    tint: Color,
) {
    val vector = when (icon) {
        SettingsHomeIcon.Search -> Icons.Default.Search
        SettingsHomeIcon.Sync -> Icons.Default.Refresh
        SettingsHomeIcon.TabsAndGestures -> Icons.AutoMirrored.Filled.List
        SettingsHomeIcon.Appearance -> Icons.Default.Face
        SettingsHomeIcon.Browser -> Icons.Default.Settings
        SettingsHomeIcon.Downloads -> ImageVector.vectorResource(R.drawable.ic_reader_download)
        SettingsHomeIcon.Userscripts,
        SettingsHomeIcon.FirefoxExtensions,
        -> ImageVector.vectorResource(R.drawable.ic_symbol_extension)
        SettingsHomeIcon.SiteCapsules -> Icons.Default.Favorite
        SettingsHomeIcon.ProtectionAndData -> Icons.Default.Lock
        SettingsHomeIcon.DeveloperOptions -> Icons.Default.Build
        SettingsHomeIcon.AboutLegal -> Icons.Default.Info
    }
    Icon(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}
