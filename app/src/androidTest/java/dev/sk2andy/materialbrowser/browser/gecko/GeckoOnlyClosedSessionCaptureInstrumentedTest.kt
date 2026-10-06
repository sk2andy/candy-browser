package dev.sk2andy.materialbrowser.browser.gecko

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.JsonReader
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** Opt-in upstream control on an isolated emulator: -e candyGeckoOnlyCapture true. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoOnlyClosedSessionCaptureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val reuseSession =
        InstrumentationRegistry.getArguments().getString("candyGeckoOnlyReuseSession") == "true"
    private val captureGcLogs =
        InstrumentationRegistry.getArguments().getString("candyGeckoOnlyGcLogs") == "true"
    private val sessions = mutableListOf<WeakReference<GeckoSession>>()
    private val views = mutableListOf<WeakReference<GeckoView>>()
    private val snapshots = JSONArray()
    private val directory by lazy {
        File(context.cacheDir, "gecko-only-closed").apply {
            mkdirs()
            Os.chmod(absolutePath, 448)
        }
    }

    @Test
    fun captureClosedRawGeckoSessionsWithoutCandyDelegates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("candyGeckoOnlyCapture") == "true")
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        assertFalse("Candy runtime was initialized before raw control", GeckoRuntimeOwner.hasRuntimeForTesting())
        directory.listFiles().orEmpty().filter { it.isFile }.forEach(File::delete)
        val profile = File(directory, "profile").apply { mkdirs() }
        val fifoDirectory = File(context.cacheDir, "gecko_temp").apply { mkdirs() }
        File(profile, "user.js").writeText(
            "user_pref(\"memory_info_dumper.watch_fifo.enabled\", true);\n" +
                "user_pref(\"memory_info_dumper.watch_fifo.directory\", \"${fifoDirectory.absolutePath}\");\n",
        )
        val previousDownloads = Os.getenv("DOWNLOADS_DIRECTORY")
        val previousCcDirectory = Os.getenv("MOZ_CC_LOG_DIRECTORY")
        val requests = AtomicInteger()
        try {
            // UiAutomation enables accessibility, matching the instrumented Candy capture.
            instrumentation.uiAutomation
            EdgeToEdgeSiteFixtureServer { target ->
                val index = target.substringAfter("/raw/", "").toIntOrNull()
                if (index != null) {
                    requests.incrementAndGet()
                    "<html><title>raw-ready-$index</title><body><h1>Raw Gecko $index</h1>" +
                        "<main>" + "<p>Controlled local content</p>".repeat(200) + "</main></body></html>"
                } else {
                    "<html></html>"
                }
            }.use { server ->
                ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                    lateinit var runtime: GeckoRuntime
                    scenario.onActivity {
                        runtime = GeckoRuntime.create(
                            context.applicationContext,
                            GeckoRuntimeSettings.Builder()
                                .arguments(arrayOf("-profile", profile.absolutePath))
                                .build(),
                        )
                    }
                    loadSessions(scenario, runtime, server)
                    assertEquals(SESSION_COUNT, requests.get())
                    assertFalse("Raw control unexpectedly created Candy runtime", GeckoRuntimeOwner.hasRuntimeForTesting())
                    Os.setenv("DOWNLOADS_DIRECTORY", directory.absolutePath, true)
                    repeat(30) { SystemClock.sleep(1_000L) }
                    capture("settled-30", requests.get())
                    captureNative("settled-30", minimize = false)
                    collectJava()
                    capture("after-java-gc", requests.get())
                    Debug.dumpHprofData(File(directory, "after-java-gc.hprof").absolutePath)
                    captureNative("after-java-gc", minimize = false)
                    captureNative("native-minimize", minimize = true)
                    collectJava()
                    capture("after-native-minimize", requests.get())
                    Debug.dumpHprofData(File(directory, "after-native-minimize.hprof").absolutePath)
                    if (captureGcLogs) captureRetainingLogs()
                }
            }
            writeSummary(complete = true)
        } finally {
            if (previousDownloads == null) {
                Os.unsetenv("DOWNLOADS_DIRECTORY")
            } else {
                Os.setenv("DOWNLOADS_DIRECTORY", previousDownloads, true)
            }
            if (previousCcDirectory == null) {
                Os.unsetenv("MOZ_CC_LOG_DIRECTORY")
            } else {
                Os.setenv("MOZ_CC_LOG_DIRECTORY", previousCcDirectory, true)
            }
            writeSummary(complete = File(directory, "complete").exists())
        }
    }

    private fun loadSessions(
        scenario: ActivityScenario<GeckoScrollTestActivity>,
        runtime: GeckoRuntime,
        server: EdgeToEdgeSiteFixtureServer,
    ) {
        var sharedSession: GeckoSession? = null
        if (reuseSession) {
            scenario.onActivity { sharedSession = GeckoSession() }
        }
        for (index in 0 until SESSION_COUNT) {
            loadAndClose(scenario, runtime, server.fixtureUrl("/raw/$index"), index, sharedSession)
        }
        // The optional shared session is also released before settling or collecting.
    }

    private fun loadAndClose(
        scenario: ActivityScenario<GeckoScrollTestActivity>,
        runtime: GeckoRuntime,
        url: String,
        index: Int,
        sharedSession: GeckoSession?,
    ) {
        val loaded = CountDownLatch(1)
        val titled = CountDownLatch(1)
        val presented = CountDownLatch(1)
        lateinit var session: GeckoSession
        lateinit var view: GeckoView
        scenario.onActivity { activity ->
            session = sharedSession ?: GeckoSession()
            session.progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    if (success) loaded.countDown()
                }
            }
            session.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {
                    if (title == "raw-ready-$index") titled.countDown()
                }

                override fun onFirstComposite(session: GeckoSession) {
                    presented.countDown()
                }
            }
            session.open(runtime)
            view = GeckoView(activity)
            view.setSession(session)
            activity.setContentView(view)
            session.loadUri(url)
        }
        try {
            assertTrue("Raw document $index did not load", loaded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue("Raw document $index title was not fresh", titled.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue("Raw document $index was not presented", presented.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        } finally {
            scenario.onActivity { activity ->
                session.progressDelegate = null
                session.contentDelegate = null
                session.setActive(false)
                view.releaseSession()
                activity.setContentView(FrameLayout(activity))
                session.close()
                if (sessions.none { it.get() === session }) {
                    sessions += WeakReference(session)
                }
                views += WeakReference(view)
            }
        }
        // All strong session/view locals disappear when this helper returns, before settling or GC.
    }

    private fun collectJava() {
        Runtime.getRuntime().gc()
        System.runFinalization()
        Runtime.getRuntime().gc()
        SystemClock.sleep(1_000L)
    }

    private fun capture(name: String, requestCount: Int) {
        val sample = JSONObject()
            .put("name", name)
            .put("mainPid", Process.myPid())
            .put("documentRequests", requestCount)
            .put("trackedSessionCount", sessions.size)
            .put("weakSessionAliveCount", sessions.count { it.get() != null })
            .put("weakViewAliveCount", views.count { it.get() != null })
            .put("candyRuntimeCreated", GeckoRuntimeOwner.hasRuntimeForTesting())
        snapshots.put(sample)
        writeSummary()
    }

    private fun captureNative(name: String, minimize: Boolean) {
        val reportDirectory = File(directory, "memory-reports")
        val previous = reportDirectory.listFiles().orEmpty().map { it.name }.toSet()
        sendNativeCommand(if (minimize) "minimize memory report\n" else "memory report\n")
        var report: File? = null
        awaitCondition("Raw control native report $name did not finish") {
            report = reportDirectory.listFiles().orEmpty().firstOrNull {
                it.name.startsWith("unified-memory-report-") && it.name.endsWith(".gz") && it.name !in previous
            }
            runCatching {
                GZIPInputStream(requireNotNull(report).inputStream()).use { stream ->
                    val buffer = ByteArray(8_192)
                    while (stream.read(buffer) >= 0) {
                        // Validate the completed gzip without parsing report strings in the app.
                    }
                }
                true
            }.getOrDefault(false)
        }
        requireNotNull(report).copyTo(File(directory, "$name.json.gz"), overwrite = true)
    }

    private fun sendNativeCommand(command: String) {
        val fifo = File(context.cacheDir, "gecko_temp/debug_info_trigger")
        assertTrue(
            "Raw control FIFO did not start",
            fifo.exists() && OsConstants.S_ISFIFO(Os.stat(fifo.absolutePath).st_mode),
        )
        val descriptor = Os.open(fifo.absolutePath, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK, 0)
        try {
            val bytes = command.toByteArray()
            assertEquals(bytes.size, Os.write(descriptor, bytes, 0, bytes.size))
        } finally {
            Os.close(descriptor)
        }
    }

    private fun captureRetainingLogs() {
        val logDirectory = File(directory, "gc-cc-logs").apply {
            mkdirs()
            Os.chmod(absolutePath, 448)
        }
        val contentPids = contentProcessPids()
        assertTrue("Raw control native report did not identify a content process", contentPids.isNotEmpty())
        val expectedPids = contentPids + Process.myPid()
        Os.setenv("MOZ_CC_LOG_DIRECTORY", logDirectory.absolutePath, true)
        sendNativeCommand("gc log\n")
        // Android's log sink creates this subdirectory below the environment override.
        val reportDirectory = File(logDirectory, "memory-reports")
        val logPattern = Regex("(gc|cc)-edges\\.(\\d+)\\.\\d+\\.log")
        awaitCondition("Raw control GC/CC parent or content logs did not complete") {
            val files = reportDirectory.listFiles().orEmpty()
            val completed = files.mapNotNull { file ->
                logPattern.matchEntire(file.name)?.takeIf { file.length() > 0L }
            }
            files.none { it.name.startsWith("incomplete-") } && expectedPids.all { pid ->
                completed.any { it.groupValues[1] == "gc" && it.groupValues[2].toInt() == pid } &&
                    completed.any { it.groupValues[1] == "cc" && it.groupValues[2].toInt() == pid }
            }
        }
        File(directory, "gc-cc-summary.json").writeText(
            JSONObject()
                .put("parentPid", Process.myPid())
                .put("expectedContentPids", JSONArray(expectedPids.filter { it != Process.myPid() }))
                .put("completeGcLogCount", reportDirectory.listFiles().orEmpty().count { it.name.startsWith("gc-edges.") })
                .put("completeCcLogCount", reportDirectory.listFiles().orEmpty().count { it.name.startsWith("cc-edges.") })
                .put("allTraces", true)
                .toString(2),
        )
    }

    private fun contentProcessPids(): Set<Int> {
        val pids = mutableSetOf<Int>()
        GZIPInputStream(File(directory, "native-minimize.json.gz").inputStream()).use { input ->
            JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() != "reports") {
                        reader.skipValue()
                        continue
                    }
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            if (reader.nextName() == "process") {
                                val process = reader.nextString()
                                if (process.startsWith("web (pid ") || process.contains("Web Content")) {
                                    Regex("\\(pid (\\d+)\\)$").find(process)?.groupValues?.get(1)
                                        ?.toIntOrNull()?.let(pids::add)
                                }
                            } else {
                                reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    reader.endArray()
                }
                reader.endObject()
            }
        }
        return pids
    }

    private fun writeSummary(complete: Boolean = false) {
        if (complete) File(directory, "complete").writeText("complete")
        File(directory, "summary.json").writeText(
            JSONObject()
                .put("mainPid", Process.myPid())
                .put("completed", complete)
                .put("sessionsLoadedAndClosed", SESSION_COUNT)
                .put("reuseSession", reuseSession)
                .put("captureGcLogs", captureGcLogs)
                .put("snapshots", snapshots)
                .put("profileArguments", JSONArray(listOf("-profile", "app-private gecko-only-closed/profile")))
                .put(
                    "startupPreferences",
                    JSONArray(listOf(
                        "memory_info_dumper.watch_fifo.enabled=true",
                        "memory_info_dumper.watch_fifo.directory=app-private cache/gecko_temp",
                    )),
                )
                .put(
                    "notes",
                    "Direct GeckoRuntime/GeckoSession/GeckoView only. Test delegates clear before close. " +
                        "All session/view capture fields are weak. Java GC precedes first HPROF; " +
                        "native minimize is separate. " +
                        "Instrumentation/UiAutomation enables accessibility and raises process importance.",
                )
                .toString(2),
        )
    }

    private fun awaitCondition(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_SECONDS * 1_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100L)
        }
        assertTrue(message, predicate())
    }

    private companion object {
        const val SESSION_COUNT = 20
        const val TIMEOUT_SECONDS = 60L
    }
}
