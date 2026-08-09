package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.DEFAULT_PROFILE_ID
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrides
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                BrowserTab(id = "pinned", lastAccessedAt = 20L, isPinned = true),
            ),
            selectedTabId = "regular",
        )

        val (tabs, selectedId) = store.loadTabs()

        assertEquals(listOf("pinned", "regular"), tabs.map(BrowserTab::id))
        assertEquals(listOf(true, false), tabs.map(BrowserTab::isPinned))
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
    fun searchEngineRoundTripsAndUnknownValueFallsBackToGoogle() {
        val store = BrowserSessionStore(context)
        store.saveSearchEngine(SearchEngine.DuckDuckGo)
        assertEquals(SearchEngine.DuckDuckGo, store.loadSearchEngine())

        preferences.edit().putString("search_engine", "unknown").commit()
        assertEquals(SearchEngine.Google, store.loadSearchEngine())
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
    fun addressBarDockPreferenceDefaultsToCenterAndRoundTrips() {
        val store = BrowserSessionStore(context)
        assertFalse(store.loadAddressBarDocked())

        store.saveAddressBarDocked(true)
        assertEquals(true, store.loadAddressBarDocked())

        store.saveAddressBarDocked(false)
        assertFalse(store.loadAddressBarDocked())
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
    fun webContentEdgeToEdgeDefaultsOffAndRoundTrips() {
        val store = BrowserSessionStore(context)
        assertFalse(store.loadWebContentEdgeToEdgeEnabled())

        store.saveWebContentEdgeToEdgeEnabled(true)
        assertEquals(true, store.loadWebContentEdgeToEdgeEnabled())

        store.saveWebContentEdgeToEdgeEnabled(false)
        assertFalse(store.loadWebContentEdgeToEdgeEnabled())
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
    fun sitePrivacyOverridesRoundTripAtomicallyWithoutDefaultOrUnsafeEntries() {
        val store = BrowserSessionStore(context)
        store.saveSitePrivacyOverrides(
            mapOf(
                "candy" to mapOf(
                    "News.Example" to SitePrivacyOverrides(
                        cookieBannerRemovalDisabled = true,
                        forceVerticalScrolling = true,
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
}
