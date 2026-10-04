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
    fun `catalog hides engine and flavor capabilities that are not available`() {
        val ids = ids(SettingsSearchAvailability())

        assertFalse("settings_browser_engine_title" in ids)
        assertFalse("settings_dns_over_https_title" in ids)
        assertFalse("settings_https_only_title" in ids)
        assertFalse("settings_video_autoplay_title" in ids)
        assertFalse("settings_inline_media_player_title" in ids)
        assertFalse("settings_inline_media_player_seek_backward" in ids)
        assertFalse("settings_force_dark_websites" in ids)
        assertFalse("gecko_extensions_title" in ids)
        assertFalse("settings_app_language" in ids)
        assertTrue("settings_scroll_bar_title" in ids)
        assertTrue("settings_webrtc_protection_title" in ids)
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
            SettingsDestination.Appearance,
            entries.getValue("settings_force_dark_websites").destination,
        )
        assertNull(entries.getValue("gecko_extensions_title").destination)
        assertEquals(SettingsDestination.Browser, entries.getValue("settings_app_language").destination)
    }

    @Test
    fun `AI and SearXNG controls follow separate current search provider gates`() {
        val defaultIds = ids(SettingsSearchAvailability())
        assertFalse("settings_ai_mode_toggle_title" in defaultIds)
        assertFalse("settings_searxng_instance_url" in defaultIds)
        assertFalse("settings_searxng_suggestion_fallback" in defaultIds)

        val engineIds = ids(SettingsSearchAvailability(supportsAiSearch = true, hasSearxngInstance = true))
        assertTrue("settings_ai_mode_toggle_title" in engineIds)
        assertTrue("settings_searxng_instance_url" in engineIds)
        assertFalse("settings_searxng_suggestion_fallback" in engineIds)

        val suggestionIds = ids(
            SettingsSearchAvailability(hasSearxngInstance = true, hasSearxngSuggestionFallback = true),
        )
        assertFalse("settings_ai_mode_toggle_title" in suggestionIds)
        assertTrue("settings_searxng_instance_url" in suggestionIds)
        assertTrue("settings_searxng_suggestion_fallback" in suggestionIds)
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
        assertFalse("settings_http_password_autofill_title" in unlockedIds)
        assertFalse("developer_options_gecko_logging_modules" in unlockedIds)
        assertFalse("developer_options_gecko_safe_area_enabled" in unlockedIds)

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
    fun `frosted settings require frosted surface and use current engine description`() {
        assertFalse("settings_frosted_blur" in ids(SettingsSearchAvailability()))
        val systemEntries = settingsSearchDefinitions(SettingsSearchAvailability(isFrostedSurface = true))
            .associateBy { it.id }
        assertTrue("settings_frosted_transparency" in systemEntries)
        assertTrue("settings_frosted_address_bar_transparency" in systemEntries)
        assertEquals(
            R.string.settings_frosted_blur_summary_system_webview,
            systemEntries.getValue("settings_frosted_blur").descriptionRes,
        )

        val geckoEntries = settingsSearchDefinitions(
            SettingsSearchAvailability(isFrostedSurface = true, isGeckoEngine = true),
        ).associateBy { it.id }
        assertEquals(
            R.string.settings_frosted_blur_summary_gecko,
            geckoEntries.getValue("settings_frosted_blur").descriptionRes,
        )
    }

    @Test
    fun `download folder and 1DM sharing reflect current manager selection`() {
        val externalIds = ids(SettingsSearchAvailability(hasOneDmSessionSharing = true))
        assertFalse("settings_download_folder_title" in externalIds)
        assertTrue("settings_download_one_dm_session_title" in externalIds)

        val builtInIds = ids(SettingsSearchAvailability(isBuiltInDownloadManager = true))
        assertTrue("settings_download_folder_title" in builtInIds)
        assertFalse("settings_download_one_dm_session_title" in builtInIds)
    }

    @Test
    fun `nested settings routes preserve parent context and stable unique identity`() {
        val entries = settingsSearchDefinitions(SettingsSearchAvailability(hasUserScriptSupport = true))
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
            R.string.settings_tabs_gestures_title,
            entries.getValue("settings_menu_actions_title").contextRes,
        )
        assertEquals(SettingsDestination.ToppingCatalog, entries.getValue("topping_discover").destination)
        assertFalse("topping_discover" in ids(SettingsSearchAvailability()))

        val allEntries = settingsSearchDefinitions(
            SettingsSearchAvailability(
                hasAppLanguages = true,
                hasBrowserEngineSelection = true,
                supportsAiSearch = true,
                hasSearxngInstance = true,
                hasSearxngSuggestionFallback = true,
                isDnsOverHttpsSupported = true,
                isHttpsOnlySupported = true,
                isVideoAutoplayBlockingSupported = true,
                isInlineMediaPlayerSupported = true,
                isForceDarkWebsitesSupported = true,
                isDeveloperOptionsUnlocked = true,
                isHttpPasswordAutofillSupported = true,
                isGeckoLoggingSupported = true,
                hasFirefoxExtensions = true,
                isBuiltInDownloadManager = true,
                hasOneDmSessionSharing = true,
                isFrostedSurface = true,
                hasUserScriptSupport = true,
                isGeckoEngine = true,
            ),
        )
        assertEquals(allEntries.size, allEntries.map { it.id }.toSet().size)
    }

    private fun ids(availability: SettingsSearchAvailability): Set<String> =
        settingsSearchDefinitions(availability).map { it.id }.toSet()
}
