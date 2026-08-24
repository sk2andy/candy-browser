package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertTrue
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

    private fun updateAppearance(
        scenario: ActivityScenario<MainActivity>,
        appearanceMode: BrowserAppearanceMode,
    ) {
        scenario.onActivity { activity ->
            activity.browserControllerForTesting().updateAppearanceSettings(
                AppearanceSettings(appearanceMode = appearanceMode),
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

    private companion object {
        const val APPEARANCE_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
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
}
