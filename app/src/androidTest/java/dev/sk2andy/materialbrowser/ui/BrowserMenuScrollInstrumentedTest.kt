package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMenuScrollInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun firstNativeSwipeAfterMenuDismissScrollsPage() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitBrowserReady(scenario)
            SystemClock.sleep(SPLASH_SETTLE_MS)
            awaitBrowserReady(scenario)
            val testTabId = scenario.readActivity { activity ->
                activity.browserControllerForTesting().createTab("https://example.test/")
            }
            try {
                val webView = loadScrollablePage(scenario)
                val maximum = awaitMaximumScrollY(webView)
                assertTrue("Test page was not scrollable", maximum > webView.height * 2)
                val moreOptions = scenario.readActivity { it.getString(R.string.cd_more_options) }
                val menuTitle = scenario.readActivity { it.getString(R.string.browser_menu_title) }

                repeat(ATTEMPTS) { attempt ->
                    instrumentation.runOnMainSync {
                        webView.flingScroll(0, 0)
                        webView.scrollTo(0, 0)
                    }
                    awaitScrollAtTop(webView)

                    assertTrue("More-options button was not clickable", clickNode(moreOptions))
                    assertTrue("Browser menu did not open", awaitNode(menuTitle, expected = true))

                    val location = IntArray(2)
                    instrumentation.runOnMainSync { webView.getLocationOnScreen(location) }
                    sendTap(
                        x = location[0] + OUTSIDE_TAP_INSET_PX,
                        y = location[1] + webView.height * 0.35f,
                    )
                    assertTrue(
                        "Browser menu did not dismiss",
                        awaitNode(menuTitle, expected = false),
                    )
                    awaitWindowFocus(scenario)
                    SystemClock.sleep(POST_DISMISS_DELAY_MS)

                    sendSwipe(
                        x = location[0] + webView.width / 2f,
                        startY = location[1] + webView.height * 0.55f,
                        endY = location[1] + webView.height * 0.30f,
                    )
                    SystemClock.sleep(SCROLL_SETTLE_MS)

                    assertTrue(
                        "Attempt $attempt lost first native swipe after menu dismissal; " +
                            "scrollY=${webView.scrollY}",
                        webView.scrollY > MIN_SCROLL_DELTA_PX,
                    )
                }
            } finally {
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().closeTab(testTabId)
                }
            }
        }
    }

    private fun loadScrollablePage(scenario: ActivityScenario<MainActivity>): WebView {
        val webView = awaitAttachedWebView(scenario)
        instrumentation.runOnMainSync {
            webView.loadDataWithBaseURL(
                "https://example.test/",
                """
                    <!doctype html>
                    <html>
                      <head>
                        <meta name="viewport"
                            content="width=device-width, initial-scale=1">
                      </head>
                      <body style="min-height:24000px">
                        <div id="probe">menu scroll test</div>
                      </body>
                    </html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
        awaitProbe(webView)
        awaitWindowFocus(scenario)
        return webView
    }

    private fun sendTap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, x, y)
        sendPointer(downTime, downTime + TAP_DURATION_MS, MotionEvent.ACTION_UP, x, y)
    }

    private fun sendSwipe(x: Float, startY: Float, endY: Float) {
        val downTime = SystemClock.uptimeMillis()
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, x, startY)
        repeat(SWIPE_MOVE_COUNT) { index ->
            SystemClock.sleep(SWIPE_STEP_MS)
            val fraction = (index + 1f) / SWIPE_MOVE_COUNT
            sendPointer(
                downTime = downTime,
                eventTime = downTime + (index + 1L) * SWIPE_STEP_MS,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = startY + (endY - startY) * fraction,
            )
        }
        sendPointer(
            downTime = downTime,
            eventTime = downTime + (SWIPE_MOVE_COUNT + 1L) * SWIPE_STEP_MS,
            action = MotionEvent.ACTION_UP,
            x = x,
            y = endY,
        )
    }

    private fun sendPointer(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                instrumentation.sendPointerSync(event)
            } finally {
                event.recycle()
            }
        }
    }

    private fun awaitAttachedWebView(
        scenario: ActivityScenario<MainActivity>,
    ): WebView {
        repeat(ATTACH_ATTEMPTS) {
            val webView = scenario.readActivity { activity ->
                activity.browserControllerForTesting().selectedWebViewForTesting()
            }
            if (
                webView.isAttachedToWindow &&
                webView.isShown &&
                webView.width > 0 &&
                webView.height > 0
            ) {
                return webView
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Browser WebView was not attached")
    }

    private fun awaitWindowFocus(scenario: ActivityScenario<MainActivity>) {
        repeat(WINDOW_FOCUS_ATTEMPTS) {
            if (scenario.readActivity { it.window.decorView.hasWindowFocus() }) return
            SystemClock.sleep(READY_POLL_INTERVAL_MS)
        }
        throw AssertionError("Browser window did not receive focus")
    }

    private fun awaitBrowserReady(scenario: ActivityScenario<MainActivity>) {
        val moreOptions = scenario.readActivity { it.getString(R.string.cd_more_options) }
        repeat(BROWSER_READY_ATTEMPTS) {
            if (
                scenario.readActivity { it.window.decorView.hasWindowFocus() } &&
                nodeExists(moreOptions)
            ) {
                return
            }
            SystemClock.sleep(READY_POLL_INTERVAL_MS)
        }
        throw AssertionError("Browser chrome did not become ready")
    }

    private fun awaitNode(text: String, expected: Boolean): Boolean {
        repeat(NODE_ATTEMPTS) {
            if (nodeExists(text) == expected) return true
            SystemClock.sleep(READY_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun nodeExists(text: String): Boolean {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.rootInActiveWindow?.let(pending::addLast)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.matches(text)) return true
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return false
    }

    private fun clickNode(text: String): Boolean {
        repeat(NODE_ATTEMPTS) {
            val pending = ArrayDeque<AccessibilityNodeInfo>()
            instrumentation.uiAutomation.rootInActiveWindow?.let(pending::addLast)
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                if (node.matches(text)) {
                    var clickable: AccessibilityNodeInfo? = node
                    while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                    if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                        instrumentation.waitForIdleSync()
                        return true
                    }
                }
                repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
            }
            SystemClock.sleep(READY_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun AccessibilityNodeInfo.matches(text: String): Boolean =
        this.text?.toString() == text || contentDescription?.toString() == text

    private fun awaitProbe(webView: WebView) {
        repeat(PROBE_ATTEMPTS) {
            if (evaluate(webView, "Boolean(document.getElementById('probe'))") == "true") return
            SystemClock.sleep(PROBE_INTERVAL_MS)
        }
        throw AssertionError("WebView test page did not finish loading")
    }

    @Suppress("DEPRECATION")
    private fun awaitMaximumScrollY(webView: WebView): Int {
        var previous = 0
        var stableSamples = 0
        repeat(SCROLL_RANGE_ATTEMPTS) {
            SystemClock.sleep(POLL_INTERVAL_MS)
            val current = AtomicReference(0)
            instrumentation.runOnMainSync {
                current.set(
                    (webView.contentHeight * webView.scale - webView.height)
                        .roundToInt()
                        .coerceAtLeast(0),
                )
            }
            stableSamples = if (current.get() == previous) stableSamples + 1 else 0
            if (current.get() > webView.height && stableSamples >= STABLE_SAMPLE_COUNT) {
                return current.get()
            }
            previous = current.get()
        }
        throw AssertionError("WebView scroll range did not settle; maximum=$previous")
    }

    private fun awaitScrollAtTop(webView: WebView) {
        var stableSamples = 0
        repeat(SCROLL_TOP_ATTEMPTS) {
            stableSamples = if (webView.scrollY == 0) stableSamples + 1 else 0
            if (stableSamples >= STABLE_TOP_SAMPLE_COUNT) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("WebView did not return to top; scrollY=${webView.scrollY}")
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
        assertTrue("JavaScript evaluation timed out", evaluated.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun <T : Any> ActivityScenario<MainActivity>.readActivity(
        block: (MainActivity) -> T,
    ): T {
        val value = AtomicReference<T>()
        onActivity { activity -> value.set(block(activity)) }
        return checkNotNull(value.get())
    }

    private companion object {
        const val ATTEMPTS = 3
        const val ATTACH_ATTEMPTS = 400
        const val BROWSER_READY_ATTEMPTS = 600
        const val WINDOW_FOCUS_ATTEMPTS = 250
        const val NODE_ATTEMPTS = 100
        const val PROBE_ATTEMPTS = 100
        const val SCROLL_RANGE_ATTEMPTS = 200
        const val SCROLL_TOP_ATTEMPTS = 100
        const val STABLE_SAMPLE_COUNT = 20
        const val STABLE_TOP_SAMPLE_COUNT = 5
        const val SWIPE_MOVE_COUNT = 5
        const val OUTSIDE_TAP_INSET_PX = 8f
        const val MIN_SCROLL_DELTA_PX = 40
        const val POLL_INTERVAL_MS = 10L
        const val READY_POLL_INTERVAL_MS = 50L
        const val PROBE_INTERVAL_MS = 50L
        const val TAP_DURATION_MS = 40L
        const val SWIPE_STEP_MS = 16L
        const val SPLASH_SETTLE_MS = 1_400L
        const val POST_DISMISS_DELAY_MS = 300L
        const val SCROLL_SETTLE_MS = 80L
    }
}
