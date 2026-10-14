package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.data.AddressBarDockEdge
import dev.sk2andy.materialbrowser.data.AddressBarDockPlacement
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarDockPersistenceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = BrowserSessionStore(context)

    @After
    fun tearDown() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun geckoAutoParkingPreservesChosenEdgeAcrossPageAndLifecycleChanges() =
        assertRememberedPlacement(AndroidBrowserEngineKind.GeckoView)

    @Test
    fun systemWebViewAutoParkingPreservesChosenEdgeAcrossPageAndLifecycleChanges() =
        assertRememberedPlacement(AndroidBrowserEngineKind.SystemWebView)

    private fun assertRememberedPlacement(engine: AndroidBrowserEngineKind) {
        EdgeToEdgeSiteFixtureServer { _ -> chatHtml() }.use { server ->
            for (edge in listOf(AddressBarDockEdge.Left, AddressBarDockEdge.Right)) {
                tearDown()
                GestureOnboardingStore(context).markCompleted()
                ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
                store.saveAndroidBrowserEngineKind(engine)
                store.saveStartupAnimationEnabled(false)
                store.saveOpenHomeOnStartupEnabled(false)
                store.saveExternalLinkPreviewEnabled(false)
                store.saveAddressBarDockingEnabled(true)
                val placement = AddressBarDockPlacement(edge, 0.35f)
                store.saveAddressBarDockPlacement(placement)
                store.saveAddressBarDockPlacement(null)
                val tab = BrowserTab(
                    id = "remembered-chat",
                    url = server.fixtureUrl("/chat"),
                    lastAccessedAt = System.currentTimeMillis(),
                )
                assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
                ActivityScenario.launch<MainActivity>(mainIntent()).use { scenario ->
                    await(scenario, "Real $engine chat editor must auto-park") {
                        it.selectedTab.title == "Ready chat" && it.isAddressBarDocked
                    }
                    capture("$engine-$edge-auto")
                    assertPlacement(scenario, placement)
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        assertTrue(controller.scrollSelectedBrowserEngineToVerticalOffset(500))
                    }
                    await(scenario, "Real renderer scroll") {
                        (it.selectedBrowserEngineScrollMetrics()?.offsetPx ?: 0) > 100
                    }
                    assertPlacement(scenario, placement)
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        controller.updateAddressBarDocked(false)
                        assertEquals(edge, controller.lastAddressBarDockEdge)
                        controller.openUrl(server.fixtureUrl("/chat-next"))
                    }
                    await(scenario, "New document auto-parks on remembered edge") {
                        it.selectedTab.url.endsWith("/chat-next") && !it.selectedTab.isLoading &&
                            it.isAddressBarDocked
                    }
                    assertPlacement(scenario, placement)
                    tapChatEditor(scenario)
                    await(scenario, "Real page keyboard opens") {
                        it.selectedBrowserEngineViewForTesting()?.let { view ->
                            ViewCompat.getRootWindowInsets(view)
                                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                        } == true
                    }
                    assertPlacement(scenario, placement)
                    capture("$engine-$edge-keyboard")
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    await(scenario, "Page keyboard closes") {
                        it.selectedBrowserEngineViewForTesting()?.let { view ->
                            ViewCompat.getRootWindowInsets(view)
                                ?.isVisible(WindowInsetsCompat.Type.ime()) == false
                        } == true
                    }
                    assertPlacement(scenario, placement)
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        val firstTab = controller.selectedTabId
                        val firstProfile = controller.activeProfileId
                        controller.createTab()
                        controller.selectTab(firstTab)
                        controller.updateProfilesEnabled(true)
                        assertTrue(controller.createProfile("🍬") != null)
                        assertTrue(controller.selectProfile(firstProfile))
                        controller.setBrowserChromeOwnsIme(true)
                        controller.setBrowserChromeOwnsIme(false)
                        controller.onPause()
                        controller.onResume()
                    }
                    assertPlacement(scenario, placement)
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    await(scenario, "Landscape renderer geometry") {
                        val view = it.selectedBrowserEngineViewForTesting()
                        view != null && view.width > view.height
                    }
                    assertPlacement(scenario, placement)
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    await(scenario, "Portrait renderer geometry") {
                        val view = it.selectedBrowserEngineViewForTesting()
                        view != null && view.height > view.width
                    }
                    scenario.recreate()
                    await(scenario, "Recreated controller restores placement") { it.isAddressBarDocked }
                    assertPlacement(scenario, placement)
                }
                ActivityScenario.launch<MainActivity>(mainIntent()).use { scenario ->
                    await(scenario, "Fresh activity restores persisted placement") { it.isAddressBarDocked }
                    assertPlacement(scenario, placement)
                    scenario.onActivity { activity ->
                        activity.browserControllerForTesting().parkAddressBarOnRight()
                    }
                    assertPlacement(scenario, placement.copy(edge = AddressBarDockEdge.Right))
                }
            }
        }
    }

    private fun assertPlacement(
        scenario: ActivityScenario<MainActivity>,
        expected: AddressBarDockPlacement,
    ) {
        scenario.onActivity { activity ->
            assertEquals(expected, activity.browserControllerForTesting().addressBarDockPlacement)
            assertEquals(expected.edge, activity.browserControllerForTesting().lastAddressBarDockEdge)
            assertEquals(expected, store.loadAddressBarDockPlacement())
            assertEquals(expected, store.loadLastAddressBarDockPlacement())
        }
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        message: String,
        condition: (BrowserController) -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        do {
            var satisfied = false
            scenario.onActivity { satisfied = condition(it.browserControllerForTesting()) }
            if (satisfied) return
            SystemClock.sleep(100L)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(message)
    }

    private fun tapChatEditor(scenario: ActivityScenario<MainActivity>) {
        var x = 0f
        var y = 0f
        scenario.onActivity { activity ->
            val view = requireNotNull(
                activity.browserControllerForTesting().selectedBrowserEngineViewForTesting(),
            )
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            x = location[0] + view.width / 2f
            y = location[1] + view.height - 80f * view.resources.displayMetrics.density
        }
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            try {
                assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    private fun mainIntent() = Intent(context, MainActivity::class.java)
        .setAction("dev.sk2andy.materialbrowser.ISSUE_302_TEST")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(800L)
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun chatHtml() = """
        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Ready chat</title>
        <style>body{margin:0;background:#f4f4f4}main{height:300vh;padding:80px 20px}
        textarea{position:fixed;bottom:0;left:5%;width:90%;height:180px;box-sizing:border-box}</style>
        <main>Chat editor obstruction regression fixture</main>
        <textarea aria-label="Ask anything" placeholder="Ask anything"></textarea>
    """.trimIndent()
}
