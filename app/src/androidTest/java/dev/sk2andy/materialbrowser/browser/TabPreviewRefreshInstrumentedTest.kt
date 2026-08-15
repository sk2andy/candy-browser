package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabPreviewRefreshInstrumentedTest {
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
    fun pixelCopyRefreshesPreviewBeforeActionsPauseAndDeparture() {
        val tabId = AtomicReference<String>()
        val webView = AtomicReference<WebView>()
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val controller = BrowserController(activity).also { this.controller = it }
            controller.onStart()
            controller.onResume()
            tabId.set(controller.selectedTabId)
            webView.set(controller.selectedWebViewForTesting())
            attachWebView(activity, webView.get())
            loadPattern(webView.get(), "https://preview.test/", initialPattern)
        }
        awaitPage(webView.get(), "https://preview.test/")
        seedStalePreview(tabId.get())
        updatePattern(webView.get(), actionPattern)

        val actionCapture = CountDownLatch(1)
        activityRule.scenario.onActivity {
            requireNotNull(controller).refreshSelectedTabPreview(actionCapture::countDown)
        }
        assertTrue(actionCapture.await(10, TimeUnit.SECONDS))
        awaitPreviewColors(tabId.get(), Color.CYAN, Color.MAGENTA)

        updatePattern(webView.get(), pausePattern)
        activityRule.scenario.onActivity {
            requireNotNull(controller).onPause()
        }
        awaitPreviewColors(tabId.get(), Color.GREEN, Color.YELLOW)

        activityRule.scenario.onActivity {
            requireNotNull(controller).onResume()
        }
        updatePattern(webView.get(), solidPattern)
        activityRule.scenario.onActivity {
            requireNotNull(controller).refreshSelectedTabPreviewBeforeDeparture {
                requireNotNull(controller).createTab()
            }
        }
        awaitPreviewColors(tabId.get(), solidColor)
    }

    @Test
    fun pauseDoesNotReplacePreviewWhenWebViewParentIsTransparent() {
        val tabId = AtomicReference<String>()
        val webView = AtomicReference<WebView>()
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val controller = BrowserController(activity).also { this.controller = it }
            controller.onStart()
            controller.onResume()
            tabId.set(controller.createTab("https://preview.test/"))
            webView.set(controller.selectedWebViewForTesting())
            attachWebView(activity, webView.get())
        }
        awaitLayout(webView.get())
        seedStalePreview(tabId.get())

        activityRule.scenario.onActivity {
            val controller = requireNotNull(controller)
            val captureCount = controller.previewCaptureRequestCountForTesting
            (webView.get().parent as View).alpha = 0f
            assertTrue(webView.get().isShown)
            controller.onPause()
            assertEquals(captureCount, controller.previewCaptureRequestCountForTesting)
        }
        awaitPreviewColors(tabId.get(), Color.GRAY)
    }

    private fun attachWebView(activity: ComponentActivity, webView: WebView) {
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
            },
        )
    }

    private fun loadPattern(webView: WebView, url: String, pattern: String) {
        webView.loadDataWithBaseURL(
            url,
            """
                <!doctype html>
                <html>
                  <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                  <body style="margin:0;min-height:100vh;background:$pattern"></body>
                </html>
            """.trimIndent(),
            "text/html",
            "utf-8",
            null,
        )
    }

    private fun awaitPage(webView: WebView, url: String) {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (SystemClock.uptimeMillis() < deadline) {
            val ready = AtomicReference(false)
            instrumentation.runOnMainSync {
                ready.set(webView.url == url && webView.width > 0 && webView.height > 0)
            }
            if (ready.get()) {
                awaitVisualState(webView)
                return
            }
            SystemClock.sleep(25)
        }
        error("WebView did not render $url")
    }

    private fun awaitLayout(webView: WebView) {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (SystemClock.uptimeMillis() < deadline) {
            val laidOut = AtomicReference(false)
            instrumentation.runOnMainSync {
                laidOut.set(
                    webView.isAttachedToWindow && webView.width > 0 && webView.height > 0,
                )
            }
            if (laidOut.get()) return
            SystemClock.sleep(25)
        }
        error("WebView did not lay out")
    }

    private fun seedStalePreview(tabId: String) {
        instrumentation.runOnMainSync {
            requireNotNull(controller).previews[tabId] =
                Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.GRAY)
                }
        }
    }

    private fun updatePattern(webView: WebView, pattern: String) {
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.evaluateJavascript(
                "document.body.style.background = '$pattern'",
            ) { latch.countDown() }
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        awaitVisualState(webView)
    }

    private fun awaitVisualState(webView: WebView) {
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.postVisualStateCallback(
                System.nanoTime(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        latch.countDown()
                    }
                },
            )
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS))
    }

    private fun awaitPreviewColors(tabId: String, vararg expectedColors: Int) {
        val deadline = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (SystemClock.uptimeMillis() < deadline) {
            val preview = AtomicReference<Bitmap?>()
            instrumentation.runOnMainSync {
                preview.set(requireNotNull(controller).previews[tabId])
            }
            if (preview.get()?.containsColors(*expectedColors) == true) return
            SystemClock.sleep(25)
        }
        val preview = AtomicReference<Bitmap?>()
        instrumentation.runOnMainSync {
            preview.set(requireNotNull(controller).previews[tabId])
        }
        assertNotNull(preview.get())
        error("Preview did not contain expected colors")
    }

    private fun Bitmap.containsColors(vararg expectedColors: Int): Boolean {
        val minimumPixels = width * height / 20
        return expectedColors.all { expected -> countPixelsNear(expected) >= minimumPixels }
    }

    private fun Bitmap.countPixelsNear(expected: Int): Int {
        var matches = 0
        val red = Color.red(expected)
        val green = Color.green(expected)
        val blue = Color.blue(expected)
        for (x in 0 until width step 8) {
            for (y in 0 until height step 8) {
                val pixel = getPixel(x, y)
                if (
                    kotlin.math.abs(Color.red(pixel) - red) <= 24 &&
                    kotlin.math.abs(Color.green(pixel) - green) <= 24 &&
                    kotlin.math.abs(Color.blue(pixel) - blue) <= 24
                ) {
                    matches += 64
                }
            }
        }
        return matches
    }

    private companion object {
        const val initialPattern = "linear-gradient(to bottom,#202020 0 50%,#d0d0d0 50% 100%)"
        const val actionPattern = "linear-gradient(to bottom,#00ffff 0 50%,#ff00ff 50% 100%)"
        const val pausePattern = "linear-gradient(to bottom,#00ff00 0 50%,#ffff00 50% 100%)"
        const val solidPattern = "#1234ab"
        val solidColor: Int = Color.rgb(0x12, 0x34, 0xab)
    }
}
