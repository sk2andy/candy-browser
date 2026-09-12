package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteMatrix
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoEdgeToEdgeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store by lazy { BrowserSessionStore(context) }
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        val tab = BrowserTab(
            id = "gecko-edge-to-edge-fixture",
            lastAccessedAt = System.currentTimeMillis(),
            url = TEST_URL,
        )
        assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun selectedGeckoViewAndSurfaceStayEdgeToEdgeAfterInsetDispatch() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitViewReady(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.onWindowInsetsChanged(
                    WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.statusBars(),
                            Insets.of(0, STATUS_BAR_INSET_PX, 0, 0),
                        )
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, 0, NAVIGATION_BAR_INSET_PX),
                        )
                        .build(),
                )
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = requireNotNull(controller.selectedGeckoViewForTesting())
                assertMargins(view, left = 0, top = 0, right = 0, bottom = 0)
                assertWindowTop(view, expectedTop = 0)
                assertWindowBottom(view, expectedBottom = activity.window.decorView.height)
                assertScreenTop(view, expectedTop = 0)
                assertMargins(
                    view.engineView(),
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = 0,
                )
                assertWindowTop(view.engineView(), expectedTop = 0)
                assertWindowBottom(
                    view.engineView(),
                    expectedBottom = activity.window.decorView.height,
                )
                assertScreenTop(view.engineView(), expectedTop = 0)
                val surfaceView = requireNotNull(view.findSurfaceView())
                assertWindowTop(surfaceView, expectedTop = 0)
                assertWindowBottom(surfaceView, expectedBottom = activity.window.decorView.height)
                assertScreenTop(surfaceView, expectedTop = 0)
                assertTrue(
                    "GeckoView must use SurfaceView to avoid copying every page frame",
                    view.hasSurfaceView(),
                )
            }
        }
    }

    @Test
    fun frostedGeckoSwitchesToCaptureCompatibleTextureViewAcrossNavigationBar() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitViewReady(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                controller.updateAppearanceSettings(
                    AppearanceSettings(
                        surfaceStyle = BrowserSurfaceStyle.Frosted,
                        frostedTransparencyPercent = 0,
                        frostedAddressBarTransparencyPercent = 50,
                    ),
                )
                controller.onWindowInsetsChanged(
                    WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.statusBars(),
                            Insets.of(0, STATUS_BAR_INSET_PX, 0, 0),
                        )
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, 0, NAVIGATION_BAR_INSET_PX),
                        )
                        .build(),
                )
            }
            awaitViewReady(scenario, expectBackdropCapture = true)
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val view = requireNotNull(
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                val textureView = requireNotNull(view.findTextureView())
                assertTrue(
                    "Frosted GeckoView must avoid a separate compositor surface",
                    !view.hasSurfaceView(),
                )
                assertWindowTop(textureView, expectedTop = 0)
                assertWindowBottom(textureView, expectedBottom = activity.window.decorView.height)
                activity.browserControllerForTesting().updateAppearanceSettings(
                    AppearanceSettings(),
                )
            }
            awaitViewReady(scenario)

            scenario.onActivity { activity ->
                val view = requireNotNull(
                    activity.browserControllerForTesting().selectedGeckoViewForTesting(),
                )
                assertTrue(
                    "Non-frosted GeckoView must restore the direct compositor surface",
                    view.hasSurfaceView(),
                )
            }
        }
    }

    @Test
    fun forcedSafeAreaUsesInnerNativeMarginsWithoutShrinkingOuterHost() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitViewReady(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = requireNotNull(controller.selectedGeckoViewForTesting())
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, true))
                controller.onWindowInsetsChanged(
                    WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.statusBars(),
                            Insets.of(0, STATUS_BAR_INSET_PX, 0, 0),
                        )
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, 0, NAVIGATION_BAR_INSET_PX),
                        )
                        .build(),
                )

                assertMargins(view, left = 0, top = 0, right = 0, bottom = 0)
                assertWindowTop(view, expectedTop = 0)
                assertMargins(
                    view.engineView(),
                    left = 0,
                    top = STATUS_BAR_INSET_PX,
                    right = 0,
                    bottom = NAVIGATION_BAR_INSET_PX,
                )
            }

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertTrue(controller.setForceSafeArea(controller.selectedTabId, false))
                controller.onWindowInsetsChanged(
                    WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.statusBars(),
                            Insets.of(0, STATUS_BAR_INSET_PX, 0, 0),
                        )
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, 0, NAVIGATION_BAR_INSET_PX),
                        )
                        .build(),
                )
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(LAYOUT_STABILITY_WINDOW_MILLIS)

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = requireNotNull(controller.selectedGeckoViewForTesting())
                assertEquals(0, controller.previewTopInsetPx(controller.selectedTabId))
                assertMargins(view.engineView(), left = 0, top = 0, right = 0, bottom = 0)
                assertWindowTop(view.engineView(), expectedTop = 0)
                assertWindowBottom(
                    view.engineView(),
                    expectedBottom = activity.window.decorView.height,
                )
            }
        }
    }

    @Test
    fun requestedSiteLayoutsStaySafeWithoutLeavingEdgeToEdge() {
        EdgeToEdgeSiteFixtureServer().use { server ->
            val tab = BrowserTab(
                id = "gecko-site-matrix-safe-area-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.url,
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))

            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                awaitViewReady(scenario)
                EdgeToEdgeSiteMatrix.allSites.forEachIndexed { index, site ->
                    if (index > 0) {
                        scenario.onActivity { activity ->
                            assertTrue(
                                activity.browserControllerForTesting()
                                    .openUrl(server.siteUrl(site)),
                            )
                        }
                    }
                    awaitSelectedTabTitle(scenario, EdgeToEdgeSiteMatrix.readyTitle(site))
                    val documentRequestsBeforeScroll = server.documentRequestCount.get()
                    scenario.onActivity { activity ->
                        assertTrue(
                            activity.browserControllerForTesting()
                                .scrollSelectedBrowserEngineToVerticalOffset(SCROLL_OFFSET_PX),
                        )
                        assertTrue(
                            activity.browserControllerForTesting()
                                .scrollSelectedBrowserEngineToVerticalOffset(0),
                        )
                    }
                    SystemClock.sleep(LAYOUT_STABILITY_WINDOW_MILLIS)

                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        val view = requireNotNull(controller.selectedGeckoViewForTesting())
                        assertEquals(0, controller.previewTopInsetPx(controller.selectedTabId))
                        assertMargins(view, left = 0, top = 0, right = 0, bottom = 0)
                        assertMargins(
                            view.engineView(),
                            left = 0,
                            top = 0,
                            right = 0,
                            bottom = 0,
                        )
                        assertWindowTop(view, expectedTop = 0)
                        assertWindowTop(view.engineView(), expectedTop = 0)
                        assertWindowBottom(view, expectedBottom = activity.window.decorView.height)
                        assertWindowBottom(
                            view.engineView(),
                            expectedBottom = activity.window.decorView.height,
                        )
                        assertEquals(
                            EdgeToEdgeSiteMatrix.readyTitle(site),
                            controller.selectedTabForTesting().title,
                        )
                    }
                    assertEquals(
                        documentRequestsBeforeScroll,
                        server.documentRequestCount.get(),
                    )
                }
            }
        }
    }

    @Test
    fun youtubeAndGoogleTouchFocusKeepSearchBelowStatusBarWhileImeResizes() {
        EdgeToEdgeSiteFixtureServer().use { server ->
            val tab = BrowserTab(
                id = "gecko-focused-search-safe-area-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.url,
            )
            assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))

            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                awaitViewReady(scenario)
                awaitSelectedTabTitle(
                    scenario,
                    EdgeToEdgeSiteMatrix.readyTitle(EdgeToEdgeSiteMatrix.allSites.first()),
                )

                EdgeToEdgeSiteMatrix.focusedSearchSites
                    .filter { site -> site.name == "YouTube" || site.name == "Google" }
                    .forEach { site ->
                    scenario.onActivity { activity ->
                        assertTrue(
                            activity.browserControllerForTesting()
                                .openUrl("${server.siteUrl(site)}#${site.name}"),
                        )
                    }
                    awaitSelectedTabTitle(
                        scenario,
                        "Candy focused search ready: ${site.name}",
                    )
                    tapSearchField(scenario)
                    awaitSelectedTabTitle(scenario, "Candy focused search safe: ${site.name}")
                    awaitImeVisibility(scenario, expectedVisible = true)
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        val view = requireNotNull(controller.selectedGeckoViewForTesting())
                        assertWindowTop(
                            view.engineView(),
                            controller.previewTopInsetPx(controller.selectedTabId),
                        )
                        WindowCompat.getInsetsController(
                            activity.window,
                            activity.window.decorView,
                        ).hide(WindowInsetsCompat.Type.ime())
                    }
                    awaitImeVisibility(scenario, expectedVisible = false)
                }
            }
        }
    }

    private fun awaitViewReady(
        scenario: ActivityScenario<MainActivity>,
        expectBackdropCapture: Boolean = false,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.browserControllerForTesting()
                    .selectedGeckoViewForTesting()
                    ?.let { view ->
                        view.isAttachedToWindow &&
                            view.width > 0 &&
                            view.height > 0 &&
                            if (expectBackdropCapture) {
                                view.findTextureView()?.isAvailable == true
                            } else {
                                view.hasSurfaceView()
                            }
                    } == true
            }
            if (ready) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(
            if (expectBackdropCapture) {
                "Gecko TextureView did not become ready"
            } else {
                "Gecko SurfaceView did not become ready"
            },
            false,
        )
    }

    private fun awaitSelectedTabTitle(
        scenario: ActivityScenario<MainActivity>,
        expectedTitle: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var lastTitle = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var title = ""
            scenario.onActivity { activity ->
                title = activity.browserControllerForTesting().selectedTabForTesting().title
            }
            if (title == expectedTitle) return
            lastTitle = title
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(
            "Selected Gecko tab did not reach title $expectedTitle; last title was $lastTitle",
            false,
        )
    }

    private fun tapSearchField(scenario: ActivityScenario<MainActivity>) {
        val coordinates = FloatArray(2)
        scenario.onActivity { activity ->
            val engineView = requireNotNull(
                activity.browserControllerForTesting().selectedGeckoViewForTesting(),
            ).engineView()
            val location = IntArray(2)
            engineView.getLocationOnScreen(location)
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
        val downTime = SystemClock.uptimeMillis()
        injectTouch(
            action = MotionEvent.ACTION_DOWN,
            downTime = downTime,
            eventTime = downTime,
            x = coordinates[0],
            y = coordinates[1],
        )
        injectTouch(
            action = MotionEvent.ACTION_UP,
            downTime = downTime,
            eventTime = SystemClock.uptimeMillis(),
            x = coordinates[0],
            y = coordinates[1],
        )
        instrumentation.waitForIdleSync()
    }

    private fun injectTouch(
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
    ) {
        MotionEvent.obtain(downTime, eventTime, action, x, y, 0).also { event ->
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(
                    "Input injection failed for ${MotionEvent.actionToString(action)}",
                    instrumentation.uiAutomation.injectInputEvent(event, true),
                )
            } finally {
                event.recycle()
            }
        }
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

    private fun View.hasSurfaceView(): Boolean =
        findSurfaceView() != null

    private fun View.findSurfaceView(): SurfaceView? = when (this) {
        is SurfaceView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findSurfaceView()
        }
        else -> null
    }

    private fun View.findTextureView(): TextureView? = when (this) {
        is TextureView -> this
        is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { index ->
            getChildAt(index).findTextureView()
        }
        else -> null
    }

    private fun View.engineView(): View = (this as ViewGroup).getChildAt(0)

    private fun assertMargins(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val margins = view.layoutParams as ViewGroup.MarginLayoutParams
        assertEquals(left, margins.leftMargin)
        assertEquals(top, margins.topMargin)
        assertEquals(right, margins.rightMargin)
        assertEquals(bottom, margins.bottomMargin)
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

    private fun assertScreenTop(view: View, expectedTop: Int) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        assertEquals(expectedTop, location[1])
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.GECKO_EDGE_TO_EDGE"
        const val TEST_URL = "https://example.invalid/candy-edge-to-edge"
        const val STATUS_BAR_INSET_PX = 96
        const val NAVIGATION_BAR_INSET_PX = 48
        const val SCROLL_OFFSET_PX = 600
        const val LAYOUT_STABILITY_WINDOW_MILLIS = 1_600L
        const val SEARCH_TAP_X_CSS_PX = 100f
        const val SEARCH_TAP_Y_CSS_PX = 16f
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
    }
}
