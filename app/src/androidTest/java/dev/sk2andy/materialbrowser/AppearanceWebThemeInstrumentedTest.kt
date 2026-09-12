package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.createLinkPeekPreviewWebView
import dev.sk2andy.materialbrowser.browser.releaseLinkPeekPreviewWebView
import dev.sk2andy.materialbrowser.browser.selectedWebViewForTesting
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceWebThemeInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private var originalSystemNightMode: String? = null

    @Before
    fun setUp() {
        preferences.edit()
            .clear()
            .putString(
                BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE,
                AndroidBrowserEngineKind.SystemWebView.stableId,
            )
            .commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        originalSystemNightMode?.let(::setSystemNightMode)
    }

    @Test
    fun liveAppAppearanceChangesReachSystemWebViewWebsites() {
        ThemeFixtureServer().use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                updateAppearance(scenario, BrowserAppearanceMode.Light)
                assertTrue(awaitNightResources(scenario, expectedDark = false))
                val controller = selectedController(scenario)
                val tabId = controller.selectedTabId
                val lightWebView = openThemeProbe(scenario, server.url)
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "light"))

                updateAppearance(scenario, BrowserAppearanceMode.Dark)
                assertTrue(awaitNightResources(scenario, expectedDark = true))
                assertTrue(selectedController(scenario) === controller)
                assertTrue(selectedController(scenario).selectedTabId == tabId)
                assertTrue(selectedWebView(scenario) !== lightWebView)
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "dark"))
            }
        }
    }

    @Test
    fun appearanceChangesPreservePrivateSystemWebViewHistoryInMemory() {
        ThemeFixtureServer().use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                updateAppearance(scenario, BrowserAppearanceMode.Light)
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().createTab(
                        initialUrl = server.url,
                        isIncognito = true,
                    )
                }
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "light"))
                val firstWebView = selectedWebView(scenario)

                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().openUrl("${server.url}second"))
                }
                assertTrue(awaitSelectedWebView(scenario) { view ->
                    view.url?.endsWith("/second") == true && view.canGoBack()
                })

                updateAppearance(scenario, BrowserAppearanceMode.Dark)

                assertTrue(awaitSelectedWebView(scenario) { view ->
                    view !== firstWebView &&
                        view.url?.endsWith("/second") == true &&
                        view.canGoBack()
                })
                scenario.onActivity { activity ->
                    assertTrue(activity.browserControllerForTesting().selectedTabForTesting().isIncognito)
                }
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "dark"))
            }
        }
    }

    @Test
    fun forceDarkWebsiteSettingUpdatesActiveSystemWebView() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            updateAppearance(scenario, BrowserAppearanceMode.Dark)
            assertTrue(awaitNightResources(scenario, expectedDark = true))

            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val webView = controller.selectedWebViewForTesting()
                assertFalse(WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings))

                controller.updateAppearanceSettings(
                    controller.appearanceSettings.copy(forceDarkWebsites = true),
                )

                assertTrue(controller.selectedWebViewForTesting() === webView)
                assertTrue(WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings))
                val preview = controller.createLinkPeekPreviewWebView(
                    url = "https://theme.example/link-peek",
                    onProgressChanged = {},
                    onCommittedUrlChanged = {},
                )
                assertTrue(WebSettingsCompat.isAlgorithmicDarkeningAllowed(preview.settings))

                controller.updateAppearanceSettings(
                    controller.appearanceSettings.copy(forceDarkWebsites = false),
                )

                assertFalse(WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.settings))
                assertFalse(WebSettingsCompat.isAlgorithmicDarkeningAllowed(preview.settings))
                controller.releaseLinkPeekPreviewWebView(preview)
            }
        }
    }

    @Test
    fun systemAppearanceFollowsRuntimeChangesAndAppOverrideWins() {
        val originalNightMode = currentSystemNightMode()
        assumeTrue(originalNightMode != null)
        originalSystemNightMode = originalNightMode
        setSystemNightMode("no")

        ThemeFixtureServer().use { server ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue(awaitNightResources(scenario, expectedDark = false))
                val lightWebView = openThemeProbe(scenario, server.url)
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "light"))

                setSystemNightMode("yes")
                assertTrue(awaitNightResources(scenario, expectedDark = true))
                assertTrue(selectedWebView(scenario) !== lightWebView)
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "dark"))

                updateAppearance(scenario, BrowserAppearanceMode.Light)
                assertTrue(awaitNightResources(scenario, expectedDark = false))
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "light"))

                setSystemNightMode("no")
                setSystemNightMode("yes")
                assertTrue(awaitNightResources(scenario, expectedDark = false))
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "light"))

                setSystemNightMode("no")
                updateAppearance(scenario, BrowserAppearanceMode.Dark)
                assertTrue(awaitNightResources(scenario, expectedDark = true))
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "dark"))

                setSystemNightMode("yes")
                setSystemNightMode("no")
                assertTrue(awaitNightResources(scenario, expectedDark = true))
                assertTrue(awaitWebsiteColorScheme(scenario, expected = "dark"))
            }
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

    private fun openThemeProbe(
        scenario: ActivityScenario<MainActivity>,
        url: String,
    ): WebView {
        val webView = AtomicReference<WebView>()
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            controller.submitAddress(url)
            webView.set(controller.selectedWebViewForTesting())
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

    private fun selectedWebView(scenario: ActivityScenario<MainActivity>): WebView {
        val webView = AtomicReference<WebView>()
        scenario.onActivity { activity ->
            webView.set(activity.browserControllerForTesting().selectedWebViewForTesting())
        }
        return webView.get()
    }

    private fun currentSystemNightMode(): String? =
        shell("cmd uimode night")
            .lineSequence()
            .firstOrNull { line -> line.startsWith("Night mode:") }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { mode -> mode in setOf("auto", "no", "yes") }

    private fun setSystemNightMode(mode: String) {
        shell("cmd uimode night $mode")
        instrumentation.waitForIdleSync()
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { output ->
            FileInputStream(output.fileDescriptor).bufferedReader().use { it.readText() }
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

    private fun awaitSelectedWebView(
        scenario: ActivityScenario<MainActivity>,
        condition: (WebView) -> Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + APPEARANCE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            var matches = false
            scenario.onActivity { activity ->
                matches = condition(activity.browserControllerForTesting().selectedWebViewForTesting())
            }
            if (matches) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private class ThemeFixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread({ serve() }, "system-webview-theme-fixture").apply {
            isDaemon = true
            start()
        }

        val url = "http://127.0.0.1:${socket.localPort}/"

        private fun serve() {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { connection ->
                        val reader = connection.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val body = THEME_PROBE_HTML.toByteArray()
                        connection.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\n".toByteArray())
                            write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                            write("Content-Length: ${body.size}\r\n".toByteArray())
                            write("Connection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (error: SocketException) {
                    if (socket.isClosed) return
                }
            }
        }

        override fun close() {
            socket.close()
            thread.join(1_000L)
        }
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
