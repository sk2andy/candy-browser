package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserScrollInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun tabPreviewCaptureRunsOutsideTheScrollPath() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val testTabId = scenario.readActivity { activity ->
                activity.browserControllerForTesting().createTab("https://example.test/")
            }
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
                          <body style="min-height:24000px;
                              background:repeating-linear-gradient(
                                  #f44336 0 240px, #2196f3 240px 480px)">
                            <div id="probe">preview capture test</div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitProbe(webView)
            awaitMaximumScrollY(webView)

            val initialCapture = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().prepareTabOverview(initialCapture::countDown)
            }
            assertTrue("Initial preview capture timed out", initialCapture.await(5, TimeUnit.SECONDS))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().previews.remove(testTabId)
                webView.scrollTo(0, webView.height * 2)
            }
            SystemClock.sleep(350)
            assertTrue(
                "Scrolling unexpectedly scheduled a preview capture",
                scenario.readActivity { activity ->
                    activity.browserControllerForTesting().previews[testTabId] == null
                },
            )

            val overviewCapture = CountDownLatch(1)
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().prepareTabOverview(overviewCapture::countDown)
            }
            assertTrue("Overview preview capture timed out", overviewCapture.await(5, TimeUnit.SECONDS))
            assertTrue(
                "Opening tab overview did not refresh the preview",
                scenario.readActivity { activity ->
                    activity.browserControllerForTesting().previews[testTabId] != null
                },
            )

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().closeTab(testTabId)
            }
        }
    }

    @Test
    fun fullBrowserWindowKeepsNativeOppositeSwipes() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val testTabId = scenario.readActivity { activity ->
                activity.browserControllerForTesting().createTab("https://example.test/")
            }
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
                            <div id="probe">full browser scroll test</div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitProbe(webView)
            val maximum = awaitMaximumScrollY(webView)
            assertTrue("Test page was not scrollable", maximum > webView.height * 2)

            repeat(30) { attempt ->
                val startScroll = maximum / 2
                instrumentation.runOnMainSync {
                    webView.flingScroll(0, 0)
                    webView.scrollTo(0, startScroll)
                }
                awaitScrollNear(webView, startScroll)

                val location = IntArray(2)
                instrumentation.runOnMainSync { webView.getLocationOnScreen(location) }
                val x = location[0] + webView.width / 2f
                val firstStartY = location[1] + webView.height * 0.65f
                val firstTravel = webView.height * -0.30f
                sendGesture(x, firstStartY, firstTravel)
                SystemClock.sleep(40)
                val beforeReverse = webView.scrollY
                assertTrue(
                    "Attempt $attempt lost the first native swipe: " +
                        "start=$startScroll beforeReverse=$beforeReverse",
                    beforeReverse > startScroll + 40,
                )

                val reverseStartY = location[1] + webView.height * 0.25f
                val reverseTravel = webView.height * 0.36f
                val reverseSamples = sendGesture(
                    x = x,
                    startY = reverseStartY,
                    travelY = reverseTravel,
                    sampleScrollY = { webView.scrollY },
                )
                val afterReverseDrag = reverseSamples.last()
                SystemClock.sleep(120)
                val afterReverseFling = webView.scrollY

                assertTrue(
                    "Attempt $attempt did not react to reverse MOVE events: " +
                        "before=$beforeReverse samples=$reverseSamples",
                    afterReverseDrag < reverseSamples.max() - 40,
                )
                assertTrue(
                    "Attempt $attempt lost native reverse momentum: " +
                        "drag=$afterReverseDrag fling=$afterReverseFling",
                    afterReverseFling < afterReverseDrag - 20,
                )
            }

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().closeTab(testTabId)
            }
        }
    }

    @Test
    fun comparesMinimalMomentumStopStrategies() {
        GestureOnboardingStore(instrumentation.targetContext).markCompleted()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val testTabId = scenario.readActivity { activity ->
                activity.browserControllerForTesting().createTab("https://example.test/")
            }
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
                            <div id="probe">momentum strategy test</div>
                          </body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitProbe(webView)
            val maximum = awaitMaximumScrollY(webView)

            MomentumStopMode.entries.forEach { mode ->
                val listener = MomentumStopListener(mode)
                instrumentation.runOnMainSync {
                    webView.setOnTouchListener(listener.takeUnless {
                        mode == MomentumStopMode.Native
                    })
                }
                val result = compareSparseReverseGestures(webView, maximum)
                Log.i(TEST_LOG_TAG, "mode=$mode $result zeroCalls=${listener.zeroCalls}")

                assertTrue(
                    "Initial swipe stalled for $mode: $result",
                    result.firstSwipeStarted == SPARSE_ATTEMPTS,
                )
                assertTrue(
                    "Reverse gesture was lost for $mode: $result",
                    result.reverseEventuallyFollowed >= SPARSE_ATTEMPTS - 1,
                )
                assertTrue(
                    "Held pointer kept stale momentum for $mode: $result",
                    result.holdStayedInReverseDirection >= SPARSE_ATTEMPTS - 1,
                )
            }

            instrumentation.runOnMainSync { webView.setOnTouchListener(null) }
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().closeTab(testTabId)
            }
        }
    }

    private fun compareSparseReverseGestures(
        webView: WebView,
        maximum: Int,
    ): SparseComparison {
        var firstSwipeStarted = 0
        var firstReverseMoveFollowed = 0
        var reverseEventuallyFollowed = 0
        var holdStayedInReverseDirection = 0

        repeat(SPARSE_ATTEMPTS) {
            val startScroll = maximum / 2
            instrumentation.runOnMainSync {
                webView.flingScroll(0, 0)
                webView.scrollTo(0, startScroll)
            }
            awaitScrollNear(webView, startScroll)

            val location = IntArray(2)
            instrumentation.runOnMainSync { webView.getLocationOnScreen(location) }
            val x = location[0] + webView.width / 2f
            sendGesture(
                x = x,
                startY = location[1] + webView.height * 0.65f,
                travelY = webView.height * -0.30f,
            )
            SystemClock.sleep(40)
            if (webView.scrollY > startScroll + 40) firstSwipeStarted++

            val reverseStartY = location[1] + webView.height * 0.25f
            val downTime = SystemClock.uptimeMillis()
            sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, x, reverseStartY)
            val afterDown = webView.scrollY
            SystemClock.sleep(12)
            val reverseY = reverseStartY + webView.height * 0.36f
            sendPointer(
                downTime = downTime,
                eventTime = downTime + 12L,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = reverseY,
            )
            val afterFirstMove = webView.scrollY
            if (afterFirstMove < afterDown - 5) firstReverseMoveFollowed++

            SystemClock.sleep(180)
            val afterHold = webView.scrollY
            if (afterHold <= afterFirstMove + 20) holdStayedInReverseDirection++

            sendPointer(
                downTime = downTime,
                eventTime = downTime + 204L,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = reverseY + webView.height * 0.08f,
            )
            val afterSecondMove = webView.scrollY
            if (afterSecondMove < maxOf(afterFirstMove, afterHold) - 20) {
                reverseEventuallyFollowed++
            }
            sendPointer(
                downTime = downTime,
                eventTime = downTime + 216L,
                action = MotionEvent.ACTION_UP,
                x = x,
                y = reverseY + webView.height * 0.08f,
            )
        }
        return SparseComparison(
            firstSwipeStarted = firstSwipeStarted,
            firstReverseMoveFollowed = firstReverseMoveFollowed,
            reverseEventuallyFollowed = reverseEventuallyFollowed,
            holdStayedInReverseDirection = holdStayedInReverseDirection,
        )
    }

    private fun sendGesture(
        x: Float,
        startY: Float,
        travelY: Float,
        sampleScrollY: (() -> Int)? = null,
    ): List<Int> {
        val downTime = SystemClock.uptimeMillis()
        sendPointer(downTime, downTime, MotionEvent.ACTION_DOWN, x, startY)
        val samples = mutableListOf<Int>()
        repeat(5) { index ->
            SystemClock.sleep(12)
            val eventTime = downTime + (index + 1) * 12L
            sendPointer(
                downTime = downTime,
                eventTime = eventTime,
                action = MotionEvent.ACTION_MOVE,
                x = x,
                y = startY + travelY * (index + 1) / 5f,
            )
            sampleScrollY?.let { samples += it() }
        }
        sendPointer(
            downTime = downTime,
            eventTime = downTime + 72L,
            action = MotionEvent.ACTION_UP,
            x = x,
            y = startY + travelY,
        )
        return samples
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
        repeat(200) {
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
            SystemClock.sleep(10)
        }
        throw AssertionError("Browser WebView was not attached")
    }

    private fun awaitProbe(webView: WebView) {
        repeat(100) {
            if (evaluate(webView, "Boolean(document.getElementById('probe'))") == "true") return
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView test page did not finish loading")
    }

    @Suppress("DEPRECATION")
    private fun awaitMaximumScrollY(webView: WebView): Int {
        var previous = 0
        var stableSamples = 0
        repeat(200) {
            SystemClock.sleep(10)
            var current = 0
            instrumentation.runOnMainSync {
                current = (webView.contentHeight * webView.scale - webView.height)
                    .roundToInt()
                    .coerceAtLeast(0)
            }
            stableSamples = if (current == previous) stableSamples + 1 else 0
            if (current > webView.height && stableSamples >= 20) return current
            previous = current
        }
        throw AssertionError("WebView scroll range did not settle, last maximum was $previous")
    }

    private fun awaitScrollNear(webView: WebView, expected: Int) {
        repeat(100) {
            if ((webView.scrollY - expected).absoluteValue < 20) return
            SystemClock.sleep(10)
        }
        throw AssertionError("WebView scroll was ${webView.scrollY}, expected $expected")
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

    private fun <T : Any> ActivityScenario<MainActivity>.readActivity(
        block: (MainActivity) -> T,
    ): T {
        val value = AtomicReference<T>()
        onActivity { activity -> value.set(block(activity)) }
        return checkNotNull(value.get())
    }

    private enum class MomentumStopMode {
        Native,
        Down,
        FirstVerticalMove,
    }

    private class MomentumStopListener(
        private val mode: MomentumStopMode,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var stopped = false
        var zeroCalls = 0
            private set

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val webView = view as WebView
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    stopped = false
                    if (mode == MomentumStopMode.Down) stop(webView)
                }

                MotionEvent.ACTION_MOVE -> if (
                    mode == MomentumStopMode.FirstVerticalMove &&
                    !stopped &&
                    (event.y - downY).absoluteValue > TOUCH_SLOP_PX &&
                    (event.y - downY).absoluteValue > (event.x - downX).absoluteValue
                ) {
                    stop(webView)
                }
            }
            return false
        }

        private fun stop(webView: WebView) {
            stopped = true
            zeroCalls++
            webView.flingScroll(0, 0)
        }
    }

    private data class SparseComparison(
        val firstSwipeStarted: Int,
        val firstReverseMoveFollowed: Int,
        val reverseEventuallyFollowed: Int,
        val holdStayedInReverseDirection: Int,
    )

    private companion object {
        const val TEST_LOG_TAG = "CandyScrollStrategy"
        const val SPARSE_ATTEMPTS = 20
        const val TOUCH_SLOP_PX = 8f
    }
}
