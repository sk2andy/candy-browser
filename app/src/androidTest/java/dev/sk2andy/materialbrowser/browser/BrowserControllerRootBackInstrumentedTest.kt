package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerRootBackInstrumentedTest {
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
    fun rootBackClosesLinkTabAndReturnsToOpener() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val openerTabId = browserController.selectedTabId
            browserController.contentActions.show(
                WebContentTarget(linkUrl = "https://example.com/child"),
            )
            browserController.openContextLinkInBackground()
            val childTab = browserController.tabs.single { it.id != openerTabId }
            browserController.selectTab(childTab.id)

            val result = browserController.closeSelectedRootTab()

            assertEquals(RootTabBackResult.ReturnedToOpener, result)
            assertEquals(openerTabId, browserController.selectedTabId)
            assertFalse(browserController.tabs.any { it.id == childTab.id })
        }
    }

    @Test
    fun rootBackRequestsOverviewWhenOpenerNoLongerExists() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val openerTabId = browserController.selectedTabId
            val childTabId = requireNotNull(
                browserController.createBackgroundTab(
                    initialUrl = "https://example.com/child",
                    openerTabId = openerTabId,
                ),
            )
            browserController.closeTab(openerTabId)
            browserController.selectTab(childTabId)

            val result = browserController.closeSelectedRootTab()

            assertEquals(RootTabBackResult.ShowTabOverview, result)
            assertFalse(browserController.tabs.any { it.id == childTabId })
        }
    }

    @Test
    fun rootBackRequestsOverviewForTabWithoutOpener() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val tabId = browserController.createTab()

            val result = browserController.closeSelectedRootTab()

            assertEquals(RootTabBackResult.ShowTabOverview, result)
            assertFalse(browserController.tabs.any { it.id == tabId })
        }
    }

    @Test
    fun rootBackKeepsPinnedTabAndRequestsOverview() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val tabId = browserController.createTab()
            assertTrue(browserController.setTabPinned(tabId, true))

            val result = browserController.closeSelectedRootTab()

            assertEquals(RootTabBackResult.ShowTabOverview, result)
            assertEquals(tabId, browserController.selectedTabId)
            assertTrue(browserController.tabs.any { it.id == tabId })
        }
    }

    private fun freshController(activity: ComponentActivity): BrowserController {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        return BrowserController(activity).also { controller = it }
    }
}
