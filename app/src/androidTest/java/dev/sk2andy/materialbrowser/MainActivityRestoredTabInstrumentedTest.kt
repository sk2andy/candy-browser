package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class MainActivityRestoredTabInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun setUp() {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        BrowserSessionStore(context).apply {
            assertTrue(saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            saveStartupAnimationEnabled(false)
            saveStartupAddressFocusMode(StartupAddressFocusMode.Never)
            saveOpenHomeOnStartupEnabled(false)
        }
    }

    @After
    fun tearDown() = clearPreferences()

    @Test
    fun launcherShowsLastTabWithoutKeyboardInputAndKeepsItAfterBackgrounding() {
        verifyRestoredLauncherTab()
    }

    @Test
    fun neverOpeningStartupKeyboardPreservesLastTabEvenWhenHomeOnStartupWasEnabled() {
        BrowserSessionStore(context).saveOpenHomeOnStartupEnabled(true)
        verifyRestoredLauncherTab()
    }

    @Test
    fun startupAnimationStillRevealsLastPageWhenStartupKeyboardIsDisabled() {
        BrowserSessionStore(context).apply {
            saveStartupAnimationEnabled(true)
            saveOpenHomeOnStartupEnabled(true)
        }
        verifyRestoredLauncherTab()
    }

    private fun verifyRestoredLauncherTab() {
        EdgeToEdgeSiteFixtureServer { FIXTURE_HTML }.use { server ->
            val tab = BrowserTab(
                id = "last-open-tab-${UUID.randomUUID()}",
                url = server.fixtureUrl("/site-matrix/restored-tab"),
                title = "Previous title",
                lastAccessedAt = System.currentTimeMillis(),
            )
            assertTrue(BrowserSessionStore(context).saveTabsImmediately(listOf(tab), tab.id))
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                awaitPage(scenario, tab)
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitPage(scenario, tab)
            }
        }
    }

    private fun awaitPage(scenario: ActivityScenario<MainActivity>, expectedTab: BrowserTab) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        var loaded = false
        while (!loaded && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(expectedTab.id, controller.selectedTabId)
                loaded = controller.selectedTab.url == expectedTab.url &&
                    controller.selectedTab.title == FIXTURE_TITLE &&
                    controller.selectedBrowserEngineViewForTesting()?.isShown == true
                assertFalse(
                    ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true,
                )
            }
            if (!loaded) SystemClock.sleep(50)
        }
        assertTrue("Last tab must load without typing or a keyboard resize", loaded)
        var painted = false
        while (!painted && SystemClock.uptimeMillis() < deadline) {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            if (screenshot != null) {
                val pixel = screenshot.getPixel(screenshot.width / 2, screenshot.height / 2)
                painted = Color.green(pixel) > 140 && Color.red(pixel) < 50 &&
                    Color.blue(pixel) < 50
                screenshot.recycle()
            }
            if (!painted) SystemClock.sleep(50)
        }
        assertTrue("Restored document must be visible before keyboard interaction", painted)
    }

    private fun clearPreferences() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private companion object {
        const val FIXTURE_TITLE = "Restored Candy page"
        const val FIXTURE_HTML = """
            <!doctype html>
            <html>
            <head><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Restored Candy page</title>
            <style>html, body { margin: 0; min-height: 100vh; background: rgb(0, 180, 0); }</style>
            </head><body></body>
            </html>
        """
    }
}
