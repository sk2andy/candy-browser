package dev.sk2andy.materialbrowser.ui

import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchCatalogTest {
    @Test
    fun `link peek swipe action appears only when link peek enabled`() {
        assertTrue("settings_link_peek_swipe_action" in ids(SettingsSearchAvailability()))
        assertFalse(
            "settings_link_peek_swipe_action" in ids(
                SettingsSearchAvailability(isLinkPeekEnabled = false),
            ),
        )
    }

    @Test
    fun `menu and tab layout search opens the dedicated selection pages`() {
        val entries = settingsSearchDefinitions(SettingsSearchAvailability()).associateBy { it.id }

        mapOf(
            "settings_menu_presentation_title" to SettingsDestination.MenuStyle,
            "settings_menu_bottom_sheet_layout_title" to SettingsDestination.MenuContentLayout,
        ).forEach { (id, destination) ->
                assertEquals(destination, entries.getValue(id).destination)
                assertEquals(R.string.settings_menu_presentation_summary, entries.getValue(id).descriptionRes)
            }
        assertEquals(SettingsDestination.TabOverview, entries.getValue("settings_tab_overview_mode").destination)
    }

    @Test
    fun `catalog keeps visible unsupported controls searchable`() {
        val ids = ids(SettingsSearchAvailability())

        assertFalse("settings_browser_engine_title" in ids)
        assertTrue("settings_dns_over_https_title" in ids)
        assertTrue("settings_https_only_title" in ids)
        assertTrue("settings_video_autoplay_title" in ids)
        assertTrue("settings_inline_media_player_title" in ids)
        assertTrue("settings_inline_media_player_seek_backward" in ids)
        assertTrue("settings_force_dark_websites" in ids)
        assertFalse("gecko_extensions_title" in ids)
        assertFalse("settings_app_language" in ids)
        assertTrue("settings_scroll_bar_title" in ids)
        assertTrue("settings_webrtc_protection_title" in ids)

        val definitions = settingsSearchDefinitions(SettingsSearchAvailability()).associateBy { it.id }
        assertEquals(
            R.string.settings_dns_over_https_system_webview_summary,
            definitions.getValue("settings_dns_over_https_title").descriptionRes,
        )
        assertEquals(
            R.string.settings_https_only_system_webview_summary,
            definitions.getValue("settings_https_only_title").descriptionRes,
        )
        assertEquals(
            R.string.settings_video_autoplay_unsupported,
            definitions.getValue("settings_video_autoplay_title").descriptionRes,
        )
        assertEquals(
            R.string.settings_force_dark_websites_system_webview_only,
            definitions.getValue("settings_force_dark_websites").descriptionRes,
        )
    }

    @Test
    fun `developer settings that are disabled remain searchable while unlocked`() {
        val definitions = settingsSearchDefinitions(
            SettingsSearchAvailability(isDeveloperOptionsUnlocked = true),
        ).associateBy { it.id }
        assertEquals(
            R.string.settings_http_password_autofill_system_webview_summary,
            definitions.getValue("settings_http_password_autofill_title").descriptionRes,
        )
        assertEquals(
            R.string.developer_options_gecko_logging_unsupported,
            definitions.getValue("developer_options_gecko_logging").descriptionRes,
        )
        assertTrue("developer_options_gecko_logging_modules" in definitions)
    }

    @Test
    fun `address pill swipe mode is searchable under gestures and controls for either engine`() {
        listOf(false, true).forEach { isGeckoEngine ->
            val definition = settingsSearchDefinitions(
                SettingsSearchAvailability(isGeckoEngine = isGeckoEngine),
            ).single { it.id == "settings_address_bar_swipe_mode_title" }

            assertEquals(SettingsDestination.Gestures, definition.destination)
            assertEquals(R.string.settings_home_gestures_title, definition.contextRes)
            assertEquals(R.string.settings_address_bar_swipe_mode_title, definition.titleRes)
            assertEquals(R.string.settings_address_bar_swipe_mode_summary, definition.descriptionRes)
        }
    }

    @Test
    fun `supported capabilities expose their settings and Firefox external action`() {
        val entries = settingsSearchDefinitions(
            SettingsSearchAvailability(
                hasBrowserEngineSelection = true,
                isDnsOverHttpsSupported = true,
                isHttpsOnlySupported = true,
                isVideoAutoplayBlockingSupported = true,
                isInlineMediaPlayerSupported = true,
                isForceDarkWebsitesSupported = true,
                hasFirefoxExtensions = true,
                hasAppLanguages = true,
            ),
        ).associateBy { it.id }

        assertEquals(
            SettingsDestination.Browser,
            entries.getValue("settings_browser_engine_title").destination,
        )
        assertEquals(
            SettingsDestination.ProtectionAndData,
            entries.getValue("settings_dns_over_https_title").destination,
        )
        assertEquals(
            SettingsDestination.ProtectionAndData,
            entries.getValue("settings_https_only_title").destination,
        )
        assertEquals(
            SettingsDestination.Player,
            entries.getValue("settings_video_autoplay_title").destination,
        )
        assertEquals(
            SettingsDestination.Player,
            entries.getValue("settings_inline_media_player_seek_backward").destination,
        )
        assertEquals(
            SettingsDestination.AppearanceWebsites,
            entries.getValue("settings_force_dark_websites").destination,
        )
        assertNull(entries.getValue("gecko_extensions_title").destination)
        assertEquals(SettingsDestination.Browser, entries.getValue("settings_app_language").destination)
    }

    @Test
    fun `AI and SearXNG controls remain searchable when inactive`() {
        val defaultIds = ids(SettingsSearchAvailability())
        assertTrue("settings_ai_mode_toggle_title" in defaultIds)
        assertTrue("settings_searxng_instance_url" in defaultIds)
        assertTrue("settings_searxng_suggestion_fallback" in defaultIds)

        assertTrue("settings_ai_mode_toggle_title" in defaultIds)
        assertTrue("settings_searxng_instance_url" in defaultIds)
        assertTrue("settings_searxng_suggestion_fallback" in defaultIds)
    }

    @Test
    fun `suggestion descriptions reflect current local SearXNG or remote provider`() {
        val localDefinition = settingsSearchDefinitions(
            SettingsSearchAvailability(searchSuggestionProvider = SearchSuggestionProvider.None),
        ).single { it.id == "settings_search_suggestions" }
        assertEquals(
            R.string.settings_search_suggestions_none_summary,
            localDefinition.descriptionRes,
        )

        val searxngDefinition = settingsSearchDefinitions(
            SettingsSearchAvailability(searchSuggestionProvider = SearchSuggestionProvider.SearXNG),
        ).single { it.id == "settings_search_suggestions" }
        assertEquals(
            R.string.settings_searxng_search_suggestions_summary,
            searxngDefinition.descriptionRes,
        )

        val remoteDefinition = settingsSearchDefinitions(
            SettingsSearchAvailability(searchSuggestionProvider = SearchSuggestionProvider.Google),
        ).single { it.id == "settings_search_suggestions" }
        assertEquals(R.string.settings_search_suggestions_summary, remoteDefinition.descriptionRes)
    }

    @Test
    fun `developer capabilities cannot bypass developer options unlock`() {
        val lockedIds = ids(
            SettingsSearchAvailability(
                isHttpPasswordAutofillSupported = true,
                isGeckoLoggingSupported = true,
                isGeckoEngine = true,
            ),
        )
        assertFalse("developer_options_title" in lockedIds)
        assertFalse("developer_options_app_logging" in lockedIds)
        assertFalse("settings_http_password_autofill_title" in lockedIds)
        assertFalse("developer_options_gecko_logging_modules" in lockedIds)
        assertFalse("developer_options_gecko_safe_area_enabled" in lockedIds)

        val unlockedIds = ids(SettingsSearchAvailability(isDeveloperOptionsUnlocked = true))
        assertTrue("developer_options_app_logging" in unlockedIds)
        assertTrue("settings_http_password_autofill_title" in unlockedIds)
        assertTrue("developer_options_gecko_logging_modules" in unlockedIds)
        assertTrue("developer_options_gecko_safe_area_enabled" in unlockedIds)

        val geckoIds = ids(
            SettingsSearchAvailability(
                isDeveloperOptionsUnlocked = true,
                isHttpPasswordAutofillSupported = true,
                isGeckoLoggingSupported = true,
                isGeckoEngine = true,
            ),
        )
        assertTrue("settings_http_password_autofill_title" in geckoIds)
        assertTrue("developer_options_gecko_logging_modules" in geckoIds)
        assertTrue("developer_options_gecko_safe_area_enabled" in geckoIds)
    }

    @Test
    fun `frosted settings remain searchable while their surface is inactive`() {
        val systemEntries = settingsSearchDefinitions(SettingsSearchAvailability())
            .associateBy { it.id }
        assertTrue("settings_frosted_transparency" in systemEntries)
        assertTrue("settings_frosted_address_bar_transparency" in systemEntries)
        assertEquals(
            R.string.settings_frosted_blur_summary_system_webview,
            systemEntries.getValue("settings_frosted_blur").descriptionRes,
        )

        val geckoEntries = settingsSearchDefinitions(
            SettingsSearchAvailability(isGeckoEngine = true),
        ).associateBy { it.id }
        assertEquals(
            R.string.settings_frosted_blur_summary_gecko,
            geckoEntries.getValue("settings_frosted_blur").descriptionRes,
        )
    }

    @Test
    fun `download dependent options stay searchable when inactive`() {
        val defaultIds = ids(SettingsSearchAvailability())
        assertTrue("settings_download_folder_title" in defaultIds)
        assertTrue("settings_download_one_dm_session_title" in defaultIds)
    }

    @Test
    fun `nested settings routes preserve parent context and stable unique identity`() {
        val entries = settingsSearchDefinitions(SettingsSearchAvailability())
            .associateBy { it.id }
        assertEquals(
            SettingsDestination.AddressBarLongPressActions,
            entries.getValue("settings_address_bar_long_press_title").destination,
        )
        assertEquals(
            SettingsDestination.LinkPeekActions,
            entries.getValue("settings_link_peek_actions_title").destination,
        )
        assertEquals(
            SettingsDestination.AddressBarActions,
            entries.getValue("settings_address_bar_actions_title").destination,
        )
        assertEquals(
            SettingsDestination.MenuActions,
            entries.getValue("settings_menu_actions_title").destination,
        )
        assertEquals(
            R.string.settings_home_gestures_title,
            entries.getValue("settings_menu_actions_title").contextRes,
        )
        assertEquals(SettingsDestination.ToppingCatalog, entries.getValue("topping_discover").destination)
        assertTrue("topping_discover" in ids(SettingsSearchAvailability()))

        val allEntries = settingsSearchDefinitions(
            SettingsSearchAvailability(
                hasAppLanguages = true,
                hasBrowserEngineSelection = true,
                isDnsOverHttpsSupported = true,
                isHttpsOnlySupported = true,
                isVideoAutoplayBlockingSupported = true,
                isInlineMediaPlayerSupported = true,
                isForceDarkWebsitesSupported = true,
                isDeveloperOptionsUnlocked = true,
                isHttpPasswordAutofillSupported = true,
                isGeckoLoggingSupported = true,
                hasFirefoxExtensions = true,
                isGeckoEngine = true,
            ),
        )
        assertEquals(allEntries.size, allEntries.map { it.id }.toSet().size)
    }

    @Test
    fun `history and browser data options open their own page`() {
        val entries = settingsSearchDefinitions(SettingsSearchAvailability()).associateBy { it.id }

        listOf(
            "history_save_title",
            "history_clear_on_exit_title",
            "recall_settings_title",
            "data_archive_export_title",
            "data_archive_import_title",
            "action_clear_browsing_data",
        ).forEach { id ->
            assertEquals(SettingsDestination.HistoryAndData, entries.getValue(id).destination)
            assertEquals(R.string.settings_home_history_title, entries.getValue(id).contextRes)
        }
    }

    @Test
    fun `expanded detail routes preserve every search identifier and owning section`() {
        val entries = settingsSearchDefinitions(SettingsSearchAvailability()).associateBy { it.id }
        val expected = mapOf(
            "settings_tab_stack_folder_mode" to SettingsDestination.TabStackFolder,
            "settings_closed_tab_undo_title" to SettingsDestination.ClosedTabUndo,
            "settings_link_long_press_action" to SettingsDestination.LinkLongPress,
            "settings_link_peek_swipe_action" to SettingsDestination.LinkPeekSwipe,
            "settings_inline_media_player_title" to SettingsDestination.PlayerMode,
            "settings_external_link_preview_title" to SettingsDestination.ExternalLinkPreview,
            "settings_appearance_mode" to SettingsDestination.AppearanceColors,
            "settings_color_palette" to SettingsDestination.AppearanceColors,
            "settings_shape_style" to SettingsDestination.AppearanceSurfaces,
        )
        expected.forEach { (id, destination) ->
            assertEquals(id, destination, entries.getValue(id).destination)
        }
        assertEquals(SettingsDestination.Player, entries.getValue("settings_inline_media_player_seek_forward").destination)
        assertEquals(SettingsDestination.Appearance, entries.getValue("settings_animations").destination)
    }

    private fun ids(availability: SettingsSearchAvailability): Set<String> =
        settingsSearchDefinitions(availability).map { it.id }.toSet()
    @Test
    fun `search targets actual detail pages while retaining stable identifiers`() {
        val entries = settingsSearchDefinitions(SettingsSearchAvailability()).associateBy { it.id }
        assertEquals(SettingsDestination.AppearanceSurfaces, entries.getValue("settings_frosted_blur").destination)
        assertEquals(SettingsDestination.AppearanceAddressBar, entries.getValue("settings_address_bar_color").destination)
        assertEquals(SettingsDestination.BrowserStartup, entries.getValue("settings_startup_address_focus_title").destination)
        assertEquals(SettingsDestination.BrowserFavorites, entries.getValue("settings_favorite_animation_speed_title").destination)
        assertEquals(SettingsDestination.BrowserWebsites, entries.getValue("settings_scroll_bar_title").destination)
        assertEquals(SettingsDestination.ExternalLinkPreview, entries.getValue("settings_external_link_preview_title").destination)
    }

}
