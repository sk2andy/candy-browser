package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
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
    fun leavingTabRefreshesPreviewForCreateSelectAndPause() {
        val sourceTabId = AtomicReference<String>()
        val createdTabId = AtomicReference<String>()
        val sourceWebView = AtomicReference<WebView>()
        val createdWebView = AtomicReference<WebView>()
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            val controller = BrowserController(activity).also { this.controller = it }
            controller.onStart()
            controller.onResume()
            sourceTabId.set(controller.selectedTabId)
            sourceWebView.set(attachSelectedWebView(activity, controller))
            loadPattern(sourceWebView.get(), "https://source.test/", initialPattern)
        }
        awaitPage(sourceWebView.get(), "https://source.test/")
        seedStalePreview(sourceTabId.get())
        updatePattern(sourceWebView.get(), createPattern)

        activityRule.scenario.onActivity {
            createdTabId.set(requireNotNull(controller).createTab())
        }
        assertPreviewContains(
            sourceTabId.get(),
            Color.rgb(255, 128, 0),
            Color.rgb(128, 0, 255),
        )

        activityRule.scenario.onActivity { activity ->
            val controller = requireNotNull(controller)
            createdWebView.set(attachSelectedWebView(activity, controller))
            loadPattern(createdWebView.get(), "https://created.test/", initialPattern)
        }
        awaitPage(createdWebView.get(), "https://created.test/")
        seedStalePreview(createdTabId.get())
        updatePattern(createdWebView.get(), selectPattern)

        activityRule.scenario.onActivity {
            requireNotNull(controller).selectTab(sourceTabId.get())
        }
        assertPreviewContains(createdTabId.get(), Color.CYAN, Color.MAGENTA)

        activityRule.scenario.onActivity { activity ->
            val controller = requireNotNull(controller)
            attachWebView(activity, controller.selectedWebViewForTesting())
        }
        updatePattern(sourceWebView.get(), pausePattern)

        activityRule.scenario.onActivity {
            requireNotNull(controller).onPause()
        }
        assertPreviewContains(sourceTabId.get(), Color.RED)
    }

    private fun attachSelectedWebView(
        activity: ComponentActivity,
        controller: BrowserController,
    ): WebView = controller.selectedWebViewForTesting().also { attachWebView(activity, it) }

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

    private fun seedStalePreview(tabId: String) {
        instrumentation.runOnMainSync {
            requireNotNull(controller).previews[tabId] =
                Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.GRAY)
                }
        }
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

    private fun assertPreviewContains(tabId: String, vararg expectedColors: Int) {
        val preview = AtomicReference<Bitmap?>()
        instrumentation.runOnMainSync {
            preview.set(requireNotNull(controller).previews[tabId])
        }
        val bitmap = preview.get()
        assertNotNull(bitmap)
        bitmap ?: return
        val minimumPixels = bitmap.width * bitmap.height / 20
        expectedColors.forEach { expected ->
            val matchingPixels = bitmap.countPixelsNear(expected)
            assertTrue(
                "color=${Integer.toHexString(expected)} pixels=$matchingPixels",
                matchingPixels >= minimumPixels,
            )
        }
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
        const val createPattern = "linear-gradient(to bottom,#ff8000 0 50%,#8000ff 50% 100%)"
        const val selectPattern = "linear-gradient(to bottom,#00ffff 0 50%,#ff00ff 50% 100%)"
        const val pausePattern = "#ff0000"
    }
}
