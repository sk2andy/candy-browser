package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabWebViewStateRepository
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerFederatedLoginInstrumentedTest {
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
    fun tabGrantChangesCookieAndUserAgentPolicyWithoutPersistence() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://login.example/",
                "<html><body>Sign in</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("login.example")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            requireNotNull(controller).detectFederatedLoginForTesting(
                "https://accounts.google.com/gsi/client",
            )
        }
        await { controller?.federatedLoginOffer != null }

        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            val offer = requireNotNull(browserController.federatedLoginOffer)
            browserController.respondToFederatedLoginOffer(
                offer.token,
                FederatedLoginPromptChoice.AllowForTab,
            )

            assertTrue(browserController.siteProtectionState(offer.tabId).thirdPartyLoginAllowed)
            assertTrue(browserController.acceptsThirdPartyCookiesForTesting(offer.tabId))
            val userAgent = browserController.selectedWebViewForTesting().settings.userAgentString
            assertFalse(userAgent.contains("; wv", ignoreCase = true))
            assertFalse(userAgent.contains("Version/", ignoreCase = true))
            assertTrue(BrowserSessionStore(activity).loadSitePrivacyOverrides().isEmpty())

            assertTrue(browserController.revokeFederatedLoginCompatibility(offer.tabId))
            assertFalse(browserController.siteProtectionState(offer.tabId).thirdPartyLoginAllowed)
            assertFalse(browserController.acceptsThirdPartyCookiesForTesting(offer.tabId))
        }
    }

    @Test
    fun profileGrantPersistsForTheExactSiteHost() {
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://login.example/",
                "<html><body>Sign in</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("login.example")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            requireNotNull(controller).detectFederatedLoginForTesting(
                "https://accounts.google.com/gsi/client",
            )
        }
        await { controller?.federatedLoginOffer != null }

        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            val offer = requireNotNull(browserController.federatedLoginOffer)
            browserController.respondToFederatedLoginOffer(
                offer.token,
                FederatedLoginPromptChoice.AllowForProfile,
            )

            val stored = BrowserSessionStore(activity).loadSitePrivacyOverrides()
            assertTrue(
                stored.getValue(offer.profileId)
                    .getValue("login.example")
                    .thirdPartyLoginAllowed == true,
            )
        }
    }

    @Test
    fun federatedLoginPopupSurvivesAppSwitchWithoutPersistingPageData() {
        var openerTabId = ""
        var popupTabId = ""
        lateinit var popup: android.webkit.WebView
        activityRule.scenario.onActivity { activity ->
            val browserController = freshController(activity)
            browserController.onStart()
            browserController.onResume()
            openerTabId = browserController.selectedTabId
            browserController.selectedWebViewForTesting().loadDataWithBaseURL(
                "https://www.reddit.com/",
                "<html><body>Reddit</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitDocumentHost("www.reddit.com")
        await { controller?.isBundledBlockingReadyForTesting() == true }

        activityRule.scenario.onActivity {
            requireNotNull(controller).detectFederatedLoginForTesting(
                "https://accounts.google.com/gsi/client",
            )
        }
        await { controller?.federatedLoginOffer != null }
        activityRule.scenario.onActivity {
            val browserController = requireNotNull(controller)
            val offer = requireNotNull(browserController.federatedLoginOffer)
            browserController.respondToFederatedLoginOffer(
                offer.token,
                FederatedLoginPromptChoice.AllowForTab,
            )
        }
        awaitDocumentHost("www.reddit.com")

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
                "https://accounts.google.com/o/oauth2/v2/auth",
                "<html><body>Google sign-in</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        await {
            val browserController = controller ?: return@await false
            browserController.selectedTabId != openerTabId &&
                browserController.selectedTab.url.startsWith("https://accounts.google.com/") &&
                !browserController.selectedTab.isLoading
        }

        activityRule.scenario.onActivity {
            popupTabId = requireNotNull(controller).selectedTabId
            popup.loadDataWithBaseURL(
                "https://www.reddit.com/login/callback",
                "<html><body>Completing sign-in</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        await {
            val tab = controller?.selectedTab ?: return@await false
            tab.url == "https://www.reddit.com/login/callback" && !tab.isLoading
        }

        activityRule.scenario.onActivity { activity ->
            val browserController = requireNotNull(controller)
            val previewCaptures = browserController.previewCaptureRequestCountForTesting
            browserController.updateInactiveTabLifetime(InactiveTabLifetime.Immediately)
            browserController.onPause()
            browserController.onStop()

            assertEquals(2, browserController.tabs.size)
            assertEquals(popupTabId, browserController.selectedTabId)
            assertTrue(browserController.selectedWebViewForTesting() === popup)
            assertEquals(previewCaptures, browserController.previewCaptureRequestCountForTesting)
            assertTrue(browserController.previews[popupTabId] == null)
            assertTrue(browserController.candyTrail(popupTabId).nodes.isEmpty())
            assertTrue(
                browserController.history.none { entry ->
                    entry.url.contains("accounts.google.com") ||
                        entry.url.contains("/login/callback")
                },
            )

            val sessionStore = BrowserSessionStore(activity)
            assertTrue(sessionStore.flush())
            assertEquals(
                listOf(openerTabId),
                sessionStore.loadTabs().first.map(BrowserTab::id),
            )
            assertEquals(openerTabId, sessionStore.loadProfiles().first.single().selectedTabId)
            val webViewStates = TabWebViewStateRepository.get(activity)
            assertTrue(webViewStates.flush())
            assertNull(webViewStates.load(popupTabId))

            browserController.onStart()
            browserController.onResume()
            assertEquals(2, browserController.tabs.size)
            assertEquals(popupTabId, browserController.selectedTabId)
            assertTrue(browserController.selectedWebViewForTesting() === popup)

            requireNotNull(popup.webChromeClient).onCloseWindow(popup)
            assertEquals(listOf(openerTabId), browserController.tabs.map(BrowserTab::id))
            assertEquals(openerTabId, browserController.selectedTabId)
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
        assertTrue("Timed out waiting for federated-login state", matched)
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
