package dev.sk2andy.materialbrowser.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import dev.sk2andy.materialbrowser.R

@Composable
internal fun AppearanceSubpageLinks(onDestinationChanged: (SettingsDestination) -> Unit) {
    SettingsPageSpacer()
    SettingsPreferenceGroup {
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_visibility),
            title = stringResource(R.string.settings_colors_title),
            subtitle = stringResource(R.string.settings_appearance_mode),
            onClick = { onDestinationChanged(SettingsDestination.AppearanceColors) },
        )
        SettingsPreferenceDivider()
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_symbol_route),
            title = stringResource(R.string.settings_address_bar_style),
            subtitle = stringResource(R.string.settings_address_bar_color) + " · " +
                stringResource(R.string.settings_address_load_style),
            onClick = { onDestinationChanged(SettingsDestination.AppearanceAddressBar) },
        )
        SettingsPreferenceDivider()
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_visibility),
            title = stringResource(R.string.settings_surfaces_shapes_title),
            subtitle = stringResource(R.string.settings_surface_style_summary),
            onClick = { onDestinationChanged(SettingsDestination.AppearanceSurfaces) },
        )
        SettingsPreferenceDivider()
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_symbol_open_in_new),
            title = stringResource(R.string.settings_browser_group_websites),
            subtitle = stringResource(R.string.settings_web_content_font_size) + " · " +
                stringResource(R.string.settings_force_dark_websites),
            onClick = { onDestinationChanged(SettingsDestination.AppearanceWebsites) },
        )
    }
}

@Composable
internal fun BrowserSubpageLinks(onDestinationChanged: (SettingsDestination) -> Unit) {
    SettingsPageSpacer()
    SettingsPreferenceGroup {
        SettingsLink(
            icon = Icons.Default.Home,
            title = stringResource(R.string.settings_browser_group_startup),
            subtitle = stringResource(R.string.settings_startup_animation_title) + " · " +
                stringResource(R.string.settings_startup_address_focus_title),
            onClick = { onDestinationChanged(SettingsDestination.BrowserStartup) },
        )
        SettingsPreferenceDivider()
        SettingsLink(
            icon = Icons.Default.Star,
            title = stringResource(R.string.settings_browser_group_favorites),
            subtitle = stringResource(R.string.settings_favorite_launch_animation_title) + " · " +
                stringResource(R.string.settings_favorite_bookmark_import_title),
            onClick = { onDestinationChanged(SettingsDestination.BrowserFavorites) },
        )
        SettingsPreferenceDivider()
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_symbol_translate),
            title = stringResource(R.string.settings_browser_group_websites),
            subtitle = stringResource(R.string.device_search_title) + " · " +
                stringResource(R.string.link_routing_title) + " · " +
                stringResource(R.string.settings_translation_provider),
            onClick = { onDestinationChanged(SettingsDestination.BrowserWebsites) },
        )
        SettingsPreferenceDivider()
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_symbol_open_in_new),
            title = stringResource(R.string.settings_browser_group_links),
            subtitle = stringResource(R.string.settings_external_app_links_title) + " · " +
                stringResource(R.string.settings_external_link_preview_title),
            onClick = { onDestinationChanged(SettingsDestination.BrowserLinks) },
        )
        SettingsPreferenceDivider()
        SettingsLink(
            icon = ImageVector.vectorResource(R.drawable.ic_player_volume),
            title = stringResource(R.string.reader_voice_settings_title),
            subtitle = stringResource(R.string.reader_voice_settings_summary),
            onClick = { onDestinationChanged(SettingsDestination.ReadAloud) },
        )
    }
}
