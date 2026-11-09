package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePageSettingsInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test
    fun independentUrlsRoundTripAndOnlyUserTabsUseCustomPage() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
            val browser = BrowserController(activity).also { controller = it }
            val settings = HomePageSettings("https://home.example/", "https://new.example/")
            browser.updateHomePageSettings(settings)
            assertEquals(settings, BrowserSessionStore(activity).loadHomePageSettings())
            browser.createUserTab(isIncognito = false)
            assertEquals("https://new.example/", browser.selectedTab.url)
            val tabId = browser.selectedTabId
            browser.openHomePage()
            assertEquals(tabId, browser.selectedTabId)
            assertEquals("https://home.example/", browser.selectedTab.url)
            browser.createTab("https://link.example/", isIncognito = false)
            assertEquals("https://link.example/", browser.selectedTab.url)
            browser.createTab(isIncognito = false)
            assertEquals(BLANK_URL, browser.selectedTab.url)
            browser.createUserTab(isIncognito = true)
            assertTrue(browser.selectedTab.isIncognito)
            assertEquals(BLANK_URL, browser.selectedTab.url)
            browser.openNormalHome()
            assertEquals(BLANK_URL, browser.selectedTab.url)
            browser.updateHomePageSettings(HomePageSettings())
            browser.createUserTab(isIncognito = false)
            assertEquals(BLANK_URL, browser.selectedTab.url)
        }
    }

    @Test
    fun corruptStoredUrlsFallBackToInternalPage() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().putString("home_page_url", "javascript:alert(1)")
                .putString("new_tab_page_url", "https://new.example/").commit()
            assertEquals(HomePageSettings("", "https://new.example/"), BrowserSessionStore(activity).loadHomePageSettings())
        }
    }
}
