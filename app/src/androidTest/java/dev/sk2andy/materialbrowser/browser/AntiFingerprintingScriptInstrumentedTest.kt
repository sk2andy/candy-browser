package dev.sk2andy.materialbrowser.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AntiFingerprintingScriptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun reducesEntropyBeforePageScriptsRun() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        AntiFingerprintingScript.create("0123456789abcdef"),
                        setOf("*"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://fingerprint.test/",
                        """
                            <html><body><script>
                              const canvas = document.createElement("canvas");
                              canvas.width = 2;
                              canvas.height = 2;
                              const context = canvas.getContext("2d");
                              context.fillStyle = "rgb(255, 0, 0)";
                              context.fillRect(0, 0, 2, 2);
                              const pixel = context.getImageData(0, 0, 1, 1).data;
                              const firstCanvas = canvas.toDataURL();
                              window.fingerprintResult = JSON.stringify({
                                hardwareConcurrency: navigator.hardwareConcurrency,
                                deviceMemory: navigator.deviceMemory,
                                maxTouchPoints: navigator.maxTouchPoints,
                                screenWidth: screen.width,
                                screenHeight: screen.height,
                                colorDepth: screen.colorDepth,
                                canvasReadbackProtected:
                                  pixel[0] !== 255 || pixel[1] !== 0 || pixel[2] !== 0,
                                canvasStable: firstCanvas === canvas.toDataURL(),
                              });
                            </script></body></html>
                        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val view = createdView.get().also(webView::set)
        val result = JSONObject(evaluate(view, "window.fingerprintResult"))

        assertEquals(4, result.getInt("hardwareConcurrency"))
        assertEquals(4, result.getInt("deviceMemory"))
        assertEquals(5, result.getInt("maxTouchPoints"))
        assertEquals(0, result.getInt("screenWidth") % 100)
        assertEquals(0, result.getInt("screenHeight") % 100)
        assertEquals(24, result.getInt("colorDepth"))
        assertTrue(result.getBoolean("canvasReadbackProtected"))
        assertTrue(result.getBoolean("canvasStable"))
    }

    private fun evaluate(view: WebView, script: String): String {
        val result = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value)
                latch.countDown()
            }
        }
        assertTrue(latch.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return JSONTokener(requireNotNull(result.get())).nextValue() as String
    }

    private companion object {
        const val RESULT_TIMEOUT_SECONDS = 5L
    }
}
