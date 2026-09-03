package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.blocking.ContentBlocker
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class BrowserControllerPopupBlockerInstrumentedTest {
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
    fun matchingUserGesturePopupIsClosedBeforeSelection() {
        var openerTabId = ""
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            openerTabId = browserController.selectedTabId
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://eurogamer.net/article",
                "<html><body>opener</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("eurogamer.net")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            val source = browserController.selectedWebViewForTesting()
            val transport = source.WebViewTransport()
            val message = Message.obtain(Handler(Looper.getMainLooper())).apply {
                obj = transport
            }

            assertTrue(
                requireNotNull(source.webChromeClient)
                    .onCreateWindow(source, false, true, message),
            )
            val popup = requireNotNull(transport.webView)
            popup.loadUrl("https://bit.ly/click")
        }

        await { controller?.tabs?.size == 1 }
        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            assertEquals(openerTabId, browserController.selectedTabId)
            assertEquals(openerTabId, browserController.tabs.single().id)
        }
    }

    @Test
    fun automaticPopupStillFailsExistingGestureGate() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val source = browserController.selectedWebViewForTesting()
            val message = Message.obtain(Handler(Looper.getMainLooper())).apply {
                obj = source.WebViewTransport()
            }

            assertTrue(
                !requireNotNull(source.webChromeClient)
                    .onCreateWindow(source, false, false, message),
            )
            assertEquals(1, browserController.tabs.size)
        }
    }

    @Test
    fun crossSiteUserGesturePopupOpensWithoutConfirmation() {
        lateinit var popup: android.webkit.WebView
        var openerTabId = ""
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            openerTabId = browserController.selectedTabId
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://news.example/article",
                "<html><body>opener</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("news.example")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            val source = browserController.selectedWebViewForTesting()
            val transport = source.WebViewTransport()
            val message = Message.obtain(Handler(Looper.getMainLooper())).apply {
                obj = transport
            }
            assertTrue(
                requireNotNull(source.webChromeClient)
                    .onCreateWindow(source, false, true, message),
            )
            popup = requireNotNull(transport.webView)
            popup.loadDataWithBaseURL(
                "https://outside.example/login",
                "<html><head><title>Loaded cross-site page</title></head><body>loaded</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }

        await {
            val browserController = controller
            browserController != null &&
                browserController.selectedTabId != openerTabId &&
                browserController.pendingPopupCountForTesting == 0 &&
                browserController.selectedTab.title == "Loaded cross-site page"
        }
        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            assertTrue(browserController.selectedWebViewForTesting() === popup)
            assertEquals(2, browserController.tabs.size)
            assertEquals(2, browserController.activeTabs.size)
            assertEquals(null, browserController.blockedPopupOffer)
        }
    }

    @Test
    fun sameSitePopupRedirectRemainsProtectedUntilListedCrossSiteTarget() {
        lateinit var popup: android.webkit.WebView
        var openerTabId = ""
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            openerTabId = browserController.selectedTabId
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://eurogamer.net/article",
                "<html><body>opener</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("eurogamer.net")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            val source = browserController.selectedWebViewForTesting()
            val transport = source.WebViewTransport()
            val message = Message.obtain(Handler(Looper.getMainLooper())).apply {
                obj = transport
            }
            assertTrue(
                requireNotNull(source.webChromeClient)
                    .onCreateWindow(source, false, true, message),
            )
            popup = requireNotNull(transport.webView)
            popup.loadDataWithBaseURL(
                "https://eurogamer.net/redirect",
                "<html><body>redirect</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }

        await {
            controller?.selectedTabId != openerTabId &&
                controller?.pendingPopupCountForTesting == 1
        }
        activityRule.scenario.onActivity {
            popup.loadUrl("https://bit.ly/click")
        }

        await { controller?.tabs?.size == 1 }
        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            assertEquals(openerTabId, browserController.selectedTabId)
            assertEquals(1, browserController.activeTabs.size)
            assertEquals(null, browserController.blockedPopupOffer)
        }
    }

    @Test
    fun sameSitePopupProtectionExpiresAfterTimeout() {
        var openerTabId = ""
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            openerTabId = browserController.selectedTabId
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://news.example/article",
                "<html><body>opener</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("news.example")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            val source = browserController.selectedWebViewForTesting()
            val transport = source.WebViewTransport()
            val message = Message.obtain(Handler(Looper.getMainLooper())).apply {
                obj = transport
            }
            assertTrue(
                requireNotNull(source.webChromeClient)
                    .onCreateWindow(source, false, true, message),
            )
            requireNotNull(transport.webView).loadDataWithBaseURL(
                "https://news.example/popup",
                "<html><body>popup</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }

        await {
            controller?.selectedTabId != openerTabId &&
                controller?.pendingPopupCountForTesting == 1
        }
        Thread.sleep(PopupNavigationRules.PENDING_TIMEOUT_MILLIS + 500L)
        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            assertEquals(0, browserController.pendingPopupCountForTesting)
            assertEquals(2, browserController.tabs.size)
        }
    }

    @Test
    fun targetIndependentNowoifRuleProvidesSynchronousDocumentDefuser() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            val source = browserController.selectedWebViewForTesting()
            source.loadDataWithBaseURL(
                "https://dailyuploads.net/file",
                "<html><body>opener</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("dailyuploads.net")

        val result = AtomicReference("pending")
        activityRule.scenario.onActivity { activity ->
            val source = requireNotNull(controller).selectedWebViewForTesting()
            val blocker = ContentBlocker(activity).also {
                it.awaitBundledBlockingForTesting()
            }
            val script = blocker.windowOpenDefuserScript("https://dailyuploads.net/file")
            source.evaluateJavascript(script) {
                source.evaluateJavascript(
                    "window.__candyWindowOpenState&&window.open('https://ads.example')===null",
                    result::set,
                )
            }
        }
        await { result.get() != "pending" }
        assertEquals("true", result.get())
    }

    @Test
    fun targetlessTransientPopupIsDiscardedAfterTimeout() {
        activityRule.scenario.onActivity { activity ->
            freshController(activity)
        }
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            val source = browserController.selectedWebViewForTesting()
            val message = Message.obtain(Handler(Looper.getMainLooper())).apply {
                obj = source.WebViewTransport()
            }
            assertTrue(
                requireNotNull(source.webChromeClient)
                    .onCreateWindow(source, false, true, message),
            )
            assertEquals(2, browserController.tabs.size)
        }

        Thread.sleep(PopupNavigationRules.PENDING_TIMEOUT_MILLIS + 500L)
        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            assertEquals(0, browserController.pendingPopupCountForTesting)
            assertEquals(0, browserController.transientPopupCountForTesting)
            assertEquals(
                browserController.tabs.joinToString { tab -> "${tab.id}:${tab.url}" },
                1,
                browserController.tabs.size,
            )
        }
    }

    private fun freshController(activity: ComponentActivity): BrowserController {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
        return BrowserController(activity).also { controller = it }
    }

    private fun await(condition: () -> Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(100) {
            instrumentation.waitForIdleSync()
            var matched = false
            activityRule.scenario.onActivity { matched = condition() }
            if (matched) return
            Thread.sleep(100)
        }
        var matched = false
        activityRule.scenario.onActivity { matched = condition() }
        assertTrue("Timed out waiting for popup state", matched)
    }

    private fun awaitDocumentHost(expectedHost: String) {
        val result = AtomicReference("pending")
        repeat(50) {
            activityRule.scenario.onActivity {
                requireNotNull(controller).selectedWebViewForTesting().evaluateJavascript(
                    "location.hostname",
                    result::set,
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (result.get().contains(expectedHost)) return
            Thread.sleep(100)
        }
        assertTrue("Timed out waiting for document host", result.get().contains(expectedHost))
    }
}
