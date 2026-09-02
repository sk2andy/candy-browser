package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class BrowserControllerFindInPageInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
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
    fun findsNavigatesClearsAndClosesOnTabChange() {
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val controller = BrowserController(activity).also { this.controller = it }
            val webView = controller.selectedWebViewForTesting()
            (webView.parent as? ViewGroup)?.removeView(webView)
            activity.setContentView(
                FrameLayout(activity).apply {
                    addView(
                        webView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    addView(
                        EditText(activity).apply {
                            showSoftInputOnFocus = false
                            requestFocus()
                        },
                        FrameLayout.LayoutParams(1, 1),
                    )
                },
            )
            webView.loadDataWithBaseURL(
                "https://find.test/",
                """
                    <html>
                    <body style="margin:0">
                      <p>Candy alpha</p>
                      <div style="height:1800px"></div>
                      <p>Candy beta</p>
                      <div style="height:1800px"></div>
                      <p>Candy gamma</p>
                    </body>
                    </html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitCondition { requireNotNull(controller).selectedTab.isLoading.not() }
        val documentReady = AtomicBoolean(false)
        activityRule.scenario.onActivity {
            requireNotNull(controller).selectedWebViewForTesting().evaluateJavascript(
                "document.body && document.body.innerText",
            ) { body ->
                documentReady.set(body.contains("Candy alpha"))
            }
        }
        awaitCondition(condition = documentReady::get)

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            assertTrue(controller.openFindInPage())
            controller.updateFindInPageQuery("Candy")
        }
        awaitCondition {
            requireNotNull(controller).findInPageState?.let { state ->
                state.isDoneCounting && state.matchCount == 3
            } == true
        }
        activityRule.scenario.onActivity {
            val state = requireNotNull(requireNotNull(controller).findInPageState)
            assertEquals(3, state.matchCount)
            assertEquals(0, state.activeMatchOrdinal)
            assertTrue(requireNotNull(controller).findNextInPage(forward = true))
        }
        awaitCondition {
            requireNotNull(controller).findInPageState?.activeMatchOrdinal == 1
        }
        awaitCondition {
            selectedWebViewScrollY() > 0
        }

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.updateFindInPageQuery("")
            assertEquals(0, requireNotNull(controller.findInPageState).matchCount)
            assertFalse(controller.findNextInPage(forward = true))
            controller.updateFindInPageQuery("Candy")
        }
        awaitCondition { requireNotNull(controller).findInPageState?.matchCount == 3 }
        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            controller.createTab()
            assertNull(controller.findInPageState)
        }
    }

    private fun awaitCondition(
        timeoutMillis: Long = 10_000L,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(40L)
        }
        assertTrue("Condition timed out", condition())
    }

    private fun selectedWebViewScrollY(): Int {
        var scrollY = 0
        activityRule.scenario.onActivity {
            scrollY = requireNotNull(controller).selectedWebViewForTesting().scrollY
        }
        return scrollY
    }
}
