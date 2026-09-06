package dev.sk2andy.materialbrowser.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import dev.sk2andy.materialbrowser.R

@Composable
internal fun SettingsHomePage(
    downloadSummary: String,
    onDestinationChanged: (SettingsDestination) -> Unit,
    onDismiss: () -> Unit,
    onOpenFirefoxExtensions: (() -> Unit)? = null,
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
            icon = Icons.Default.Refresh,
            title = stringResource(R.string.sync_settings_title),
            subtitle = stringResource(R.string.settings_home_sync_summary),
            onClick = { onDestinationChanged(SettingsDestination.Sync) },
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
        onOpenFirefoxExtensions?.let { openExtensions ->
            SettingsLink(
                icon = ImageVector.vectorResource(R.drawable.ic_symbol_extension),
                title = stringResource(R.string.gecko_extensions_title),
                subtitle = stringResource(R.string.gecko_extensions_summary),
                onClick = openExtensions,
            )
            SettingsPageSpacer()
        }
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
