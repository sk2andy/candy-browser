package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceWebThemeInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences("browser_session", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun liveAppearanceChangesReachAndroidResourcesAndWebsites() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            updateAppearance(scenario, BrowserAppearanceMode.Light)
            assertTrue(awaitNightResources(scenario, expectedDark = false))
            val controller = selectedController(scenario)
            val tabId = controller.selectedTabId
            val lightWebView = loadThemeProbe(scenario)
            assertTrue(awaitWebsiteColorScheme(scenario, expected = "light"))

            updateAppearance(scenario, BrowserAppearanceMode.Dark)
            assertTrue(awaitNightResources(scenario, expectedDark = true))
            assertTrue(selectedController(scenario) === controller)
            assertTrue(selectedController(scenario).selectedTabId == tabId)
            val darkWebView = loadThemeProbe(scenario)
            assertTrue(darkWebView !== lightWebView)
            assertTrue(awaitWebsiteColorScheme(scenario, expected = "dark"))
        }
    }

    @Test
    fun darkAppearanceKeepsAlgorithmicWebContentDarkeningDisabled() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            updateAppearance(scenario, BrowserAppearanceMode.Dark)
            assertTrue(awaitNightResources(scenario, expectedDark = true))

            scenario.onActivity { activity ->
                val settings = activity.browserControllerForTesting()
                    .selectedWebViewForTesting()
                    .settings
                assertFalse(WebSettingsCompat.isAlgorithmicDarkeningAllowed(settings))
            }
        }
    }

    @Test
    fun forceDarkWebsiteSettingUpdatesActiveWebViewsInPlace() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            updateAppearance(scenario, BrowserAppearanceMode.Dark)
            assertTrue(awaitNightResources(scenario, expectedDark = true))

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val mainWebView = controller.selectedWebViewForTesting()
                val linkPeekWebView = controller.createLinkPeekPreviewWebView(
                    url = "https://theme.example/link-peek",
                    onProgressChanged = {},
                    onCommittedUrlChanged = {},
                )
                assertTrue(controller.openExternalLinkPreview("https://theme.example/external"))
                val externalPreviewState = requireNotNull(controller.externalLinkPreviewState)
                assertTrue(controller.prepareExternalLinkPreview(externalPreviewState.sessionId))
                val externalPreviewWebView =
                    requireNotNull(controller.externalLinkPreviewWebViewForTesting())

                controller.updateAppearanceSettings(
                    controller.appearanceSettings.copy(forceDarkWebsites = true),
                )

                assertTrue(controller.selectedWebViewForTesting() === mainWebView)
                assertTrue(
                    controller.externalLinkPreviewWebViewForTesting() === externalPreviewWebView,
                )
                assertTrue(controller.activeLinkPeekPreviewCountForTesting == 1)
                val activeWebViews = listOf(mainWebView, linkPeekWebView, externalPreviewWebView)
                activeWebViews.forEach { webView ->
                    assertTrue(WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings))
                }

                controller.updateAppearanceSettings(
                    controller.appearanceSettings.copy(forceDarkWebsites = false),
                )

                assertTrue(controller.selectedWebViewForTesting() === mainWebView)
                assertTrue(
                    controller.externalLinkPreviewWebViewForTesting() === externalPreviewWebView,
                )
                activeWebViews.forEach { webView ->
                    assertFalse(WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings))
                }

                controller.releaseLinkPeekPreviewWebView(linkPeekWebView)
                controller.dismissExternalLinkPreview(externalPreviewState.sessionId)
            }
        }
    }

    @Test
    fun transparentPagesKeepAuthorTextReadableAcrossWebsiteAppearancePolicies() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))

        listOf(
            WebsiteAppearanceCase(
                id = "light",
                label = "light",
                appearanceMode = BrowserAppearanceMode.Light,
                forceDarkWebsites = false,
            ),
            WebsiteAppearanceCase(
                id = "dark",
                label = "dark",
                appearanceMode = BrowserAppearanceMode.Dark,
                forceDarkWebsites = false,
            ),
            WebsiteAppearanceCase(
                id = "forced-dark",
                label = "forced dark",
                appearanceMode = BrowserAppearanceMode.Dark,
                forceDarkWebsites = true,
            ),
        ).forEach { appearanceCase ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                updateAppearance(
                    scenario = scenario,
                    appearanceMode = appearanceCase.appearanceMode,
                    forceDarkWebsites = appearanceCase.forceDarkWebsites,
                )
                assertTrue(
                    awaitNightResources(
                        scenario = scenario,
                        expectedDark = appearanceCase.appearanceMode != BrowserAppearanceMode.Light,
                    ),
                )
                val probe = loadContrastProbe(
                    scenario = scenario,
                    probeId = appearanceCase.id,
                )
                assertTrue(awaitContrastProbeCommit(probe))
                assertTrue(awaitWebViewLayout(scenario, probe.webView))
                assertTrue(awaitWebViewDraw(probe.webView))

                val samples = captureContrastSamples(scenario, probe.webView)
                assertTrue(
                    "${appearanceCase.label} background was not uniform: $samples",
                    samples.consistentBackgroundCount * 100 >=
                        samples.backgroundCount * MINIMUM_UNIFORM_BACKGROUND_PERCENT,
                )
                assertTrue(
                    "${appearanceCase.label} text did not contrast with background: $samples",
                    samples.contrastingTextCount * 100 >=
                        samples.textCount * MINIMUM_CONTRASTING_TEXT_PERCENT,
                )
            }
        }
    }

    private fun updateAppearance(
        scenario: ActivityScenario<MainActivity>,
        appearanceMode: BrowserAppearanceMode,
        forceDarkWebsites: Boolean = false,
    ) {
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().updateAppearanceSettings(
                AppearanceSettings(
                    appearanceMode = appearanceMode,
                    forceDarkWebsites = forceDarkWebsites,
                ),
            )
        }
    }

    private fun awaitNightResources(
        scenario: ActivityScenario<MainActivity>,
        expectedDark: Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + APPEARANCE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val matches = AtomicBoolean(false)
            scenario.onActivity { activity ->
                val nightMode = activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                matches.set(
                    nightMode == if (expectedDark) {
                        Configuration.UI_MODE_NIGHT_YES
                    } else {
                        Configuration.UI_MODE_NIGHT_NO
                    },
                )
            }
            if (matches.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun loadThemeProbe(scenario: ActivityScenario<MainActivity>): WebView {
        val webView = AtomicReference<WebView>()
        scenario.onActivity { activity ->
            webView.set(
                activity.browserControllerForTesting().selectedWebViewForTesting().apply {
                    loadDataWithBaseURL(
                        "https://theme.example/",
                        THEME_PROBE_HTML,
                        "text/html",
                        "utf-8",
                        null,
                    )
                },
            )
        }
        return webView.get()
    }

    private fun loadContrastProbe(
        scenario: ActivityScenario<MainActivity>,
        probeId: String,
    ): ContrastProbe {
        val probe = AtomicReference<ContrastProbe>()
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            val container = FrameLayout(activity)
            activity.setContentView(container)
            controller.attachSelectedWebView(container)
            val webView = controller.selectedWebViewForTesting()
            val expectedUrl = "https://contrast.example/$probeId/"
            val committed = CountDownLatch(1)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageCommitVisible(view: WebView, url: String) {
                    if (url == expectedUrl) committed.countDown()
                }
            }
            webView.loadDataWithBaseURL(
                expectedUrl,
                CONTRAST_PROBE_HTML.replace(CONTRAST_PROBE_ID_PLACEHOLDER, probeId),
                "text/html",
                "utf-8",
                null,
            )
            probe.set(
                ContrastProbe(
                    id = probeId,
                    expectedUrl = expectedUrl,
                    webView = webView,
                    committed = committed,
                ),
            )
        }
        return probe.get()
    }

    private fun selectedController(
        scenario: ActivityScenario<MainActivity>,
    ): BrowserController {
        val controller = AtomicReference<BrowserController>()
        scenario.onActivity { activity ->
            controller.set(activity.browserControllerForTesting())
        }
        return controller.get()
    }

    private fun awaitWebsiteColorScheme(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ): Boolean {
        val result = AtomicReference<String?>()
        val deadline = SystemClock.uptimeMillis() + APPEARANCE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().selectedWebViewForTesting()
                    .evaluateJavascript(THEME_PROBE_SCRIPT) { value ->
                        result.set(value.removeSurrounding("\""))
                    }
            }
            if (result.get() == expected) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitContrastProbeCommit(probe: ContrastProbe): Boolean {
        if (!probe.committed.await(APPEARANCE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) return false
        val result = AtomicReference<String?>()
        val deadline = SystemClock.uptimeMillis() + APPEARANCE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                probe.webView.evaluateJavascript(
                    "document.baseURI === '${probe.expectedUrl}' && " +
                        "document.body.dataset.probe === '${probe.id}'",
                ) { value -> result.set(value) }
            }
            if (result.get() == "true") return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitWebViewLayout(
        scenario: ActivityScenario<MainActivity>,
        expectedWebView: WebView,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + APPEARANCE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val laidOut = AtomicBoolean(false)
            scenario.onActivity { activity ->
                val webView = activity.browserControllerForTesting().selectedWebViewForTesting()
                laidOut.set(
                    webView === expectedWebView &&
                        webView.isShown &&
                        webView.width > 0 &&
                        webView.height > 0,
                )
            }
            if (laidOut.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitWebViewDraw(webView: WebView): Boolean {
        val drawn = CountDownLatch(1)
        val observer = AtomicReference<ViewTreeObserver?>()
        val listener = AtomicReference<ViewTreeObserver.OnDrawListener?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val viewTreeObserver = webView.viewTreeObserver
            val drawObserved = AtomicBoolean(false)
            val drawListener = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (!drawObserved.compareAndSet(false, true)) return
                    webView.post {
                        if (viewTreeObserver.isAlive) {
                            viewTreeObserver.removeOnDrawListener(this)
                        }
                        webView.postOnAnimation { drawn.countDown() }
                    }
                }
            }
            observer.set(viewTreeObserver)
            listener.set(drawListener)
            viewTreeObserver.addOnDrawListener(drawListener)
            webView.postInvalidateOnAnimation()
        }
        val completed = drawn.await(APPEARANCE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        if (!completed) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val viewTreeObserver = observer.get()
                val drawListener = listener.get()
                if (viewTreeObserver?.isAlive == true && drawListener != null) {
                    viewTreeObserver.removeOnDrawListener(drawListener)
                }
            }
        }
        return completed
    }

    private fun captureContrastSamples(
        scenario: ActivityScenario<MainActivity>,
        expectedWebView: WebView,
    ): ContrastSamples {
        val bitmap = AtomicReference<Bitmap>()
        val copyResult = AtomicReference<Int>()
        val copied = CountDownLatch(1)
        scenario.onActivity { activity ->
            val webView = activity.browserControllerForTesting().selectedWebViewForTesting()
            check(webView === expectedWebView)
            val location = IntArray(2).also(webView::getLocationInWindow)
            val source = Rect(
                location[0],
                location[1],
                location[0] + webView.width,
                location[1] + webView.height,
            )
            val destination = Bitmap.createBitmap(
                webView.width,
                webView.height,
                Bitmap.Config.ARGB_8888,
            )
            bitmap.set(destination)
            PixelCopy.request(
                activity.window,
                source,
                destination,
                { result ->
                    copyResult.set(result)
                    copied.countDown()
                },
                Handler(Looper.getMainLooper()),
            )
        }

        assertTrue(
            "Timed out while capturing website contrast",
            copied.await(APPEARANCE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
        )
        val captured = requireNotNull(bitmap.get())
        val result = requireNotNull(copyResult.get())
        if (result != PixelCopy.SUCCESS) {
            captured.recycle()
            throw AssertionError("PixelCopy failed with result $result")
        }

        val textColors = captured.sampleColors(
            left = captured.width / 16,
            top = captured.height / 3,
            right = captured.width * 7 / 16,
            bottom = captured.height * 2 / 3,
        )
        val backgroundColors = captured.sampleColors(
            left = captured.width * 9 / 16,
            top = captured.height / 3,
            right = captured.width * 15 / 16,
            bottom = captured.height * 2 / 3,
        )
        val backgroundColor = Color.rgb(
            backgroundColors.medianChannel { color -> Color.red(color) },
            backgroundColors.medianChannel { color -> Color.green(color) },
            backgroundColors.medianChannel { color -> Color.blue(color) },
        )
        val samples = ContrastSamples(
            contrastingTextCount = textColors.count { color ->
                color.channelDelta(backgroundColor) >= MINIMUM_CONTRAST_CHANNEL_DELTA
            },
            textCount = textColors.size,
            consistentBackgroundCount = backgroundColors.count { color ->
                color.channelDelta(backgroundColor) <= MAXIMUM_BACKGROUND_CHANNEL_DELTA
            },
            backgroundCount = backgroundColors.size,
        )
        captured.recycle()
        return samples
    }

    private fun Bitmap.sampleColors(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): List<Int> = buildList {
        for (y in top until bottom step PIXEL_SAMPLE_STEP) {
            for (x in left until right step PIXEL_SAMPLE_STEP) {
                add(getPixel(x, y))
            }
        }
    }

    private fun List<Int>.medianChannel(channel: (Int) -> Int): Int =
        map(channel).sorted()[size / 2]

    private fun Int.channelDelta(other: Int): Int = maxOf(
        abs(Color.red(this) - Color.red(other)),
        abs(Color.green(this) - Color.green(other)),
        abs(Color.blue(this) - Color.blue(other)),
    )

    private companion object {
        const val APPEARANCE_TIMEOUT_MILLIS = 5_000L
        const val MAXIMUM_BACKGROUND_CHANNEL_DELTA = 16
        const val MINIMUM_CONTRAST_CHANNEL_DELTA = 96
        const val MINIMUM_CONTRASTING_TEXT_PERCENT = 10
        const val MINIMUM_UNIFORM_BACKGROUND_PERCENT = 95
        const val PIXEL_SAMPLE_STEP = 8
        const val POLL_INTERVAL_MILLIS = 50L
        const val CONTRAST_PROBE_ID_PLACEHOLDER = "{{probe_id}}"
        const val CONTRAST_PROBE_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                html, body {
                    margin: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: transparent;
                }
                body {
                    color: #000;
                    font: 900 48px/1 sans-serif;
                }
                p {
                    margin: 0;
                    width: 45%;
                    position: fixed;
                    top: 33%;
                }
            </style>
            <body data-probe="{{probe_id}}">
                <p>MMMM<br>MMMM<br>MMMM</p>
            </body>
        """
        const val THEME_PROBE_SCRIPT =
            "getComputedStyle(document.documentElement).getPropertyValue('--theme').trim()"
        const val THEME_PROBE_HTML = """
            <!doctype html>
            <style>
                :root { --theme: light; }
                @media (prefers-color-scheme: dark) { :root { --theme: dark; } }
            </style>
        """
    }

    private data class WebsiteAppearanceCase(
        val id: String,
        val label: String,
        val appearanceMode: BrowserAppearanceMode,
        val forceDarkWebsites: Boolean,
    )

    private data class ContrastSamples(
        val contrastingTextCount: Int,
        val textCount: Int,
        val consistentBackgroundCount: Int,
        val backgroundCount: Int,
    )

    private data class ContrastProbe(
        val id: String,
        val expectedUrl: String,
        val webView: WebView,
        val committed: CountDownLatch,
    )
}
