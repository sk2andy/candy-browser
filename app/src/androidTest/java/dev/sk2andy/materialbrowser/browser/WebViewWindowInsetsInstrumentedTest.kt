package dev.sk2andy.materialbrowser.browser

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebViewWindowInsetsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun coverPageOnlyDrawsEdgeToEdgeAfterExplicitOptIn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(false)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitSelectedWebView(scenario)
            val expectedTopCssPixels = AtomicReference<Float>()
            val expectedTopPixels = AtomicInteger()
            val density = AtomicReference<Float>()
            scenario.onActivity { activity ->
                val topPixels = ViewCompat.getRootWindowInsets(webView)
                    ?.getInsets(
                        WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout(),
                    )
                    ?.top
                    ?: 0
                expectedTopPixels.set(topPixels)
                expectedTopCssPixels.set(topPixels / activity.resources.displayMetrics.density)
                density.set(activity.resources.displayMetrics.density)
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                          </head>
                          <body>
                            <div id="probe" style="padding-top:env(safe-area-inset-top)"></div>
                            <button id="open-app" style="position:fixed;top:12px;right:12px">
                              App öffnen
                            </button>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitWebViewTop(webView, expectedTopPixels.get())
            val topInset = evaluate(
                webView,
                "parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop)",
            ).toFloat()
            val controlTopCssPixels = evaluate(
                webView,
                "document.getElementById('open-app').getBoundingClientRect().top",
            ).toFloat()
            val pageWasMutated = evaluate(
                webView,
                "Boolean(document.getElementById('candy-browser-status-inset-style') || " +
                    "document.body.hasAttribute('data-candy-browser-status-inset'))",
            )

            assertTrue(expectedTopCssPixels.get() > 0f)
            assertEquals(0f, topInset, 0.1f)
            assertTrue(
                expectedTopPixels.get() +
                    (controlTopCssPixels * density.get()).roundToInt() >= expectedTopPixels.get(),
            )
            assertEquals("false", pageWasMutated)

            scenario.onActivity { activity ->
                activity.browserControllerForTesting()
                    .updateWebContentEdgeToEdgeEnabled(true)
            }
            awaitWebViewTop(webView, 0)
            assertEquals(0, previewTopInset(scenario))
            assertEquals(
                expectedTopCssPixels.get(),
                evaluate(
                    webView,
                    "parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop)",
                ).toFloat(),
                0.5f,
            )

            scenario.onActivity { activity ->
                activity.browserControllerForTesting()
                    .updateWebContentEdgeToEdgeEnabled(true)
            }
            awaitWebViewTop(webView, 0)

            val coverTabId = AtomicReference<String>()
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    coverTabId.set(selectedTabId)
                    createTab(isIncognito = false)
                    submitAddress("https://foreground.test/")
                }
            }
            awaitSelectedWebView(scenario)
            assertFalse(webView.isAttachedToWindow)

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.updateWebContentEdgeToEdgeEnabled(false)
                assertEquals(
                    expectedTopPixels.get(),
                    controller.previewTopInsetPx(coverTabId.get()),
                )
                controller.updateWebContentEdgeToEdgeEnabled(true)
                assertEquals(0, controller.previewTopInsetPx(coverTabId.get()))
            }
        }
    }

    @Test
    fun pageWithoutCoverStaysBelowSystemBarsAndReceivesZeroCssSafeArea() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().run {
                    updateWebContentEdgeToEdgeEnabled(true)
                    submitAddress("https://example.test/")
                }
            }
            val webView = awaitWebView(scenario)
            val expectedTopPixels = AtomicInteger()
            scenario.onActivity {
                expectedTopPixels.set(
                    ViewCompat.getRootWindowInsets(webView)
                        ?.getInsets(
                            WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout(),
                        )
                        ?.top
                        ?: 0,
                )
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                          <body style="min-height:4000px">
                            <div id="sticky" style="position:fixed;top:0">Sticky action</div>
                            <div id="probe" style="padding-top:env(safe-area-inset-top)"></div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }

            awaitProbe(webView)
            awaitWebViewTop(webView, expectedTopPixels.get())
            val topInset = evaluate(
                webView,
                "parseFloat(getComputedStyle(document.getElementById('probe')).paddingTop)",
            ).toFloat()

            assertTrue(expectedTopPixels.get() > 0)
            assertEquals(0f, topInset, 0.1f)
            awaitWebViewBottomAtParentBottom(webView)
            assertEquals(expectedTopPixels.get(), previewTopInset(scenario))

            evaluate(webView, "window.scrollTo(0, 1000)")
            awaitWebViewTop(webView, expectedTopPixels.get())
            assertEquals(expectedTopPixels.get(), previewTopInset(scenario))
            assertEquals(
                0f,
                evaluate(webView, "document.getElementById('sticky').getBoundingClientRect().top")
                    .toFloat(),
                0.1f,
            )

            evaluate(webView, "window.scrollTo(0, 0)")
            awaitWebViewTop(webView, expectedTopPixels.get())
            assertEquals(expectedTopPixels.get(), previewTopInset(scenario))

            evaluate(
                webView,
                "document.querySelector('meta[name=viewport]').content = 'viewport-fit=cover'",
            )
            awaitWebViewTop(webView, 0)

            evaluate(
                webView,
                "document.querySelector('meta[name=viewport]').content = " +
                    "'viewport-fit=cover, viewport-fit=contain'",
            )
            awaitWebViewTop(webView, expectedTopPixels.get())

            evaluate(
                webView,
                "document.querySelector('meta[name=viewport]').content = " +
                    "'viewport-fit=cover; width=device-width'",
            )
            awaitWebViewTop(webView, expectedTopPixels.get())

            evaluate(
                webView,
                "document.querySelector('meta[name=viewport]').content = 'viewport-fit=cover'",
            )
            awaitWebViewTop(webView, 0)

            evaluate(webView, "document.querySelector('meta[name=viewport]').remove()")
            awaitWebViewTop(webView, expectedTopPixels.get())
        }
    }

    @Test
    fun browserChromeOwnedImeFramesAreDeduplicatedAndRestored() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().submitAddress("https://example.test/")
            }
            val webView = awaitWebView(scenario)
            val dispatchCount = AtomicInteger()
            val lastImeBottom = AtomicInteger(-1)

            scenario.onActivity { activity ->
                ViewCompat.setOnApplyWindowInsetsListener(webView) { _, insets ->
                    dispatchCount.incrementAndGet()
                    lastImeBottom.set(
                        insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
                    )
                    insets
                }
                val controller = activity.browserControllerForTesting()
                controller.onWindowInsetsChanged(imeInsets(bottom = 0, visible = false))
                controller.setBrowserChromeOwnsIme(true)
                val countAfterOwnership = dispatchCount.get()

                controller.onWindowInsetsChanged(imeInsets(bottom = 400, visible = true))
                controller.onWindowInsetsChanged(imeInsets(bottom = 700, visible = true))

                assertEquals(countAfterOwnership, dispatchCount.get())
                assertEquals(0, lastImeBottom.get())

                controller.setBrowserChromeOwnsIme(false)
                assertEquals(countAfterOwnership + 1, dispatchCount.get())
                assertEquals(700, lastImeBottom.get())

                controller.onWindowInsetsChanged(imeInsets(bottom = 0, visible = false))
                assertEquals(countAfterOwnership + 2, dispatchCount.get())
                assertEquals(0, lastImeBottom.get())
            }
        }
    }

    private fun imeInsets(bottom: Int, visible: Boolean): WindowInsetsCompat =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
            .setVisible(WindowInsetsCompat.Type.ime(), visible)
            .build()

    private fun awaitWebView(scenario: ActivityScenario<MainActivity>): WebView {
        val result = AtomicReference<WebView>()
        repeat(200) {
            scenario.onActivity { activity ->
                result.compareAndSet(null, findWebView(activity.window.decorView))
            }
            result.get()?.let { return it }
            SystemClock.sleep(50)
        }
        assertNotNull("WebView was not attached", result.get())
        return result.get()
    }

    private fun awaitSelectedWebView(scenario: ActivityScenario<MainActivity>): WebView {
        val result = AtomicReference<WebView>()
        repeat(200) {
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().selectedWebViewForTesting()
                    .takeIf { webView ->
                        webView.isAttachedToWindow && webView.width > 0 && webView.height > 0
                    }
                    ?.let { webView -> result.compareAndSet(null, webView) }
            }
            result.get()?.let { return it }
            SystemClock.sleep(50)
        }
        return checkNotNull(result.get()) { "Selected WebView was not attached" }
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun awaitProbe(webView: WebView) {
        repeat(100) {
            if (evaluate(webView, "Boolean(document.getElementById('probe'))") == "true") return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView test page did not finish loading")
    }

    private fun previewTopInset(scenario: ActivityScenario<MainActivity>): Int {
        val result = AtomicInteger()
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            result.set(controller.previewTopInsetPx(controller.selectedTabId))
        }
        return result.get()
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

    private fun awaitWebViewBottomAtParentBottom(webView: WebView) {
        val webViewLocation = IntArray(2)
        val parentLocation = IntArray(2)
        repeat(100) {
            var webViewBottom = 0
            var parentBottom = 0
            instrumentation.runOnMainSync {
                val parent = webView.parent as View
                webView.getLocationInWindow(webViewLocation)
                parent.getLocationInWindow(parentLocation)
                webViewBottom = webViewLocation[1] + webView.height
                parentBottom = parentLocation[1] + parent.height
            }
            if (webViewBottom == parentBottom) return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView did not reach its parent's bottom edge")
    }

    private fun evaluate(webView: WebView, script: String): String {
        val result = AtomicReference<String>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) { value ->
                result.set(value)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(10, TimeUnit.SECONDS))
        return result.get()
    }
}
