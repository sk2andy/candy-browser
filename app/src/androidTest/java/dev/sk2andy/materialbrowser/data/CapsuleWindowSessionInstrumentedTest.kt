package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.SearchEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleWindowSessionInstrumentedTest {
    private val context = object : ContextWrapper(
        InstrumentationRegistry.getInstrumentation().targetContext,
    ) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("capsule_window_test_$name", mode)
    }
    private val capsuleId = "04a74ad8-7533-460c-bfbf-a135968940d5"
    private val otherCapsuleId = "0f574fca-8727-4392-b34f-c30a1ee5a058"

    @Before
    @After
    fun clearPreferences() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            BrowserSessionStore.capsulePreferencesName(capsuleId),
            BrowserSessionStore.capsulePreferencesName(otherCapsuleId),
        ).forEach { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test
    fun capsuleSessionsKeepIndependentTabsAndShareBrowserSettings() {
        val browser = BrowserSessionStore(context)
        val capsule = BrowserSessionStore(context, capsuleId)
        val otherCapsule = BrowserSessionStore(context, otherCapsuleId)
        val browserTab = BrowserTab(lastAccessedAt = 1L, id = "browser-tab", title = "Browser", url = "https://example.com")
        val capsuleTab = BrowserTab(lastAccessedAt = 1L, id = "capsule-tab", title = "Capsule", url = "https://mail.example")
        val otherTab = BrowserTab(lastAccessedAt = 1L, id = "other-tab", title = "Other", url = "https://other.example")
        browser.saveTabs(listOf(browserTab), browserTab.id)
        capsule.saveTabs(listOf(capsuleTab), capsuleTab.id)
        otherCapsule.saveTabs(listOf(otherTab), otherTab.id)
        browser.saveSearchEngine(SearchEngine.DuckDuckGo)

        assertEquals("browser-tab", BrowserSessionStore(context).loadTabs().second)
        assertEquals("capsule-tab", BrowserSessionStore(context, capsuleId).loadTabs().second)
        assertEquals("other-tab", BrowserSessionStore(context, otherCapsuleId).loadTabs().second)
        assertEquals(listOf("browser-tab"), browser.loadTabs().first.map(BrowserTab::id))
        assertEquals(SearchEngine.DuckDuckGo, capsule.loadSearchEngine())
    }
}
