package dev.sk2andy.materialbrowser.browser.systemwebview

import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewAnnotationsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun trustedHighlightTapReachesSessionListenerAfterScrollAndDetachStopsIt() {
        withLoadedSession { session, view, url ->
            val clicked = AtomicReference<String?>()
            composeRule.runOnIdle { session.setWebAnnotationClickListener(clicked::set) }
            val annotations = JSONArray().put(JSONObject().put("id", "saved-quote").put("quote", "Saved article quote"))
            assertTrue(apply(session, JSONObject().put("operation", "highlights").put("url", url).put("annotations", annotations)))
            assertTrue(apply(session, JSONObject().put("operation", "scroll-to-annotation").put("url", url)
                .put("id", "saved-quote").put("quote", "Saved article quote")))
            assertEquals("true", evaluate(view, "scrollY > 500"))
            evaluate(view, "document.getElementById('quote').click()")
            assertEquals(null, clicked.get())
            tapQuote(view)
            composeRule.waitUntil(5_000) { clicked.get() == "saved-quote" }
            composeRule.runOnIdle { session.setWebAnnotationClickListener(null) }
            clicked.set(null)
            evaluate(view, "globalThis.defaultClicks = 0; document.getElementById('quote').addEventListener('click', () => defaultClicks++)")
            tapQuote(view)
            composeRule.waitUntil(5_000) { evaluate(view, "defaultClicks") == "1" }
            assertEquals(null, clicked.get())
        }
    }

    private fun tapQuote(view: WebView) {
        val presented = CountDownLatch(1)
        composeRule.runOnUiThread {
            view.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { presented.countDown() }
            })
        }
        assertTrue(presented.await(5, TimeUnit.SECONDS))
        val point = JSONArray(evaluate(view, """
            (() => {
              const range = document.createRange(); range.selectNodeContents(document.getElementById('quote'));
              const rect = range.getClientRects()[0];
              return [rect.left + rect.width / 2, rect.top + rect.height / 2, devicePixelRatio];
            })()
        """.trimIndent()))
        val location = IntArray(2)
        composeRule.runOnUiThread { view.getLocationOnScreen(location) }
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(
            location[0] + (point.getDouble(0) * point.getDouble(2)).toInt(),
            location[1] + (point.getDouble(1) * point.getDouble(2)).toInt(),
        ))
    }

    private fun apply(session: AndroidBrowserEngineSessionPort, payload: JSONObject): Boolean {
        val finished = CountDownLatch(1)
        val result = AtomicReference(false)
        composeRule.runOnIdle { session.executeWebTextOperation(payload.toString()) { result.set(it); finished.countDown() } }
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun evaluate(view: WebView, script: String): String {
        val finished = CountDownLatch(1)
        val result = AtomicReference<String>()
        composeRule.runOnUiThread { view.evaluateJavascript(script) { result.set(it); finished.countDown() } }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun withLoadedSession(action: (AndroidBrowserEngineSessionPort, WebView, String) -> Unit) {
        EdgeToEdgeSiteFixtureServer {
            """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body><p id="quote" style="margin-top:1800px;margin-bottom:1200px">Saved article quote</p></body></html>"""
        }.use { server ->
            val url = server.fixtureUrl("/annotations")
            val loaded = CountDownLatch(1)
            lateinit var factory: SystemWebViewBrowserEngineFactory
            lateinit var session: AndroidBrowserEngineSessionPort
            lateinit var host: FrameLayout
            lateinit var view: WebView
            composeRule.runOnIdle {
                factory = SystemWebViewBrowserEngineFactory(composeRule.activity)
                session = factory.create(
                    tabId = "annotations", profileId = "annotations", isPrivate = false,
                    contentKind = BrowserEngineContentKind.RegularTab,
                    eventSink = BrowserEngineEventSink { event ->
                        if (event.type == BrowserEngineEventType.NavigationCommitted && event.address == url) loaded.countDown()
                    },
                )
                host = FrameLayout(composeRule.activity)
                composeRule.activity.addContentView(host, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                ))
                view = session.createView(composeRule.activity) as WebView
                host.addView(view)
                session.execute(BrowserEngineCommands.load(url))
            }
            try {
                assertTrue(loaded.await(20, TimeUnit.SECONDS))
                action(session, view, url)
            } finally {
                composeRule.runOnIdle {
                    host.removeAllViews()
                    session.execute(BrowserEngineCommands.close())
                    factory.shutdown()
                }
            }
        }
    }
}
