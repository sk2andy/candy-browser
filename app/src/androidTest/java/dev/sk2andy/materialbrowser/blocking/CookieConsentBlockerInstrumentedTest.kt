package dev.sk2andy.materialbrowser.blocking

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CookieConsentBlockerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun hidesInitialAndLateCookieBannersWithoutHidingUnrelatedContent() {
        val view = loadPage(
            """
                <!doctype html>
                <html><head></head><body style="overflow: hidden; overflow-y: hidden">
                  <div id="CybotCookiebotDialog">Cookie banner</div>
                  <div id="BorlabsCookieBox">Borlabs banner</div>
                  <div id="normal-modal">Normal content</div>
                </body></html>
            """.trimIndent(),
        )
        evaluate(view, ContentBlocker(instrumentation.targetContext).consentScript)

        val result = evaluate(
            view,
            """
                (() => {
                  const lateBanner = document.createElement('div');
                  lateBanner.id = 'didomi-host';
                  document.body.appendChild(lateBanner);
                  const injectedStyle = document.getElementById('material-browser-easylist-cookie-css');
                  return [
                    injectedStyle !== null,
                    injectedStyle && injectedStyle.sheet ? injectedStyle.sheet.cssRules.length : -1,
                    getComputedStyle(document.getElementById('CybotCookiebotDialog')).display,
                    getComputedStyle(document.getElementById('BorlabsCookieBox')).display,
                    getComputedStyle(lateBanner).display,
                    getComputedStyle(document.getElementById('normal-modal')).display,
                    document.body.style.overflow || 'cleared',
                    document.body.style.overflowY || 'cleared'
                  ].join('|');
                })();
            """.trimIndent(),
        )

        assertEquals("\"true|121|none|none|none|block|cleared|cleared\"", result)
    }

    @Test
    fun preservesScrollLockWhenNoKnownCookieBannerExists() {
        val view = loadPage(
            """
                <!doctype html>
                <html><head></head><body style="overflow: hidden">
                  <div id="normal-modal">Normal content</div>
                </body></html>
            """.trimIndent(),
        )
        evaluate(view, ContentBlocker(instrumentation.targetContext).consentScript)

        val result = evaluate(
            view,
            """
                [
                  document.body.style.overflow,
                  getComputedStyle(document.getElementById('normal-modal')).display
                ].join('|');
            """.trimIndent(),
        )

        assertEquals("\"hidden|block\"", result)
    }

    @Test
    fun installsCookieCssBeforePageJavaScriptWhenDocumentStartIsSupported() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val blocker = ContentBlocker(instrumentation.targetContext)
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(this, blocker.consentScript, setOf("*"))
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://example.test/",
                        """
                            <!doctype html>
                            <html><head><script>
                              const banner = document.createElement('div');
                              banner.id = 'CybotCookiebotDialog';
                              document.documentElement.appendChild(banner);
                              window.blockerState = [
                                document.getElementById('material-browser-easylist-cookie-css') !== null,
                                getComputedStyle(banner).display
                              ].join('|');
                            </script></head><body></body></html>
                        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(10, TimeUnit.SECONDS))
        val view = createdView.get().also(webView::set)

        assertEquals("\"true|none\"", evaluate(view, "window.blockerState"))
    }

    @Test
    fun documentStartScriptSkipsPausedHostBeforePageJavaScript() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val blocker = ContentBlocker(instrumentation.targetContext)
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        blocker.consentScriptFor(setOf("paused.test")),
                        setOf("*"),
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://paused.test/",
                        """
                            <!doctype html>
                            <html><head><script>
                              const banner = document.createElement('div');
                              banner.id = 'CybotCookiebotDialog';
                              document.documentElement.appendChild(banner);
                              window.blockerState = [
                                document.getElementById('material-browser-easylist-cookie-css') !== null,
                                getComputedStyle(banner).display
                              ].join('|');
                            </script></head><body></body></html>
                        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(10, TimeUnit.SECONDS))
        val view = createdView.get().also(webView::set)

        assertEquals("\"false|block\"", evaluate(view, "window.blockerState"))
    }

    @Test
    fun unlocksScrollWhenCmpAppliesLockAfterPageFinished() {
        val view = loadPage(
            """
                <!doctype html>
                <html><head></head><body><main>Article</main></body></html>
            """.trimIndent(),
        )
        evaluate(view, ContentBlocker(instrumentation.targetContext).consentScript)

        evaluate(
            view,
            """
                (() => {
                  const lateBanner = document.createElement('div');
                  lateBanner.id = 'CybotCookiebotDialog';
                  document.body.appendChild(lateBanner);
                  document.body.style.overflow = 'hidden';
                })();
            """.trimIndent(),
        )

        assertEquals("\"cleared|none\"", evaluate(
            view,
            """
                [
                  document.body.style.overflow || 'cleared',
                  getComputedStyle(document.getElementById('CybotCookiebotDialog')).display
                ].join('|');
            """.trimIndent(),
        ))
    }

    @Test
    fun preservesClassBasedScrollLockWhenHiddenCmpExists() {
        val view = loadPage(
            """
                <!doctype html>
                <html><head><style>.unrelated-modal-open { overflow-y: hidden }</style></head>
                <body class="unrelated-modal-open">
                  <div id="CybotCookiebotDialog">Cookie banner</div>
                  <div id="normal-modal">Unrelated modal</div>
                </body></html>
            """.trimIndent(),
        )
        evaluate(view, ContentBlocker(instrumentation.targetContext).consentScript)

        assertEquals(
            "\"hidden|cleared|none|block\"",
            evaluate(
                view,
                """
                    [
                      getComputedStyle(document.body).overflowY,
                      document.body.style.overflowY || 'cleared',
                      getComputedStyle(document.getElementById('CybotCookiebotDialog')).display,
                      getComputedStyle(document.getElementById('normal-modal')).display
                    ].join('|');
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun knownRejectActionRunsInsideCrossOriginFrame() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val frameNavigated = CountDownLatch(1)
        val mainLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        val script = ConsentBlockerScript.create(
            cssBytes = ByteArray(0),
            actionRules = listOf(
                BundledConsentAction("cmp-reject", "cmp.test", "#reject-all"),
            ),
        )
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    WebViewCompat.addDocumentStartJavaScript(this, script, setOf("*"))
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val html = when (request.url.toString()) {
                                "https://top.test/" ->
                                    "<html><body><iframe src='https://cmp.test/frame'></iframe></body></html>"
                                "https://cmp.test/frame" ->
                                    "<html><body><button id='reject-all' " +
                                        "onclick=\"location.href='https://cmp.test/done'\">Reject</button></body></html>"
                                "https://cmp.test/done" -> {
                                    frameNavigated.countDown()
                                    "<html><body>Done</body></html>"
                                }
                                else -> return null
                            }
                            return WebResourceResponse(
                                "text/html",
                                "utf-8",
                                ByteArrayInputStream(html.toByteArray()),
                            )
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            if (url == "https://top.test/") mainLoaded.countDown()
                        }
                    }
                    loadUrl("https://top.test/")
                },
            )
        }
        val view = createdView.get().also(webView::set)

        assertTrue(mainLoaded.await(10, TimeUnit.SECONDS))
        assertTrue(frameNavigated.await(10, TimeUnit.SECONDS))
        assertEquals("\"https://top.test/\"", evaluate(view, "location.href"))
    }

    private fun loadPage(html: String): WebView {
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    loadDataWithBaseURL(
                        "https://example.test/",
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(10, TimeUnit.SECONDS))
        return createdView.get().also(webView::set)
    }

    private fun evaluate(view: WebView, script: String): String {
        val result = AtomicReference<String>()
        val evaluated = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value)
                evaluated.countDown()
            }
        }

        assertTrue(evaluated.await(10, TimeUnit.SECONDS))
        return result.get()
    }
}
