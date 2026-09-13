package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.engine.BrowserWebContentColorScheme
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoRuntimeSettings

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GeckoAppearanceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun coldSystemDarkAndLiveAppOverridesReachWebsiteMediaQuery() {
        assumeTrue(
            "Set system dark before starting instrumentation",
            shell("cmd uimode night").substringAfter(':').trim() == "yes",
        )
        assumeTrue("Cold-start check requires a fresh process", !GeckoRuntimeOwner.hasRuntimeForTesting())
        exerciseAppearanceTransitions(coldStart = true)
    }

    @Test
    fun systemDarkAfterAppLightOverrideReachesWebsiteMediaQuery() {
        exerciseAppearanceTransitions(coldStart = false)
    }

    private fun exerciseAppearanceTransitions(coldStart: Boolean) {
        val preferences = context.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val originalPreferences = preferences.all
        val originalRuntimeScheme = AtomicReference(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
        instrumentation.runOnMainSync {
            if (GeckoRuntimeOwner.hasRuntimeForTesting()) {
                val runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
                originalRuntimeScheme.set(runtime.preferredColorSchemeForTesting())
            }
        }
        val originalNightMode = shell("cmd uimode night").substringAfter(':').trim()
        assertTrue(originalNightMode in setOf("auto", "no", "yes"))
        if (coldStart) {
            assertEquals(
                Configuration.UI_MODE_NIGHT_YES,
                context.applicationContext.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK,
            )
        }

        try {
            if (!coldStart) setSystemNightMode("no")
            preferences.edit().clear().putString(
                BrowserSessionStore.KEY_ANDROID_BROWSER_ENGINE,
                AndroidBrowserEngineKind.GeckoView.stableId,
            ).commit()
            ThemeFixtureServer().use { server ->
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        assertTrue(
                            activity.browserControllerForTesting().openUrl(
                                url = server.url,
                                inNewTab = false,
                                authorizeInitialExternalNavigation = false,
                            ),
                        )
                    }
                    if (!coldStart) setSystemNightMode("yes")
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.System,
                        expectedDark = true,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM,
                    )

                    setSystemNightMode("no")
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.System,
                        expectedDark = false,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM,
                    )

                    updateAppearance(scenario, BrowserAppearanceMode.Dark)
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.Dark,
                        expectedDark = true,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_DARK,
                    )

                    updateAppearance(scenario, BrowserAppearanceMode.Light)
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.Light,
                        expectedDark = false,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_LIGHT,
                    )
                    setSystemNightMode("yes")
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.Light,
                        expectedDark = false,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_LIGHT,
                    )

                    updateAppearance(scenario, BrowserAppearanceMode.System)
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.System,
                        expectedDark = true,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM,
                    )
                }
            }
        } finally {
            preferences.edit().clear().apply {
                originalPreferences.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }.commit()
            setSystemNightMode(originalNightMode)
            instrumentation.runOnMainSync {
                if (GeckoRuntimeOwner.hasRuntimeForTesting()) {
                    val runtime = GeckoRuntimeOwner.getOrCreate(context)
                    runtime.onConfigurationChanged(context.applicationContext.resources.configuration)
                    runtime.setWebContentColorScheme(
                        when (originalRuntimeScheme.get()) {
                            GeckoRuntimeSettings.COLOR_SCHEME_DARK -> BrowserWebContentColorScheme.Dark
                            GeckoRuntimeSettings.COLOR_SCHEME_LIGHT -> BrowserWebContentColorScheme.Light
                            else -> BrowserWebContentColorScheme.System
                        },
                    )
                }
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

    private fun assertAppearance(
        scenario: ActivityScenario<MainActivity>,
        appearanceMode: BrowserAppearanceMode,
        expectedDark: Boolean,
        expectedScheme: Int,
    ) {
        val expectedNight = if (expectedDark) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val expectedTitle = if (expectedDark) DARK_TITLE else LIGHT_TITLE
        val snapshot = AtomicReference<String>()
        val matches = AtomicReference(false)
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val activityNight = activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                val applicationNight = activity.applicationContext.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                val runtime = GeckoRuntimeOwner.getOrCreate(context) as GeckoViewRuntimeHandle
                val scheme = runtime.preferredColorSchemeForTesting()
                val title = controller.selectedTabForTesting().title
                val storedAppearance = BrowserSessionStore(context).loadAppearanceSettings().appearanceMode
                snapshot.set(
                    "appearance=${controller.appearanceSettings.appearanceMode}, " +
                        "stored=$storedAppearance, activityNight=$activityNight, " +
                        "applicationNight=$applicationNight, preferredColorScheme=$scheme, title=$title",
                )
                matches.set(
                    controller.appearanceSettings.appearanceMode == appearanceMode &&
                        activityNight == expectedNight &&
                        (appearanceMode != BrowserAppearanceMode.System || applicationNight == expectedNight) &&
                        scheme == expectedScheme && title == expectedTitle,
                )
            }
            if (matches.get()) break
            SystemClock.sleep(POLL_MILLIS)
        }
        Log.i("CandyGeckoAppearance", snapshot.get())
        assertTrue(snapshot.get(), matches.get())
        assertEquals(appearanceMode, BrowserSessionStore(context).loadAppearanceSettings().appearanceMode)
    }

    private fun setSystemNightMode(mode: String) {
        shell("cmd uimode night $mode")
        if (mode == "yes" || mode == "no") {
            val expectedNight = if (mode == "yes") {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
            while (SystemClock.elapsedRealtime() < deadline) {
                val night = context.applicationContext.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                if (night == expectedNight) break
                SystemClock.sleep(POLL_MILLIS)
            }
            assertEquals(
                expectedNight,
                context.applicationContext.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK,
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { output ->
            FileInputStream(output.fileDescriptor).bufferedReader().use { it.readText() }
        }

    private class ThemeFixtureServer : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val thread = Thread({ serve() }, "gecko-theme-fixture").apply {
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
                        val body = """
                            <!doctype html><html><head><script>
                            const scheme = matchMedia('(prefers-color-scheme: dark)');
                            const publishScheme = () => {
                                document.title = scheme.matches ? '$DARK_TITLE' : '$LIGHT_TITLE';
                            };
                            scheme.addEventListener('change', publishScheme);
                            publishScheme();
                            </script></head><body>theme probe</body></html>
                        """.trimIndent().toByteArray()
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
        const val LIGHT_TITLE = "theme-light"
        const val DARK_TITLE = "theme-dark"
        const val TIMEOUT_MILLIS = 20_000L
        const val POLL_MILLIS = 50L
    }
}
