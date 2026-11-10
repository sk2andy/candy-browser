package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.userscript.GreasyForkInstallStage
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GreasyForkDownloadResult
import dev.sk2andy.materialbrowser.data.UserScriptStore
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
class BrowserControllerGreasyForkInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private lateinit var controller: BrowserController
    private lateinit var host: FrameLayout
    private lateinit var webView: WebView
    private var downloads = 0
    private val script = (UserScriptParser.parse("preview", SCRIPT) as UserScriptParseResult.Accepted).script

    @Before
    fun setUp() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear()
                .putString(
                    BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE,
                    AndroidBrowserEngineKind.SystemWebView.stableId,
                ).commit()
            UserScriptStore(activity).clear()
            controller = BrowserController(activity)
            controller.onStart()
            controller.onResume()
            host = FrameLayout(activity)
            activity.addContentView(host, FrameLayout.LayoutParams(-1, -1))
            webView = requireNotNull(controller.attachSelectedBrowserEngineView(host).findWebView())
            controller.greasyForkDownloadForTesting = { _, complete ->
                downloads++
                complete(GreasyForkDownloadResult.Success(script))
            }
        }
    }

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller.destroy()
            UserScriptStore(activity).clear()
            activity.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test
    fun installingRequiresConfirmationAndPersistsOnlyAfterApproval() {
        activityRule.scenario.onActivity { activity ->
            assertTrue(navigate(INSTALL_URL))
            assertEquals(1, downloads)
            assertTrue(controller.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.Loading)
            assertTrue(controller.userScripts.isEmpty())
            assertTrue(UserScriptStore(activity).load().isEmpty())
        }
        waitUntil { it.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.Review }
        activityRule.scenario.onActivity { activity ->
            assertTrue(controller.userScripts.isEmpty())
            assertTrue(UserScriptStore(activity).load().isEmpty())
            controller.dismissGreasyForkInstall()
            assertNull(controller.greasyForkInstallPrompt)
            assertTrue(navigate(INSTALL_URL))
        }
        waitUntil { it.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.Review }
        activityRule.scenario.onActivity { controller.confirmGreasyForkInstall() }
        waitUntil { it.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.Installed }
        activityRule.scenario.onActivity { activity ->
            assertEquals(2, downloads)
            assertEquals(listOf(script.source), controller.userScripts.map { it.source })
            assertEquals(listOf(script.source), UserScriptStore(activity).load().map { it.source })
            controller.dismissGreasyForkInstall()
            assertTrue(navigate(INSTALL_URL))
        }
        waitUntil { it.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.Review }
        activityRule.scenario.onActivity { controller.confirmGreasyForkInstall() }
        waitUntil { it.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.Installed }
        activityRule.scenario.onActivity { assertEquals(1, controller.userScripts.size) }
    }

    @Test
    fun cancelledDownloadDoesNotOpenReviewOrSave() {
        lateinit var complete: (GreasyForkDownloadResult) -> Unit
        activityRule.scenario.onActivity {
            controller.greasyForkDownloadForTesting = { _, callback -> complete = callback }
            assertTrue(navigate(INSTALL_URL))
            controller.dismissGreasyForkInstall()
            complete(GreasyForkDownloadResult.Success(script))
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityRule.scenario.onActivity { activity ->
            assertNull(controller.greasyForkInstallPrompt)
            assertTrue(controller.userScripts.isEmpty())
            assertTrue(UserScriptStore(activity).load().isEmpty())
        }
    }

    @Test
    fun privateTabDoesNotDownloadOrPersistScript() {
        activityRule.scenario.onActivity { activity ->
            controller.createTab(isIncognito = true)
            webView = requireNotNull(controller.attachSelectedBrowserEngineView(host).findWebView())
            assertTrue(navigate(INSTALL_URL))
            assertTrue(controller.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.PrivateBlocked)
            assertEquals(0, downloads)
            controller.confirmGreasyForkInstall()
            assertTrue(controller.userScripts.isEmpty())
            assertTrue(UserScriptStore(activity).load().isEmpty())
        }
    }

    @Test
    fun ordinaryOrNonGestureNavigationsDoNotStartInstallation() {
        activityRule.scenario.onActivity {
            navigate("https://example.invalid/page")
            navigate(INSTALL_URL, hasGesture = false)
            assertFalse(navigate(INSTALL_URL, isMainFrame = false))
            assertEquals(0, downloads)
            assertNull(controller.greasyForkInstallPrompt)
        }
    }

    @Test
    fun delayedGreasyForkInstallLinkDoesNotNeedPreservedGesture() {
        activityRule.scenario.onActivity {
            webView.loadDataWithBaseURL(
                "https://greasyfork.org/en/scripts/476331-insert-code",
                "<html><head><title>Greasy Fork source</title></head><body>Source</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        waitUntil { it.selectedTab.title == "Greasy Fork source" }
        activityRule.scenario.onActivity {
            assertTrue(navigate(INSTALL_URL, hasGesture = false))
            assertEquals(1, downloads)
        }
        waitUntil { it.greasyForkInstallPrompt?.stage is GreasyForkInstallStage.Review }
        activityRule.scenario.onActivity {
            controller.dismissGreasyForkInstall()
            webView.loadDataWithBaseURL(
                "https://example.invalid/",
                "<html><head><title>Other source</title></head><body>Source</body></html>",
                "text/html",
                "utf-8",
                null,
            )
        }
        waitUntil { it.selectedTab.title == "Other source" }
        activityRule.scenario.onActivity {
            navigate(INSTALL_URL, hasGesture = false)
            assertEquals(1, downloads)
            assertNull(controller.greasyForkInstallPrompt)
        }
    }

    private fun navigate(
        url: String,
        hasGesture: Boolean = true,
        isMainFrame: Boolean = true,
    ): Boolean = webView.webViewClient.shouldOverrideUrlLoading(
        webView,
        object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = isMainFrame
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = hasGesture
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        },
    )

    private fun waitUntil(condition: (BrowserController) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            activityRule.scenario.onActivity { done = condition(controller) }
            if (done) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Greasy Fork installation did not reach the expected state")
    }

    private fun View?.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findWebView() }
        else -> null
    }

    private companion object {
        const val INSTALL_URL = "https://update.greasyfork.org/scripts/476331/Insert%20code.user.js"
        val SCRIPT = """
            // ==UserScript==
            // @name Example
            // @match https://example.com/*
            // @grant none
            // ==/UserScript==
            document.title = 'Example';
        """.trimIndent()
    }
}
