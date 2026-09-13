package dev.sk2andy.materialbrowser.browser.gecko

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.BrowserPerformanceTrace
import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.Executors
import org.mozilla.geckoview.ProfilerController

/** Explicit diagnostic builds only. Profiles stay local and may contain normal-page URLs. */
@UiThread
internal object GeckoPerformanceDiagnostics {
    private const val PROFILE_NAME = "gecko-profile.json.gz"
    private const val START_TIMEOUT_MS = 10_000L
    private const val START_POLL_MS = 100L
    private const val PROFILER_STATE_ACTION = "org.mozilla.fenix.PROFILER_STATE_CHANGED"
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val io by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "gecko-profile-io") }
    }
    private val sessions = IdentityHashMap<Any, Boolean>()
    private val listeners = mutableSetOf<() -> Unit>()
    private val gapListeners = mutableSetOf<() -> Unit>()
    private val fileLock = Any()
    private var applicationContext: Context? = null
    private var initialized = false
    private var stopPending = false

    @Volatile private var generation = 0L
    @Volatile private var hasPrivateSession = false
    @Volatile private var publishedGeneration: Long? = null
    @Volatile var isRecording = false
        private set
    @Volatile var isProfilerActive = false
        private set
    @Volatile var status = if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) "idle" else "disabled"
        private set

    private val profilerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != PROFILER_STATE_ACTION || !intent.hasExtra("isActive")) return
            isProfilerActive = intent.getBooleanExtra("isActive", false)
            if (isProfilerActive && !stopPending &&
                (hasPrivateSession || status !in setOf("starting", "recording"))
            ) {
                // An activation arriving after discard/error must not leave a hidden profiler
                // running forever, including after the canceled private owner has closed.
                cancelCapture(if (hasPrivateSession) "private_session" else "discarded")
            }
        }
    }

    fun addStateListener(listener: () -> Unit) {
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) listeners.add(listener)
    }

    fun removeStateListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun addGapListener(listener: () -> Unit) {
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) gapListeners.add(listener)
    }

    fun removeGapListener(listener: () -> Unit) {
        gapListeners.remove(listener)
    }

    /** Also removes profiles left by a killed diagnostic process or a replaced APK. */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        applicationContext = context.applicationContext
        if (BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) {
            context.applicationContext.registerReceiver(
                profilerStateReceiver,
                IntentFilter(PROFILER_STATE_ACTION),
                "${context.packageName}.permission.PROFILER_INTERNAL",
                handler,
                Context.RECEIVER_NOT_EXPORTED,
            )
        }
        if (!deleteProfiles(context.applicationContext)) updateStatus("cleanup_failed")
    }

    fun registerSession(owner: Any, isPrivate: Boolean) {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return
        sessions[owner] = isPrivate
        hasPrivateSession = sessions.values.any { it }
        if (hasPrivateSession) cancelCapture("private_session")
    }

    fun unregisterSession(owner: Any) {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return
        sessions.remove(owner)
        hasPrivateSession = sessions.values.any { it }
        if (!hasPrivateSession && status == "private_session" && !stopPending) updateStatus("idle")
    }

    fun start(context: Context): String {
        initialize(context)
        val rejection = GeckoPerformanceDiagnosticsRules.startRejection(
            enabled = BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS,
            hasRuntime = GeckoRuntimeOwner.hasRuntime(),
            hasPrivateSession = hasPrivateSession,
            isBusy = status in setOf("starting", "recording", "stopping", "exporting") || stopPending || profilerActive(),
        )
        if (rejection != null) {
            if (rejection != "busy") updateStatus(rejection)
            return rejection
        }
        generation++
        val captureGeneration = generation
        publishedGeneration = null
        if (!deleteProfiles(context.applicationContext)) {
            updateStatus("cleanup_failed")
            return status
        }
        updateStatus("starting")
        val started = runCatching {
            ProfilerController.startProfiler(
                arrayOf(
                    "GeckoMain",
                    "StyleThread",
                    "Renderer",
                    "Compositor",
                    "WRRenderBackend",
                    "Media",
                    "Video",
                    "AndroidUI",
                    "main",
                ),
                // Gecko 155 races Java MarkerStorage against sampler teardown. Native/JS
                // sampling avoids that library crash; Android UI work remains in Perfetto.
                arrayOf("js", "stackwalk", "cpuallthreads", "processcpu"),
            )
        }.isSuccess
        if (!started) {
            cancelCapture("error")
            return status
        }
        waitUntilActive(captureGeneration, START_TIMEOUT_MS / START_POLL_MS)
        handler.postDelayed({
            if (generation == captureGeneration && isRecording) stop(context.applicationContext)
        }, GeckoPerformanceDiagnosticsRules.MAX_CAPTURE_DURATION_MS)
        return status
    }

    fun stop(context: Context): String {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return "disabled"
        if (!isRecording || stopPending) return status
        val captureGeneration = generation
        BrowserPerformanceTrace.event(BrowserPerformanceTrace.Phase.DiagnosticsStop)
        isRecording = false
        stopPending = true
        updateStatus("stopping")
        runCatching {
            ProfilerController.stopProfiler().withHandler(handler).accept(
                { bytes ->
                    stopPending = false
                    profilerActive()
                    if (captureGeneration != generation || hasPrivateSession) {
                        finishCancellation()
                        return@accept
                    }
                    val rejection = GeckoPerformanceDiagnosticsRules.profileRejection(
                        compressedBytes = bytes?.size?.toLong() ?: 0L,
                        hasGzipHeader = bytes?.let(GeckoPerformanceDiagnosticsRules::hasGzipHeader) == true,
                    )
                    if (rejection != null || bytes == null) {
                        updateStatus(rejection ?: "error")
                        return@accept
                    }
                    updateStatus("exporting")
                    writeProfile(context.applicationContext, captureGeneration, bytes)
                },
                {
                    stopPending = false
                    if (captureGeneration == generation) cancelCapture("error") else finishCancellation()
                },
            )
        }.onFailure {
            stopPending = false
            if (captureGeneration == generation) cancelCapture("error") else finishCancellation()
        }
        return status
    }

    fun discard(context: Context): String {
        initialize(context)
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return "disabled"
        cancelCapture(if (hasPrivateSession) "private_session" else "discarded")
        return status
    }

    fun markGap(): Boolean {
        if (!isRecording) return false
        BrowserPerformanceTrace.event(BrowserPerformanceTrace.Phase.DiagnosticsGap)
        gapListeners.toList().forEach { it() }
        return true
    }

    fun profilerActive(): Boolean {
        return BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS && GeckoRuntimeOwner.hasRuntime() && isProfilerActive
    }

    @AnyThread
    fun profileForRead(context: Context): File? = synchronized(fileLock) {
        readableProfile(context)
    }

    @AnyThread
    fun openProfileForRead(context: Context): ParcelFileDescriptor? = synchronized(fileLock) {
        readableProfile(context)?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
    }

    private fun readableProfile(context: Context): File? {
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS || hasPrivateSession || publishedGeneration != generation) return null
        return File(context.cacheDir, PROFILE_NAME).takeIf {
            it.isFile && it.length() in 2..GeckoPerformanceDiagnosticsRules.MAX_PROFILE_BYTES
        }
    }

    private fun waitUntilActive(captureGeneration: Long, pollsRemaining: Long) {
        if (captureGeneration != generation || hasPrivateSession) return
        if (profilerActive()) {
            isRecording = true
            updateStatus("recording")
            BrowserPerformanceTrace.event(BrowserPerformanceTrace.Phase.DiagnosticsStart)
            return
        }
        if (pollsRemaining <= 0) {
            cancelCapture("error")
            return
        }
        handler.postDelayed({ waitUntilActive(captureGeneration, pollsRemaining - 1) }, START_POLL_MS)
    }

    private fun cancelCapture(nextStatus: String) {
        val mayBeActive = isRecording || status == "starting" || profilerActive()
        generation++
        publishedGeneration = null
        isRecording = false
        val cleaned = applicationContext?.let(::deleteProfiles) != false
        updateStatus(if (cleaned) nextStatus else "cleanup_failed")
        if (!mayBeActive || stopPending || !GeckoRuntimeOwner.hasRuntime()) return
        stopPending = true
        stopWithoutExport()
    }

    /** Starting and stopping share Gecko's queue; never publish canceled result bytes. */
    private fun stopWithoutExport() {
        runCatching {
            ProfilerController.stopProfiler().withHandler(handler).accept(
                { finishCancellation() },
                { finishCancellation() },
            )
        }.onFailure { finishCancellation() }
    }

    private fun finishCancellation() {
        if (profilerActive()) {
            stopPending = true
            handler.postDelayed({ stopWithoutExport() }, START_POLL_MS)
            return
        }
        stopPending = false
        if (!hasPrivateSession && status == "private_session") updateStatus("idle")
    }

    private fun writeProfile(context: Context, captureGeneration: Long, bytes: ByteArray) {
        io.execute {
            val temporary = File(context.cacheDir, "gecko-profile-$captureGeneration.tmp")
            val written = runCatching {
                if (!canPublish(captureGeneration, bytes.size.toLong())) return@runCatching false
                temporary.outputStream().use { it.write(bytes) }
                synchronized(fileLock) {
                    if (!canPublish(captureGeneration, bytes.size.toLong())) return@synchronized false
                    if (!temporary.renameTo(File(context.cacheDir, PROFILE_NAME))) return@synchronized false
                    publishedGeneration = captureGeneration
                    true
                }
            }.getOrDefault(false)
            val temporaryCleaned = !temporary.exists() || temporary.delete()
            handler.post {
                if (!temporaryCleaned) {
                    // A stale writer can finish after cancellation deleted its not-yet-created
                    // file. Retry cleanup and fail closed even if a newer capture has started.
                    cancelCapture("cleanup_failed")
                    return@post
                }
                if (captureGeneration != generation || hasPrivateSession) return@post
                updateStatus(if (written) "ready" else "error")
            }
        }
    }

    private fun canPublish(captureGeneration: Long, compressedBytes: Long): Boolean =
        GeckoPerformanceDiagnosticsRules.canPublish(captureGeneration, generation, hasPrivateSession, compressedBytes)

    private fun deleteProfiles(context: Context): Boolean = runCatching {
        synchronized(fileLock) {
            publishedGeneration = null
            val files = context.cacheDir.listFiles { file ->
                file.name == PROFILE_NAME || (file.name.startsWith("gecko-profile-") && file.name.endsWith(".tmp"))
            } ?: return@synchronized !context.cacheDir.exists()
            var cleaned = true
            files.forEach { if (it.exists() && !it.delete()) cleaned = false }
            cleaned
        }
    }.getOrDefault(false)

    private fun updateStatus(nextStatus: String) {
        status = nextStatus
        listeners.toList().forEach { it() }
    }
}
