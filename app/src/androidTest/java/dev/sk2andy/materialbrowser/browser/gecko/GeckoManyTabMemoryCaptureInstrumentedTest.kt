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
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.AppLogEvent
import dev.sk2andy.materialbrowser.data.AppLogStore
import dev.sk2andy.materialbrowser.data.AppLogging
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated-emulator capture: -e candyManyTabMemoryCapture true -e candyMemoryHostAck true.
 * The host acknowledges each stage after its process-group sample or managed heap dump.
 * Instrumentation raises process importance; UI automation enables accessibility.
 * Home reaches ProcessLifecycle STOP, but the instrumented process is not normally cached or frozen.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoManyTabMemoryCaptureInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val snapshots = JSONArray()
    private val blankIframe = InstrumentationRegistry.getArguments().getString("candyMemoryBlankIframe") == "true"
    private val trackedSessions = linkedMapOf<Int, WeakReference<Any>>()
    private val trackedNativeSessions = linkedMapOf<Int, WeakReference<Any>>()
    private val trackedViews = linkedMapOf<Int, WeakReference<Any>>()
    private val directory by lazy {
        File(context.cacheDir, "many-tab-memory").apply {
            mkdirs()
            Os.chmod(absolutePath, 448)
        }
    }

    @Test
    fun captureNaturalManyTabBackgroundAndRepeatedUnloadCycles() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("candyManyTabMemoryCapture") == "true")
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        require(InstrumentationRegistry.getArguments().getString("candyMemoryHostAck") == "true")
        directory.listFiles().orEmpty().filter { it.isFile }.forEach(File::delete)
        val requests = Array(TAB_COUNT) { AtomicInteger() }
        val preferences = context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val previousPreferences = preferences.all
        val previousDownloads = Os.getenv("DOWNLOADS_DIRECTORY")
        try {
            preferences.edit().clear().commit()
            val store = BrowserSessionStore(context)
            GestureOnboardingStore(context).markCompleted()
            ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
            store.saveStartupAnimationEnabled(false)
            store.saveOpenHomeOnStartupEnabled(false)
            store.saveSearchSuggestionProvider(SearchSuggestionProvider.None)
            store.saveRecallEnabled(false)
            assertTrue(store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView))
            EdgeToEdgeSiteFixtureServer { target ->
                val index = target.substringBefore('?').removePrefix("/manytab/").toIntOrNull()
                if (index != null && index in requests.indices) {
                    requests[index].incrementAndGet()
                    fixtureHtml(index)
                } else {
                    "<html></html>"
                }
            }.use { server ->
                ActivityScenario.launch<MainActivity>(foregroundIntent(clearTask = true)).use { scenario ->
                    val tabs = mutableListOf<String>()
                    scenario.onActivity { activity ->
                        AppLogging.setEnabled(true)
                        AppLogging.clear()
                        val controller = activity.browserControllerForTesting()
                        assertEquals(10, controller.residentTabLimit)
                        assertEquals(
                            3,
                            controller.developerSettings.browserMemorySettings.foregroundTabIdleTimeoutMinutes,
                        )
                        assertEquals(0, controller.developerSettings.browserMemorySettings.backgroundWarmTabCount)
                        tabs += controller.selectedTabId
                        controller.submitAddress(server.fixtureUrl("/manytab/0"))
                    }
                    awaitLoaded(scenario, 0, 0, requests)
                    // Install/filter initialization precedes the measured 20-tab workload.
                    repeat(60) { SystemClock.sleep(1_000L) }
                    Os.setenv("DOWNLOADS_DIRECTORY", directory.absolutePath, true)
                    requireNativeFifo()
                    for (index in 1 until TAB_COUNT) {
                        scenario.onActivity { activity ->
                            tabs += activity.browserControllerForTesting().createTab(
                                initialUrl = server.fixtureUrl("/manytab/$index"),
                                isIncognito = false,
                            )
                        }
                        awaitLoaded(scenario, index, 0, requests)
                    }
                    awaitController(scenario, "Capacity guards did not settle") {
                        (blankIframe || it.residentTabIdsForTesting().size <= 10) &&
                            countField(it, "pendingMemorySessionChecks") == 0
                    }
                    capture(scenario, "natural-fg", requests, native = true)
                    val backgroundStart = enterBackground()
                    for (seconds in if (blankIframe) listOf(5, 30, 120) else listOf(5, 30, 120, 300)) {
                        waitUntil(backgroundStart + seconds * 1_000L)
                        capture(
                            scenario,
                            "natural-bg-$seconds",
                            requests,
                            native = seconds == 30 || seconds == (if (blankIframe) 120 else 300),
                            backgroundStart = backgroundStart,
                        )
                    }
                    repeat(if (blankIframe) 0 else 4) { cycle ->
                        context.startActivity(foregroundIntent())
                        awaitCondition("Process did not return to foreground") {
                            lifecycle().isAtLeast(Lifecycle.State.STARTED)
                        }
                        for (index in TAB_COUNT - 10 until TAB_COUNT) {
                            val previousRequests = requests[index].get()
                            var resident = false
                            var previousTitle: String? = null
                            scenario.onActivity { activity ->
                                val controller = activity.browserControllerForTesting()
                                resident = controller.isTabSessionResident(tabs[index])
                                previousTitle = controller.tabs.first { it.id == tabs[index] }.title
                                controller.selectTab(tabs[index])
                            }
                            awaitLoaded(
                                scenario,
                                index,
                                if (resident) -1 else previousRequests,
                                requests,
                                previousTitle.takeUnless { resident },
                            )
                        }
                        capture(scenario, "cycle-${cycle + 1}-fg", requests, native = true)
                        val start = enterBackground()
                        waitUntil(start + 30_000L)
                        capture(
                            scenario,
                            "cycle-${cycle + 1}-bg-30",
                            requests,
                            native = true,
                            backgroundStart = start,
                        )
                    }
                    // Host takes managed HPROF without -g, then with -g, only after natural phases.
                    hostPhase("java-hprof-no-explicit-gc", action = "hprof")
                    capture(scenario, "post-java-hprof", requests, native = true)
                    hostPhase("java-hprof-explicit-gc", action = "hprof-gc")
                    capture(scenario, "post-java-gc", requests, native = true)
                    capture(scenario, "gecko-minimize", requests, native = true, minimize = true)
                    capture(scenario, "post-gecko-minimize", requests)
                    hostPhase("java-hprof-post-gecko-minimize", action = "hprof")
                }
            }
            writeSummary(complete = true)
        } finally {
            if (previousDownloads == null) {
                Os.unsetenv("DOWNLOADS_DIRECTORY")
            } else {
                Os.setenv("DOWNLOADS_DIRECTORY", previousDownloads, true)
            }
            val restored = preferences.edit().clear()
            previousPreferences.forEach { (key, value) ->
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
            AppLogging.setEnabled(BrowserSessionStore(context).loadDeveloperSettings().appLoggingEnabled)
            writeSummary(complete = File(directory, "complete").exists())
        }
    }

    private fun capture(
        scenario: ActivityScenario<MainActivity>,
        name: String,
        requests: Array<AtomicInteger>,
        native: Boolean = false,
        minimize: Boolean = false,
        backgroundStart: Long? = null,
    ) {
        // These are sequential snapshots, not an atomic Android/native/process-group capture.
        // Debug total PSS includes SwapPss; the external smaps Pss excludes swap.
        val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        val sample = JSONObject()
            .put("name", name)
            .put("elapsedMillis", SystemClock.elapsedRealtime())
            .put("mainPid", Process.myPid())
            .put("processLifecycleState", lifecycle().name)
            .put("mainTotalPssKiB", memory.totalPss)
            .put("mainPrivateDirtyKiB", memory.totalPrivateDirty)
            .put("mainMemoryStats", JSONObject(memory.memoryStats))
            .put("javaUsedBytes", runtime.totalMemory() - runtime.freeMemory())
            .put("javaCommittedBytes", runtime.totalMemory())
            .put("nativeAllocatedBytes", Debug.getNativeHeapAllocatedSize())
            .put("documentRequests", requests.sumOf { it.get() })
            .put("hostSamplingSynchronized", true)
        if (backgroundStart != null) {
            sample.put("backgroundElapsedMillis", SystemClock.elapsedRealtime() - backgroundStart)
        }
        scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            assertTrue(controller.tabs.none { it.isIncognito })
            val sessions = field(controller, "browserEngineSessions") as Map<*, *>
            sessions.values.filterNotNull().forEach(::trackSession)
            val bindings = field(controller, "geckoViewBindings") as Map<*, *>
            bindings.values.filterNotNull().forEach { binding ->
                field(binding, "view")?.let {
                    trackedViews.putIfAbsent(System.identityHashCode(it), WeakReference(it))
                }
            }
            sample.put("tabCount", controller.tabs.size)
                .put("residentSessionCount", sessions.size)
                .put("residentSessionIds", JSONArray(sessions.values.map { System.identityHashCode(it) }))
                .put("viewBindingCount", bindings.size)
                .put("pendingMemoryGuardCount", countField(controller, "pendingMemorySessionChecks"))
                .put("pendingPreviewCount", countField(controller, "pendingGeckoPreviewCaptures"))
                .put("pendingViewAttachCount", countField(controller, "pendingGeckoViewAttachRetries"))
                .put("isAppInBackground", field(controller, "isAppInBackground"))
                .put("backgroundBudgetApplied", field(controller, "backgroundSessionBudgetApplied"))
                .put("uiMemoryTrimmed", field(controller, "uiMemoryTrimmed"))
                .put("trackedAdapterCount", trackedSessions.size)
                .put("weakAdapterAliveCount", trackedSessions.values.count { it.get() != null })
                .put("trackedNativeSessionCount", trackedNativeSessions.size)
                .put("weakNativeSessionAliveCount", trackedNativeSessions.values.count { it.get() != null })
                .put(
                    "weakClosedNativeSessionAliveCount",
                    trackedNativeSessions.values.count { reference ->
                        reference.get()?.let { field(it, "mWindow") == null } == true
                    },
                )
                .put(
                    "nativeSessionQueuedCallCount",
                    trackedNativeSessions.values.sumOf { reference ->
                        reference.get()?.let(::nativeQueuedCallCount) ?: 0
                    },
                )
                .put("trackedViewCount", trackedViews.size)
                .put("weakViewAliveCount", trackedViews.values.count { it.get() != null })
        }
        instrumentation.runOnMainSync {
            val runtimeOwner = field(GeckoRuntimeOwner, "runtime")
            val privacy = runtimeOwner?.let { field(it, "privacyHost") }
            if (privacy != null) {
                val bindings = field(privacy, "bindings") as Map<*, *>
                sample.put("privacyBindingCount", bindings.size)
                    .put("privacyPendingUntilReadyCount", (field(privacy, "pendingUntilReady") as List<*>).size)
                    .put(
                        "privacyPendingInputQueryCount",
                        bindings.values.filterNotNull().count {
                            field(requireNotNull(field(it, "userInputRequest")), "result") != null
                        },
                    )
            }
        }
        val logs = (field(AppLogging, "store") as? AppLogStore)?.snapshot().orEmpty()
        sample.put(
            "completedBackgroundGuardOutcomeEventCounts",
            JSONObject().apply {
                put(
                    "falseUnloaded",
                    logs.lineSequence().count {
                        it.endsWith(" " + AppLogEvent.BackgroundMemorySessionUnloaded.name)
                    },
                )
                put(
                    "trueProtected",
                    logs.lineSequence().count {
                        it.endsWith(" " + AppLogEvent.BackgroundMemoryInputProtected.name)
                    },
                )
                put(
                    "unknownAfterRetries",
                    logs.lineSequence().count {
                        it.endsWith(" " + AppLogEvent.BackgroundMemoryInputUnknown.name)
                    },
                )
                put(
                    "scope",
                    "Cumulative completed production outcome events; " +
                        "intermediate null attempts and queued log writes are not counted",
                )
            },
        )
        snapshots.put(sample)
        writeSummary()
        hostPhase(name, action = "memory")
        // Native reporters touch memory; separate input probes run last and can wake Gecko.
        // Later natural samples therefore include earlier diagnostic wakeups.
        if (native) {
            val start = SystemClock.elapsedRealtime()
            val saved = captureNative(name, minimize)
            sample.put("nativeFile", saved.name)
                .put("nativeMemoryMinimized", minimize)
                .put("nativeCaptureDurationMillis", SystemClock.elapsedRealtime() - start)
            sample.put("separateUserInputProbe", probeUserInput(scenario))
            writeSummary()
        }
    }

    private fun awaitLoaded(
        scenario: ActivityScenario<MainActivity>,
        index: Int,
        previousRequests: Int,
        requests: Array<AtomicInteger>,
        previousTitle: String? = null,
    ) {
        awaitController(scenario, "Fresh heavy fixture $index did not finish") { controller ->
            val ready = controller.selectedTab.title.startsWith("many-ready:$index:") && !controller.selectedTab.isLoading
            if (ready) {
                (field(controller, "browserEngineSessions") as Map<*, *>).values.filterNotNull().forEach {
                    trackSession(it)
                }
                (field(controller, "geckoViewBindings") as Map<*, *>).values.filterNotNull().forEach { binding ->
                    field(binding, "view")?.let {
                        trackedViews.putIfAbsent(System.identityHashCode(it), WeakReference(it))
                    }
                }
            }
            ready && (previousTitle == null || controller.selectedTab.title != previousTitle) &&
                (previousRequests == -1 || requests[index].get() > previousRequests)
        }
    }

    /** Separate probes after the memory sample, never intercepting production guard callbacks. */
    private fun probeUserInput(scenario: ActivityScenario<MainActivity>): JSONObject {
        awaitController(scenario, "Production guards did not settle before separate probes") {
            countField(it, "pendingMemorySessionChecks") == 0
        }
        val counts = Array(3) { AtomicInteger() }
        val remaining = AtomicInteger()
        val start = SystemClock.elapsedRealtime()
        scenario.onActivity { activity ->
            val sessions = field(activity.browserControllerForTesting(), "browserEngineSessions") as Map<*, *>
            remaining.set(sessions.size)
            sessions.values.filterIsInstance<AndroidBrowserEngineSessionPort>().forEach { session ->
                session.containsUserInput { result ->
                    val resultIndex = when (result) {
                        true -> 0
                        false -> 1
                        null -> 2
                    }
                    counts[resultIndex].incrementAndGet()
                    remaining.decrementAndGet()
                }
            }
        }
        awaitCondition("Separate user-input probes did not finish") { remaining.get() == 0 }
        return JSONObject()
            .put("trueCount", counts[0].get())
            .put("falseCount", counts[1].get())
            .put("unknownCount", counts[2].get())
            .put("durationMillis", SystemClock.elapsedRealtime() - start)
            .put(
                "scope",
                "Separate current-resident queries after sample; production eviction callbacks are not intercepted",
            )
    }

    private fun enterBackground(): Long {
        assertTrue("Home navigation failed", device.pressHome())
        awaitCondition("Process remained foreground after Home") { !lifecycle().isAtLeast(Lifecycle.State.STARTED) }
        return SystemClock.elapsedRealtime()
    }

    private fun foregroundIntent(clearTask: Boolean = false) = Intent(context, MainActivity::class.java)
        .setAction("dev.sk2andy.materialbrowser.test.MANY_TAB_MEMORY_CAPTURE")
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )

    private fun hostPhase(name: String, action: String) {
        val marker = JSONObject()
            .put("name", name)
            .put("action", action)
            .put("mainPid", Process.myPid())
            .put("elapsedMillis", SystemClock.elapsedRealtime())
            .put("package", context.packageName)
        File(directory, "stage.part").writeText(marker.toString())
        assertTrue(File(directory, "stage.part").renameTo(File(directory, "stage.json")))
        awaitCondition("External host sampler did not acknowledge $name") { File(directory, "ack-$name").exists() }
    }

    private fun requireNativeFifo() {
        val fifo = File(context.cacheDir, "gecko_temp/debug_info_trigger")
        assertTrue(
            "Native FIFO was not enabled before Gecko startup",
            fifo.exists() && File("/proc/self/fd").listFiles().orEmpty().any { descriptor ->
                runCatching {
                    File(Os.readlink(descriptor.absolutePath)).canonicalFile == fifo.canonicalFile
                }.getOrDefault(false)
            },
        )
    }

    private fun captureNative(name: String, minimize: Boolean): File {
        val fifo = File(context.cacheDir, "gecko_temp/debug_info_trigger")
        assertTrue(OsConstants.S_ISFIFO(Os.stat(fifo.absolutePath).st_mode))
        val reportDirectory = File(directory, "memory-reports")
        val previous = reportDirectory.listFiles().orEmpty().map { it.name }.toSet()
        val descriptor = Os.open(fifo.absolutePath, OsConstants.O_WRONLY or OsConstants.O_NONBLOCK, 0)
        try {
            val command = (if (minimize) "minimize memory report\n" else "memory report\n").toByteArray()
            assertEquals(command.size, Os.write(descriptor, command, 0, command.size))
        } finally {
            Os.close(descriptor)
        }
        var saved: File? = null
        awaitCondition("Native report $name was not completed") {
            saved = reportDirectory.listFiles().orEmpty().firstOrNull {
                it.name.startsWith("unified-memory-report-") && it.name.endsWith(".gz") && it.name !in previous
            }
            runCatching {
                GZIPInputStream(requireNotNull(saved).inputStream()).use { stream ->
                    val buffer = ByteArray(8_192)
                    while (stream.read(buffer) >= 0) {
                        // Validate gzip completion without building a JSON object graph.
                    }
                }
                true
            }.getOrDefault(false)
        }
        return requireNotNull(saved).copyTo(File(directory, "$name.json.gz"), overwrite = true)
    }

    private fun writeSummary(complete: Boolean = false) {
        if (complete) File(directory, "complete").writeText("complete")
        File(directory, "summary.json").writeText(
            JSONObject()
                .put("package", context.packageName)
                .put("mainPid", Process.myPid())
                .put("tabCount", TAB_COUNT)
                .put("jsBytesPerTab", 12 * 1_048_576)
                .put("domRowsPerTab", 2_500)
                .put("decodedImagesPerTab", 4)
                .put("imageWidth", 512)
                .put("imageHeight", 512)
                .put("blankIframePerTab", blankIframe)
                .put("completed", complete)
                .put("snapshots", snapshots)
                .put(
                    "notes",
                    "Natural phases precede Java HPROF and explicit GC. " +
                        "Native reports validated offline for exact parent PID. " +
                        "RSS sums double-count shared pages; Android total PSS may include SwapPss. " +
                        "Weak alive counts before GC do not prove leaks. " +
                        "Harness stores only WeakReferences to adapters/views. " +
                        "Separate user-input probes run after memory samples and may wake Gecko. " +
                        "Native gzip validation is streaming; JSON attribution is offline. " +
                        "Instrumentation, HTTP fixture and sampler wakeups are part of this controlled run, " +
                        "not a naturally frozen user process.",
                )
                .toString(2),
        )
    }

    private fun lifecycle(): Lifecycle.State {
        var state = Lifecycle.State.INITIALIZED
        instrumentation.runOnMainSync { state = ProcessLifecycleOwner.get().lifecycle.currentState }
        return state
    }

    private fun nativeQueuedCallCount(session: Any): Int {
        val queue = requireNotNull(field(session, "mNativeQueue"))
        return synchronized(queue) { (field(queue, "mQueue") as List<*>).size }
    }

    private fun trackSession(adapter: Any) {
        trackedSessions.putIfAbsent(System.identityHashCode(adapter), WeakReference(adapter))
        val native = field(requireNotNull(field(adapter, "session")), "session") ?: return
        trackedNativeSessions.putIfAbsent(System.identityHashCode(native), WeakReference(native))
    }

    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private fun countField(owner: Any, name: String) = (field(owner, name) as Map<*, *>).size

    private fun awaitController(
        scenario: ActivityScenario<MainActivity>,
        message: String,
        predicate: (BrowserController) -> Boolean,
    ) = awaitCondition(message) {
        var ready = false
        scenario.onActivity { ready = predicate(it.browserControllerForTesting()) }
        ready
    }

    private fun awaitCondition(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MILLIS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100L)
        }
        assertTrue(message, predicate())
    }

    private fun waitUntil(deadline: Long) {
        while (SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(minOf(1_000L, deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1))
        }
    }

    // Keep fixture source text stable: its bytes contribute to native script/source allocation reports.
    // The fixture server always binds 127.0.0.1, a trustworthy loopback origin for randomUUID().
    // Do not move this nonce probe to an ordinary HTTP origin without replacing its secure-context API.
    private fun fixtureHtml(index: Int) = """
        <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>many-loading:$index</title>
        <style>body{margin:0;font:16px sans-serif}header{position:sticky;top:0;background:#b7ddff}img{width:256px;height:256px}</style>
        <header>Controlled heavy page $index</header><main id="rows"></main><section id="images"></section>
        <script>
        (async()=>{
            const payload=new Uint8Array(12*1024*1024);payload.fill($index+1);
            window.manyTabPayload=payload;
            const fragment=document.createDocumentFragment();
            for(let i=0;i<2500;i++){const row=document.createElement('article');row.textContent=('row '+i+' ').padEnd(200,'x');
                const child=document.createElement('span');child.textContent=' detail '+i;row.appendChild(child);fragment.appendChild(row);}
            rows.appendChild(fragment);
            let seed=$index+1;
            for(let imageIndex=0;imageIndex<4;imageIndex++){
                const canvas=document.createElement('canvas');canvas.width=canvas.height=512;
                const ctx=canvas.getContext('2d');const data=ctx.createImageData(512,512);
                for(let i=0;i<data.data.length;i+=4){seed=(Math.imul(seed,1664525)+1013904223)|0;
                    data.data[i]=seed&255;data.data[i+1]=(seed>>>8)&255;data.data[i+2]=(seed>>>16)&255;data.data[i+3]=255;}
                ctx.putImageData(data,0,0);const image=new Image();image.src=canvas.toDataURL('image/png');images.appendChild(image);await image.decode();
            }
            ${if (blankIframe) "const frame=document.createElement('iframe');frame.src='about:blank';document.body.appendChild(frame);" else ""}
            await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
            document.title='many-ready:$index:'+crypto.randomUUID();
        })();
        </script>
    """.trimIndent()

    private companion object {
        const val TAB_COUNT = 20
        const val TIMEOUT_MILLIS = 90_000L
    }
}
