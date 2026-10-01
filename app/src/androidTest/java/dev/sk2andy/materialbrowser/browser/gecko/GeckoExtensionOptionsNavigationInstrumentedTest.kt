package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserEngineNavigationTarget
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.HistoryEntry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.WebExtension

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoExtensionOptionsNavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: BrowserController
    private lateinit var host: FrameLayout
    private lateinit var optionsUrl: String
    private lateinit var originalEngineKind: AndroidBrowserEngineKind
    private var originalTabs: Pair<List<BrowserTab>, String?>? = null
    private var originalHistory: List<HistoryEntry>? = null
    private var originalResidentTabLimit = 10

    @Before
    fun setUp() {
        val installed = AtomicReference<WebExtension>()
        val failure = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        composeRule.runOnIdle {
            val store = BrowserSessionStore(composeRule.activity)
            originalEngineKind = store.loadAndroidBrowserEngineKind()
            originalTabs = store.loadTabs()
            originalHistory = store.loadHistory()
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
            val runtime = GeckoRuntimeOwner.getOrCreate(composeRule.activity)
                as GeckoViewRuntimeHandle
            runtime.ensureBuiltInExtensionFixture()
                .withHandler(Handler(Looper.getMainLooper()))
                .accept(
                    { extension ->
                        installed.set(checkNotNull(extension))
                        latch.countDown()
                    },
                    { error ->
                        failure.set(error)
                        latch.countDown()
                    },
                )
        }
        assertTrue("Fixture install timed out", latch.await(20, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Fixture install failed", it) }
        optionsUrl = checkNotNull(installed.get()?.metaData?.optionsPageUrl)
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            if (::controller.isInitialized) controller.destroy()
            val store = BrowserSessionStore(composeRule.activity)
            if (::originalEngineKind.isInitialized) {
                assertTrue(store.saveAndroidBrowserEngineKind(originalEngineKind))
            }
            store.saveResidentTabLimit(originalResidentTabLimit)
            originalTabs?.let { (tabs, selection) ->
                assertTrue(store.saveTabsImmediately(tabs, selection.orEmpty()))
            }
            originalHistory?.let { assertTrue(store.commitHistory(it)) }
        }
    }

    @Test
    fun sameExtensionOptionsNavigationStaysInsideGeckoAndRejectsForeignOrigin() {
        openOptions()
        composeRule.runOnIdle {
            val assetUrl = optionsUrl.substringBeforeLast('/') + "/asset-viewer.html?url=filters.txt"
            assertEquals(GeckoNavigationRequestDecision.Allow, decision(assetUrl))
            assertEquals(
                GeckoNavigationRequestDecision.Allow,
                decision(assetUrl, BrowserEngineNavigationTarget.New),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Allow,
                decision(assetUrl, BrowserEngineNavigationTarget.New, hasUserGesture = false),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Allow,
                decision("about:blank", BrowserEngineNavigationTarget.New, hasUserGesture = false),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                decision("moz-extension://foreign-extension/asset-viewer.html"),
            )
            assertEquals(
                GeckoNavigationRequestDecision.Deny,
                decision(optionsUrl.replace("://", "://user@")),
            )
            assertNull(controller.externalAppPrompt)
        }
    }

    @Test
    fun evictedOptionsRendererReloadsCurrentExtensionUrlWithoutPersistingTab() {
        val currentUrl = "$optionsUrl?restored=1#current"
        val ownerId = composeRule.runOnIdle { controller.selectedTabId }
        val optionsId = openOptions(currentUrl)
        lateinit var oldSession: AndroidBrowserEngineSessionPort
        var previousGeneration = 0
        composeRule.runOnIdle {
            oldSession = sessionFor(optionsId)
            previousGeneration = navigationGeneration(optionsId)
            controller.updateResidentTabLimit(1)
            controller.selectTab(ownerId)
            controller.attachSelectedBrowserEngineView(host)
        }
        composeRule.waitUntil(20_000) {
            composeRule.runOnIdle { optionsId !in controller.residentTabIdsForTesting() }
        }
        composeRule.runOnIdle {
            controller.selectTab(optionsId)
            controller.attachSelectedBrowserEngineView(host)
            assertNotSame(oldSession, sessionFor(optionsId))
        }
        awaitLoadedOptions(currentUrl, afterGeneration = previousGeneration)
        composeRule.runOnIdle {
            assertEquals("Fixture", controller.selectedFirefoxExtensionOptionsTitle)
            val store = BrowserSessionStore(composeRule.activity)
            assertTrue(store.flush())
            assertTrue(store.loadTabs().first.none { it.id == optionsId })
            assertTrue(store.loadHistory().none { it.url == currentUrl })
            assertEquals(
                controller.tabs.first { it.id == ownerId }.profileId,
                controller.tabs.first { it.id == optionsId }.profileId,
            )
        }
    }

    @Test
    fun userOpenedExtensionAssetWindowAdoptsGeckoSessionAndKeepsOptionsContext() {
        val ownerId = openOptions("$optionsUrl?navigation-window=1")
        val targetUrl = optionsUrl.substringBeforeLast('/') + "/asset-viewer.html"
        val clickPoint = composeRule.runOnIdle {
            val location = IntArray(2)
            host.getLocationOnScreen(location)
            val density = composeRule.activity.resources.displayMetrics.density
            intArrayOf(
                location[0] + (126 * density).toInt(),
                location[1] + (128 * density).toInt(),
            )
        }
        assertTrue(
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .click(clickPoint[0], clickPoint[1]),
        )
        composeRule.waitUntil(20_000) {
            composeRule.runOnIdle { controller.selectedTabId != ownerId }
        }
        composeRule.runOnIdle { controller.attachSelectedBrowserEngineView(host) }
        composeRule.waitUntil(20_000) {
            composeRule.runOnIdle {
                controller.tabs.first { it.id == controller.selectedTabId }.let { tab ->
                    tab.url == targetUrl && tab.title == "Candy fixture asset viewer" &&
                        !tab.isLoading && tab.error == null
                }
            }
        }
        composeRule.runOnIdle {
            val child = controller.tabs.first { it.id == controller.selectedTabId }
            val owner = controller.tabs.first { it.id == ownerId }
            assertEquals(ownerId, child.openerTabId)
            assertEquals(owner.profileId, child.profileId)
            assertEquals(owner.isIncognito, child.isIncognito)
            assertEquals("Fixture", controller.selectedFirefoxExtensionOptionsTitle)
            assertNull(controller.externalAppPrompt)
            val store = BrowserSessionStore(composeRule.activity)
            assertTrue(store.flush())
            assertTrue(store.loadTabs().first.none { it.id == child.id })
        }
    }

    private fun openOptions(url: String = optionsUrl): String {
        val tabId = composeRule.runOnIdle {
            assertTrue(
                controller.openSelectedFirefoxExtensionOptionsPage(
                    GeckoExtensionOptionsTarget(FIXTURE_ID, "Fixture", url),
                ),
            )
            controller.attachSelectedBrowserEngineView(host)
            controller.selectedTabId
        }
        awaitLoadedOptions(url)
        return tabId
    }

    private fun awaitLoadedOptions(url: String, afterGeneration: Int? = null) {
        composeRule.waitUntil(30_000) {
            composeRule.runOnIdle {
                controller.tabs.first { it.id == controller.selectedTabId }.let { tab ->
                    tab.url == url && tab.title == "Candy fixture options" &&
                        !tab.isLoading && tab.error == null &&
                        (afterGeneration == null || navigationGeneration(tab.id) > afterGeneration)
                }
            }
        }
    }

    private fun decision(
        url: String,
        target: BrowserEngineNavigationTarget = BrowserEngineNavigationTarget.Current,
        hasUserGesture: Boolean = true,
    ): GeckoNavigationRequestDecision =
        controller.dispatchSelectedGeckoNavigationRequestForTesting(
            GeckoMainFrameNavigationRequest(
                url = url,
                isRedirect = false,
                hasUserGesture = hasUserGesture,
                isDirectNavigation = false,
                target = target,
            ),
        )

    private fun sessionFor(tabId: String): AndroidBrowserEngineSessionPort {
        val field = BrowserController::class.java.getDeclaredField("browserEngineSessions")
        field.isAccessible = true
        val sessions = field.get(controller) as Map<*, *>
        return sessions[tabId] as AndroidBrowserEngineSessionPort
    }

    private fun navigationGeneration(tabId: String): Int {
        val field = BrowserController::class.java.getDeclaredField("navigationGenerations")
        field.isAccessible = true
        val generations = field.get(controller) as Map<*, *>
        return generations[tabId] as? Int ?: 0
    }

    private companion object {
        const val FIXTURE_ID = "candy-firefox-fixture@sk2andy.dev"
    }
}
