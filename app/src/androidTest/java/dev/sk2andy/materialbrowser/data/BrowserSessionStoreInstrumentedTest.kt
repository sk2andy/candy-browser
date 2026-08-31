package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.SearxngRules
import dev.sk2andy.materialbrowser.browser.SearxngSettings
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.browser.TabWebViewResidencyRules
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrides
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserSessionStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences("browser_session", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun legacyTabGetsCurrentTimestampAndRoundTrips() {
        preferences.edit()
            .putString("tabs", """[{"id":"legacy","title":"Alt","url":"https://example.com"}]""")
            .putString("selected_tab", "legacy")
            .commit()
        val store = BrowserSessionStore(context)

        val (legacyTabs, selectedId) = store.loadTabs(nowMillis = 42_000L)
        assertEquals(42_000L, legacyTabs.single().lastAccessedAt)
        assertFalse(legacyTabs.single().isIncognito)
        assertFalse(legacyTabs.single().isPinned)
        assertEquals(DEFAULT_PROFILE_ID, legacyTabs.single().profileId)
        assertEquals("legacy", selectedId)

        store.saveTabs(
            listOf(BrowserTab(id = "saved", lastAccessedAt = 84_000L)),
            selectedTabId = "saved",
        )
        assertEquals(84_000L, store.loadTabs(nowMillis = 1L).first.single().lastAccessedAt)
    }

    @Test
    fun pinnedTabsRoundTripBeforeRegularTabs() {
        val store = BrowserSessionStore(context)
        store.saveTabs(
            tabs = listOf(
                BrowserTab(id = "regular", lastAccessedAt = 10L),
                BrowserTab(
                    id = "pinned",
                    lastAccessedAt = 20L,
                    openerTabId = "regular",
                    isPinned = true,
                ),
            ),
            selectedTabId = "regular",
        )

        val (tabs, selectedId) = store.loadTabs()

        assertEquals(listOf("pinned", "regular"), tabs.map(BrowserTab::id))
        assertEquals(listOf(true, false), tabs.map(BrowserTab::isPinned))
        assertEquals("regular", tabs.first().openerTabId)
        assertEquals("regular", selectedId)
    }

    @Test
    fun profilesAndPerProfileSelectionsRoundTrip() {
        val store = BrowserSessionStore(context)
        val profiles = listOf(
            BrowserProfile(id = "candy", emoji = "🍬", selectedTabId = "personal-tab"),
            BrowserProfile(
                id = "work",
                emoji = "💼",
                selectedTabId = "work-tab",
                isolationEnabled = true,
            ),
        )

        store.saveProfiles(profiles, activeProfileId = "work")
        store.saveTabs(
            tabs = listOf(
                BrowserTab(id = "personal-tab", lastAccessedAt = 10L),
                BrowserTab(id = "work-tab", lastAccessedAt = 20L, profileId = "work"),
            ),
            selectedTabId = "work-tab",
        )

        val (restoredProfiles, activeProfileId) = store.loadProfiles()
        val (restoredTabs, selectedTabId) = store.loadTabs()

        assertEquals(profiles, restoredProfiles)
        assertEquals("work", activeProfileId)
        assertEquals(listOf(DEFAULT_PROFILE_ID, "work"), restoredTabs.map(BrowserTab::profileId))
        assertEquals("work-tab", selectedTabId)
    }

    @Test
    fun profilesDefaultToEnabledAndRoundTripDisabledState() {
        val store = BrowserSessionStore(context)

        assertTrue(store.loadProfilesEnabled())

        store.saveProfilesEnabled(false)

        assertFalse(store.loadProfilesEnabled())
    }

    @Test
    fun scrollBarDefaultsOffAndRoundTrips() {
        val store = BrowserSessionStore(context)

        assertFalse(store.loadScrollBarEnabled())

        store.saveScrollBarEnabled(true)
        assertTrue(store.loadScrollBarEnabled())

        store.saveScrollBarEnabled(false)
        assertFalse(store.loadScrollBarEnabled())
    }

    @Test
    fun aiModeToggleDefaultsToHiddenAndRoundTripsVisibleState() {
        val store = BrowserSessionStore(context)

        assertFalse(store.loadAiModeToggleVisible())

        store.saveAiModeToggleVisible(true)

        assertTrue(store.loadAiModeToggleVisible())

        store.saveAiModeToggleVisible(false)

        assertFalse(store.loadAiModeToggleVisible())
    }

    @Test
    fun addressBarDockingDefaultsToEnabledAndRoundTrips() {
        val store = BrowserSessionStore(context)

        assertTrue(store.loadAddressBarDockingEnabled())

        store.saveAddressBarDockingEnabled(false)
        assertFalse(store.loadAddressBarDockingEnabled())

        store.saveAddressBarDockingEnabled(true)
        assertTrue(store.loadAddressBarDockingEnabled())
    }

    @Test
    fun downloadSettingsDefaultSafelyAndRoundTrip() {
        val store = BrowserSessionStore(context)

        assertEquals(BrowserDownloadSettings(), store.loadDownloadSettings())

        val settings = BrowserDownloadSettings(
            managerMode = DownloadManagerMode.External,
            externalManagerId = "view|idm.internet.download.manager|idm.internet.download.manager.Downloader",
            shareSessionDataWithOneDm = true,
        )
        store.saveDownloadSettings(settings)

        assertEquals(settings, store.loadDownloadSettings())
    }

    @Test
    fun appearanceSettingsDefaultSafelyAndRoundTrip() {
        val store = BrowserSessionStore(context)

        assertEquals(AppearanceSettings(), store.loadAppearanceSettings())

        val settings = AppearanceSettings(
            appearanceMode = BrowserAppearanceMode.Amoled,
            colorPalette = BrowserColorPalette.Candy,
            surfaceStyle = BrowserSurfaceStyle.Frosted,
            shapeStyle = BrowserShapeStyle.Angular,
            frostedTransparencyPercent = 70,
            frostedAddressBarTransparencyPercent = 50,
            frostedBlurPercent = 90,
        )
        store.saveAppearanceSettings(settings)

        assertEquals(settings, store.loadAppearanceSettings())
    }

    @Test
    fun corruptAppearanceSettingsFallBackPerField() {
        preferences.edit()
            .putString("appearance_mode", "unknown")
            .putString("color_palette", "candy")
            .putString("surface_style", "unknown")
            .putString("shape_style", "extra_rounded")
            .putInt("frosted_transparency_percent", 200)
            .putInt("frosted_address_bar_transparency_percent", -1)
            .putString("frosted_blur_percent", "invalid")
            .commit()

        assertEquals(
            AppearanceSettings(
                appearanceMode = BrowserAppearanceMode.System,
                colorPalette = BrowserColorPalette.Candy,
                surfaceStyle = BrowserSurfaceStyle.Clear,
                shapeStyle = BrowserShapeStyle.ExtraRounded,
                frostedTransparencyPercent = 80,
                frostedAddressBarTransparencyPercent = 0,
                frostedBlurPercent = AppearanceSettings.DEFAULT_FROSTED_BLUR_PERCENT,
            ),
            BrowserSessionStore(context).loadAppearanceSettings(),
        )
    }

    @Test
    fun legacyFrostedTransparencySeedsAddressBarTransparency() {
        preferences.edit()
            .putInt("frosted_transparency_percent", 70)
            .commit()

        val settings = BrowserSessionStore(context).loadAppearanceSettings()

        assertEquals(70, settings.frostedTransparencyPercent)
        assertEquals(70, settings.frostedAddressBarTransparencyPercent)
    }

    @Test
    fun corruptExternalDownloadManagerFallsBackToBuiltIn() {
        preferences.edit()
            .putString("download_manager_mode", "external")
            .putString("external_download_manager_id", "\n")
            .putBoolean("share_session_data_with_one_dm", true)
            .commit()

        val settings = BrowserSessionStore(context).loadDownloadSettings()

        assertEquals(DownloadManagerMode.BuiltIn, settings.managerMode)
        assertEquals(null, settings.externalManagerId)
        assertTrue(settings.shareSessionDataWithOneDm)
    }

    @Test
    fun missingOrInvalidProfilesFallBackToCandy() {
        val store = BrowserSessionStore(context)
        assertEquals(listOf("candy"), store.loadProfiles().first.map(BrowserProfile::id))
        assertEquals("candy", store.loadProfiles().second)

        preferences.edit()
            .putString("profiles", "not-json")
            .putString("active_profile", "missing")
            .commit()

        assertEquals(listOf("candy"), store.loadProfiles().first.map(BrowserProfile::id))
        assertEquals("candy", store.loadProfiles().second)
    }

    @Test
    fun legacyProfileDefaultsToSharedStorage() {
        preferences.edit()
            .putString(
                "profiles",
                """[{"id":"legacy","emoji":"🧭","selectedTabId":"tab"}]""",
            )
            .putString("active_profile", "legacy")
            .commit()

        val profile = BrowserSessionStore(context).loadProfiles().first.single()

        assertFalse(profile.isolationEnabled)
    }

    @Test
    fun recallDefaultsOffAndRoundTrips() {
        val store = BrowserSessionStore(context)

        assertFalse(store.loadRecallEnabled())
        store.saveRecallEnabled(true)
        assertTrue(store.loadRecallEnabled())
    }

    @Test
    fun historyProfileAndRecordingModeRoundTrip() {
        val store = BrowserSessionStore(context)
        val entry = HistoryEntry(
            url = "https://work.example/",
            title = "Work",
            lastVisitedAt = 42L,
            profileId = "work",
        )

        store.saveHistory(listOf(entry))
        store.saveHistoryRecordingMode(HistoryRecordingMode.ClearOnExit)

        assertEquals(listOf(entry), store.loadHistory())
        assertEquals(HistoryRecordingMode.ClearOnExit, store.loadHistoryRecordingMode())
        assertEquals(
            "clear_on_exit",
            preferences.getString("history_recording_mode", null),
        )
    }

    @Test
    fun legacyHistoryDefaultsToCandyProfileAndInvalidModeDisablesRecording() {
        preferences.edit()
            .putString(
                "history",
                """[{"url":"https://legacy.example/","title":"Legacy","lastVisitedAt":7}]""",
            )
            .putString("history_recording_mode", "unknown")
            .commit()

        val store = BrowserSessionStore(context)

        assertEquals(DEFAULT_PROFILE_ID, store.loadHistory().single().profileId)
        assertEquals(HistoryRecordingMode.Disabled, store.loadHistoryRecordingMode())
    }

    @Test
    fun legacyRecordingModeNamesRemainReadable() {
        val store = BrowserSessionStore(context)

        preferences.edit().putString("history_recording_mode", "ClearOnExit").commit()

        assertEquals(HistoryRecordingMode.ClearOnExit, store.loadHistoryRecordingMode())
    }

    @Test
    fun historyAndTrailRedactionCommitAndAcknowledgeTogether() {
        val store = BrowserSessionStore(context)
        val history = listOf(HistoryEntry("https://retained.example/", "Retained", 1L))
        val redaction = PendingCandyTrailRedaction(
            id = "redaction-id",
            tabIds = setOf("tab-a", "tab-b"),
            sinceInclusiveMillis = 10L,
            untilExclusiveMillis = 20L,
        )

        assertTrue(store.saveHistoryAndTrailRedaction(history, redaction))
        assertEquals(history, store.loadHistory())
        assertEquals(listOf(redaction), store.loadPendingCandyTrailRedactions())

        assertTrue(store.removePendingCandyTrailRedaction(redaction.id))
        assertTrue(store.loadPendingCandyTrailRedactions().isEmpty())
    }

    @Test
    fun acknowledgingOneRedactionRetainsNewerJournalEntries() {
        val firstStore = BrowserSessionStore(context)
        val secondStore = BrowserSessionStore(context)
        val first = PendingCandyTrailRedaction("first", setOf("tab-a"), 1L, 2L)
        val second = PendingCandyTrailRedaction("second", setOf("tab-b"), 3L, 4L)

        assertTrue(firstStore.saveHistoryAndTrailRedaction(emptyList(), first))
        assertTrue(secondStore.saveHistoryAndTrailRedaction(emptyList(), second))
        assertTrue(firstStore.removePendingCandyTrailRedaction(first.id))

        assertEquals(listOf(second), secondStore.loadPendingCandyTrailRedactions())
    }

    @Test
    fun pendingWebViewProfileDeletionsRoundTrip() {
        val store = BrowserSessionStore(context)

        store.savePendingWebViewProfileDeletions(setOf("profile-a", "profile-b"))

        assertEquals(
            setOf("profile-a", "profile-b"),
            store.loadPendingWebViewProfileDeletions(),
        )
    }

    @Test
    fun incognitoTabsAndSelectionAreNeverRestored() {
        val store = BrowserSessionStore(context)
        store.saveTabs(
            tabs = listOf(
                BrowserTab(id = "normal", lastAccessedAt = 10L),
                BrowserTab(id = "private", lastAccessedAt = 20L, isIncognito = true),
            ),
            selectedTabId = "private",
        )

        val (tabs, selectedId) = store.loadTabs()

        assertEquals(listOf("normal"), tabs.map(BrowserTab::id))
        assertEquals("normal", selectedId)
        assertFalse(preferences.getString("tabs", "").orEmpty().contains("private"))
    }

    @Test
    fun previouslyPersistedIncognitoTabIsDiscardedOnLoad() {
        preferences.edit()
            .putString(
                "tabs",
                """[{"id":"normal","lastAccessedAt":1,"isIncognito":false},{"id":"private","lastAccessedAt":2,"isIncognito":true}]""",
            )
            .putString("selected_tab", "private")
            .commit()

        val (tabs, selectedId) = BrowserSessionStore(context).loadTabs()

        assertEquals(listOf("normal"), tabs.map(BrowserTab::id))
        assertEquals(null, selectedId)
    }

    @Test
    fun inactiveTabLifetimeRoundTripsAndUnknownValueFallsBackToNever() {
        val store = BrowserSessionStore(context)
        store.saveInactiveTabLifetime(InactiveTabLifetime.SevenDays)
        assertEquals(InactiveTabLifetime.SevenDays, store.loadInactiveTabLifetime())

        preferences.edit().putString("inactive_tab_lifetime", "unknown").commit()
        assertEquals(InactiveTabLifetime.Never, store.loadInactiveTabLifetime())
    }

    @Test
    fun residentTabLimitDefaultsRoundTripsAndIsClamped() {
        val store = BrowserSessionStore(context)
        assertEquals(TabWebViewResidencyRules.DEFAULT_LIMIT, store.loadResidentTabLimit())

        store.saveResidentTabLimit(15)
        assertEquals(15, store.loadResidentTabLimit())

        store.saveResidentTabLimit(Int.MAX_VALUE)
        assertEquals(TabWebViewResidencyRules.MAX_LIMIT, store.loadResidentTabLimit())

        preferences.edit().putString("resident_tab_limit", "invalid").commit()
        assertEquals(TabWebViewResidencyRules.DEFAULT_LIMIT, store.loadResidentTabLimit())
    }

    @Test
    fun searchEngineRoundTripsAndUnknownValueFallsBackToGoogle() {
        val store = BrowserSessionStore(context)
        store.saveSearchEngine(SearchEngine.DuckDuckGo)
        assertEquals(SearchEngine.DuckDuckGo, store.loadSearchEngine())

        preferences.edit().putString("search_engine", "unknown").commit()
        assertEquals(SearchEngine.Google, store.loadSearchEngine())
    }

    @Test
    fun searxngSettingsRoundTripAndCorruptFallbackIsDisabled() {
        val store = BrowserSessionStore(context)
        store.saveSearxngSettings(
            SearxngSettings(
                instanceUrl = " https://search.example/searxng/ ",
                suggestionFallback = SearchSuggestionProvider.Brave,
            ),
        )
        assertEquals(
            SearxngSettings(
                instanceUrl = "https://search.example/searxng",
                suggestionFallback = SearchSuggestionProvider.Brave,
            ),
            store.loadSearxngSettings(),
        )

        preferences.edit()
            .putString("searxng_instance_url", "x".repeat(SearxngRules.MAX_INSTANCE_URL_LENGTH + 20))
            .putString("searxng_suggestion_fallback", SearchSuggestionProvider.SearXNG.stableId)
            .commit()

        val corrupt = store.loadSearxngSettings()
        assertEquals("", corrupt.instanceUrl)
        assertEquals(SearchSuggestionProvider.None, corrupt.suggestionFallback)

        preferences.edit()
            .putString("searxng_instance_url", "https://alice:secret@search.example?token=secret")
            .commit()
        assertEquals("", store.loadSearxngSettings().instanceUrl)
    }

    @Test
    fun dismissResistanceRoundTripsAndIsClamped() {
        val store = BrowserSessionStore(context)
        assertEquals(40, store.loadDismissResistancePercent())

        store.saveDismissResistancePercent(60)
        assertEquals(60, store.loadDismissResistancePercent())

        store.saveDismissResistancePercent(500)
        assertEquals(90, store.loadDismissResistancePercent())
    }

    @Test
    fun tabOverviewModeRoundTripsAndUnknownValueFallsBackToHero() {
        val store = BrowserSessionStore(context)
        assertEquals(TabOverviewMode.Hero, store.loadTabOverviewMode())

        store.saveTabOverviewMode(TabOverviewMode.Grid)
        assertEquals(TabOverviewMode.Grid, store.loadTabOverviewMode())

        store.saveTabOverviewMode(TabOverviewMode.List)
        assertEquals(TabOverviewMode.List, store.loadTabOverviewMode())

        preferences.edit().putString("tab_overview_mode", "unknown").commit()
        assertEquals(TabOverviewMode.Hero, store.loadTabOverviewMode())
    }

    @Test
    fun tabOrderingPreferencesDefaultOffAndRoundTrip() {
        val store = BrowserSessionStore(context)
        assertFalse(store.loadTabListStartsAtBottom())
        assertFalse(store.loadAutomaticTabSortingEnabled())

        store.saveTabListStartsAtBottom(true)
        store.saveAutomaticTabSortingEnabled(true)

        assertTrue(store.loadTabListStartsAtBottom())
        assertTrue(store.loadAutomaticTabSortingEnabled())
    }

    @Test
    fun addressBarDockPlacementDefaultsMigratesAndRoundTrips() {
        val store = BrowserSessionStore(context)
        assertNull(store.loadAddressBarDockPlacement())

        store.saveAddressBarDocked(true)
        assertEquals(AddressBarDockPlacement.Default, store.loadAddressBarDockPlacement())

        val placement = AddressBarDockPlacement(
            edge = AddressBarDockEdge.Left,
            verticalFraction = 0.42f,
        )
        store.saveAddressBarDockPlacement(placement)
        assertEquals(placement, store.loadAddressBarDockPlacement())
        assertEquals(placement, store.loadLastAddressBarDockPlacement())

        store.saveAddressBarDockPlacement(null)
        assertNull(store.loadAddressBarDockPlacement())
        assertEquals(placement, store.loadLastAddressBarDockPlacement())

        store.saveAddressBarDockPlacement(placement)

        preferences.edit()
            .putInt("address_bar_dock_edge", 7)
            .putString("address_bar_dock_vertical_fraction", "broken")
            .commit()
        assertEquals(AddressBarDockPlacement.Default, store.loadAddressBarDockPlacement())

        store.saveAddressBarDockPlacement(null)
        assertNull(store.loadAddressBarDockPlacement())
        assertEquals(AddressBarDockPlacement.Default, store.loadLastAddressBarDockPlacement())

        preferences.edit().putString("address_bar_docked", "broken").commit()
        assertNull(store.loadAddressBarDockPlacement())
    }

    @Test
    fun tabButtonPreferenceDefaultsVisibleAndRoundTrips() {
        val store = BrowserSessionStore(context)
        assertTrue(store.loadTabButtonVisible())

        store.saveTabButtonVisible(false)
        assertFalse(store.loadTabButtonVisible())

        store.saveTabButtonVisible(true)
        assertTrue(store.loadTabButtonVisible())
    }

    @Test
    fun fullImmersiveModeDefaultsOffAndRoundTrips() {
        val store = BrowserSessionStore(context)
        assertFalse(store.loadFullImmersiveModeEnabled())

        store.saveFullImmersiveModeEnabled(true)
        assertTrue(store.loadFullImmersiveModeEnabled())

        store.saveFullImmersiveModeEnabled(false)
        assertFalse(store.loadFullImmersiveModeEnabled())
    }

    @Test
    fun videoAutoplayBlockingDefaultsOffAndRoundTrips() {
        val store = BrowserSessionStore(context)
        assertFalse(store.loadVideoAutoplayBlocked())

        store.saveVideoAutoplayBlocked(true)
        assertTrue(store.loadVideoAutoplayBlocked())

        store.saveVideoAutoplayBlocked(false)
        assertFalse(store.loadVideoAutoplayBlocked())
    }

    @Test
    fun legacyWebContentEdgeToEdgePreferenceIsRemoved() {
        preferences.edit().putBoolean("web_content_edge_to_edge", true).commit()

        BrowserSessionStore(context).clearLegacyWebContentEdgeToEdgePreference()

        assertFalse(preferences.contains("web_content_edge_to_edge"))
    }

    @Test
    fun permanentSiteExceptionsRoundTripByProfileWithoutUnsafeHosts() {
        val store = BrowserSessionStore(context)
        store.savePermanentSiteExceptions(
            mapOf(
                "candy" to setOf("News.Example", "notexample.com"),
                "work" to setOf("tracker.example"),
                "" to setOf("ignored.example"),
            ),
        )

        assertEquals(
            mapOf(
                "candy" to setOf("news.example", "notexample.com"),
                "work" to setOf("tracker.example"),
            ),
            store.loadPermanentSiteExceptions(),
        )

        preferences.edit().putString("site_exceptions", "not-json").commit()
        assertEquals(emptyMap<String, Set<String>>(), store.loadPermanentSiteExceptions())
    }

    @Test
    fun mutedDomainsRoundTripByProfileAsRegistrableDomains() {
        val store = BrowserSessionStore(context)
        store.saveMutedDomains(
            mapOf(
                "candy" to setOf("Music.News.Example.co.uk", "unsafe host"),
                "work" to setOf("video.example"),
                "" to setOf("ignored.example"),
            ),
        )

        assertEquals(
            mapOf(
                "candy" to setOf("example.co.uk"),
                "work" to setOf("video.example"),
            ),
            store.loadMutedDomains(),
        )

        preferences.edit().putString("muted_domains", "not-json").commit()
        assertEquals(emptyMap<String, Set<String>>(), store.loadMutedDomains())
    }

    @Test
    fun desktopViewDomainsRoundTripByProfileAsRegistrableDomains() {
        val store = BrowserSessionStore(context)
        store.saveDesktopViewDomains(
            mapOf(
                "candy" to setOf("Mobile.News.Example.co.uk", "unsafe host"),
                "work" to setOf("docs.example"),
                "" to setOf("ignored.example"),
            ),
        )

        assertEquals(
            mapOf(
                "candy" to setOf("example.co.uk"),
                "work" to setOf("docs.example"),
            ),
            store.loadDesktopViewDomains(),
        )

        preferences.edit().putString("desktop_view_domains", "not-json").commit()
        assertEquals(emptyMap<String, Set<String>>(), store.loadDesktopViewDomains())
    }

    @Test
    fun sitePrivacyOverridesRoundTripAtomicallyWithoutDefaultOrUnsafeEntries() {
        val store = BrowserSessionStore(context)
        store.saveSitePrivacyOverrides(
            mapOf(
                "candy" to mapOf(
                    "News.Example" to SitePrivacyOverrides(
                        cookieBannerRemovalDisabled = true,
                        forceVerticalScrolling = true,
                        forcePageZooming = true,
                        forceSafeArea = true,
                    ),
                    "default.example" to SitePrivacyOverrides(),
                    "unsafe host" to SitePrivacyOverrides(forceVerticalScrolling = true),
                ),
                "" to mapOf(
                    "ignored.example" to SitePrivacyOverrides(forceVerticalScrolling = true),
                ),
            ),
        )

        assertEquals(
            mapOf(
                "candy" to mapOf(
                    "news.example" to SitePrivacyOverrides(
                        cookieBannerRemovalDisabled = true,
                        forceVerticalScrolling = true,
                        forcePageZooming = true,
                        forceSafeArea = true,
                    ),
                ),
            ),
            store.loadSitePrivacyOverrides(),
        )

        preferences.edit().putString("site_privacy_overrides", "not-json").commit()
        assertEquals(
            emptyMap<String, Map<String, SitePrivacyOverrides>>(),
            store.loadSitePrivacyOverrides(),
        )
    }

    @Test
    fun sitePrivacyOverridesAreBoundedPerProfile() {
        val store = BrowserSessionStore(context)
        store.saveSitePrivacyOverrides(
            mapOf(
                "candy" to (1..70).associate { index ->
                    "site$index.example" to SitePrivacyOverrides(forceVerticalScrolling = true)
                },
            ),
        )

        assertEquals(64, store.loadSitePrivacyOverrides().getValue("candy").size)
    }

    @Test
    fun explicitFalsePrivacyOverridesRoundTripAndLegacyValuesMigrate() {
        val store = BrowserSessionStore(context)
        store.saveSitePrivacyOverrides(
            mapOf(
                "candy" to mapOf(
                    "disabled.example" to SitePrivacyOverrides(forceVerticalScrolling = false),
                    "consent.example" to SitePrivacyOverrides(
                        cookieBannerRemovalDisabled = false,
                    ),
                    "zoom.example" to SitePrivacyOverrides(forcePageZooming = false),
                    "safe.example" to SitePrivacyOverrides(forceSafeArea = false),
                ),
            ),
        )

        assertEquals(
            false,
            store.loadSitePrivacyOverrides()
                .getValue("candy")
                .getValue("disabled.example")
                .forceVerticalScrolling,
        )
        assertEquals(
            false,
            store.loadSitePrivacyOverrides()
                .getValue("candy")
                .getValue("consent.example")
                .cookieBannerRemovalDisabled,
        )
        assertEquals(
            false,
            store.loadSitePrivacyOverrides()
                .getValue("candy")
                .getValue("zoom.example")
                .forcePageZooming,
        )
        assertEquals(
            false,
            store.loadSitePrivacyOverrides()
                .getValue("candy")
                .getValue("safe.example")
                .forceSafeArea,
        )

        preferences.edit().putString(
            "site_privacy_overrides",
            """
            [
              {"profileId":"candy","host":"legacy.example","forceVerticalScrolling":false,"cookieBannerRemovalDisabled":true},
              {"profileId":"candy","host":"legacy-visible.example","forceVerticalScrolling":true,"cookieBannerRemovalDisabled":false}
            ]
            """.trimIndent(),
        ).commit()
        assertEquals(
            null,
            store.loadSitePrivacyOverrides()
                .getValue("candy")
                .getValue("legacy.example")
                .forceVerticalScrolling,
        )
        assertEquals(
            true,
            store.loadSitePrivacyOverrides()
                .getValue("candy")
                .getValue("legacy.example")
                .cookieBannerRemovalDisabled,
        )
        assertEquals(
            null,
            store.loadSitePrivacyOverrides()
                .getValue("candy")
                .getValue("legacy-visible.example")
                .cookieBannerRemovalDisabled,
        )
    }
}
