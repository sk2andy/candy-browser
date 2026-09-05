package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerStartupHomeInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun startupHomeKeepsRestoredTabAndDoesNotDuplicateBlankTab() {
        activityRule.scenario.onActivity { activity ->
            val restoredTab = BrowserTab(
                id = "restored",
                lastAccessedAt = 1L,
                url = "https://example.com",
            )
            BrowserSessionStore(activity).saveTabsImmediately(
                tabs = listOf(restoredTab),
                selectedTabId = restoredTab.id,
            )
            val browserController = BrowserController(activity).also { controller = it }

            assertTrue(browserController.openNormalHome())

            assertEquals(2, browserController.activeTabs.size)
            assertTrue(browserController.activeTabs.any { it.id == restoredTab.id })
            assertTrue(browserController.selectedTab.isFreshBlankTab)

            assertTrue(browserController.openNormalHome())

            assertEquals(2, browserController.activeTabs.size)
            assertTrue(browserController.selectedTab.isFreshBlankTab)
        }
    }

    @Test
    fun startupHomeKeepsSelectionWhenTabLimitIsReached() {
        activityRule.scenario.onActivity { activity ->
            val restoredTabs = List(MAX_TABS) { index ->
                BrowserTab(
                    id = "restored-$index",
                    lastAccessedAt = index.toLong() + 1L,
                    url = "https://example.com/$index",
                )
            }
            val selectedTab = restoredTabs.last()
            BrowserSessionStore(activity).saveTabsImmediately(
                tabs = restoredTabs,
                selectedTabId = selectedTab.id,
            )
            val browserController = BrowserController(activity).also { controller = it }

            assertFalse(browserController.openNormalHome())

            assertEquals(MAX_TABS, browserController.activeTabs.size)
            assertEquals(selectedTab.id, browserController.selectedTabId)
            assertEquals(selectedTab.url, browserController.selectedTab.url)
        }
    }
}
