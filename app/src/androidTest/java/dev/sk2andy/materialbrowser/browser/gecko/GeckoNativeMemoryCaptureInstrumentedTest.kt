package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.content.Intent
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in native capture on an isolated emulator: -e candyMemoryCapture true. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoNativeMemoryCaptureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun captureNativeReportAroundLiveCandyDomChurnAndBackground() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("candyMemoryCapture") == "true")
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        val directory = File(context.cacheDir, "native-memory-capture").apply { mkdirs() }
        // Repeated opt-in runs replace only this harness's known output files. A failed run must
        // not expose a previous APK's stable snapshot as evidence for the current PID.
        listOf(
            "before-dom-churn.json.gz", "after-dom-churn.json.gz", "while-background.json.gz",
            "after-background-resume.json.gz", "after-minimize.json.gz", "capture-summary.json",
            "after-background-resume.hprof", "after-minimize.hprof",
        ).forEach { File(directory, it).delete() }
        val originalEnvironment = Os.getenv("DOWNLOADS_DIRECTORY")
        val command = AtomicReference("idle")
        val snapshots = mutableListOf<JSONObject>()
        val warmupSeconds = InstrumentationRegistry.getArguments().getString("candyMemoryWarmupSeconds")
            ?.toIntOrNull() ?: 60
        require(warmupSeconds in 0..600)
        val preferences = context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val originalPreferences = preferences.all

        try {
            preferences.edit().clear().commit()
            val store = BrowserSessionStore(context)
            GestureOnboardingStore(context).markCompleted()
            ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
            store.saveStartupAnimationEnabled(false)
            store.saveOpenHomeOnStartupEnabled(false)
            store.saveExternalLinkPreviewEnabled(false)
            store.saveSearchSuggestionProvider(SearchSuggestionProvider.None)
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))

            EdgeToEdgeSiteFixtureServer { target ->
                if (target.substringBefore('?') == "/memory-command") command.get() else FIXTURE_HTML
            }.use { server ->
                val tab = BrowserTab(
                    id = "native-memory-fixture",
                    lastAccessedAt = System.currentTimeMillis(),
                    url = server.fixtureUrl("/site-matrix/native-memory"),
                )
                assertTrue(store.saveTabsImmediately(listOf(tab), tab.id))
                val intent = Intent(context, MainActivity::class.java)
                    .setAction("dev.sk2andy.materialbrowser.test.NATIVE_MEMORY_CAPTURE")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    awaitController(scenario, "Native fixture did not load") {
                        it.selectedTab.title == "memory-ready" && !it.selectedTab.isLoading
                    }
                    // Default extensions install asynchronously. Let both baseline and changed
                    // APKs settle the same controlled fixture before the first native snapshot.
                    repeat(warmupSeconds) { SystemClock.sleep(1_000L) }
                    // GeckoLoader sets its downloads environment during boot. Override afterwards,
                    // before the first native save, so raw reports stay in this isolated app cache.
                    Os.setenv("DOWNLOADS_DIRECTORY", directory.absolutePath, true)
                    var diagnosticTabId = ""
                    scenario.onActivity { activity ->
                        val controller = activity.browserControllerForTesting()
                        assertTrue("Controlled capture must not include private tabs", controller.tabs.none { it.isIncognito })
                        diagnosticTabId = controller.createTab(initialUrl = "about:memory", isIncognito = false)
                    }
                    requireNativeFifo()
                    snapshots += captureNative(scenario, directory, "before-dom-churn")

                    scenario.onActivity { it.browserControllerForTesting().selectTab(tab.id) }
                    command.set("churn")
                    device.wait(Until.findObject(By.text("Run DOM churn")), 5_000L)?.click()
                    awaitController(scenario, "DOM churn did not finish") { it.selectedTab.title == "memory-churn-100" }
                    scenario.onActivity { it.browserControllerForTesting().selectTab(diagnosticTabId) }
                    snapshots += captureNative(scenario, directory, "after-dom-churn")

                    scenario.onActivity { it.browserControllerForTesting().selectTab(tab.id) }
                    awaitController(scenario, "Fixture did not resume before background") {
                        it.selectedTab.title == "memory-churn-100" && !it.selectedTab.isLoading
                    }
                    snapshots += captureAndroid("before-background")
                    assertTrue("Home navigation failed", device.pressHome())
                    awaitCondition("Target process remained foreground after Home") {
                        !processLifecycleState().isAtLeast(Lifecycle.State.STARTED)
                    }
                    SystemClock.sleep(BACKGROUND_MILLIS)
                    snapshots += captureNativeFifo(directory, "while-background")
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .setAction("dev.sk2andy.materialbrowser.test.NATIVE_MEMORY_CAPTURE")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    )
                    awaitCondition("Target process did not foreground on resume") {
                        processLifecycleState().isAtLeast(Lifecycle.State.STARTED)
                    }
                    awaitController(scenario, "Fixture was lost across background") {
                        it.selectedTab.title == "memory-churn-100" && !it.selectedTab.isLoading
                    }
                    scenario.onActivity { it.browserControllerForTesting().selectTab(diagnosticTabId) }
                    snapshots += captureNative(scenario, directory, "after-background-resume")
                    snapshots += captureNativeFifo(directory, "after-minimize", minimizeMemory = true)
                    snapshots += captureAndroid("after-minimize-android")
                    val hprof = File(directory, "after-minimize.hprof")
                    Debug.dumpHprofData(hprof.absolutePath)
                }
            }
        } finally {
            try {
                File(directory, "capture-summary.json").writeText(
                    JSONObject()
                        .put("package", context.packageName)
                        .put("mainPid", Process.myPid())
                        .put("domCycles", 100)
                        .put("warmupSeconds", warmupSeconds)
                        .put("backgroundMillis", BACKGROUND_MILLIS)
                        .put("snapshots", org.json.JSONArray(snapshots))
                        .put("backgroundNativeCaptured", snapshots.any {
                            it.optString("name") == "while-background" && it.has("nativeFile")
                        })
                        .put("notes", "First four native FIFO snapshots collect parent and children without forced Gecko GC. " +
                            "Fifth snapshot explicitly minimizes Gecko memory. HPROF follows all native snapshots and may collect Java heap. " +
                            "after-minimize-android samples after native report parsing and includes parser overhead. " +
                            "DOM fixture creates fixed-header candidates but does not assert Candy ownership. " +
                            "60-second background samples after the default 30-second grace interval. Raw reports remain app-private.")
                        .toString(2),
                )
            } finally {
                try {
                    if (originalEnvironment == null) Os.unsetenv("DOWNLOADS_DIRECTORY")
                    else Os.setenv("DOWNLOADS_DIRECTORY", originalEnvironment, true)
                } finally {
                    val restored = preferences.edit().clear()
                    originalPreferences.forEach { (key, value) ->
                        when (value) {
                            is String -> restored.putString(key, value)
                            is Boolean -> restored.putBoolean(key, value)
                            is Int -> restored.putInt(key, value)
                            is Long -> restored.putLong(key, value)
                            is Float -> restored.putFloat(key, value)
                            is Set<*> -> restored.putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                    restored.commit()
                }
            }
        }
    }

    private fun captureNative(
        scenario: ActivityScenario<MainActivity>,
        directory: File,
        name: String,
    ): JSONObject {
        awaitController(scenario, "Native memory page did not load") {
            it.selectedTab.url == "about:memory" && !it.selectedTab.isLoading
        }
        return captureNativeFifo(directory, name)
    }

    private fun requireNativeFifo() {
        val fifo = File(context.cacheDir, "gecko_temp/debug_info_trigger")
        val alreadyWatching = File("/proc/self/fd").listFiles().orEmpty().any { descriptor ->
            runCatching {
                File(Os.readlink(descriptor.absolutePath)).canonicalFile == fifo.canonicalFile
            }.getOrDefault(false)
        }
        assertTrue(
            "Native capture requires startup user.js prefs in the isolated Gecko profile before launch: " +
                "memory_info_dumper.watch_fifo.enabled=true and watch_fifo.directory set to app cache/gecko_temp. " +
                "An on-disk FIFO without a reader in this process is insufficient.",
            alreadyWatching && fifo.exists(),
        )
    }

    private fun captureNativeFifo(
        directory: File,
        name: String,
        minimizeMemory: Boolean = false,
    ): JSONObject {
        val androidSnapshot = captureAndroid(name)
        val fifo = File(context.cacheDir, "gecko_temp/debug_info_trigger")
        assertTrue("Native memory trigger must be a FIFO", OsConstants.S_ISFIFO(Os.stat(fifo.absolutePath).st_mode))
        val reportDirectory = File(directory, "memory-reports")
        val previousFiles = reportDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()
        val descriptor = Os.open(fifo.absolutePath, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK, 0)
        try {
            val command = (if (minimizeMemory) "minimize memory report\n" else "memory report\n")
                .toByteArray(Charsets.UTF_8)
            assertTrue("Native FIFO command was not written completely", Os.write(descriptor, command, 0, command.size) == command.size)
        } finally {
            Os.close(descriptor)
        }
        var report: File? = null
        var nativeRoot: JSONObject? = null
        awaitCondition("Native memory report was not saved to app-private cache") {
            report = reportDirectory.listFiles()?.firstOrNull {
                it.name.startsWith("unified-memory-report-") && it.name.endsWith(".gz") && it.name !in previousFiles
            }
            nativeRoot = runCatching {
                GZIPInputStream(requireNotNull(report).inputStream()).bufferedReader().use { JSONObject(it.readText()) }
            }.getOrNull()
            nativeRoot?.optInt("version") == 1
        }
        val root = requireNotNull(nativeRoot)
        val reports = root.getJSONArray("reports")
        val processes = mutableSetOf<String>()
        for (index in 0 until reports.length()) processes += reports.getJSONObject(index).getString("process")
        assertTrue("Native report omitted parent process", "Main Process (pid ${Process.myPid()})" in processes)
        val destination = File(directory, "$name.json.gz")
        requireNotNull(report).copyTo(destination, overwrite = true)
        return androidSnapshot
            .put("nativeFile", destination.name)
            .put("nativeMemoryMinimized", minimizeMemory)
            .put("androidSampleBeforeNativeCapture", true)
            .put("nativeProcessCount", processes.size)
            .put("nativeReportCount", reports.length())
    }

    private fun captureAndroid(name: String): JSONObject {
        val memory = Debug.MemoryInfo()
        Debug.getMemoryInfo(memory)
        val javaRuntime = Runtime.getRuntime()
        return JSONObject()
            .put("name", name)
            .put("elapsedMillis", SystemClock.elapsedRealtime())
            .put("mainPid", Process.myPid())
            .put("processLifecycleState", processLifecycleState().name)
            .put("mainTotalPssKiB", memory.totalPss)
            .put("mainTotalPrivateDirtyKiB", memory.totalPrivateDirty)
            .put("mainMemoryStats", JSONObject(memory.memoryStats))
            .put("javaUsedBytes", javaRuntime.totalMemory() - javaRuntime.freeMemory())
            .put("javaCommittedBytes", javaRuntime.totalMemory())
            .put("nativeAllocatedBytes", Debug.getNativeHeapAllocatedSize())
    }

    private fun processLifecycleState(): Lifecycle.State {
        var state = Lifecycle.State.INITIALIZED
        instrumentation.runOnMainSync { state = ProcessLifecycleOwner.get().lifecycle.currentState }
        return state
    }

    private fun awaitController(
        scenario: ActivityScenario<MainActivity>,
        message: String,
        condition: (BrowserController) -> Boolean,
    ) = awaitCondition(message) {
        var ready = false
        scenario.onActivity { ready = condition(it.browserControllerForTesting()) }
        ready
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50L)
        }
        assertTrue(message, condition())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 45_000L
        const val BACKGROUND_MILLIS = 60_000L
        const val FIXTURE_HTML = """
            <!doctype html>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>memory-ready</title>
            <body><h1>Candy native memory fixture</h1><button>Run DOM churn</button><main id="fixture"></main>
            <script>
            let finished = false;
            const fixture = document.getElementById('fixture');
            const poll = async () => {
              try {
                const command = await fetch('/memory-command', {cache: 'no-store'}).then(response => response.text());
                if (command === 'churn' && !finished) {
                  finished = true;
                  for (let cycle = 0; cycle < 100; cycle++) {
                    const block = document.createElement('section');
                    const header = document.createElement('header');
                    header.style.cssText = 'position:fixed;top:8px;left:0;right:0;height:48px;background:orange;z-index:100';
                    header.textContent = 'Fixed header ' + cycle;
                    block.appendChild(header);
                    for (let index = 0; index < 100; index++) {
                      const node = document.createElement('div');
                      node.textContent = 'fixture row ' + cycle + ':' + index;
                      node.className = 'memory-fixture-node';
                      block.appendChild(node);
                    }
                    fixture.appendChild(block);
                    block.getBoundingClientRect();
                    await new Promise(resolve => requestAnimationFrame(resolve));
                    block.remove();
                  }
                  document.title = 'memory-churn-100';
                }
              } finally {
                if (!finished) setTimeout(poll, 250);
              }
            };
            poll();
            </script></body>
        """
    }
}
