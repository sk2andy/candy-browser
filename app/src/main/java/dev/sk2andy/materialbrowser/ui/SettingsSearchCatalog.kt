package dev.sk2andy.materialbrowser.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserAddressBarColorPreset
import dev.sk2andy.materialbrowser.data.BrowserAddressBarStyle
import dev.sk2andy.materialbrowser.data.BrowserAddressLoadStyle
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserColorPalette
import dev.sk2andy.materialbrowser.data.BrowserShapeStyle
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode

internal data class SettingsSearchAvailability(
    val searchSuggestionProvider: SearchSuggestionProvider = SearchSuggestionProvider.DuckDuckGo,
    val hasAppLanguages: Boolean = false,
    val hasBrowserEngineSelection: Boolean = false,
    val supportsAiSearch: Boolean = false,
    val hasSearxngInstance: Boolean = false,
    val hasSearxngSuggestionFallback: Boolean = false,
    val isDnsOverHttpsSupported: Boolean = false,
    val isHttpsOnlySupported: Boolean = false,
    val isVideoAutoplayBlockingSupported: Boolean = false,
    val isInlineMediaPlayerSupported: Boolean = false,
    val isForceDarkWebsitesSupported: Boolean = false,
    val isDeveloperOptionsUnlocked: Boolean = false,
    val isHttpPasswordAutofillSupported: Boolean = false,
    val isGeckoLoggingSupported: Boolean = false,
    val hasFirefoxExtensions: Boolean = false,
    val isBuiltInDownloadManager: Boolean = false,
    val hasOneDmSessionSharing: Boolean = false,
    val isFrostedSurface: Boolean = false,
    val hasUserScriptSupport: Boolean = false,
    val isGeckoEngine: Boolean = false,
)

internal data class SettingsSearchDefinition(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:StringRes val contextRes: Int,
    val destination: SettingsDestination?,
)

@Composable
internal fun settingsSearchEntries(
    availability: SettingsSearchAvailability,
): List<SettingsSearchEntry> = settingsSearchDefinitions(availability).map { definition ->
    SettingsSearchEntry(
        id = definition.id,
        title = stringResource(definition.titleRes),
        description = definition.localizedDescription(),
        context = stringResource(definition.contextRes),
        destination = definition.destination,
    )
}

@Composable
private fun SettingsSearchDefinition.localizedDescription(): String = when (id) {
    "settings_search_engine" -> SearchEngine.entries.joinToString(", ") { it.displayName }
    "settings_search_suggestions" -> listOf(
        stringResource(descriptionRes),
        SearchSuggestionProvider.entries.map { it.displayName() }.joinToString(", "),
    ).joinToString("\n")
    "settings_tab_overview_mode", "settings_tab_stack_folder_mode" ->
        TabOverviewMode.entries.map { it.displayName() }.joinToString(", ")
    "settings_auto_close_tabs" ->
        InactiveTabLifetime.entries.map { it.displayName() }.joinToString(", ")
    "settings_appearance_mode" ->
        BrowserAppearanceMode.entries.map { it.displayName() }.joinToString(", ")
    "settings_color_palette" ->
        BrowserColorPalette.entries.map { it.displayName() }.joinToString(", ")
    "settings_address_bar_color" ->
        BrowserAddressBarColorPreset.entries.map { it.displayName() }.joinToString(", ")
    "settings_surface_style" -> listOf(
        stringResource(descriptionRes),
        BrowserSurfaceStyle.entries.map { it.displayName() }.joinToString(", "),
    ).joinToString("\n")
    "settings_shape_style" ->
        BrowserShapeStyle.entries.map { it.displayName() }.joinToString(", ")
    "settings_address_load_style" ->
        BrowserAddressLoadStyle.entries.map { it.displayName() }.joinToString(", ")
    "settings_address_bar_style" ->
        BrowserAddressBarStyle.entries.map { it.displayName() }.joinToString(", ")
    else -> stringResource(descriptionRes)
}

