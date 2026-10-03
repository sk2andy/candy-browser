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
                sourceTabId = openerTabId,
            )
            browserController.openContextLinkInBackground()
            val childTab = browserController.tabs.single { it.id != openerTabId }
            assertEquals(openerTabId, childTab.openerTabId)
            browserController.selectTab(childTab.id)

            val result = browserController.performSelectedRootTabBack()

            assertEquals(RootTabBackDecision.CloseAndReturnToOpener, result)
            assertEquals(openerTabId, browserController.selectedTabId)
            assertFalse(browserController.tabs.any { it.id == childTab.id })
        }
    }

    @Test
    fun rootBackDelegatesToSystemAndKeepsOnlyActiveTab() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val tabId = browserController.selectedTabId

            val result = browserController.performSelectedRootTabBack()

            assertEquals(RootTabBackDecision.DelegateToSystem, result)
            assertEquals(tabId, browserController.selectedTabId)
            assertTrue(browserController.tabs.any { it.id == tabId })
        }
    }

    @Test
    fun rootBackReturnsHomeWhenLinkPeekSourceWasClosed() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val openerTabId = browserController.selectedTabId
            browserController.contentActions.show(
                WebContentTarget(linkUrl = "https://example.com/child"),
                sourceTabId = openerTabId,
            )
            assertTrue(browserController.openContextLinkInForeground("https://example.com/child"))
            val childTabId = browserController.selectedTabId
            browserController.closeTab(openerTabId)
            browserController.selectTab(childTabId)

            val result = browserController.performSelectedRootTabBack()

            assertEquals(RootTabBackDecision.ReturnToHome, result)
            assertTrue(browserController.tabs.any { it.id == childTabId })
            assertEquals(BLANK_URL, browserController.selectedTab.url)
            assertFalse(browserController.selectedTab.canGoBack)
            assertFalse(browserController.selectedTab.canGoForward)
            assertEquals(
                RootTabBackDecision.DelegateToSystem,
                browserController.selectedRootTabBackDecision,
            )
        }
    }

    @Test
    fun rootBackFromForegroundLinkPeekTabReturnsToSource() {
        assertForegroundLinkPeekRootBack()
    }

    @Test
    fun rootBackFromPinnedLinkPeekTabReturnsToSourceWithoutClosing() {
        assertForegroundLinkPeekRootBack(isPinned = true)
    }

    @Test
    fun rootBackFromPrivateLinkPeekTabReturnsToPrivateSource() {
        assertForegroundLinkPeekRootBack(isPrivate = true)
    }

    @Test
    fun rootBackRequestsOverviewForTabWithoutOpener() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val tabId = browserController.createTab()

            val result = browserController.performSelectedRootTabBack()

            assertEquals(RootTabBackDecision.CloseAndShowTabOverview, result)
            assertFalse(browserController.tabs.any { it.id == tabId })
        }
    }

    @Test
    fun rootBackKeepsPinnedTabAndDelegatesToSystem() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val tabId = browserController.createTab()
            assertTrue(browserController.setTabPinned(tabId, true))

            val result = browserController.performSelectedRootTabBack()

            assertEquals(RootTabBackDecision.DelegateToSystem, result)
            assertEquals(tabId, browserController.selectedTabId)
            assertTrue(browserController.tabs.any { it.id == tabId })
        }
    }

    @Test
    fun rootBackReturnsPinnedWebsiteHomeWithoutChangingPinOrSiblings() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val tabId = browserController.createTab()
            assertTrue(browserController.setTabPinned(tabId, true))
            browserController.openUrl("https://example.com/first")
            val tabIds = browserController.activeTabs.map { it.id }

            assertEquals(RootTabBackDecision.ReturnToHome, browserController.performSelectedRootTabBack())

            assertEquals(tabIds, browserController.activeTabs.map { it.id })
            assertEquals(tabId, browserController.selectedTabId)
            assertTrue(browserController.selectedTab.isPinned)
            assertEquals(BLANK_URL, browserController.selectedTab.url)
            assertEquals(RootTabBackDecision.DelegateToSystem, browserController.selectedRootTabBackDecision)
            val storedTab = BrowserSessionStore(activity).loadTabs().first.single { it.id == tabId }
            assertEquals(BLANK_URL, storedTab.url)
            assertTrue(storedTab.isPinned)
        }
    }

    @Test
    fun rootBackReturnsPrivateWebsiteToPrivateHome() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            assertTrue(browserController.setBlankTabIncognito(true))
            val tabId = browserController.selectedTabId
            browserController.openUrl("https://example.com/private")

            assertEquals(RootTabBackDecision.ReturnToHome, browserController.performSelectedRootTabBack())

            assertEquals(tabId, browserController.selectedTabId)
            assertEquals(BLANK_URL, browserController.selectedTab.url)
            assertTrue(browserController.selectedTab.isIncognito)
            assertFalse(browserController.selectedTab.canGoBack)
            assertFalse(browserController.selectedTab.canGoForward)
            assertEquals(RootTabBackDecision.DelegateToSystem, browserController.selectedRootTabBackDecision)
            assertTrue(BrowserSessionStore(activity).loadTabs().first.none { it.id == tabId || it.isIncognito })
        }
    }

    @Test
    fun newTabNavigationSucceedsBeyondFiftyTabs() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            repeat(50) {
                browserController.createTab()
            }
            val selectedTabId = browserController.selectedTabId

            val opened = browserController.openUrl(
                url = "https://example.com/from-another-app",
                inNewTab = true,
            )

            assertTrue(opened)
            assertFalse(selectedTabId == browserController.selectedTabId)
            assertEquals(52, browserController.tabs.size)
            assertEquals(
                "https://example.com/from-another-app",
                browserController.selectedTab.url,
            )
        }
    }

    private fun assertForegroundLinkPeekRootBack(
        isPinned: Boolean = false,
        isPrivate: Boolean = false,
    ) {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            if (isPrivate) assertTrue(browserController.setBlankTabIncognito(true))
            val sourceTabId = browserController.selectedTabId
            browserController.openUrl("https://example.com/source")
            browserController.contentActions.show(
                WebContentTarget(linkUrl = "https://example.com/child"),
                sourceTabId = sourceTabId,
            )
            assertTrue(browserController.openContextLinkInForeground("https://example.com/child"))
            val childTabId = browserController.selectedTabId
            assertEquals(sourceTabId, browserController.selectedTab.openerTabId)
            assertEquals(isPrivate, browserController.selectedTab.isIncognito)
            if (isPinned) assertTrue(browserController.setTabPinned(childTabId, true))

            assertEquals(
                if (isPinned) RootTabBackDecision.ReturnToOpener else RootTabBackDecision.CloseAndReturnToOpener,
                browserController.performSelectedRootTabBack(),
            )

            assertEquals(sourceTabId, browserController.selectedTabId)
            assertEquals("https://example.com/source", browserController.selectedTab.url)
            assertEquals(isPrivate, browserController.selectedTab.isIncognito)
            if (isPinned) {
                val child = browserController.tabs.single { it.id == childTabId }
                assertTrue(child.isPinned)
                assertEquals("https://example.com/child", child.url)
            } else {
                assertTrue(browserController.tabs.none { it.id == childTabId })
            }
            if (isPrivate) {
                assertTrue(BrowserSessionStore(activity).loadTabs().first.none(BrowserTab::isIncognito))
            }
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
