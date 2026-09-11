package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteMatrix
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemWebViewEdgeToEdgeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store by lazy { BrowserSessionStore(context) }
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private lateinit var server: EdgeToEdgeSiteFixtureServer

    @Before
    fun setUp() {
        server = EdgeToEdgeSiteFixtureServer()
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        val tab = BrowserTab(
            id = "system-webview-edge-to-edge-fixture",
            lastAccessedAt = System.currentTimeMillis(),
            url = server.url,
        )
        assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        server.close()
    }

    @Test
    fun requestedSiteLayoutsAndFocusedSearchesStaySafeAndEdgeToEdge() {
        ActivityScenario.launch<MainActivity>(testIntent()).use { scenario ->
            val webView = awaitViewReady(scenario)
            EdgeToEdgeSiteMatrix.allSites.forEachIndexed { index, site ->
                if (index > 0) {
                    scenario.onActivity { activity ->
                        assertTrue(
                            activity.browserControllerForTesting().openUrl(server.siteUrl(site)),
                        )
                    }
                }
                awaitWebViewTitle(scenario, EdgeToEdgeSiteMatrix.readyTitle(site))
                val documentRequests = server.documentRequestCount.get()
                val result = evaluate(
                    scenario,
                    "JSON.stringify(globalThis.__candySiteMatrix.results[0])",
                )
                assertTrue(
                    "System WebView profile ${site.name} failed: $result",
                    evaluateBoolean(scenario, "globalThis.__candySiteMatrix.passed"),
                )
                assertTrue(
                    "${site.name} must use exactly one stable safe-area mode",
                    evaluateBoolean(
                        scenario,
                        "globalThis.__candySiteMatrix.results[0].protectionModeValid",
                    ),
                )
                scenario.onActivity { activity -> assertWebViewGeometry(activity, webView) }
                SystemClock.sleep(LAYOUT_STABILITY_WINDOW_MILLIS)
                scenario.onActivity { activity -> assertWebViewGeometry(activity, webView) }
                assertTrue(
                    "${site.name} became unsafe after delayed top-inset verification: $result",
                    evaluateBoolean(scenario, "globalThis.__candySiteMatrix.passed"),
                )
                assertEquals(
                    "Scrolling ${site.name} reloaded the current page",
                    documentRequests,
                    server.documentRequestCount.get(),
                )
            }
        }
    }

    @Test
    fun coverAwarePageUsesExactlyOneSupportedSafeAreaMode() {
        ActivityScenario.launch<MainActivity>(testIntent()).use { scenario ->
            val webView = awaitViewReady(scenario)
            val instagram = EdgeToEdgeSiteMatrix.requestedSites.first { site ->
                site.name == "Instagram"
            }
            scenario.onActivity { activity ->
                assertTrue(activity.browserControllerForTesting().openUrl(server.siteUrl(instagram)))
            }
            awaitWebViewTitle(scenario, EdgeToEdgeSiteMatrix.readyTitle(instagram))

            val engineSafeAreaApplied = evaluateNumber(
                scenario,
                "globalThis.__candySiteMatrix.results.find(" +
                    "result => result.name === 'Instagram').safeAreaPaddingTop",
            ) > CSS_TOLERANCE
            val candyCompatibilityApplied = evaluateBoolean(
                scenario,
                "globalThis.__candySiteMatrix.results.find(" +
                    "result => result.name === 'Instagram').candyCompatibilityApplied",
            )
            assertTrue(
                "viewport-fit=cover must use either supported engine CSS insets or Candy",
                engineSafeAreaApplied.xor(candyCompatibilityApplied),
            )
            scenario.onActivity { activity -> assertWebViewGeometry(activity, webView) }
        }
    }

    @Test
    fun youtubeAndGoogleTouchFocusKeepSearchBelowStatusBarWhileImeResizes() {
        ActivityScenario.launch<MainActivity>(testIntent()).use { scenario ->
            awaitViewReady(scenario)

            EdgeToEdgeSiteMatrix.focusedSearchSites
                .filter { site -> site.name == "YouTube" || site.name == "Google" }
                .forEach { site ->
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.browserControllerForTesting()
                            .openUrl("${server.siteUrl(site)}#${site.name}"),
                    )
                }
                awaitWebViewTitle(scenario, "Candy focused search ready: ${site.name}")
                tapSearchField(scenario)
                awaitWebViewTitle(scenario, "Candy focused search safe: ${site.name}")
                awaitImeVisibility(scenario, expectedVisible = true)
                scenario.onActivity { activity ->
                    val webView = requireNotNull(
                        activity.browserControllerForTesting()
                            .selectedBrowserEngineViewForTesting()
                            ?.findSystemWebView(),
                    )
                    assertWebViewGeometry(activity, webView)
                    WindowCompat.getInsetsController(
                        activity.window,
                        activity.window.decorView,
                    ).hide(WindowInsetsCompat.Type.ime())
                }
                awaitImeVisibility(scenario, expectedVisible = false)
            }
        }
    }

    @Test
    fun forcedSafeAreaMovesSystemWebViewBottomIntoNativeSafeFrame() {
        ActivityScenario.launch<MainActivity>(testIntent()).use { scenario ->
            awaitViewReady(scenario)
            val instagram = EdgeToEdgeSiteMatrix.requestedSites.first { site ->
                site.name == "Instagram"
            }
            scenario.onActivity { activity ->
                assertTrue(activity.browserControllerForTesting().openUrl(server.siteUrl(instagram)))
            }
            awaitWebViewTitle(scenario, EdgeToEdgeSiteMatrix.readyTitle(instagram))
            var safeBottom = 0
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                safeBottom = requireNotNull(
                    ViewCompat.getRootWindowInsets(activity.window.decorView),
                ).getInsets(
                    WindowInsetsCompat.Type.statusBars() or
                        WindowInsetsCompat.Type.navigationBars() or
                        WindowInsetsCompat.Type.displayCutout(),
                ).bottom
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, true))
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(INSET_PROPAGATION_MILLIS)

            scenario.onActivity { activity ->
                val webView = requireNotNull(
                    activity.browserControllerForTesting()
                        .selectedBrowserEngineViewForTesting()
                        ?.findSystemWebView(),
                )
                val margins = webView.layoutParams as ViewGroup.MarginLayoutParams
                assertTrue("Expected a non-zero bottom safe area", safeBottom > 0)
                assertEquals(safeBottom, margins.bottomMargin)
                assertWindowBottom(webView, activity.window.decorView.height - safeBottom)
            }
            assertEquals(
                "Native safe-area ownership must clear the renderer CSS inset",
                0.0,
                evaluateNumber(
                    scenario,
                    "Number.parseFloat(" +
                        "getComputedStyle(document.querySelector('#header')).paddingTop)",
                ),
                CSS_TOLERANCE,
            )

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, false))
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(INSET_PROPAGATION_MILLIS)

            scenario.onActivity { activity ->
                val webView = requireNotNull(
                    activity.browserControllerForTesting()
                        .selectedBrowserEngineViewForTesting()
                        ?.findSystemWebView(),
                )
                assertWebViewGeometry(activity, webView)
            }
            assertEquals(
                "Disabling Force safe area must keep renderer top ownership cleared",
                0.0,
                evaluateNumber(
                    scenario,
                    "Number.parseFloat(" +
                        "getComputedStyle(document.querySelector('#header')).paddingTop)",
                ),
                CSS_TOLERANCE,
            )
            assertTrue(
                "Disabling Force safe area must restore Candy document top protection",
                evaluateNumber(
                    scenario,
                    "Number.parseFloat(document.documentElement.style.getPropertyValue(" +
                        "'--candy-browser-content-top-inset'))",
                ) > 0,
            )
        }
    }

    private fun testIntent(): Intent =
        Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION)

    private fun assertWebViewGeometry(
        activity: MainActivity,
        webView: android.webkit.WebView,
    ) {
        val view = requireNotNull(
            activity.browserControllerForTesting().selectedBrowserEngineViewForTesting(),
        )
        val controller = activity.browserControllerForTesting()
        assertEquals(0, controller.previewTopInsetPx(controller.selectedTabId))
        assertWindowTop(view, 0)
        assertWindowTop(webView, 0)
        assertWindowBottom(view, activity.window.decorView.height)
        assertWindowBottom(webView, activity.window.decorView.height)
        assertEquals(
            0,
            (webView.layoutParams as ViewGroup.MarginLayoutParams).topMargin,
        )
    }

    private fun awaitViewReady(scenario: ActivityScenario<MainActivity>): android.webkit.WebView {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var lastTitle: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var readyView: android.webkit.WebView? = null
            scenario.onActivity { activity ->
                readyView = activity.browserControllerForTesting()
                    .selectedBrowserEngineViewForTesting()
                    ?.findSystemWebView()
                    ?.takeIf { webView ->
                        lastTitle = webView.title
                        webView.isAttachedToWindow &&
                            webView.width > 0 &&
                            webView.height > 0 &&
                            webView.title == EdgeToEdgeSiteMatrix.readyTitle(
                                EdgeToEdgeSiteMatrix.allSites.first(),
                            )
                    }
            }
            readyView?.let { return it }
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("System WebView site matrix did not become ready; title=$lastTitle", false)
        error("unreachable")
    }

    private fun awaitWebViewTitle(
        scenario: ActivityScenario<MainActivity>,
        expectedTitle: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var lastTitle: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                lastTitle = activity.browserControllerForTesting()
                    .selectedBrowserEngineViewForTesting()
                    ?.findSystemWebView()
                    ?.title
            }
            if (lastTitle == expectedTitle) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Expected title=$expectedTitle; last title=$lastTitle", false)
    }

    private fun tapSearchField(scenario: ActivityScenario<MainActivity>) {
        val coordinates = FloatArray(2)
        scenario.onActivity { activity ->
            val webView = requireNotNull(
                activity.browserControllerForTesting()
                    .selectedBrowserEngineViewForTesting()
                    ?.findSystemWebView(),
            )
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            val density = activity.resources.displayMetrics.density
            val safeTop = requireNotNull(
                ViewCompat.getRootWindowInsets(activity.window.decorView),
            ).getInsets(
                WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            ).top
            coordinates[0] = location[0] + SEARCH_TAP_X_CSS_PX * density
            coordinates[1] = location[1] + safeTop + SEARCH_TAP_Y_CSS_PX * density
        }
        assertTrue(
            UiDevice.getInstance(instrumentation).click(
                coordinates[0].toInt(),
                coordinates[1].toInt(),
            ),
        )
    }

    private fun awaitImeVisibility(
        scenario: ActivityScenario<MainActivity>,
        expectedVisible: Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var visible = false
            scenario.onActivity { activity ->
                visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
            if (visible == expectedVisible) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Expected IME visible=$expectedVisible", false)
    }

    private fun View.findSystemWebView(): android.webkit.WebView? = when (this) {
        is android.webkit.WebView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findSystemWebView()
        }
        else -> null
    }

    private fun assertWindowTop(view: View, expectedTop: Int) {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        assertEquals(expectedTop, location[1])
    }

    private fun assertWindowBottom(view: View, expectedBottom: Int) {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        assertEquals(expectedBottom, location[1] + view.height)
    }

    private fun evaluateBoolean(
        scenario: ActivityScenario<MainActivity>,
        expression: String,
    ): Boolean = evaluate(scenario, expression).toBooleanStrict()

    private fun evaluateNumber(
        scenario: ActivityScenario<MainActivity>,
        expression: String,
    ): Double = evaluate(scenario, expression).toDouble()

    private fun evaluate(
        scenario: ActivityScenario<MainActivity>,
        expression: String,
    ): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        scenario.onActivity { activity ->
            val webView = requireNotNull(
                activity.browserControllerForTesting()
                    .selectedBrowserEngineViewForTesting()
                    ?.findSystemWebView(),
            )
            webView.evaluateJavascript(expression) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue(
            "JavaScript result timed out for $expression",
            completed.await(5, TimeUnit.SECONDS),
        )
        return requireNotNull(result.get())
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION =
            "dev.sk2andy.materialbrowser.test.SYSTEM_WEBVIEW_EDGE_TO_EDGE"
        const val CSS_TOLERANCE = 1.0
        const val SEARCH_TAP_X_CSS_PX = 100f
        const val SEARCH_TAP_Y_CSS_PX = 16f
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 50L
        const val LAYOUT_STABILITY_WINDOW_MILLIS = 600L
        const val INSET_PROPAGATION_MILLIS = 200L
    }
}
