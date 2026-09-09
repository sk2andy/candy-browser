package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
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
    fun selectedGeckoViewProtectsFixedHeadersAndUsesDirectSurfaceBackend() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            awaitViewReady(scenario)
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = requireNotNull(controller.selectedGeckoViewForTesting())
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
        }
    }

    private fun awaitViewReady(scenario: ActivityScenario<MainActivity>) {
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
                            view.hasSurfaceView()
                    } == true
            }
            if (ready) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Gecko SurfaceView did not become ready", false)
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
        const val TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 50L
    }
}
