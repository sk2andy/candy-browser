package dev.sk2andy.materialbrowser.browser

import android.os.SystemClock
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebContentTopInsetScriptInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val webView = AtomicReference<WebView>()

    @After
    fun tearDown() {
        webView.getAndSet(null)?.let { view ->
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun reinjectionNeverAbandonsEdgeToEdgeAfterPersistentLayoutFailure() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(TopInsetBridge(fallbackReceived))
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            """
                const blocker = document.createElement('style');
                blocker.textContent =
                  'html:root::before { height: 0 !important; min-height: 0 !important; }';
                document.head.appendChild(blocker);
            """.trimIndent(),
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        evaluate(
            view,
            """
                globalThis.__candyReconcileContentTopInset();
                globalThis.__candyReconcileContentTopInset();
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        assertEquals(
            "true",
            evaluate(
                view,
                "Boolean(document.querySelector(" +
                    "'style[data-candy-browser-owned=\"true\"]'))",
            ),
        )
        assertFalse(
            "Persistent layout failure moved the WebView out of edge-to-edge",
            fallbackReceived.await(STALE_TIMER_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun bridgeFailureSettingsCannotRequestNativeFallback() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            """
                const blocker = document.createElement('style');
                blocker.textContent =
                  'html:root::before { height: 0 !important; min-height: 0 !important; }';
                document.head.appendChild(blocker);
            """.trimIndent(),
        )
        evaluate(view, "globalThis.__candyReconfigureContentTopInset();")

        assertFalse(
            "Bridge failure settings moved the WebView out of edge-to-edge",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun fixedAndStickyHeadersKeepTheirSafeTopAcrossScrollAndVisibilityChanges() {
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = CountDownLatch(1),
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 300vh; }
                  #fixed, #sticky {
                    box-sizing: border-box;
                    left: 0;
                    top: 0;
                    width: 100%;
                    height: 64px;
                    background: white;
                  }
                  #fixed { position: fixed; }
                  #sticky { position: sticky; margin-top: 96px; }
                </style></head><body>
                <header id="fixed"><button>Vimeo</button></header>
                <header id="sticky"><button>Sticky</button></header>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        val density = evaluate(view, "devicePixelRatio").toDouble()
        val expectedTop = TOP_INSET_PX / density
        val fixedTopBefore = elementTop(view, "#fixed")

        evaluate(view, "scrollTo(0, 400)")
        SystemClock.sleep(SCROLL_REGRESSION_WINDOW_MILLIS)
        val fixedTopAfterScroll = elementTop(view, "#fixed")
        val stickyTopAfterScroll = elementTop(view, "#sticky")

        evaluate(view, "document.querySelector('#fixed').style.display = 'none'")
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        evaluate(view, "document.querySelector('#fixed').style.display = ''")
        SystemClock.sleep(SCROLL_REGRESSION_WINDOW_MILLIS)
        val fixedTopAfterVisibilityChange = elementTop(view, "#fixed")

        assertTrue(fixedTopBefore >= expectedTop - CSS_PIXEL_TOLERANCE)
        assertEquals(fixedTopBefore, fixedTopAfterScroll, CSS_PIXEL_TOLERANCE)
        assertEquals(fixedTopBefore, fixedTopAfterVisibilityChange, CSS_PIXEL_TOLERANCE)
        assertTrue(stickyTopAfterScroll >= expectedTop - CSS_PIXEL_TOLERANCE)
    }

    @Test
    fun visibleHeaderReturningToFlowDropsItsOwnedTranslation() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1)),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 200vh; }
                  #header { position: fixed; inset: 0 0 auto 0; height: 64px; background: white; }
                </style></head><body>
                <header id="header"><button>Menu</button></header>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        assertEquals(
            "true",
            evaluate(
                view,
                "document.querySelector('#header').getAttribute(" +
                    "'data-candy-browser-top-inset-offset')",
            ).removeSurrounding("\""),
        )

        evaluate(
            view,
            "document.querySelector('#header').style.position='relative'",
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)

        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertEquals(TOP_INSET_PX / density, elementTop(view, "#header"), CSS_PIXEL_TOLERANCE)
        assertEquals(
            "null",
            evaluate(
                view,
                "document.querySelector('#header').getAttribute(" +
                    "'data-candy-browser-top-inset-offset')",
            ),
        )
        assertEquals(
            "\"\"",
            evaluate(
                view,
                "document.querySelector('#header').style.getPropertyValue(" +
                    "'--candy-browser-owned-top-inset-offset')",
            ),
        )
    }

    @Test
    fun laterDomMutationResumesSuspendedLayoutRecovery() {
        val view = loadPage(
            TopInsetBridge(
                fallbackReceived = CountDownLatch(1),
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(
            view,
            """
                const blocker = document.createElement('style');
                blocker.id = 'blocker';
                blocker.textContent =
                  'html:root::before { height: 0 !important; min-height: 0 !important; }';
                document.head.appendChild(blocker);
                globalThis.__candyReconcileContentTopInset();
                globalThis.__candyReconcileContentTopInset();
            """.trimIndent(),
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)

        evaluate(
            view,
            "document.querySelector('#blocker').remove()",
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS * 2)

        val density = evaluate(view, "devicePixelRatio").toDouble()
        assertEquals(
            TOP_INSET_PX / density,
            evaluate(view, "parseFloat(getComputedStyle(document.documentElement,'::before').height)")
                .toDouble(),
            CSS_PIXEL_TOLERANCE,
        )
    }

    @Test
    fun viewportCoverAttributeChangeTransfersOwnershipWithoutManualReconcile() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1), viewportCoverAllowed = true),
            html = """
                <html><head>
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                </head><body><main>Content</main></body></html>
            """.trimIndent(),
        )
        evaluate(view, WebContentTopInsetScript.installScript)
        assertEquals(
            "true",
            evaluate(view, "Boolean(document.querySelector('style[data-candy-browser-owned]'))"),
        )

        evaluate(
            view,
            "document.querySelector('meta[name=viewport]').content += ',viewport-fit=cover'",
        )
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)

        assertEquals(
            "false",
            evaluate(view, "Boolean(document.querySelector('style[data-candy-browser-owned]'))"),
        )
    }

    @Test
    fun compactFixedMenuTouchTargetClearsStatusBar() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
            html = """
                <html><head></head><body>
                <button id="menu" style="position:fixed;top:0;left:0;width:48px;height:48px">
                  Menu
                </button>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        val devicePixelRatio = evaluate(view, "devicePixelRatio").toDouble()
        val menuTop = evaluate(
            view,
            "document.querySelector('#menu').getBoundingClientRect().top",
        ).toDouble()
        val minimumMenuTop = TOP_INSET_PX / devicePixelRatio + COMPACT_CONTROL_PADDING_CSS_PIXELS
        assertTrue(
            "Compact menu touch target remained at the status-bar edge: " +
                "top=$menuTop minimum=$minimumMenuTop",
            menuTop >= minimumMenuTop - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "Compact menu padding triggered native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun compactMenuOverNonInteractiveHeaderStaysEdgeToEdgeAfterScroll() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = 100,
                requiredFailureCount = 2,
            ),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 300vh; }
                  #drawer-shell, #drawer-backdrop {
                    position: fixed;
                    inset: 0;
                    z-index: 9998;
                  }
                  #drawer-shell { background: transparent; }
                  #drawer-backdrop { background: rgba(0, 0, 0, 0.6); z-index: 9997; }
                  #navd {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 60px;
                    height: 64px;
                  }
                  #header {
                    position: fixed;
                    top: 40px;
                    left: 0;
                    width: 100%;
                    height: 80px;
                    z-index: 1;
                    background: white;
                  }
                  #sign-in { position: absolute; top: 8px; right: 8px; }
                </style></head><body>
                <div id="drawer-backdrop"></div>
                <div id="drawer-shell">
                  <div role="button" style="height:120px">Hidden drawer action</div>
                </div>
                <div id="navd"><div><div id="menu" role="button">Menu</div></div></div>
                <header id="header"><button id="sign-in">Sign in</button></header>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        evaluate(view, "scrollTo(0, 400); scrollTo(0, 0);")
        SystemClock.sleep(MUTATION_SETTLE_MILLIS)
        evaluate(view, "globalThis.__candyReconcileContentTopInset();")

        val devicePixelRatio = evaluate(view, "devicePixelRatio").toDouble()
        val menuTop = evaluate(
            view,
            "document.querySelector('#menu').getBoundingClientRect().top",
        ).toDouble()
        assertEquals(
            "true",
            evaluate(
                view,
                "document.querySelector('#navd')" +
                    ".getAttribute('data-candy-browser-top-inset-offset')",
            ).removeSurrounding("\""),
        )
        val minimumMenuTop = TOP_INSET_PX / devicePixelRatio + COMPACT_CONTROL_PADDING_CSS_PIXELS
        assertTrue(
            "Compact menu remained in the status bar beside a separate header: " +
                "top=$menuTop minimum=$minimumMenuTop",
            menuTop >= minimumMenuTop - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "A separate header caused the edge-to-edge document to request native fallback",
            fallbackReceived.await(NO_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun searchHeaderActivatedByInputClearsStatusBarBeforeLayoutQuietPeriod() {
        val fallbackReceived = CountDownLatch(1)
        val view = loadPage(
            bridge = TopInsetBridge(
                fallbackReceived = fallbackReceived,
                layoutQuietPeriodMillis = SEARCH_LAYOUT_QUIET_PERIOD_MILLIS,
                requiredFailureCount = 3,
            ),
            html = """
                <html><head><style>
                  html, body { margin: 0; min-height: 200vh; }
                  #query { margin-top: 120px; }
                  #drawer-shell, #drawer-backdrop {
                    position: fixed;
                    inset: 0;
                    z-index: 9998;
                  }
                  #drawer-shell { background: transparent; }
                  #drawer-backdrop { background: rgba(0, 0, 0, 0.6); z-index: 9997; }
                  #search-header {
                    position: fixed;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 64px;
                    background: white;
                    z-index: 10000;
                  }
                </style></head><body>
                <input id="query" aria-label="Search">
                <div id="drawer-backdrop"></div>
                <div id="drawer-shell">
                  <div role="button" style="height:120px">Hidden drawer action</div>
                </div>
                <form id="search-header" hidden>
                  <button type="button">Add</button>
                  <input value="Vimeo sample video">
                  <button type="button">Close</button>
                </form>
                <main>Suggestions</main>
                <script>
                  document.querySelector('#query').addEventListener('input', () => {
                    document.querySelector('#search-header').hidden = false;
                  });
                </script>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)
        val devicePixelRatio = evaluate(view, "devicePixelRatio").toDouble()
        val expectedTop = TOP_INSET_PX / devicePixelRatio

        evaluate(
            view,
            "document.querySelector('#query').dispatchEvent(" +
                "new InputEvent('input', { bubbles: true, inputType: 'insertText', data: 'v' }));",
        )
        val deadline = SystemClock.elapsedRealtime() + IMMEDIATE_LAYOUT_TIMEOUT_MILLIS
        var headerTop = Double.NEGATIVE_INFINITY
        while (SystemClock.elapsedRealtime() < deadline) {
            headerTop = evaluate(
                view,
                "document.querySelector('#search-header').getBoundingClientRect().top",
            ).toDouble()
            if (headerTop >= expectedTop - CSS_PIXEL_TOLERANCE) break
            SystemClock.sleep(FRAME_SETTLE_MILLIS)
        }

        assertTrue(
            "Input-activated search header remained in the status bar before layout quiet: " +
                "top=$headerTop expected=$expectedTop",
            headerTop >= expectedTop - CSS_PIXEL_TOLERANCE,
        )
        assertFalse(
            "Immediate search-header reconciliation requested native fallback",
            fallbackReceived.await(IMMEDIATE_FALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun viewportWideInteractiveHeaderKeepsExactStatusInset() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1)),
            html = """
                <html><head></head><body>
                <header id="header" style="position:fixed;top:0;left:0;width:100%;height:48px">
                  <button>Menu</button>
                </header>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        assertElementUsesExactStatusInset(view, "#header")
    }

    @Test
    fun compactDecorativeElementKeepsExactStatusInset() {
        val view = loadPage(
            bridge = TopInsetBridge(CountDownLatch(1)),
            html = """
                <html><head></head><body>
                <div id="decoration" style="position:fixed;top:0;left:0;width:24px;height:24px">
                  Candy
                </div>
                <main>Content</main>
                </body></html>
            """.trimIndent(),
        )

        evaluate(view, WebContentTopInsetScript.installScript)

        assertElementUsesExactStatusInset(view, "#decoration")
    }

    private fun assertElementUsesExactStatusInset(
        view: WebView,
        selector: String,
    ) {
        val devicePixelRatio = evaluate(view, "devicePixelRatio").toDouble()
        val elementTop = evaluate(
            view,
            "document.querySelector('$selector').getBoundingClientRect().top",
        ).toDouble()
        val expectedTop = TOP_INSET_PX / devicePixelRatio
        assertTrue(
            "$selector unexpectedly received compact-control padding: " +
                "top=$elementTop expected=$expectedTop",
            kotlin.math.abs(elementTop - expectedTop) <= CSS_PIXEL_TOLERANCE,
        )
    }

    private fun elementTop(
        view: WebView,
        selector: String,
    ): Double = evaluate(
        view,
        "document.querySelector('$selector').getBoundingClientRect().top",
    ).toDouble()

    private fun loadPage(
        bridge: TopInsetBridge,
        html: String = "<html><head></head><body><main>Content</main></body></html>",
    ): WebView {
        val pageLoaded = CountDownLatch(1)
        val createdView = AtomicReference<WebView>()
        instrumentation.runOnMainSync {
            createdView.set(
                WebView(instrumentation.targetContext).apply {
                    settings.javaScriptEnabled = true
                    addJavascriptInterface(bridge, WebContentTopInsetScript.bridgeName)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageLoaded.countDown()
                        }
                    }
                    measure(
                        View.MeasureSpec.makeMeasureSpec(VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(
                            VIEWPORT_HEIGHT_PX,
                            View.MeasureSpec.EXACTLY,
                        ),
                    )
                    layout(0, 0, VIEWPORT_WIDTH_PX, VIEWPORT_HEIGHT_PX)
                    loadDataWithBaseURL(
                        "https://safe-area.test/",
                        html,
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        assertTrue(pageLoaded.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return createdView.get().also(webView::set)
    }

    private fun evaluate(view: WebView, script: String): String {
        val evaluated = CountDownLatch(1)
        val result = AtomicReference<String>()
        instrumentation.runOnMainSync {
            view.evaluateJavascript(script) { value ->
                result.set(value)
                evaluated.countDown()
            }
        }
        assertTrue(evaluated.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private class TopInsetBridge(
        private val fallbackReceived: CountDownLatch,
        private val layoutQuietPeriodMillis: Int = 400,
        private val requiredFailureCount: Int = 3,
        private val viewportCoverAllowed: Boolean = false,
    ) {
        @JavascriptInterface
        fun topInsetPx(): Int = TOP_INSET_PX

        @JavascriptInterface
        fun viewportCoverAllowed(): Boolean = viewportCoverAllowed

        @JavascriptInterface
        fun navigationGeneration(): Int = NAVIGATION_GENERATION

        @JavascriptInterface
        fun policyRevision(): Long = POLICY_REVISION

        @JavascriptInterface
        fun safeAreaLayoutQuietPeriodMillis(): Int = layoutQuietPeriodMillis

        @JavascriptInterface
        fun safeAreaRequiredFailureCount(): Int = requiredFailureCount

        @JavascriptInterface
        fun fallbackToNative(
            generation: Int,
            revision: Long,
        ) {
            if (generation == NAVIGATION_GENERATION && revision == POLICY_REVISION) {
                fallbackReceived.countDown()
            }
        }
    }

    private companion object {
        const val TOP_INSET_PX = 96
        const val NAVIGATION_GENERATION = 7
        const val POLICY_REVISION = 11L
        const val MUTATION_SETTLE_MILLIS = 100L
        const val COMPACT_CONTROL_PADDING_CSS_PIXELS = 8.0
        const val CSS_PIXEL_TOLERANCE = 0.5
        const val NO_FALLBACK_WINDOW_MILLIS = 350L
        const val SEARCH_LAYOUT_QUIET_PERIOD_MILLIS = 800
        const val IMMEDIATE_LAYOUT_TIMEOUT_MILLIS = 250L
        const val IMMEDIATE_FALLBACK_WINDOW_MILLIS = 100L
        const val FRAME_SETTLE_MILLIS = 16L
        const val VIEWPORT_WIDTH_PX = 1_080
        const val VIEWPORT_HEIGHT_PX = 1_920
        const val STALE_TIMER_WINDOW_MILLIS = 600L
        const val SCROLL_REGRESSION_WINDOW_MILLIS = 600L
        const val PAGE_TIMEOUT_SECONDS = 5L
    }
}
