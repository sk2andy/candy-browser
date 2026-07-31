package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.absoluteValue
import kotlin.math.sign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PagePullRefreshTouchListenerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun observesWebViewGestureWithoutConsumingAndRechecksEnabledState() {
        instrumentation.runOnMainSync {
            val webView = RecordingWebView()
            var enabled = true
            var refreshCount = 0
            val progress = mutableListOf<Float>()
            val listener = PagePullRefreshTouchListener(
                maxStartScroll = 96f,
                triggerDistance = 72f,
                touchSlop = 8f,
                isEnabled = { enabled },
                onProgress = progress::add,
                onRefresh = { refreshCount++ },
            )

            assertFalse(listener.send(webView, MotionEvent.ACTION_DOWN, x = 100f, y = 100f))
            assertEquals(1, webView.flingCancelCount)
            assertFalse(listener.send(webView, MotionEvent.ACTION_MOVE, x = 100f, y = 180f))
            assertTrue(progress.last() >= 1f)
            enabled = false
            assertFalse(listener.send(webView, MotionEvent.ACTION_UP, x = 100f, y = 180f))
            assertEquals(0, refreshCount)

            enabled = true
            listener.send(webView, MotionEvent.ACTION_DOWN, x = 100f, y = 100f)
            listener.send(webView, MotionEvent.ACTION_MOVE, x = 100f, y = 180f)
            listener.send(webView, MotionEvent.ACTION_UP, x = 100f, y = 180f)
            assertEquals(1, refreshCount)

            listener.send(webView, MotionEvent.ACTION_DOWN, x = 100f, y = 100f)
            listener.send(webView, MotionEvent.ACTION_MOVE, x = 180f, y = 105f)
            listener.send(webView, MotionEvent.ACTION_MOVE, x = 100f, y = 220f)
            listener.send(webView, MotionEvent.ACTION_UP, x = 100f, y = 220f)
            assertEquals(1, refreshCount)
            assertEquals(3, webView.flingCancelCount)

            webView.destroy()
        }
    }

    @Test
    fun heldPointerCanReverseAnActiveWebViewFling() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val webView = awaitWebView(scenario)
            instrumentation.runOnMainSync {
                webView.stopLoading()
                webView.loadDataWithBaseURL(
                    "https://example.test/",
                    """
                        <!doctype html>
                        <html>
                          <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                          <body style="min-height:24000px"><div id="probe">scroll test</div></body>
                        </html>
                    """.trimIndent(),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
            awaitProbe(webView)
            instrumentation.runOnMainSync { webView.scrollTo(0, 4_000) }
            awaitScrollNear(webView, 4_000)

            val beforeFling = webView.scrollY
            instrumentation.runOnMainSync { webView.flingScroll(0, 12_000) }
            SystemClock.sleep(120)
            val beforeDown = webView.scrollY
            val flingDelta = beforeDown - beforeFling
            assertTrue("WebView did not start a fling", flingDelta.absoluteValue > 20)

            val x = webView.width / 2f
            val y = webView.height / 2f
            val downTime = SystemClock.uptimeMillis()
            dispatch(webView, downTime, MotionEvent.ACTION_DOWN, x, y)
            SystemClock.sleep(80)
            val stoppedAt = webView.scrollY
            val fingerDirection = flingDelta.sign
            repeat(4) { index ->
                dispatch(
                    webView = webView,
                    downTime = downTime,
                    action = MotionEvent.ACTION_MOVE,
                    x = x,
                    y = y + fingerDirection * (index + 1) * 45f,
                )
                SystemClock.sleep(24)
            }
            val afterReverseDrag = webView.scrollY
            dispatch(
                webView = webView,
                downTime = downTime,
                action = MotionEvent.ACTION_UP,
                x = x,
                y = y + fingerDirection * 180f,
            )

            val reverseDelta = afterReverseDrag - stoppedAt
            assertTrue(
                "Held pointer did not reverse fling: fling=$flingDelta reverse=$reverseDelta",
                reverseDelta * flingDelta < 0 && reverseDelta.absoluteValue > 40,
            )
        }
    }

    private inner class RecordingWebView : WebView(instrumentation.targetContext) {
        var flingCancelCount = 0
            private set

        override fun flingScroll(vx: Int, vy: Int) {
            assertEquals(0, vx)
            assertEquals(0, vy)
            flingCancelCount++
            super.flingScroll(vx, vy)
        }
    }

    private fun PagePullRefreshTouchListener.send(
        webView: WebView,
        action: Int,
        x: Float,
        y: Float,
    ): Boolean {
        val eventTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(eventTime, eventTime, action, x, y, 0)
        return try {
            onTouch(webView, event)
        } finally {
            event.recycle()
        }
    }

    private fun awaitWebView(scenario: ActivityScenario<MainActivity>): WebView {
        val result = AtomicReference<WebView>()
        repeat(100) {
            scenario.onActivity { activity ->
                result.compareAndSet(null, findWebView(activity.window.decorView))
            }
            result.get()?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("WebView was not attached")
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

    private fun awaitScrollNear(webView: WebView, expected: Int) {
        repeat(100) {
            if ((webView.scrollY - expected).absoluteValue < 20) return
            SystemClock.sleep(50)
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

    private fun dispatch(
        webView: WebView,
        downTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        try {
            instrumentation.runOnMainSync { webView.dispatchTouchEvent(event) }
        } finally {
            event.recycle()
        }
    }
}
