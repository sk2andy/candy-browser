package dev.sk2andy.materialbrowser

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenWebContentHostInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun fullscreenUsesSensorRotationAndRestoresWindowState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val customView = View(activity)
                val chromeClient = requireNotNull(
                    activity.browserControllerForTesting().selectedWebViewForTesting().webChromeClient,
                )

                chromeClient.onShowCustomView(customView, WebChromeClient.CustomViewCallback {})

                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR,
                    activity.requestedOrientation,
                )
                assertTrue(customView.isAttachedToWindow)

                chromeClient.onHideCustomView()

                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.requestedOrientation,
                )
                assertNull(customView.parent)
            }
        }
    }

    @Test
    fun systemBackDismissesFullscreenAndNotifiesWebContent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val callbackCount = AtomicInteger()
                val chromeClient = requireNotNull(
                    activity.browserControllerForTesting().selectedWebViewForTesting().webChromeClient,
                )
                chromeClient.onShowCustomView(
                    View(activity),
                    WebChromeClient.CustomViewCallback(callbackCount::incrementAndGet),
                )

                activity.onBackPressedDispatcher.onBackPressed()

                assertEquals(1, callbackCount.get())
                assertEquals(
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    activity.requestedOrientation,
                )
                chromeClient.onHideCustomView()
            }
        }
    }

    @Test
    fun mainActivityDeclaresRotationConfigurationHandling() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val activityInfo = activity.packageManager.getActivityInfo(
                    ComponentName(activity, MainActivity::class.java),
                    0,
                )
                val handledChanges = activityInfo.configChanges

                assertTrue(handledChanges and ActivityInfo.CONFIG_ORIENTATION != 0)
                assertTrue(handledChanges and ActivityInfo.CONFIG_SCREEN_LAYOUT != 0)
                assertTrue(handledChanges and ActivityInfo.CONFIG_SCREEN_SIZE != 0)
            }
        }
    }

    @Test
    fun chromiumFullscreenRequestSupportsExitAndReentry() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(SPLASH_SETTLE_MILLIS)
            assertTrue("Browser window did not receive focus", awaitWindowFocus(scenario))
            val webView = AtomicReference<WebView>()
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().selectedWebViewForTesting().also {
                    webView.set(it)
                    it.loadDataWithBaseURL(
                        "https://fullscreen.test/",
                        """
                            <!doctype html>
                            <html>
                              <head>
                                <meta name="viewport"
                                    content="width=device-width, initial-scale=1">
                              </head>
                              <body style="margin:0">
                                <button id="enter" style="position:fixed;inset:0;width:100%;height:100%"
                                    onclick="document.documentElement.requestFullscreen()">
                                  Enter fullscreen
                                </button>
                              </body>
                            </html>
                        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            }
            val target = webView.get()
            assertTrue("Fullscreen test page did not load", awaitPageReady(target))
            assertTrue("Fullscreen test page was not laid out", awaitWebViewLayout(target))

            sendTap(target)
            assertTrue(
                "Chromium fullscreen request was not presented",
                awaitOrientation(scenario, ActivityInfo.SCREEN_ORIENTATION_SENSOR),
            )

            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            assertTrue(
                "Exiting Chromium fullscreen did not restore orientation",
                awaitOrientation(scenario, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
            )
            assertTrue("Chromium did not finish exiting fullscreen", awaitFullscreenExit(target))

            sendTap(target)
            assertTrue(
                "Chromium fullscreen could not be entered a second time",
                awaitOrientation(scenario, ActivityInfo.SCREEN_ORIENTATION_SENSOR),
            )
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        }
    }

    private fun awaitPageReady(webView: WebView): Boolean {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (evaluateJavascript(webView, "document.readyState") == "\"complete\"") return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitWebViewLayout(webView: WebView): Boolean {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val ready = AtomicReference(false)
            instrumentation.runOnMainSync {
                ready.set(
                    webView.isAttachedToWindow &&
                        webView.isShown &&
                        webView.hasWindowFocus() &&
                        webView.width > 0 &&
                        webView.height > 0,
                )
            }
            if (ready.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitWindowFocus(scenario: ActivityScenario<MainActivity>): Boolean {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val hasFocus = AtomicReference(false)
            scenario.onActivity { hasFocus.set(it.window.decorView.hasWindowFocus()) }
            if (hasFocus.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitFullscreenExit(webView: WebView): Boolean {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            if (
                evaluateJavascript(webView, "document.fullscreenElement === null") == "true"
            ) {
                return true
            }
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun evaluateJavascript(webView: WebView, script: String): String? {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(script) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        return if (completed.await(JAVASCRIPT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            result.get()
        } else {
            null
        }
    }

    private fun awaitOrientation(
        scenario: ActivityScenario<MainActivity>,
        expected: Int,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val orientation = AtomicInteger()
            scenario.onActivity { orientation.set(it.requestedOrientation) }
            if (orientation.get() == expected) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun sendTap(webView: WebView) {
        val x = webView.width / 2f
        val y = webView.height / 2f
        val downTime = SystemClock.uptimeMillis()
        val handled = AtomicReference(false)
        instrumentation.runOnMainSync {
            val downHandled = dispatchPointer(
                webView = webView,
                downTime = downTime,
                eventTime = downTime,
                action = MotionEvent.ACTION_DOWN,
                x = x,
                y = y,
            )
            val upHandled = dispatchPointer(
                webView = webView,
                downTime = downTime,
                eventTime = downTime + TAP_DURATION_MILLIS,
                action = MotionEvent.ACTION_UP,
                x = x,
                y = y,
            )
            handled.set(downHandled && upHandled)
        }
        assertTrue("Browser WebView did not handle fullscreen tap", handled.get())
    }

    private fun dispatchPointer(
        webView: WebView,
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ): Boolean = MotionEvent.obtain(downTime, eventTime, action, x, y, 0).let { event ->
        try {
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            webView.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private companion object {
        const val JAVASCRIPT_TIMEOUT_MILLIS = 1_000L
        const val POLL_INTERVAL_MILLIS = 50L
        const val SPLASH_SETTLE_MILLIS = 1_400L
        const val TAP_DURATION_MILLIS = 24L
        const val TIMEOUT_MILLIS = 5_000L
    }
}
