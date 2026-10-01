package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.engine.BrowserWebContentColorScheme
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import java.io.FileInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    @Test
    fun systemAppearanceFollowsNightChangesWhileActivityIsStopped() {
        exerciseAppearanceTransitions(coldStart = false, stopForSystemChange = true)
    }

    @Test
    fun resumeRepairsMissedViewNightConfigurationWithoutReloadingPage() {
        exerciseAppearanceTransitions(coldStart = false, missViewConfiguration = true)
    }

    private fun exerciseAppearanceTransitions(
        coldStart: Boolean,
        stopForSystemChange: Boolean = false,
        missViewConfiguration: Boolean = false,
    ) {
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
                    val themeDark = AtomicReference<Boolean>()
                    scenario.onActivity { activity ->
                        val probe = ComposeView(activity).apply {
                            setContent {
                                CandyTheme(settings = activity.browserControllerForTesting().appearanceSettings) {
                                    val backgroundDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                                    SideEffect { themeDark.set(backgroundDark) }
                                }
                            }
                        }
                        activity.findViewById<ViewGroup>(android.R.id.content).addView(probe)
                    }
                    scenario.onActivity { activity ->
                        assertTrue(
                            activity.browserControllerForTesting().openUrl(
                                url = server.url,
                                inNewTab = false,
                                authorizeInitialExternalNavigation = false,
                            ),
                        )
                    }
                    if (!coldStart) {
                        if (stopForSystemChange) scenario.moveToState(Lifecycle.State.CREATED)
                        setSystemNightMode("yes")
                        if (stopForSystemChange) resumeActivity(scenario)
                    }
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.System,
                        expectedDark = true,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM,
                        themeDark = themeDark,
                    )

                    if (missViewConfiguration) {
                        assertResumeRepairsConfiguration(scenario, themeDark, server, expectedDark = true)
                    }

                    if (stopForSystemChange) scenario.moveToState(Lifecycle.State.CREATED)
                    setSystemNightMode("no")
                    if (stopForSystemChange) resumeActivity(scenario)
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.System,
                        expectedDark = false,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM,
                        themeDark = themeDark,
                    )

                    if (missViewConfiguration) {
                        assertResumeRepairsConfiguration(scenario, themeDark, server, expectedDark = false)
                    }

                    updateAppearance(scenario, BrowserAppearanceMode.Dark)
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.Dark,
                        expectedDark = true,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_DARK,
                        themeDark = themeDark,
                    )

                    if (stopForSystemChange) {
                        scenario.moveToState(Lifecycle.State.CREATED)
                        setSystemNightMode("yes")
                        resumeActivity(scenario)
                        assertAppearance(
                            scenario,
                            appearanceMode = BrowserAppearanceMode.Dark,
                            expectedDark = true,
                            expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_DARK,
                            themeDark = themeDark,
                        )
                        scenario.moveToState(Lifecycle.State.CREATED)
                        setSystemNightMode("no")
                        resumeActivity(scenario)
                        assertAppearance(
                            scenario,
                            appearanceMode = BrowserAppearanceMode.Dark,
                            expectedDark = true,
                            expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_DARK,
                            themeDark = themeDark,
                        )
                    }

                    updateAppearance(scenario, BrowserAppearanceMode.Light)
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.Light,
                        expectedDark = false,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_LIGHT,
                        themeDark = themeDark,
                    )
                    if (stopForSystemChange) scenario.moveToState(Lifecycle.State.CREATED)
                    setSystemNightMode("yes")
                    if (stopForSystemChange) resumeActivity(scenario)
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.Light,
                        expectedDark = false,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_LIGHT,
                        themeDark = themeDark,
                    )

                    updateAppearance(scenario, BrowserAppearanceMode.System)
                    assertAppearance(
                        scenario,
                        appearanceMode = BrowserAppearanceMode.System,
                        expectedDark = true,
                        expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM,
                        themeDark = themeDark,
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

    private fun resumeActivity(scenario: ActivityScenario<MainActivity>) {
        val originalActivity = AtomicReference<MainActivity>()
        scenario.onActivity { originalActivity.set(it) }
        // Bring the existing task back without relying on the invoker's recreated empty Activity.
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.REORDER_TASKS)
        try {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            activityManager.moveTaskToFront(originalActivity.get().taskId, 0)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
        scenario.moveToState(Lifecycle.State.RESUMED)
        scenario.onActivity { assertSame(originalActivity.get(), it) }
    }

    private fun assertResumeRepairsConfiguration(
        scenario: ActivityScenario<MainActivity>,
        themeDark: AtomicReference<Boolean>,
        server: ThemeFixtureServer,
        expectedDark: Boolean,
    ) {
        val originalActivity = AtomicReference<MainActivity>()
        val originalTabId = AtomicReference<String>()
        val deliveredNight = AtomicInteger(Configuration.UI_MODE_NIGHT_UNDEFINED)
        val configurationProbe = AtomicReference<View>()
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.onActivity { activity ->
            originalActivity.set(activity)
            originalTabId.set(activity.browserControllerForTesting().selectedTabForTesting().id)
            val probe = object : View(activity) {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    super.onConfigurationChanged(newConfig)
                    deliveredNight.set(newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                }
            }
            configurationProbe.set(probe)
            activity.findViewById<ViewGroup>(android.R.id.content).addView(probe, ViewGroup.LayoutParams(0, 0))
            // Model views/runtime that missed the background night-mode delivery.
            val staleConfiguration = Configuration(activity.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (expectedDark) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
            }
            activity.window.decorView.dispatchConfigurationChanged(staleConfiguration)
            GeckoRuntimeOwner.getOrCreate(context).onConfigurationChanged(staleConfiguration)
        }
        awaitTheme(themeDark, expectedDark = !expectedDark)
        awaitWebsiteTitle(scenario, if (expectedDark) LIGHT_TITLE else DARK_TITLE)
        val requestsBeforeResume = server.pageRequests.get()
        val nightAtStart = AtomicInteger(Configuration.UI_MODE_NIGHT_UNDEFINED)
        val lifecycleMonitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity === originalActivity.get() && stage == Stage.STARTED) {
                nightAtStart.set(deliveredNight.get())
            }
        }
        instrumentation.runOnMainSync { lifecycleMonitor.addLifecycleCallback(callback) }
        try {
            // ActivityScenario resumes before pausing to STARTED; observe the actual start callback.
            scenario.moveToState(Lifecycle.State.STARTED)
        } finally {
            instrumentation.runOnMainSync { lifecycleMonitor.removeLifecycleCallback(callback) }
            scenario.onActivity { activity ->
                activity.findViewById<ViewGroup>(android.R.id.content).removeView(configurationProbe.get())
            }
        }
        assertEquals(
            "Start must deliver the current configuration before resume",
            if (expectedDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
            nightAtStart.get(),
        )
        assertAppearance(
            scenario,
            appearanceMode = BrowserAppearanceMode.System,
            expectedDark = expectedDark,
            expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM,
            themeDark = themeDark,
        )
        repeat(2) { resume ->
            if (resume > 0) scenario.moveToState(Lifecycle.State.CREATED)
            resumeActivity(scenario)
            scenario.onActivity { activity ->
                assertSame(originalActivity.get(), activity)
                assertEquals(originalTabId.get(), activity.browserControllerForTesting().selectedTabForTesting().id)
            }
            assertAppearance(
                scenario,
                appearanceMode = BrowserAppearanceMode.System,
                expectedDark = expectedDark,
                expectedScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM,
                themeDark = themeDark,
            )
            // Allow asynchronous navigation to arrive before checking document preservation.
            SystemClock.sleep(500L)
            assertEquals(
                "Resume must preserve the loaded document",
                requestsBeforeResume,
                server.pageRequests.get(),
            )
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
        themeDark: AtomicReference<Boolean>,
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
                        "applicationNight=$applicationNight, composeDark=${themeDark.get()}, " +
                        "preferredColorScheme=$scheme, title=$title",
                )
                matches.set(
                    controller.appearanceSettings.appearanceMode == appearanceMode &&
                        activityNight == expectedNight &&
                        (appearanceMode != BrowserAppearanceMode.System || applicationNight == expectedNight) &&
                        scheme == expectedScheme && title == expectedTitle && themeDark.get() == expectedDark,
                )
            }
            if (matches.get()) break
            SystemClock.sleep(POLL_MILLIS)
        }
        Log.i("CandyGeckoAppearance", snapshot.get())
        assertTrue(snapshot.get(), matches.get())
        assertEquals(appearanceMode, BrowserSessionStore(context).loadAppearanceSettings().appearanceMode)
    }

    private fun awaitTheme(themeDark: AtomicReference<Boolean>, expectedDark: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (themeDark.get() != expectedDark && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_MILLIS)
        }
        assertEquals(expectedDark, themeDark.get())
    }

    private fun awaitWebsiteTitle(scenario: ActivityScenario<MainActivity>, expectedTitle: String) {
        val title = AtomicReference<String>()
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity ->
                title.set(activity.browserControllerForTesting().selectedTabForTesting().title)
            }
            if (title.get() == expectedTitle) break
            SystemClock.sleep(POLL_MILLIS)
        }
        assertEquals(expectedTitle, title.get())
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
        val pageRequests = AtomicInteger()
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
                        if (reader.readLine()?.startsWith("GET / HTTP/") == true) {
                            pageRequests.incrementAndGet()
                        }
                        while (!reader.readLine().isNullOrEmpty()) {
                            // Consume request headers.
                        }
                        val body = """
                            <!doctype html><html><head><style>
                            :root { color-scheme: light dark; }
                            body { background: white; color: black; }
                            @media (prefers-color-scheme: dark) {
                                body { background: black; color: white; }
                            }
                            </style><script>
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
