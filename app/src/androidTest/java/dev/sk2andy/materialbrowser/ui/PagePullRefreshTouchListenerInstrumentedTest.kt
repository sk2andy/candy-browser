package dev.sk2andy.materialbrowser.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
            val webView = WebView(instrumentation.targetContext)
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

            webView.destroy()
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
}
