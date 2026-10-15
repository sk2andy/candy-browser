package dev.sk2andy.materialbrowser.browser.sitewidget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SiteWidgetCaptureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = BrowserSessionStore(context)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    fun setUp() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        store.saveStartupAnimationEnabled(false)
        store.saveOpenHomeOnStartupEnabled(false)
        store.saveSearchSuggestionProvider(SearchSuggestionProvider.None)
        store.saveRecallEnabled(false)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun geckoPageCapturePreservesSourceAndViewportAndExpiresAfterNavigation() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        verifyPageCapture(AndroidBrowserEngineKind.GeckoView)
    }

    @Test
    fun systemWebViewPageCapturePreservesSourceAndViewportAndExpiresAfterNavigation() {
        verifyPageCapture(AndroidBrowserEngineKind.SystemWebView)
    }

    @Test
    fun privateGeckoPageRejectsWidgetCapture() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        verifyPrivateCapture(AndroidBrowserEngineKind.GeckoView)
    }

    @Test
    fun privateSystemWebViewPageRejectsWidgetCapture() {
        verifyPrivateCapture(AndroidBrowserEngineKind.SystemWebView)
    }

    private fun verifyPageCapture(engine: AndroidBrowserEngineKind) {
        assertTrue(store.saveAndroidBrowserEngineKind(engine))
        fixtureServer().use { server ->
            launchBrowser().use { scenario ->
                val url = server.fixtureUrl(PAGE_PATH)
                scenario.onActivity { it.browserControllerForTesting().submitAddress(url) }
                awaitPage(scenario, PAGE_TITLE)
                val capture = awaitPaintedCapture(scenario)
                try {
                    assertFalse(capture.bitmap.isRecycled)
                    assertTrue(capture.bitmap.width in 1..1_280)
                    assertTrue(capture.bitmap.height > 0)
                    assertFalse(capture.source.isPrivate)
                    assertEquals(url, capture.source.url)
                    assertEquals(PAGE_TITLE, capture.source.title)
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        val engineView = requireNotNull(controller.selectedBrowserEngineViewForTesting())
                        val view = nativeGeckoView(engineView) ?: engineView
                        assertEquals(controller.selectedTabId, capture.source.tabId)
                        assertEquals(controller.activeProfileId, capture.source.profileId)
                        assertTrue(controller.isCurrentSiteWidgetSource(capture.source))
                        val expectedHeight = capture.bitmap.width.toDouble() * view.height / view.width
                        assertTrue(
                            "Widget capture must preserve the full viewport aspect ratio",
                            abs(capture.bitmap.height - expectedHeight) <= 1.0,
                        )
                        controller.submitAddress(server.fixtureUrl(NEXT_PATH))
                    }
                    awaitPage(scenario, NEXT_TITLE)
                    scenario.onActivity { activity ->
                        assertFalse(
                            "A previous document must not remain eligible for widget persistence",
                            activity.browserControllerForTesting().isCurrentSiteWidgetSource(capture.source),
                        )
                    }
                } finally {
                    capture.bitmap.recycle()
                }
            }
        }
    }

    private fun verifyPrivateCapture(engine: AndroidBrowserEngineKind) {
        assertTrue(store.saveAndroidBrowserEngineKind(engine))
        fixtureServer().use { server ->
            launchBrowser().use { scenario ->
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().createTab(
                        initialUrl = server.fixtureUrl(PAGE_PATH),
                        isIncognito = true,
                    )
                }
                awaitPage(scenario, PAGE_TITLE)
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertTrue(controller.selectedTab.isIncognito)
                    var completions = 0
                    val handle = controller.captureSelectedPageForSiteWidget { capture ->
                        completions++
                        assertEquals(Looper.getMainLooper(), Looper.myLooper())
                        assertNull(capture)
                    }
                    assertNull(handle)
                    assertEquals(1, completions)
                }
            }
        }
    }

    private fun awaitPaintedCapture(scenario: ActivityScenario<MainActivity>): SiteWidgetCapture {
        val deadline = SystemClock.elapsedRealtime() + PAGE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val capture = requestCapture(scenario)
            if (capture != null) {
                val center = capture.bitmap.getPixel(capture.bitmap.width / 2, capture.bitmap.height / 2)
                if (
                    abs(Color.red(center) - 0) <= 3 &&
                    abs(Color.green(center) - 127) <= 3 &&
                    abs(Color.blue(center) - 153) <= 3
                ) return capture
                capture.bitmap.recycle()
            }
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError("Widget capture did not contain the fixture's painted page")
    }

    private fun requestCapture(scenario: ActivityScenario<MainActivity>): SiteWidgetCapture? {
        val completed = CountDownLatch(1)
        val result = AtomicReference<SiteWidgetCapture?>()
        val completions = AtomicInteger()
        val completedOnMain = AtomicBoolean(false)
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().captureSelectedPageForSiteWidget { capture ->
                result.set(capture)
                completions.incrementAndGet()
                completedOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                completed.countDown()
            }
        }
        assertTrue("Widget capture did not complete", completed.await(CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        assertEquals(1, completions.get())
        assertTrue("Widget capture must complete on the main thread", completedOnMain.get())
        return result.get()
    }

    private fun awaitPage(scenario: ActivityScenario<MainActivity>, title: String) {
        val deadline = SystemClock.elapsedRealtime() + PAGE_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val view = controller.selectedBrowserEngineViewForTesting()
                ready = controller.selectedTab.title == title && !controller.selectedTab.isLoading &&
                    view != null && view.isAttachedToWindow && view.isShown &&
                    view.width > 0 && view.height > 0
            }
            if (ready) {
                instrumentation.waitForIdleSync()
                return
            }
            SystemClock.sleep(POLL_MS)
        }
        throw AssertionError("Widget fixture did not load: $title")
    }

    private fun launchBrowser(): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
    )

    private fun nativeGeckoView(view: View): GeckoView? = when (view) {
        is GeckoView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { index ->
            nativeGeckoView(view.getChildAt(index))
        }
        else -> null
    }

    private fun fixtureServer() = EdgeToEdgeSiteFixtureServer { path ->
        val title = if (path.substringBefore('?') == NEXT_PATH) NEXT_TITLE else PAGE_TITLE
        """
            <!doctype html>
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta name="color-scheme" content="light only">
            <title>$title</title>
            <style>html,body { margin:0; min-height:100vh; background:#007f99; color-scheme:light; }</style>
            </head><body></body></html>
        """.trimIndent()
    }

    private companion object {
        const val PAGE_PATH = "/widget-page"
        const val NEXT_PATH = "/widget-next"
        const val PAGE_TITLE = "Candy widget capture"
        const val NEXT_TITLE = "Candy widget next document"
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.TEST_SITE_WIDGET_CAPTURE"
        const val PAGE_TIMEOUT_MS = 20_000L
        const val POLL_MS = 50L
        const val CAPTURE_TIMEOUT_SECONDS = 6L
    }
}
