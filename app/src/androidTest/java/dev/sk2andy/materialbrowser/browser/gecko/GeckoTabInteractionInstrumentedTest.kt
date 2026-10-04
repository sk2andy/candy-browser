package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
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
import dev.sk2andy.materialbrowser.data.GeckoSessionStateStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoTabInteractionInstrumentedTest {
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
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
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
    fun regularGeckoTabsRemainInteractiveAcrossRepeatedSwitchesAndResume() {
        verifyInteraction(AndroidBrowserEngineKind.GeckoView, isPrivate = false)
    }

    @Test
    fun privateGeckoTabsRemainInteractiveWithoutPersistingPages() {
        verifyInteraction(AndroidBrowserEngineKind.GeckoView, isPrivate = true)
    }

    @Test
    fun systemWebViewTabsPreserveInteractionAndPageState() {
        verifyInteraction(AndroidBrowserEngineKind.SystemWebView, isPrivate = false)
    }

    private fun verifyInteraction(engine: AndroidBrowserEngineKind, isPrivate: Boolean) {
        store.saveAndroidBrowserEngineKind(engine)
        val requests = AtomicInteger()
        EdgeToEdgeSiteFixtureServer { path ->
            if (path == "/a" || path == "/b") {
                requests.incrementAndGet()
                fixtureHtml(path.removePrefix("/"))
            } else {
                "<html></html>"
            }
        }.use { server ->
            lateinit var firstId: String
            lateinit var secondId: String
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
            ).use { scenario ->
                try {
                    scenario.onActivity { activity ->
                        assertEquals(engine, activity.browserControllerForTesting().browserEngineKind)
                        firstId = activity.browserControllerForTesting().createTab(
                            initialUrl = server.fixtureUrl("/a"),
                            isIncognito = isPrivate,
                        )
                    }
                    awaitTitle(scenario, "a:0")
                    val firstSession = nativeSession(scenario)
                    if (engine == AndroidBrowserEngineKind.GeckoView) {
                        assertTrue("First Gecko renderer must own a session", firstSession != null)
                    }
                    tapPage(scenario)
                    awaitTitle(scenario, "a:1")
                    scenario.onActivity { activity ->
                        secondId = activity.browserControllerForTesting().createTab(
                            initialUrl = server.fixtureUrl("/b"),
                            isIncognito = isPrivate,
                        )
                    }
                    awaitTitle(scenario, "b:0")
                    val secondSession = nativeSession(scenario)
                    if (engine == AndroidBrowserEngineKind.GeckoView) {
                        awaitTitle(scenario, "a:1", firstId, visible = false)
                    }
                    if (engine == AndroidBrowserEngineKind.GeckoView) {
                        assertTrue("Second Gecko renderer must own a session", secondSession != null)
                    }
                    var firstCount = 1
                    var secondCount = 0
                    repeat(SWITCH_CYCLES) { cycle ->
                        tapPage(scenario)
                        awaitTitle(scenario, "b:${++secondCount}")
                        if (cycle == 0) SystemClock.sleep(TAB_BACKGROUND_MILLIS)
                        scenario.onActivity { it.browserControllerForTesting().selectTab(firstId) }
                        awaitTitle(scenario, "a:$firstCount")
                        assertSame(firstSession, nativeSession(scenario))
                        if (engine == AndroidBrowserEngineKind.GeckoView) {
                            awaitTitle(scenario, "b:$secondCount", secondId, visible = false)
                        }
                        tapPage(scenario)
                        awaitTitle(scenario, "a:${++firstCount}")
                        if (cycle == 1) {
                            scenario.moveToState(Lifecycle.State.CREATED)
                            SystemClock.sleep(BACKGROUND_MILLIS)
                            scenario.moveToState(Lifecycle.State.RESUMED)
                            tapPage(scenario)
                            awaitTitle(scenario, "a:${++firstCount}")
                        }
                        if (cycle < SWITCH_CYCLES - 1) {
                            scenario.onActivity { it.browserControllerForTesting().selectTab(secondId) }
                            awaitTitle(scenario, "b:$secondCount")
                            assertSame(secondSession, nativeSession(scenario))
                            if (engine == AndroidBrowserEngineKind.GeckoView) {
                                awaitTitle(scenario, "a:$firstCount", firstId, visible = false)
                            }
                        }
                    }
                    assertEquals("Tab switches/resume must not reload either document", 2, requests.get())
                    if (isPrivate) assertPrivateDataAbsent(firstId, secondId)
                } finally {
                    saveEvidence(engine, isPrivate)
                }
            }
            if (isPrivate) assertPrivateDataAbsent(firstId, secondId)
        }
    }

    private fun assertPrivateDataAbsent(firstId: String, secondId: String) {
        val storedTabs = preferences.getString(BrowserSessionStore.KEY_TABS, "").orEmpty()
        assertFalse(storedTabs.contains("127.0.0.1"))
        assertFalse(storedTabs.contains(firstId))
        assertFalse(storedTabs.contains(secondId))
        assertEquals(null, GeckoSessionStateStore(context).load(firstId))
        assertEquals(null, GeckoSessionStateStore(context).load(secondId))
    }

    private fun saveEvidence(engine: AndroidBrowserEngineKind, isPrivate: Boolean) {
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(context.getExternalFilesDir(null), "issue-257-${engine.stableId}-$isPrivate.png")
                .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            image.recycle()
        }
    }

    private fun tapPage(scenario: ActivityScenario<MainActivity>) {
        val view = awaitView(scenario)
        var x = 0f
        var y = 0f
        scenario.onActivity {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            x = location[0] + view.width * 0.5f
            y = location[1] + view.height * 0.35f
        }
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).let { event ->
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                } finally {
                    event.recycle()
                }
            }
        }
    }

    private fun awaitView(scenario: ActivityScenario<MainActivity>): View {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            var view: View? = null
            scenario.onActivity { activity ->
                view = activity.browserControllerForTesting().selectedBrowserEngineViewForTesting()
                    ?.takeIf { it.isAttachedToWindow && it.isShown && it.width > 0 && it.height > 0 }
            }
            if (view != null) return requireNotNull(view)
            SystemClock.sleep(50L)
        }
        throw AssertionError("Selected renderer did not attach")
    }

    private fun nativeSession(scenario: ActivityScenario<MainActivity>): GeckoSession? {
        val view = awaitView(scenario)
        var session: GeckoSession? = null
        scenario.onActivity { session = nativeView(view)?.session }
        return session
    }

    private fun nativeView(view: View): GeckoView? {
        if (view is GeckoView) return view
        if (view is ViewGroup) {
            repeat(view.childCount) { index ->
                nativeView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun awaitTitle(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
        tabId: String? = null,
        visible: Boolean = true,
    ) {
        val expectedTitle = "$expected:${if (visible) "visible" else "hidden"}"
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var title = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                title = if (tabId == null) controller.selectedTab.title else {
                    controller.tabs.first { it.id == tabId }.title
                }
            }
            if (title == expectedTitle) {
                instrumentation.waitForIdleSync()
                if (visible) awaitPagePixels(scenario, expected.substringAfter(':').toInt())
                return
            }
            SystemClock.sleep(50L)
        }
        assertEquals("Page did not preserve counter/visibility or accept input", expectedTitle, title)
    }

    private fun awaitPagePixels(scenario: ActivityScenario<MainActivity>, count: Int) {
        val view = awaitView(scenario)
        val expectedColor = if (count % 2 == 0) Color.rgb(0, 127, 153) else Color.rgb(182, 0, 134)
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        var observedColor = Color.TRANSPARENT
        while (SystemClock.elapsedRealtime() < deadline) {
            val location = IntArray(2)
            scenario.onActivity { view.getLocationOnScreen(location) }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val pixels = screenshot.copy(Bitmap.Config.ARGB_8888, false)
            screenshot.recycle()
            try {
                observedColor = pixels.getPixel(
                    location[0] + view.width / 2,
                    location[1] + view.height * 7 / 10,
                )
                if (observedColor == expectedColor) return
            } finally {
                pixels.recycle()
            }
            SystemClock.sleep(100L)
        }
        assertEquals("Live counter changed but rendered page remained stale", expectedColor, observedColor)
    }

    private fun fixtureHtml(page: String): String = """
        <!doctype html><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>body{margin:0;background:#007f99;color:white;font:24px sans-serif}
        button{position:fixed;left:0;top:20vh;width:100vw;height:30vh;font-size:32px}</style>
        <h1>Tab $page</h1><p>Live page state, no reload</p><button>Tap counter: 0</button>
        <script>
        let count = 0;
        const report = () => { document.title = '$page:' + count + ':' + document.visibilityState; };
        document.addEventListener('visibilitychange', report);
        report();
        document.querySelector('button').onclick = () => {
            count++;
            document.body.style.background = count % 2 ? '#b60086' : '#007f99';
            document.querySelector('button').textContent = 'Tap counter: ' + count;
            report();
        };
        </script>
    """.trimIndent()

    private companion object {
        const val SWITCH_CYCLES = 4
        const val TIMEOUT_MILLIS = 20_000L
        const val TAB_BACKGROUND_MILLIS = 12_000L
        const val BACKGROUND_MILLIS = 31_000L
        const val TEST_ACTIVITY_ACTION = "dev.sk2andy.materialbrowser.TEST_GECKO_TAB_INTERACTION"
    }
}
