package dev.sk2andy.materialbrowser.browser.gecko

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserEngineNavigationTarget
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.SnoozedTab
import dev.sk2andy.materialbrowser.data.SnoozedTabStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoInternalPageNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: BrowserController
    private lateinit var host: FrameLayout
    private lateinit var originalEngineKind: AndroidBrowserEngineKind
    private var originalTabs: Pair<List<BrowserTab>, String?>? = null
    private var originalHistory: List<HistoryEntry>? = null
    private var originalSnoozedTabs: List<SnoozedTab>? = null
    private var originalResidentTabLimit = 10

    @Before
    fun setUp() {
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalTabs = store.loadTabs()
            originalHistory = store.loadHistory()
            originalSnoozedTabs = SnoozedTabStore(composeRule.activity).load()
            originalResidentTabLimit = store.loadResidentTabLimit()
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            controller = BrowserController(composeRule.activity)
            host = FrameLayout(composeRule.activity)
            composeRule.activity.addContentView(
                host,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            controller.onResume()
        }
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            if (::controller.isInitialized) controller.destroy()
            val store = BrowserSessionStore(composeRule.activity)
            if (::originalEngineKind.isInitialized) {
                assertTrue(store.saveAndroidBrowserEngineKind(originalEngineKind))
            }
            originalTabs?.let { (tabs, selection) ->
                assertTrue(store.saveTabsImmediately(tabs, selection.orEmpty()))
            }
            originalHistory?.let { assertTrue(store.commitHistory(it)) }
            originalSnoozedTabs?.let {
                assertTrue(SnoozedTabStore(composeRule.activity).save(it))
            }
            store.saveResidentTabLimit(originalResidentTabLimit)
        }
    }

    @Test
    fun addressSubmissionLoadsConfigAndBuildconfigWithReloadAndBack() {
        openPage("about:config")
        composeRule.runOnIdle {
            assertTrue(controller.selectedTab.title.isNotBlank())
            assertNull(controller.externalAppPrompt)
            controller.submitAddress("about:buildconfig")
        }
        awaitPage("about:buildconfig")
        composeRule.runOnIdle {
            assertTrue(controller.selectedTab.title.isNotBlank())
            assertTrue(controller.selectedTab.canGoBack)
            controller.reload()
        }
        awaitPage("about:buildconfig")
        composeRule.runOnIdle { controller.goBack() }
        awaitPage("about:config")
        composeRule.runOnIdle {
            assertTrue(controller.selectedTab.canGoForward)
            controller.goForward()
        }
        awaitPage("about:buildconfig")
    }

    @Test
    fun privateInternalPageStaysOutOfPersistentTabsAndHistory() {
        composeRule.runOnIdle {
            controller.createTab(initialUrl = "about:config", isIncognito = true)
            controller.attachSelectedBrowserEngineView(host)
        }
        awaitPage("about:config")
        composeRule.runOnIdle {
            val tabId = controller.selectedTabId
            assertTrue(controller.selectedTab.isIncognito)
            val store = BrowserSessionStore(composeRule.activity)
            assertTrue(store.flush())
            assertTrue(store.loadTabs().first.none { it.id == tabId })
            assertTrue(store.loadHistory().none { it.url == "about:config" })
        }
    }

    @Test
    fun webpageRedirectAndInternalPopupCannotOpenConfiguration() {
        openPage("about:buildconfig")
        composeRule.runOnIdle {
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                controller.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = "about:config",
                        isRedirect = false,
                        hasUserGesture = true,
                        isDirectNavigation = false,
                        target = BrowserEngineNavigationTarget.New,
                    ),
                ),
            )
            // Model the committed web source without starting a remote page request.
            controller.dispatchGeckoEngineEventForTesting(
                BrowserEngineEvent(
                    tabId = controller.selectedTabId,
                    type = BrowserEngineEventType.NavigationCommitted,
                    address = "https://example.com",
                    title = "Example",
                    canGoBack = false,
                    canGoForward = false,
                    failureDescription = null,
                ),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                controller.dispatchSelectedGeckoNavigationRequestForTesting(
                    GeckoMainFrameNavigationRequest(
                        url = "about:config",
                        isRedirect = true,
                        hasUserGesture = true,
                        isDirectNavigation = false,
                    ),
                ),
            )
            assertNull(controller.externalAppPrompt)
        }
    }

    @Test
    fun webHistoryReturnsToInternalPageAndEvictedTabReloads() {
        EdgeToEdgeSiteFixtureServer { "<title>Internal page history fixture</title>" }.use { server ->
            openPage("about:config")
            val internalTabId = composeRule.runOnIdle { controller.selectedTabId }
            composeRule.runOnIdle { controller.submitAddress(server.url) }
            awaitPage(server.url)
            composeRule.runOnIdle { controller.goBack() }
            awaitPage("about:config")
            composeRule.runOnIdle {
                controller.updateResidentTabLimit(1)
                controller.createTab(initialUrl = "about:buildconfig")
                controller.attachSelectedBrowserEngineView(host)
            }
            awaitPage("about:buildconfig")
            composeRule.waitUntil(20_000) {
                composeRule.runOnIdle { internalTabId !in controller.residentTabIdsForTesting() }
            }
            composeRule.runOnIdle {
                controller.selectTab(internalTabId)
                controller.attachSelectedBrowserEngineView(host)
            }
            awaitPage("about:config")
        }
    }

    @Test
    fun systemWebviewKeepsAboutInputAsSearch() {
        composeRule.runOnIdle {
            val profileId = controller.selectedTab.profileId
            controller.destroy()
            val store = BrowserSessionStore(composeRule.activity)
            val nowMillis = System.currentTimeMillis()
            val internalTab = BrowserTab(
                id = "internal-page-engine-switch",
                lastAccessedAt = nowMillis,
                profileId = profileId,
                url = "about:config",
            )
            assertTrue(store.saveTabsImmediately(listOf(internalTab), internalTab.id))
            assertTrue(
                SnoozedTabStore(composeRule.activity).save(
                    listOf(
                        SnoozedTab(
                            tab = internalTab.copy(id = "internal-page-snoozed"),
                            wakeAtMillis = nowMillis + 60_000,
                            createdAtMillis = nowMillis,
                        ),
                    ),
                ),
            )
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView))
            controller = BrowserController(composeRule.activity)
            assertFalse(controller.usesGeckoEngine)
            assertEquals(BLANK_URL, controller.selectedTab.url)
            assertEquals(BLANK_URL, controller.snoozedTabs.single().tab.url)
            assertTrue(controller.openSnoozedTabNow("internal-page-snoozed"))
            assertEquals(BLANK_URL, controller.selectedTab.url)
            controller.submitAddress("about:config")
            assertTrue(controller.selectedTab.url.startsWith("https://"))
            assertTrue(controller.selectedTab.url.contains("about%3Aconfig"))
        }
    }

    private fun openPage(url: String) {
        composeRule.runOnIdle {
            controller.createTab()
            controller.submitAddress(url)
            controller.attachSelectedBrowserEngineView(host)
        }
        awaitPage(url)
    }

    private fun awaitPage(url: String) {
        composeRule.waitUntil(30_000) {
            composeRule.runOnIdle {
                controller.selectedTab.let { tab ->
                    tab.url == url && !tab.isLoading && tab.error == null && tab.title.isNotBlank()
                }
            }
        }
    }
}