internal fun settingsSearchDefinitions(
    availability: SettingsSearchAvailability,
): List<SettingsSearchDefinition> = buildList {
    settingsPage(
        destination = SettingsDestination.Search,
        id = "settings_section_search",
        titleRes = R.string.settings_section_search,
        descriptionRes = R.string.settings_home_search_summary,
    ) {
        setting(
            id = "settings_search_engine",
            titleRes = R.string.settings_search_engine,
            descriptionRes = R.string.settings_home_search_summary,
        )
        if (availability.supportsAiSearch) {
            setting(
                id = "settings_ai_mode_toggle_title",
                titleRes = R.string.settings_ai_mode_toggle_title,
                descriptionRes = R.string.settings_ai_mode_toggle_subtitle,
            )
        }
        if (availability.hasSearxngInstance) {
            setting(
                id = "settings_searxng_instance_url",
                titleRes = R.string.settings_searxng_instance_url,
                descriptionRes = R.string.settings_searxng_instance_url_summary,
            )
        }
        setting(
            id = "settings_history_suggestions_title",
            titleRes = R.string.settings_history_suggestions_title,
            descriptionRes = R.string.settings_history_suggestions_summary,
        )
        setting(
            id = "settings_search_suggestions",
            titleRes = R.string.settings_search_suggestions,
            descriptionRes = when (availability.searchSuggestionProvider) {
                SearchSuggestionProvider.None -> R.string.settings_search_suggestions_none_summary
                SearchSuggestionProvider.SearXNG -> R.string.settings_searxng_search_suggestions_summary
                else -> R.string.settings_search_suggestions_summary
            },
        )
        if (availability.hasSearxngSuggestionFallback) {
            setting(
                id = "settings_searxng_suggestion_fallback",
                titleRes = R.string.settings_searxng_suggestion_fallback,
                descriptionRes = R.string.settings_searxng_suggestion_fallback_summary,
            )
        }
    }

    settingsPage(
        destination = SettingsDestination.TabsAndGestures,
        id = "settings_tabs_gestures_title",
        titleRes = R.string.settings_tabs_gestures_title,
        descriptionRes = R.string.settings_home_tabs_gestures_summary,
    ) {
        setting(
            id = "settings_tab_overview_mode",
            titleRes = R.string.settings_tab_overview_mode,
            descriptionRes = R.string.settings_home_tabs_gestures_summary,
        )
        setting(
            id = "settings_tab_list_starts_at_bottom_title",
            titleRes = R.string.settings_tab_list_starts_at_bottom_title,
            descriptionRes = R.string.settings_tab_list_starts_at_bottom_subtitle,
        )
        setting(
            id = "settings_tab_stack_folder_mode",
            titleRes = R.string.settings_tab_stack_folder_mode,
            descriptionRes = R.string.settings_home_tabs_gestures_summary,
        )
        setting(
            id = "settings_automatic_tab_sorting_title",
            titleRes = R.string.settings_automatic_tab_sorting_title,
            descriptionRes = R.string.settings_automatic_tab_sorting_subtitle,
        )
        setting(
            id = "settings_closed_tab_undo_title",
            titleRes = R.string.settings_closed_tab_undo_title,
            descriptionRes = R.string.settings_closed_tab_undo_summary,
        )
        setting(
            id = "settings_resident_tab_limit",
            titleRes = R.string.settings_resident_tab_limit,
            descriptionRes = R.string.settings_home_tabs_gestures_summary,
        )
        setting(
            id = "settings_auto_close_tabs",
            titleRes = R.string.settings_auto_close_tabs,
            descriptionRes = R.string.settings_home_tabs_gestures_summary,
        )
        setting(
            id = "settings_profiles_title",
            titleRes = R.string.settings_profiles_title,
            descriptionRes = R.string.settings_profiles_subtitle,
        )
        setting(
            id = "settings_address_bar_long_press_title",
            titleRes = R.string.settings_address_bar_long_press_title,
            descriptionRes = R.string.settings_address_bar_long_press_summary,
            destination = SettingsDestination.AddressBarLongPressActions,
        )
        setting(
            id = "settings_link_long_press_action",
            titleRes = R.string.settings_link_long_press_action,
            descriptionRes = R.string.settings_home_tabs_gestures_summary,
        )
        setting(
            id = "settings_link_peek_actions_title",
            titleRes = R.string.settings_link_peek_actions_title,
            descriptionRes = R.string.settings_link_peek_actions_summary,
            destination = SettingsDestination.LinkPeekActions,
        )
        setting(
            id = "settings_address_bar_actions_title",
            titleRes = R.string.settings_address_bar_actions_title,
            descriptionRes = R.string.settings_address_bar_actions_summary,
            destination = SettingsDestination.AddressBarActions,
        )
        setting(
            id = "settings_menu_actions_title",
            titleRes = R.string.settings_menu_actions_title,
            descriptionRes = R.string.settings_menu_actions_summary,
            destination = SettingsDestination.MenuActions,
        )
        setting(
            id = "settings_address_bar_docking_title",
            titleRes = R.string.settings_address_bar_docking_title,
            descriptionRes = R.string.settings_address_bar_docking_subtitle,
        )
        setting(
            id = "settings_tab_dismiss_resistance",
            titleRes = R.string.settings_tab_dismiss_resistance,
            descriptionRes = R.string.settings_home_tabs_gestures_summary,
        )
    }

    settingsPage(
        destination = SettingsDestination.Browser,
        id = "settings_section_browser",
        titleRes = R.string.settings_section_browser,
        descriptionRes = R.string.settings_home_browser_summary,
    ) {
        if (availability.hasAppLanguages) {
            setting(
                id = "settings_app_language",
                titleRes = R.string.settings_app_language,
                descriptionRes = R.string.settings_app_language_system,
            )
        }
        if (availability.hasBrowserEngineSelection) {
            setting(
                id = "settings_browser_engine_title",
                titleRes = R.string.settings_browser_engine_title,
                descriptionRes = if (availability.isGeckoEngine) {
                    R.string.settings_browser_engine_gecko_summary
                } else {
                    R.string.settings_browser_engine_system_summary
                },
            )
        }
        setting(
            id = "settings_default_browser",
            titleRes = R.string.settings_default_browser,
            descriptionRes = R.string.settings_make_default_browser,
        )
        setting(
            id = "settings_startup_animation_title",
            titleRes = R.string.settings_startup_animation_title,
            descriptionRes = R.string.settings_startup_animation_subtitle,
        )
        setting(
            id = "settings_startup_address_focus_title",
            titleRes = R.string.settings_startup_address_focus_title,
            descriptionRes = R.string.settings_startup_address_focus_summary,
        )
        setting(
            id = "settings_open_home_on_startup_title",
            titleRes = R.string.settings_open_home_on_startup_title,
            descriptionRes = R.string.settings_open_home_on_startup_subtitle,
        )
        setting(
            id = "settings_favorite_launch_animation_title",
            titleRes = R.string.settings_favorite_launch_animation_title,
            descriptionRes = R.string.settings_favorite_launch_animation_subtitle,
        )
        setting(
            id = "settings_favorite_animation_speed_title",
            titleRes = R.string.settings_favorite_animation_speed_title,
            descriptionRes = R.string.settings_favorite_launch_animation_subtitle,
        )
        setting(
            id = "settings_favorite_bookmark_import_title",
            titleRes = R.string.settings_favorite_bookmark_import_title,
            descriptionRes = R.string.settings_favorite_bookmark_import_summary,
        )
        setting(
            id = "settings_full_immersive_mode_title",
            titleRes = R.string.settings_full_immersive_mode_title,
            descriptionRes = R.string.settings_full_immersive_mode_subtitle,
        )
        setting(
            id = "settings_scroll_bar_title",
            titleRes = R.string.settings_scroll_bar_title,
            descriptionRes = R.string.settings_scroll_bar_subtitle,
        )
        setting(
            id = "settings_translation_provider",
            titleRes = R.string.settings_translation_provider,
            descriptionRes = R.string.settings_translation_provider_summary,
        )
        setting(
            id = "settings_external_app_links_title",
            titleRes = R.string.settings_external_app_links_title,
            descriptionRes = R.string.settings_external_app_links_summary,
        )
        setting(
            id = "settings_external_link_preview_title",
            titleRes = R.string.settings_external_link_preview_title,
            descriptionRes = R.string.settings_external_link_preview_subtitle,
        )
    }

    settingsPage(
        destination = SettingsDestination.Player,
        id = "settings_player_title",
        titleRes = R.string.settings_player_title,
        descriptionRes = R.string.settings_home_player_summary,
    ) {
        if (availability.isVideoAutoplayBlockingSupported) {
            setting(
                id = "settings_video_autoplay_title",
                titleRes = R.string.settings_video_autoplay_title,
                descriptionRes = R.string.settings_video_autoplay_subtitle,
            )
            setting(
                id = "settings_inline_media_player_title",
                titleRes = R.string.settings_inline_media_player_title,
                descriptionRes = R.string.settings_inline_media_player_subtitle,
            )
            setting(
                id = "settings_inline_media_player_seek_backward",
                titleRes = R.string.settings_inline_media_player_seek_backward,
                descriptionRes = R.string.settings_inline_media_player_seek_subtitle,
            )
            setting(
                id = "settings_inline_media_player_seek_forward",
                titleRes = R.string.settings_inline_media_player_seek_forward,
                descriptionRes = R.string.settings_inline_media_player_seek_subtitle,
            )
        }
    }

    settingsPage(
        destination = SettingsDestination.Downloads,
        id = "settings_downloads_title",
        titleRes = R.string.settings_downloads_title,
        descriptionRes = R.string.settings_download_manager_ask,
    ) {
        setting(
            id = "settings_download_manager_title",
            titleRes = R.string.settings_download_manager_title,
            descriptionRes = R.string.settings_download_manager_ask,
        )
        if (availability.isBuiltInDownloadManager) {
            setting(
                id = "settings_download_folder_title",
                titleRes = R.string.settings_download_folder_title,
                descriptionRes = R.string.settings_download_folder_summary,
            )
        }
        if (availability.hasOneDmSessionSharing) {
            setting(
                id = "settings_download_one_dm_session_title",
                titleRes = R.string.settings_download_one_dm_session_title,
                descriptionRes = R.string.settings_download_one_dm_session_summary,
            )
        }
    }

    settingsPage(
        destination = SettingsDestination.Appearance,
        id = "settings_appearance_title",
        titleRes = R.string.settings_appearance_title,
        descriptionRes = R.string.settings_home_appearance_summary,
    ) {
        setting(
            id = "settings_appearance_mode",
            titleRes = R.string.settings_appearance_mode,
            descriptionRes = R.string.settings_home_appearance_summary,
        )
        setting(
            id = "settings_animations",
            titleRes = R.string.settings_animations,
            descriptionRes = R.string.settings_animations_summary,
        )
        if (availability.isForceDarkWebsitesSupported) {
            setting(
                id = "settings_force_dark_websites",
                titleRes = R.string.settings_force_dark_websites,
                descriptionRes = R.string.settings_force_dark_websites_summary,
            )
        }
        setting(
            id = "settings_web_content_font_size",
            titleRes = R.string.settings_web_content_font_size,
            descriptionRes = R.string.settings_home_appearance_summary,
        )
        setting(
            id = "settings_color_palette",
            titleRes = R.string.settings_color_palette,
            descriptionRes = R.string.settings_home_appearance_summary,
        )
        setting(
            id = "settings_address_bar_color",
            titleRes = R.string.settings_address_bar_color,
            descriptionRes = R.string.settings_home_appearance_summary,
        )
        setting(
            id = "settings_surface_style",
            titleRes = R.string.settings_surface_style,
            descriptionRes = R.string.settings_surface_style_summary,
        )
        if (availability.isFrostedSurface) {
            setting(
                id = "settings_frosted_transparency",
                titleRes = R.string.settings_frosted_transparency,
                descriptionRes = R.string.settings_surface_style_summary,
            )
            setting(
                id = "settings_frosted_address_bar_transparency",
                titleRes = R.string.settings_frosted_address_bar_transparency,
                descriptionRes = R.string.settings_surface_style_summary,
            )
            setting(
                id = "settings_frosted_blur",
                titleRes = R.string.settings_frosted_blur,
                descriptionRes = if (availability.isGeckoEngine) {
                    R.string.settings_frosted_blur_summary_gecko
                } else {
                    R.string.settings_frosted_blur_summary_system_webview
                },
            )
        }
        setting(
            id = "settings_shape_style",
            titleRes = R.string.settings_shape_style,
            descriptionRes = R.string.settings_home_appearance_summary,
        )
        setting(
            id = "settings_address_load_style",
            titleRes = R.string.settings_address_load_style,
            descriptionRes = R.string.settings_home_appearance_summary,
        )
        setting(
            id = "settings_address_bar_style",
            titleRes = R.string.settings_address_bar_style,
            descriptionRes = R.string.settings_home_appearance_summary,
        )
    }

    settingsPage(
        destination = SettingsDestination.SiteCapsules,
        id = "capsule_settings_title",
        titleRes = R.string.capsule_settings_title,
        descriptionRes = R.string.settings_home_capsules_summary,
    ) {
    }

    settingsPage(
        destination = SettingsDestination.Userscripts,
        id = "userscript_title",
        titleRes = R.string.userscript_title,
        descriptionRes = R.string.settings_home_userscripts_summary,
    ) {
        if (availability.hasUserScriptSupport) {
            setting(
                id = "topping_discover",
                titleRes = R.string.topping_discover,
                descriptionRes = R.string.settings_home_userscripts_summary,
                destination = SettingsDestination.ToppingCatalog,
            )
            setting(
                id = "userscript_add",
                titleRes = R.string.userscript_add,
                descriptionRes = R.string.userscript_editor_help,
            )
            setting(
                id = "userscript_import",
                titleRes = R.string.userscript_import,
                descriptionRes = R.string.userscript_editor_help,
            )
        }
    }

    if (availability.hasFirefoxExtensions) {
        settingsPage(
            destination = null,
            id = "gecko_extensions_title",
            titleRes = R.string.gecko_extensions_title,
            descriptionRes = R.string.gecko_extensions_summary,
        ) {
        }
    }

    settingsPage(
        destination = SettingsDestination.ProtectionAndData,
        id = "settings_protection_data_title",
        titleRes = R.string.settings_protection_data_title,
        descriptionRes = R.string.settings_home_protection_summary,
    ) {
        setting(
            id = "privacy_xray_title",
            titleRes = R.string.privacy_xray_title,
            descriptionRes = R.string.privacy_xray_scope_note,
        )
        setting(
            id = "permission_radar_title",
            titleRes = R.string.permission_radar_title,
            descriptionRes = R.string.permission_radar_settings_summary,
        )
        setting(
            id = "filter_studio_title",
            titleRes = R.string.filter_studio_title,
            descriptionRes = R.string.filter_studio_settings_summary,
        )
        setting(
            id = "settings_block_ads_title",
            titleRes = R.string.settings_block_ads_title,
            descriptionRes = R.string.settings_block_ads_subtitle,
        )
        setting(
            id = "settings_hide_cookie_banners_title",
            titleRes = R.string.settings_hide_cookie_banners_title,
            descriptionRes = R.string.settings_hide_cookie_banners_subtitle,
        )
        setting(
            id = "settings_block_third_party_cookies_title",
            titleRes = R.string.settings_block_third_party_cookies_title,
            descriptionRes = R.string.settings_block_third_party_cookies_subtitle,
        )
        setting(
            id = "settings_do_not_track_title",
            titleRes = R.string.settings_do_not_track_title,
            descriptionRes = R.string.settings_do_not_track_summary,
        )
        setting(
            id = "settings_global_privacy_control_title",
            titleRes = R.string.settings_global_privacy_control_title,
            descriptionRes = R.string.settings_global_privacy_control_summary,
        )
        setting(
            id = "settings_auto_de_amp_title",
            titleRes = R.string.settings_auto_de_amp_title,
            descriptionRes = R.string.settings_auto_de_amp_summary,
        )
        if (availability.isHttpsOnlySupported) {
            setting(
                id = "settings_https_only_title",
                titleRes = R.string.settings_https_only_title,
                descriptionRes = R.string.settings_https_only_all_summary,
            )
        }
        if (availability.isDnsOverHttpsSupported) {
            setting(
                id = "settings_dns_over_https_title",
                titleRes = R.string.settings_dns_over_https_title,
                descriptionRes = R.string.settings_dns_over_https_gecko_summary,
            )
        }
        setting(
            id = "settings_webrtc_protection_title",
            titleRes = R.string.settings_webrtc_protection_title,
            descriptionRes = R.string.settings_webrtc_standard_summary,
        )
        setting(
            id = "history_save_title",
            titleRes = R.string.history_save_title,
            descriptionRes = R.string.history_save_summary,
        )
        setting(
            id = "history_clear_on_exit_title",
            titleRes = R.string.history_clear_on_exit_title,
            descriptionRes = R.string.history_clear_on_exit_summary,
        )
        setting(
            id = "recall_settings_title",
            titleRes = R.string.recall_settings_title,
            descriptionRes = R.string.recall_settings_summary,
        )
        setting(
            id = "data_archive_export_title",
            titleRes = R.string.data_archive_export_title,
            descriptionRes = R.string.data_archive_export_summary,
        )
        setting(
            id = "data_archive_import_title",
            titleRes = R.string.data_archive_import_title,
            descriptionRes = R.string.data_archive_import_summary,
        )
        setting(
            id = "action_clear_browsing_data",
            titleRes = R.string.action_clear_browsing_data,
            descriptionRes = R.string.settings_home_protection_summary,
        )
    }

    settingsPage(
        destination = SettingsDestination.Sync,
        id = "sync_settings_title",
        titleRes = R.string.sync_settings_title,
        descriptionRes = R.string.settings_home_sync_summary,
    ) {
        setting(
            id = "sync_setup_guide_title",
            titleRes = R.string.sync_setup_guide_title,
            descriptionRes = R.string.sync_setup_guide_summary,
        )
        setting(
            id = "sync_setup_server_title",
            titleRes = R.string.sync_setup_server_title,
            descriptionRes = R.string.sync_setup_server_summary,
        )
        setting(
            id = "sync_setup_extension_title",
            titleRes = R.string.sync_setup_extension_title,
            descriptionRes = R.string.sync_setup_extension_summary,
        )
        setting(
            id = "sync_setup_workspace_title",
            titleRes = R.string.sync_setup_workspace_title,
            descriptionRes = R.string.sync_setup_workspace_summary,
        )
        setting(
            id = "sync_setup_android_title",
            titleRes = R.string.sync_setup_android_title,
            descriptionRes = R.string.sync_setup_android_summary,
        )
        setting(
            id = "sync_endpoint_label",
            titleRes = R.string.sync_endpoint_label,
            descriptionRes = R.string.sync_setup_android_summary,
        )
        setting(
            id = "sync_username_label",
            titleRes = R.string.sync_username_label,
            descriptionRes = R.string.sync_setup_android_summary,
        )
        setting(
            id = "sync_server_password_label",
            titleRes = R.string.sync_server_password_label,
            descriptionRes = R.string.sync_server_password_summary,
        )
        setting(
            id = "sync_local_profile_label",
            titleRes = R.string.sync_local_profile_label,
            descriptionRes = R.string.settings_home_sync_summary,
        )
        setting(
            id = "sync_device_name_label",
            titleRes = R.string.sync_device_name_label,
            descriptionRes = R.string.settings_home_sync_summary,
        )
        setting(
            id = "sync_device_icon_label",
            titleRes = R.string.sync_device_icon_label,
            descriptionRes = R.string.settings_home_sync_summary,
        )
        setting(
            id = "sync_accent_color_label",
            titleRes = R.string.sync_accent_color_label,
            descriptionRes = R.string.settings_home_sync_summary,
        )
        setting(
            id = "sync_passphrase_label",
            titleRes = R.string.sync_passphrase_label,
            descriptionRes = R.string.sync_passphrase_warning_message,
        )
        setting(
            id = "sync_passphrase_confirm_label",
            titleRes = R.string.sync_passphrase_confirm_label,
            descriptionRes = R.string.sync_passphrase_warning_message,
        )
    }

    if (availability.isDeveloperOptionsUnlocked) {
        settingsPage(
            destination = SettingsDestination.DeveloperOptions,
            id = "developer_options_title",
            titleRes = R.string.developer_options_title,
            descriptionRes = R.string.developer_options_summary,
        ) {
            setting(
                id = "developer_options_app_logging",
                titleRes = R.string.developer_options_app_logging,
                descriptionRes = R.string.developer_options_app_logging_summary,
            )
            setting(
                id = "developer_options_export_logs",
                titleRes = R.string.developer_options_export_logs,
                descriptionRes = R.string.developer_options_export_logs_summary,
            )
            setting(
                id = "developer_options_clear_logs",
                titleRes = R.string.developer_options_clear_logs,
                descriptionRes = R.string.developer_options_clear_logs_summary,
            )
            setting(
                id = "developer_options_input_diagnostics",
                titleRes = R.string.developer_options_input_diagnostics,
                descriptionRes = R.string.developer_options_input_diagnostics_summary,
            )
            setting(
                id = "developer_options_copy_diagnostics",
                titleRes = R.string.developer_options_copy_diagnostics,
                descriptionRes = R.string.developer_options_copy_diagnostics_summary,
            )
            setting(
                id = "developer_options_show_onboarding",
                titleRes = R.string.developer_options_show_onboarding,
                descriptionRes = R.string.developer_options_show_onboarding_summary,
            )
            setting(
                id = "developer_options_show_release_notes",
                titleRes = R.string.developer_options_show_release_notes,
                descriptionRes = R.string.developer_options_show_release_notes_summary,
            )
            setting(
                id = "developer_options_force_safe_area_fallback",
                titleRes = R.string.developer_options_force_safe_area_fallback,
                descriptionRes = R.string.developer_options_force_safe_area_fallback_summary,
            )
            setting(
                id = "developer_options_scroll_dispatch_mode",
                titleRes = R.string.developer_options_scroll_dispatch_mode,
                descriptionRes = R.string.developer_options_scroll_dispatch_mode_summary,
            )
            setting(
                id = "developer_options_layout_quiet_period",
                titleRes = R.string.developer_options_layout_quiet_period,
                descriptionRes = R.string.developer_options_layout_quiet_period_summary,
            )
            setting(
                id = "developer_options_required_failures",
                titleRes = R.string.developer_options_required_failures,
                descriptionRes = R.string.developer_options_required_failures_summary,
            )
            if (availability.isHttpPasswordAutofillSupported) {
                setting(
                    id = "settings_http_password_autofill_title",
                    titleRes = R.string.settings_http_password_autofill_title,
                    descriptionRes = R.string.settings_http_password_autofill_gecko_summary,
                )
                setting(
                    id = "developer_options_gecko_logging",
                    titleRes = R.string.developer_options_gecko_logging,
                    descriptionRes = R.string.developer_options_gecko_logging_summary,
                )
                setting(
                    id = "developer_options_gecko_logging_modules",
                    titleRes = R.string.developer_options_gecko_logging_modules,
                    descriptionRes = R.string.developer_options_gecko_logging_modules_summary,
                )
                setting(
                    id = "developer_options_export_gecko_logs",
                    titleRes = R.string.developer_options_export_gecko_logs,
                    descriptionRes = R.string.developer_options_export_gecko_logs_summary,
                )
                setting(
                    id = "developer_options_clear_gecko_logs",
                    titleRes = R.string.developer_options_clear_gecko_logs,
                    descriptionRes = R.string.developer_options_clear_gecko_logs_summary,
                )
                setting(
                    id = "developer_options_gecko_safe_area_enabled",
                    titleRes = R.string.developer_options_gecko_safe_area_enabled,
                    descriptionRes = R.string.developer_options_gecko_safe_area_enabled_summary,
                )
                setting(
                    id = "developer_options_gecko_add_inset_to_negative_top",
                    titleRes = R.string.developer_options_gecko_add_inset_to_negative_top,
                    descriptionRes = R.string.developer_options_gecko_add_inset_to_negative_top_summary,
                )
                setting(
                    id = "developer_options_gecko_recheck_added_elements",
                    titleRes = R.string.developer_options_gecko_recheck_added_elements,
                    descriptionRes = R.string.developer_options_gecko_recheck_added_elements_summary,
                )
                setting(
                    id = "developer_options_gecko_recheck_changed_elements",
                    titleRes = R.string.developer_options_gecko_recheck_changed_elements,
                    descriptionRes = R.string.developer_options_gecko_recheck_changed_elements_summary,
                )
                setting(
                    id = "developer_options_gecko_require_interaction",
                    titleRes = R.string.developer_options_gecko_require_interaction,
                    descriptionRes = R.string.developer_options_gecko_require_interaction_summary,
                )
                setting(
                    id = "developer_options_gecko_recheck_on_resize",
                    titleRes = R.string.developer_options_gecko_recheck_on_resize,
                    descriptionRes = R.string.developer_options_gecko_recheck_on_resize_summary,
                )
                setting(
                    id = "developer_options_gecko_interaction_window",
                    titleRes = R.string.developer_options_gecko_interaction_window,
                    descriptionRes = R.string.developer_options_gecko_interaction_window_summary,
                )
                setting(
                    id = "developer_options_gecko_mutation_debounce",
                    titleRes = R.string.developer_options_gecko_mutation_debounce,
                    descriptionRes = R.string.developer_options_gecko_mutation_debounce_summary,
                )
                setting(
                    id = "developer_options_gecko_max_elements_per_batch",
                    titleRes = R.string.developer_options_gecko_max_elements_per_batch,
                    descriptionRes = R.string.developer_options_gecko_max_elements_per_batch_summary,
                )
                setting(
                    id = "developer_options_gecko_max_batch_duration",
                    titleRes = R.string.developer_options_gecko_max_batch_duration,
                    descriptionRes = R.string.developer_options_gecko_max_batch_duration_summary,
                )
                setting(
                    id = "developer_options_gecko_max_initial_elements",
                    titleRes = R.string.developer_options_gecko_max_initial_elements,
                    descriptionRes = R.string.developer_options_gecko_max_initial_elements_summary,
                )
            }
        }
    }

    settingsPage(
        destination = SettingsDestination.AboutLegal,
        id = "settings_section_about_legal",
        titleRes = R.string.settings_section_about_legal,
        descriptionRes = R.string.settings_home_about_summary,
    ) {
        setting(
            id = "settings_imprint_title",
            titleRes = R.string.settings_imprint_title,
            descriptionRes = R.string.settings_imprint_summary,
        )
        setting(
            id = "settings_open_source_title",
            titleRes = R.string.settings_open_source_title,
            descriptionRes = R.string.settings_open_source_summary,
        )
        setting(
            id = "settings_uassets_source_title",
            titleRes = R.string.settings_uassets_source_title,
            descriptionRes = R.string.settings_uassets_source_summary,
        )
    }
}

private fun MutableList<SettingsSearchDefinition>.settingsPage(
    destination: SettingsDestination?,
    id: String,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    settings: SettingsSearchPageBuilder.() -> Unit,
) {
    add(
        SettingsSearchDefinition(
            id = id,
            titleRes = titleRes,
            descriptionRes = descriptionRes,
            contextRes = R.string.settings_title,
            destination = destination,
        ),
    )
    SettingsSearchPageBuilder(this, destination, titleRes).settings()
}

private class SettingsSearchPageBuilder(
    private val definitions: MutableList<SettingsSearchDefinition>,
    private val pageDestination: SettingsDestination?,
    @param:StringRes private val pageTitleRes: Int,
) {
    fun setting(
        id: String,
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int,
        destination: SettingsDestination? = pageDestination,
    ) {
        definitions.add(
            SettingsSearchDefinition(
                id = id,
                titleRes = titleRes,
                descriptionRes = descriptionRes,
                contextRes = pageTitleRes,
                destination = destination,
            ),
        )
    }
}
