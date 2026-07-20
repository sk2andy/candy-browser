package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.SearchEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("legacy", selectedId)

        store.saveTabs(
            listOf(BrowserTab(id = "saved", lastAccessedAt = 84_000L)),
            selectedTabId = "saved",
        )
        assertEquals(84_000L, store.loadTabs(nowMillis = 1L).first.single().lastAccessedAt)
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
}
