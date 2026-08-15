package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusBarFrostedGlassInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun overlayKeepsEdgeToEdgeWebContentScrollableAndNonInteractive() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            scenario.onActivity {
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport"
                                content="width=device-width, initial-scale=1, viewport-fit=cover">
                          </head>
                          <body style="min-height:4000px;background:linear-gradient(#f00,#00f)">
                            <div id="probe">frosted safe area</div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitProbe(webView)
            awaitWebViewTop(webView, expectedTop = 0)

            val overlay = awaitOverlay(scenario)
            val statusBarHeight = ViewCompat.getRootWindowInsets(webView)
                ?.getInsets(WindowInsetsCompat.Type.statusBars())
                ?.top
                ?: 0
            val overlayLocation = IntArray(2)
            instrumentation.runOnMainSync { overlay.getLocationInWindow(overlayLocation) }

            assertTrue(statusBarHeight > 0)
            assertEquals(0, overlayLocation[1])
            assertTrue(overlay.height > statusBarHeight)
            assertFalse(overlay.isClickable)
            assertFalse(overlay.isFocusable)
            assertEquals(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                overlay.importantForAccessibility,
            )

            instrumentation.runOnMainSync { webView.scrollTo(0, 900) }
            awaitScrollY(webView, minimum = 900)
            awaitWebViewTop(webView, expectedTop = 0)
            assertSame(overlay, awaitOverlay(scenario))
        }
    }

    private fun awaitWebView(scenario: ActivityScenario<MainActivity>): WebView {
        repeat(200) {
            var webView: WebView? = null
            scenario.onActivity { activity ->
                webView = activity.browserControllerForTesting().selectedWebViewForTesting()
                    .takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
            }
            webView?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView was not attached")
    }

    private fun awaitOverlay(scenario: ActivityScenario<MainActivity>): View {
        repeat(100) {
            var overlay: View? = null
            scenario.onActivity { activity ->
                overlay = findTaggedView(
                    activity.window.decorView,
                    StatusBarFrostedGlassTestTags.Overlay,
                )?.takeIf { it.isAttachedToWindow && it.height > 0 }
            }
            overlay?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Status-bar frosted glass was not attached")
    }

    private fun findTaggedView(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findTaggedView(view.getChildAt(index), tag)?.let { return it }
        }
        return null
    }

    private fun awaitProbe(webView: WebView) {
        repeat(100) {
            var found = false
            val result = java.util.concurrent.CountDownLatch(1)
            instrumentation.runOnMainSync {
                webView.evaluateJavascript("Boolean(document.getElementById('probe'))") { value ->
                    found = value == "true"
                    result.countDown()
                }
            }
            assertTrue(result.await(10, java.util.concurrent.TimeUnit.SECONDS))
            if (found) return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView test page did not finish loading")
    }

    private fun awaitWebViewTop(webView: WebView, expectedTop: Int) {
        val location = IntArray(2)
        repeat(100) {
            instrumentation.runOnMainSync { webView.getLocationInWindow(location) }
            if (location[1] == expectedTop) return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView top was ${location[1]}, expected $expectedTop")
    }

    private fun awaitScrollY(webView: WebView, minimum: Int) {
        repeat(100) {
            if (webView.scrollY >= minimum) return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView did not scroll to $minimum")
    }
}
