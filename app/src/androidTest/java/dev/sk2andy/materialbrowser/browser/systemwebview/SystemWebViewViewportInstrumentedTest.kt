package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemWebViewViewportInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @Before
    fun setUp() {
        preferences().edit().clear().putString(
            BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE,
            AndroidBrowserEngineKind.SystemWebView.stableId,
        ).commit()
    }

    @After
    fun tearDown() {
        composeRule.runOnIdle { controller?.destroy() }
        preferences().edit().clear().commit()
    }

    @Test
    fun legacyCenteredPanelFitsWideMobileViewport() {
        val result = loadViewport(meta = null, fixedPanel = true)
        assertTrue(
            "Legacy page needs a browser-wide viewport: $result",
            result.getDouble("width") >= 850,
        )
        assertTrue("Panel starts outside viewport: $result", result.getDouble("left") >= 0)
        assertTrue(
            "Panel ends outside viewport: $result",
            result.getDouble("right") <= result.getDouble("width"),
        )
        assertTrue("Legacy page must fit screen width: $result", result.getDouble("scale") < 1)
    }

    @Test
    fun authoredFixedViewportWidthIsHonoredInMobileMode() {
        val result = loadViewport(meta = "width=850")
        assertEquals(850.0, result.getDouble("width"), 1.0)
        assertTrue(result.getDouble("scale") < 1)
    }

    @Test
    fun responsiveMobileViewportKeepsDeviceWidthAndAuthoredScale() {
        val result = loadViewport(meta = "width=device-width,initial-scale=1")
        assertEquals(result.getDouble("deviceWidth"), result.getDouble("width"), 1.0)
        assertEquals(1.0, result.getDouble("scale"), 0.01)
    }

    @Test
    fun returningFromDesktopModePreservesMobileViewportSupport() {
        val loaded = CountDownLatch(1)
        lateinit var webView: WebView
        lateinit var factory: SystemWebViewBrowserEngineFactory
        lateinit var session: AndroidBrowserEngineSessionPort
        composeRule.runOnIdle {
            val activity = composeRule.activity
            factory = SystemWebViewBrowserEngineFactory(activity)
            session = factory.create(
                tabId = "viewport-round-trip",
                profileId = "default",
                isPrivate = false,
                contentKind = BrowserEngineContentKind.RegularTab,
                eventSink = BrowserEngineEventSink {},
            )
            val host = session.createView(activity)
            activity.setContentView(host)
            webView = requireNotNull(host.findWebView())
            session.setDesktopMode(true)
            session.setDesktopMode(false)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    loaded.countDown()
                }
            }
            webView.loadDataWithBaseURL(
                "https://viewport.test/",
                """
                    <meta name='viewport' content='width=device-width,initial-scale=0.75'>
                    <main>Mobile</main>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        try {
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            val result = evaluateViewport(webView)
            assertEquals(0.75, result.getDouble("scale"), 0.01)
        } finally {
            composeRule.runOnIdle {
                session.execute(BrowserEngineCommands.close())
                factory.shutdown()
            }
        }
    }

    private fun loadViewport(
        meta: String?,
        fixedPanel: Boolean = false,
    ): JSONObject {
        val panelStyle = if (fixedPanel) {
            """
                position: absolute;
                width: 800px;
                height: 600px;
                left: 50%;
                margin-left: -421px;
                padding: 15px;
                border: 6px solid;
            """.trimIndent()
        } else {
            "width: 100%; height: 64px;"
        }
        val html = """
            <!doctype html><title>Candy viewport ready</title>
            ${meta?.let { "<meta name='viewport' content='$it'>" }.orEmpty()}
            <style>
              body { margin: 0; }
              main { $panelStyle }
            </style>
            <main>Viewport fixture</main>
        """.trimIndent()
        EdgeToEdgeSiteFixtureServer { html }.use { server ->
            lateinit var webView: WebView
            composeRule.runOnIdle {
                val activity = composeRule.activity
                val browserController = BrowserController(activity)
                controller = browserController
                val host = FrameLayout(activity)
                activity.addContentView(
                    host,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                val engineView = requireNotNull(browserController.attachSelectedBrowserEngineView(host))
                webView = requireNotNull(engineView.findWebView())
                assertTrue(browserController.openUrl(server.fixtureUrl("/viewport")))
            }
            awaitLoaded()
            return evaluateViewport(webView)
        }
    }

    private fun evaluateViewport(webView: WebView): JSONObject {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        composeRule.runOnIdle {
            webView.evaluateJavascript(
                "JSON.stringify({width:document.documentElement.clientWidth," +
                    "deviceWidth:innerWidth*visualViewport.scale,scale:visualViewport.scale," +
                    "left:document.querySelector('main').getBoundingClientRect().left," +
                    "right:document.querySelector('main').getBoundingClientRect().right})",
            ) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
        return JSONObject(JSONObject("{\"result\":${result.get()}}").getString("result"))
    }

    private fun awaitLoaded() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            controller?.selectedTab?.let { it.title == "Candy viewport ready" && !it.isLoading } == true
        }
    }

    private fun preferences() = composeRule.activity.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun View.findWebView(): WebView? = when (this) {
        is WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findWebView() }
        else -> null
    }
}
