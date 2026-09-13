package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.util.UUID
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoPerformanceDiagnosticsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        assumeTrue(BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS)
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        instrumentation.runOnMainSync { GeckoPerformanceDiagnostics.discard(context) }
    }

    @After
    fun tearDown() {
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) {
            instrumentation.runOnMainSync { GeckoPerformanceDiagnostics.discard(context) }
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun ordinaryAppCannotControlOrReadDiagnosticProvider() {
        val uri = Uri.parse("content://${BuildConfig.APPLICATION_ID}.performance")
        val command = runCatching { context.contentResolver.call(uri, "start", null, null) }
        val read = runCatching {
            context.contentResolver.openFileDescriptor(uri.buildUpon().appendPath("gecko-profile.json.gz").build(), "r")
                ?.close()
        }

        assertTrue(command.exceptionOrNull() is SecurityException)
        assertTrue(read.exceptionOrNull() is SecurityException)
    }

    @Test
    fun captureExportsRealGeckoSamplesAndGapMarker() {
        withFixture { scenario ->
            startCapture(scenario)
            // Exercise repeated observations while the asynchronous content-state relay settles.
            repeat(10) {
                scenario.onActivity { assertTrue(GeckoPerformanceDiagnostics.markGap()) }
                SystemClock.sleep(200)
            }
            scenario.onActivity { GeckoPerformanceDiagnostics.stop(context) }
            awaitStatus("ready")

            val file = GeckoPerformanceDiagnostics.profileForRead(context)
            assertNotNull(file)
            val json = requireNotNull(file).inputStream().use { input ->
                GZIPInputStream(input).bufferedReader().use { it.readText() }
            }
            assertTrue("Page gap marker is required in the actual native profile", json.contains("Candy.Diagnostics.UserObservedGap"))
            assertTrue("A real sampled thread is required", hasSamples(JSONObject(json)))
            assertFalse(GeckoPerformanceDiagnostics.isRecording)
        }
    }

    @Test
    fun openingPrivateSessionDiscardsCaptureAndBlocksRestart() {
        withFixture { scenario ->
            startCapture(scenario)
            lateinit var privateSession: GeckoBrowserSession
            scenario.onActivity { activity ->
                privateSession = GeckoRuntimeOwner.getOrCreate(activity.applicationContext).createSession(
                    profileId = "diagnostics-private-${UUID.randomUUID()}",
                    isPrivate = true,
                )
                assertFalse(GeckoPerformanceDiagnostics.isRecording)
                assertEquals("private_session", GeckoPerformanceDiagnostics.start(context))
                assertNull(GeckoPerformanceDiagnostics.profileForRead(context))
            }
            try {
                SystemClock.sleep(500)
                assertNull(GeckoPerformanceDiagnostics.profileForRead(context))
            } finally {
                scenario.onActivity { privateSession.close() }
            }
            awaitStatus("idle")
        }
    }

    private fun withFixture(block: (ActivityScenario<MainActivity>) -> Unit) {
        EdgeToEdgeSiteFixtureServer().use { server ->
            val tab = BrowserTab(
                id = "gecko-diagnostics-fixture",
                lastAccessedAt = System.currentTimeMillis(),
                url = server.url,
            )
            assertTrue(BrowserSessionStore(context).saveTabsImmediately(listOf(tab), tab.id))
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java)
                    .setAction("dev.sk2andy.materialbrowser.test.GECKO_PERFORMANCE_DIAGNOSTICS"),
            ).use { scenario ->
                scenario.onActivity { activity ->
                    activity.browserControllerForTesting().submitAddress(server.url)
                }
                val deadline = SystemClock.uptimeMillis() + 30_000
                var ready = false
                var lastTitle = ""
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        lastTitle = controller.selectedTabForTesting().title
                        ready = controller.selectedGeckoViewForTesting() != null &&
                            lastTitle.isNotEmpty() && lastTitle != "Preparing Candy site matrix"
                    }
                    if (!ready) SystemClock.sleep(100)
                }
                assertTrue("Gecko fixture did not load its bridge; last title=$lastTitle", ready)
                block(scenario)
            }
        }
    }

    @Test
    fun privateSessionRejectsPendingStopExport() {
        withFixture { scenario ->
            startCapture(scenario)
            SystemClock.sleep(500)
            scenario.onActivity { activity ->
                GeckoPerformanceDiagnostics.stop(context)
                val privateSession = GeckoRuntimeOwner.getOrCreate(activity.applicationContext).createSession(
                    profileId = "diagnostics-pending-${UUID.randomUUID()}",
                    isPrivate = true,
                )
                assertNull(GeckoPerformanceDiagnostics.profileForRead(context))
                privateSession.close()
            }
            awaitStatus("idle")
            assertNull(GeckoPerformanceDiagnostics.profileForRead(context))
            assertFalse(GeckoPerformanceDiagnostics.isRecording)
        }
    }

    private fun startCapture(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { GeckoPerformanceDiagnostics.start(context) }
        awaitStatus("recording")
    }

    @Test
    fun discardedStartingCaptureCanRestartWithoutPublishingStaleProfile() {
        withFixture { scenario ->
            scenario.onActivity {
                GeckoPerformanceDiagnostics.start(context)
                GeckoPerformanceDiagnostics.discard(context)
            }
            val deadline = SystemClock.uptimeMillis() + 30_000
            var result = "busy"
            while (result == "busy" && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { result = GeckoPerformanceDiagnostics.start(context) }
                if (result == "busy") SystemClock.sleep(100)
            }
            awaitStatus("recording")
            assertNull(GeckoPerformanceDiagnostics.profileForRead(context))
            scenario.onActivity { GeckoPerformanceDiagnostics.discard(context) }
            assertFalse(GeckoPerformanceDiagnostics.isRecording)
            assertNull(GeckoPerformanceDiagnostics.profileForRead(context))
        }
    }

    private fun awaitStatus(expected: String) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (GeckoPerformanceDiagnostics.status != expected && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
        }
        assertEquals(expected, GeckoPerformanceDiagnostics.status)
    }

    private fun hasSamples(profile: JSONObject): Boolean {
        val threads = profile.optJSONArray("threads")
        if (threads != null) {
            for (index in 0 until threads.length()) {
                if ((threads.getJSONObject(index).optJSONObject("samples")?.optJSONArray("data")?.length() ?: 0) > 0) {
                    return true
                }
            }
        }
        val processes = profile.optJSONArray("processes") ?: return false
        for (index in 0 until processes.length()) {
            if (hasSamples(processes.getJSONObject(index))) return true
        }
        return false
    }
}
