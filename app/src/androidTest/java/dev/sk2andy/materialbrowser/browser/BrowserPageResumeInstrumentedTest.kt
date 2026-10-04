package dev.sk2andy.materialbrowser.browser

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

/** Exercises a live document across the long background interval reported in issue 258. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class BrowserPageResumeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store = BrowserSessionStore(context)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun geckoPageRemainsInteractiveAfterRepeatedLongBackgroundIntervals() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        verifyResume(AndroidBrowserEngineKind.GeckoView, isPrivate = false)
    }

    @Test
    fun privateGeckoPageRemainsInteractiveAfterRepeatedLongBackgroundIntervals() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        verifyResume(AndroidBrowserEngineKind.GeckoView, isPrivate = true)
    }

    @Test
    fun systemWebViewPageRemainsInteractiveAfterRepeatedLongBackgroundIntervals() {
        verifyResume(AndroidBrowserEngineKind.SystemWebView, isPrivate = false)
    }

    private fun verifyResume(engine: AndroidBrowserEngineKind, isPrivate: Boolean) {
        configureSession(engine)
        val requests = AtomicInteger()
        EdgeToEdgeSiteFixtureServer { path ->
            when (path.substringBefore('?')) {
                PAGE_PATH -> {
                    requests.incrementAndGet()
                    PAGE_HTML
                }
                WARM_PATH -> WARM_HTML
                NEXT_PATH -> NEXT_HTML
                else -> ""
            }
        }.use { server ->
            val url = server.fixtureUrl(PAGE_PATH)
            val intent = Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().createTab(
                        initialUrl = server.fixtureUrl(WARM_PATH),
                        isIncognito = isPrivate,
                    )
                }
                awaitReport(scenario) { it.getString("page") == "warm" }
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().submitAddress(url)
                }
                awaitReport(scenario) { it.getString("page") == "main" }
                awaitCondition("The main page did not retain its warm-up history entry") {
                    var canGoBack = false
                    scenario.onActivity { activity ->
                        canGoBack = activity.browserControllerForTesting().selectedTab.canGoBack
                    }
                    canGoBack
                }
                val originalView = awaitEngineView(scenario)
                val originalNativeSession = nativeGeckoView(originalView)?.session
                var originalTabId = ""
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    originalTabId = controller.selectedTabId
                    assertTrue("The fixture must have a history entry before backgrounding", controller.selectedTab.canGoBack)
                }
                clickPageControl(scenario, "click")
                awaitReport(scenario) { it.getInt("clicks") == 1 }
                val evidencePrefix = "${engine.stableId}-${if (isPrivate) "private" else "regular"}"
                assertPagePixelsAndSaveEvidence(scenario, YELLOW, "$evidencePrefix-before-background")
                scrollPage(scenario, previousOffset = 0)

                repeat(2) { cycle ->
                    val before = awaitReport(scenario) { it.getInt("clicks") == cycle + 1 }
                    val requestsBeforeBackground = requests.get()
                    scenario.moveToState(Lifecycle.State.CREATED)
                    val backgroundStarted = SystemClock.elapsedRealtime()
                    SystemClock.sleep(BACKGROUND_MILLIS)
                    assertTrue(
                        "The regression must keep the Activity stopped for at least 30 seconds",
                        SystemClock.elapsedRealtime() - backgroundStarted >= 30_000L,
                    )
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    val resumed = awaitReport(scenario) {
                        it.getInt("ticks") > before.getInt("ticks")
                    }
                    assertEquals("Backgrounding must preserve JavaScript state", cycle + 1, resumed.getInt("clicks"))
                    assertTrue(
                        "Backgrounding must preserve scroll position: before=$before, resumed=$resumed",
                        abs(before.getInt("scrollY") - resumed.getInt("scrollY")) <= 5,
                    )
                    assertEquals("Resume must not reload the document", requestsBeforeBackground, requests.get())
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        assertEquals(originalTabId, controller.selectedTabId)
                        assertEquals(isPrivate, controller.selectedTab.isIncognito)
                        assertTrue("Resume must retain the existing history entry", controller.selectedTab.canGoBack)
                        assertSame(originalView, controller.selectedBrowserEngineViewForTesting())
                        if (originalNativeSession != null) {
                            assertSame(originalNativeSession, nativeGeckoView(originalView)?.session)
                        }
                    }
                    val retainedColor = if (resumed.getInt("clicks") % 2 == 0) CYAN else YELLOW
                    assertPagePixelsAndSaveEvidence(
                        scenario,
                        retainedColor,
                        "$evidencePrefix-resume-${cycle + 1}-before-interaction",
                    )
                    if (engine == AndroidBrowserEngineKind.GeckoView) assertResumeCoverHidden(scenario)
                    scrollPage(scenario, previousOffset = resumed.getInt("scrollY"))
                    clickPageControl(scenario, "click")
                    val clicked = awaitReport(scenario) { it.getInt("clicks") == cycle + 2 }
                    val expectedColor = if (clicked.getInt("clicks") % 2 == 0) CYAN else YELLOW
                    assertPagePixelsAndSaveEvidence(
                        scenario,
                        expectedColor,
                        "$evidencePrefix-resume-${cycle + 1}",
                    )
                    assertEquals("Interaction after resume must not reload the document", requestsBeforeBackground, requests.get())
                    if (isPrivate) {
                        assertTrue(
                            "Private tab must remain outside persisted session state",
                            store.loadTabs().first.none { it.id == originalTabId || it.isIncognito },
                        )
                    }
                }

                val beforeNavigation = awaitReport(scenario) { it.getInt("clicks") == 3 }
                clickPageControl(scenario, "next")
                awaitReport(scenario) { it.getString("page") == "next" }
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(server.fixtureUrl(NEXT_PATH), controller.selectedTab.url)
                    assertTrue("Navigation must retain the first history entry", controller.selectedTab.canGoBack)
                    controller.goBack()
                }
                val returned = awaitReport(scenario) { it.getString("page") == "main" }
                assertTrue(
                    "Back must restore the original scroll position: before=$beforeNavigation, returned=$returned",
                    abs(beforeNavigation.getInt("scrollY") - returned.getInt("scrollY")) <= 5,
                )
                scenario.onActivity { activity ->
                    assertEquals(url, activity.browserControllerForTesting().selectedTab.url)
                }
                clickPageControl(scenario, "click")
                awaitReport(scenario) { it.getInt("clicks") == returned.getInt("clicks") + 1 }
            }
        }
    }

    private fun configureSession(engine: AndroidBrowserEngineKind) {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        store.saveStartupAnimationEnabled(false)
        assertTrue(store.saveAndroidBrowserEngineKind(engine))
    }

    private fun awaitEngineView(scenario: ActivityScenario<MainActivity>): View {
        var engineView: View? = null
        awaitCondition("Browser renderer did not attach") {
            scenario.onActivity { activity ->
                engineView = activity.browserControllerForTesting().selectedBrowserEngineViewForTesting()
                    ?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
            }
            engineView != null
        }
        return requireNotNull(engineView)
    }

    private fun clickPageControl(scenario: ActivityScenario<MainActivity>, control: String) {
        var previousCoordinates: PageControlCoordinates? = null
        var stableReports = 0
        awaitCondition("Page control coordinates did not settle: $control") {
            val report = awaitReport(scenario) { it.has(control) }.getJSONObject(control)
            var coordinates: PageControlCoordinates? = null
            scenario.onActivity { activity ->
                val outerView = requireNotNull(activity.browserControllerForTesting().selectedBrowserEngineViewForTesting())
                // Page CSS coordinates belong to Gecko's inset engine view, not Candy's outer host.
                val rendererView = nativeGeckoView(outerView) ?: outerView
                val location = IntArray(2)
                rendererView.getLocationOnScreen(location)
                val x = report.getInt("x")
                val y = report.getInt("y")
                if (
                    rendererView.isAttachedToWindow && rendererView.isShown &&
                    x in 0 until rendererView.width && y in 0 until rendererView.height
                ) {
                    coordinates = PageControlCoordinates(location[0] + x, location[1] + y)
                }
            }
            stableReports = if (coordinates != null && coordinates == previousCoordinates) stableReports + 1 else 0
            previousCoordinates = coordinates
            stableReports >= 3
        }
        val coordinates = requireNotNull(previousCoordinates)
        assertTrue(device.click(coordinates.x, coordinates.y))
    }

    private fun scrollPage(scenario: ActivityScenario<MainActivity>, previousOffset: Int) {
        val view = awaitEngineView(scenario)
        val location = IntArray(2)
        scenario.onActivity { view.getLocationOnScreen(location) }
        assertTrue(
            device.swipe(
                location[0] + view.width / 2,
                location[1] + view.height * 3 / 4,
                location[0] + view.width / 2,
                location[1] + view.height / 3,
                20,
            ),
        )
        awaitReport(scenario) { it.getInt("scrollY") > previousOffset + 100 }
        var previousObservedOffset = -1
        var stableReports = 0
        awaitCondition("Native scrolling did not settle before the next lifecycle transition") {
            val offset = awaitReport(scenario) { it.getString("page") == "main" }.getInt("scrollY")
            stableReports = if (offset == previousObservedOffset) stableReports + 1 else 0
            previousObservedOffset = offset
            stableReports >= 3
        }
    }

    private fun assertResumeCoverHidden(scenario: ActivityScenario<MainActivity>) {
        var coverVisibility = View.VISIBLE
        awaitCondition("Gecko resume cover must leave the live document visible before interaction") {
            scenario.onActivity { activity ->
                val view = requireNotNull(activity.browserControllerForTesting().selectedBrowserEngineViewForTesting())
                val nativeView = requireNotNull(nativeGeckoView(view))
                val cover = requireNotNull(
                    (0 until nativeView.childCount).map(nativeView::getChildAt).filterIsInstance<ImageView>().singleOrNull(),
                ) { "Gecko renderer must own exactly one resume-cover ImageView" }
                coverVisibility = cover.visibility
            }
            coverVisibility == View.GONE
        }
        assertEquals(View.GONE, coverVisibility)
    }

    private fun assertPagePixelsAndSaveEvidence(
        scenario: ActivityScenario<MainActivity>,
        expectedColor: Int,
        name: String,
    ) {
        var lastColor = Color.TRANSPARENT
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var viewState = ""
        var lastScreenshot: Bitmap? = null
        try {
            while (SystemClock.elapsedRealtime() < deadline) {
                lateinit var window: Window
                var x = 0
                var y = 0
                scenario.onActivity { activity ->
                    val view = requireNotNull(activity.browserControllerForTesting().selectedBrowserEngineViewForTesting())
                    val location = IntArray(2)
                    view.getLocationInWindow(location)
                    x = location[0] + view.width / 2
                    y = location[1] + view.height / 2
                    window = activity.window
                    viewState = rendererState(view)
                }
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot(window))
                lastScreenshot?.recycle()
                lastScreenshot = bitmap
                lastColor = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
                if (lastColor == expectedColor) {
                    saveEvidence(bitmap, name)
                    return
                }
                SystemClock.sleep(POLL_MILLIS)
            }
            lastScreenshot?.let { saveEvidence(it, name) }
            assertEquals("Resumed page did not render the JavaScript color change; $viewState", expectedColor, lastColor)
        } finally {
            lastScreenshot?.recycle()
        }
    }

    private fun saveEvidence(bitmap: Bitmap, name: String) {
        val directory = File(context.getExternalFilesDir(null), "issue258").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun rendererState(view: View): String = buildString {
        append("${view.javaClass.simpleName}(visible=${view.visibility}")
        if (view is ImageView) append(", image=${view.drawable != null}")
        append(")")
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) append(" ${rendererState(view.getChildAt(index))}")
        }
    }

    private fun awaitReport(
        scenario: ActivityScenario<MainActivity>,
        ready: (JSONObject) -> Boolean,
    ): JSONObject {
        var lastTitle = ""
        var report: JSONObject? = null
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                lastTitle = activity.browserControllerForTesting().selectedTab.title
            }
            report = runCatching { JSONObject(lastTitle) }.getOrNull()
            if (report?.let(ready) == true) return requireNotNull(report)
            SystemClock.sleep(POLL_MILLIS)
        }
        error("Resume fixture did not reach its expected state; last title: $lastTitle")
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue(message, condition())
    }

    private fun nativeGeckoView(view: View): GeckoView? = when (view) {
        is GeckoView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { index ->
            nativeGeckoView(view.getChildAt(index))
        }
        else -> null
    }

    private data class PageControlCoordinates(val x: Int, val y: Int)

    private companion object {
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.test.PAGE_RESUME"
        const val PAGE_PATH = "/site-matrix/resume"
        const val WARM_PATH = "/site-matrix/resume-warm"
        const val NEXT_PATH = "/site-matrix/resume-next"
        const val BACKGROUND_MILLIS = 31_000L
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 100L
        const val CYAN = 0xFF00BCD4.toInt()
        const val YELLOW = 0xFFFFC107.toInt()
        const val WARM_HTML = """
            <!doctype html><html><head><title>{"page":"warm"}</title>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            </head><body>Existing history entry before backgrounding.</body></html>
        """
        val PAGE_HTML = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              html, body { margin:0; min-height:6000px; background:#00bcd4; }
              button, a { box-sizing:border-box; position:fixed; top:18vh; height:14vh;
                width:36vw; border:0; background:white; color:black; font:20px sans-serif;
                display:flex; align-items:center; justify-content:center; text-decoration:none; }
              #click { left:8vw; } #next { right:8vw; }
            </style></head><body>
            <button id="click">Change page color</button>
            <a id="next" href="/site-matrix/resume-next">Next page</a>
            <script>
              let clicks = 0;
              let ticks = 0;
              const position = id => {
                const rect = document.getElementById(id).getBoundingClientRect();
                return {x:Math.round((rect.left + rect.width / 2) * devicePixelRatio),
                  y:Math.round((rect.top + rect.height / 2) * devicePixelRatio)};
              };
              const report = () => document.title = JSON.stringify({
                page:'main', clicks, ticks, scrollY:Math.round(scrollY),
                click:position('click'), next:position('next')
              });
              document.getElementById('click').onclick = () => {
                clicks++;
                document.documentElement.style.background = clicks % 2 ? '#ffc107' : '#00bcd4';
                document.body.style.background = clicks % 2 ? '#ffc107' : '#00bcd4';
                report();
              };
              setInterval(() => { ticks++; report(); }, 100);
              addEventListener('scroll', report);
              addEventListener('pageshow', report);
              report();
            </script></body></html>
        """.trimIndent()
        const val NEXT_HTML = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>{"page":"next"}</title>
            <style>body { background:#8bc34a; font:24px sans-serif; padding:25vh 12vw; }</style>
            </head><body>Navigation works after two long background intervals.</body></html>
        """
    }
}
