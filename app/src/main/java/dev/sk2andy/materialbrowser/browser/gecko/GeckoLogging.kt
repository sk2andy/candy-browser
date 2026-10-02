package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.UiThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sk2andy.materialbrowser.data.DeveloperSettings
import dev.sk2andy.materialbrowser.data.GeckoLogStore
import dev.sk2andy.materialbrowser.data.GeckoLoggingRules
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoPreferenceController.SetGeckoPreference
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class GeckoLoggingStatus {
    Disabled,
    Starting,
    Recording,
    PausedForPrivateBrowsing,
    PausedForExport,
    LimitReached,
    Failed,
}

/** Owns raw, parent-process Gecko capture. Preferences are defaults, never persisted in Gecko. */
@androidx.annotation.OptIn(markerClass = [org.mozilla.geckoview.ExperimentalGeckoViewApi::class])
internal object GeckoLogging {
    private const val MAX_CAPTURE_DURATION_MILLIS = 5 * 60_000L
    private const val CAPTURE_CHECK_INTERVAL_MILLIS = 1_000L
    private const val PREFERENCE_TIMEOUT_MILLIS = 10_000L

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val privateOwners = mutableSetOf<Any>()
    private val pausedOwners = mutableSetOf<Any>()
    private val stopWaiters = mutableListOf<(Boolean) -> Unit>()
    private val captureLimitCheck = Runnable(::checkCaptureLimit)
    private var store: GeckoLogStore? = null
    private var attached = false
    private var enabled = false
    private var modules = GeckoLoggingRules.DEFAULT_MODULES
    private var appliedModules = emptySet<String>()
    private var recording = false
    private var stoppedConfirmed = false
    private var updating = false
    private var failed = false
    private var limitReached = false
    private var clearRequested = false
    private var revision = 0L
    private var startedAt = 0L

    var status by mutableStateOf(GeckoLoggingStatus.Disabled)
        private set

    @UiThread
    fun attach(context: Context) {
        if (attached) return
        store = GeckoLogStore(File(context.noBackupFilesDir, GeckoLoggingRules.DIRECTORY_NAME))
        attached = true
        reconcile()
    }

    @UiThread
    fun configure(settings: DeveloperSettings) {
        val normalizedModules = GeckoLoggingRules.normalizedModules(settings.geckoLoggingModules)
        if (enabled == settings.geckoLoggingEnabled && modules == normalizedModules) return
        if (enabled && !settings.geckoLoggingEnabled) clearRequested = true
        enabled = settings.geckoLoggingEnabled
        modules = normalizedModules
        failed = false
        limitReached = false
        revision++
        reconcile()
    }

    @UiThread
    fun setPrivateBrowsingActive(active: Boolean, owner: Any) {
        val changed = if (active) privateOwners.add(owner) else privateOwners.remove(owner)
        if (!changed) return
        revision++
        reconcile()
    }

    /** Private sessions await the parent-process stop acknowledgement before opening their window. */
    @UiThread
    fun beforePrivateSession(owner: Any, onStopped: (Boolean) -> Unit) {
        setPrivateBrowsingActive(true, owner)
        awaitStopped(onStopped)
    }

    @UiThread
    fun hasPrivateSessionOwner(owner: Any): Boolean = owner in privateOwners

    private fun awaitStopped(callback: (Boolean) -> Unit) {
        when {
            !attached -> callback(true)
            !updating && stoppedConfirmed -> callback(true)
            !updating && failed -> callback(false)
            else -> stopWaiters += callback
        }
    }

    private fun shouldCapture(): Boolean = enabled && !failed && !limitReached &&
        privateOwners.isEmpty() && pausedOwners.isEmpty()

    @UiThread
    private fun reconcile() {
        publishStatus()
        if (!attached || updating) return
        val generation = revision
        val capture = shouldCapture()
        val requestedModules = if (capture) {
            modules.split(',').associate { entry ->
                entry.substringBefore(':') to entry.substringAfter(':').toInt()
            }
        } else {
            emptyMap()
        }
        updating = true
        stoppedConfirmed = false
        handler.removeCallbacks(captureLimitCheck)
        publishStatus()
        scope.launch {
            var success = false
            try {
                // Disable levels before closing the file: an empty file name routes enabled modules to stderr.
                appliedModules = appliedModules + requestedModules.keys
                if (!stopCapture()) return@launch
                recording = false
                stoppedConfirmed = true
                completeStopWaiters(success = true)
                withContext(Dispatchers.IO) { requireNotNull(store).trim() }
                if (clearRequested) {
                    if (!withContext(Dispatchers.IO) { requireNotNull(store).clear() }) return@launch
                    clearRequested = false
                }
                success = true
                if (!capture || generation != revision) return@launch
                success = false
                val path = withContext(Dispatchers.IO) { requireNotNull(store).newCapturePath() }
                if (generation != revision) {
                    success = true
                    return@launch
                }
                if (!setPrefs(listOf(
                        stringPref("logging.config.LOG_FILE", path),
                        boolPref("logging.config.add_timestamp", true),
                        boolPref("logging.config.sync", true),
                    ))
                ) return@launch
                if (generation != revision) {
                    success = true
                    return@launch
                }
                stoppedConfirmed = false
                if (!setPrefs(requestedModules.map { (module, level) -> intPref("logging.$module", level) })) {
                    return@launch
                }
                recording = true
                startedAt = SystemClock.elapsedRealtime()
                success = true
            } catch (_: TimeoutCancellationException) {
                // A crashed/unresponsive runtime must not leave private entry waiting forever.
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Do not acknowledge private entry when native stop or file preparation failed.
            } finally {
                if (!success) {
                    failed = true
                    stoppedConfirmed = false
                    completeStopWaiters(success = false)
                    publishStatus()
                    // A partially successful module write may already have enabled native capture.
                    // Keep failures sticky, but make one bounded attempt to revoke every owned module.
                    val stopped = withContext(NonCancellable) {
                        withTimeoutOrNull(PREFERENCE_TIMEOUT_MILLIS) {
                            try {
                                stopCapture()
                            } catch (_: Exception) {
                                false
                            }
                        } == true
                    }
                    if (stopped) {
                        recording = false
                        stoppedConfirmed = true
                    }
                    completeStopWaiters(success = stopped)
                }
                updating = false
                publishStatus()
                if (success && generation != revision) {
                    reconcile()
                } else if (success && recording) {
                    handler.postDelayed(captureLimitCheck, CAPTURE_CHECK_INTERVAL_MILLIS)
                }
            }
        }
    }

    private suspend fun stopCapture(): Boolean =
        setPrefs(appliedModules.map { module -> intPref("logging.$module", 0) }) &&
            setPrefs(listOf(stringPref("logging.config.LOG_FILE", "")))

    /** DEFAULT writes alone can succeed while a USER override keeps native recording enabled. */
    private suspend fun setPrefs(prefs: List<SetGeckoPreference<*>>): Boolean {
        if (prefs.isEmpty()) return true
        val result = GeckoPreferenceController.setGeckoPrefs(prefs).awaitLoggingResult() ?: return false
        if (prefs.any { result[it.pref] != true }) return false
        for (pref in prefs) {
            GeckoPreferenceController.clearGeckoUserPref(pref.pref).awaitLoggingResult()
        }
        val actual = GeckoPreferenceController.getGeckoPrefs(prefs.map { it.pref })
            .awaitLoggingResult()?.associateBy { it.pref } ?: return false
        return prefs.all { expected -> actual[expected.pref]?.value == expected.value }
    }

    private fun completeStopWaiters(success: Boolean) {
        val callbacks = stopWaiters.toList()
        stopWaiters.clear()
        callbacks.forEach { it(success) }
    }

    private fun publishStatus() {
        status = when {
            failed -> GeckoLoggingStatus.Failed
            !enabled -> GeckoLoggingStatus.Disabled
            limitReached -> GeckoLoggingStatus.LimitReached
            privateOwners.isNotEmpty() -> GeckoLoggingStatus.PausedForPrivateBrowsing
            pausedOwners.isNotEmpty() -> GeckoLoggingStatus.PausedForExport
            recording && !updating -> GeckoLoggingStatus.Recording
            else -> GeckoLoggingStatus.Starting
        }
    }

    private fun checkCaptureLimit() {
        if (!recording || updating) return
        val generation = revision
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { store?.totalBytes() ?: 0L }.getOrNull()
            }
            if (!recording || updating || generation != revision) return@launch
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (bytes == null || elapsed >= MAX_CAPTURE_DURATION_MILLIS || bytes >= GeckoLoggingRules.MAX_EXPORT_BYTES) {
                limitReached = bytes != null
                failed = bytes == null
                revision++
                reconcile()
            } else {
                handler.postDelayed(captureLimitCheck, CAPTURE_CHECK_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun pause(owner: Any): Boolean = withContext(Dispatchers.Main.immediate) {
        pausedOwners += owner
        revision++
        reconcile()
        suspendCancellableCoroutine { continuation ->
            awaitStopped { success -> if (continuation.isActive) continuation.resume(success) }
        }
    }

    private suspend fun resume(owner: Any) = withContext(Dispatchers.Main.immediate) {
        pausedOwners -= owner
        revision++
        reconcile()
    }

    suspend fun export(context: Context, uri: Uri, diagnostics: String): Boolean {
        val owner = Any()
        return try {
            if (!pause(owner)) false else withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        val logs = store?.snapshot().orEmpty()
                        output.write("$diagnostics\n\nRaw Gecko logs — may contain URLs and cookies\n$logs".toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
        } finally {
            withContext(NonCancellable) { resume(owner) }
        }
    }

    suspend fun clear(): Boolean {
        val owner = Any()
        return try {
            if (!pause(owner)) false else {
                val cleared = withContext(Dispatchers.IO) {
                    runCatching { store?.clear() ?: true }.getOrDefault(false)
                }
                if (!cleared) withContext(Dispatchers.Main.immediate) {
                    failed = true
                    publishStatus()
                }
                cleared
            }
        } finally {
            withContext(NonCancellable) { resume(owner) }
        }
    }

    private fun intPref(name: String, value: Int): SetGeckoPreference<Int> =
        SetGeckoPreference.setIntPref(name, value, GeckoPreferenceController.PREF_BRANCH_DEFAULT)

    private fun stringPref(name: String, value: String): SetGeckoPreference<String> =
        SetGeckoPreference.setStringPref(name, value, GeckoPreferenceController.PREF_BRANCH_DEFAULT)

    private fun boolPref(name: String, value: Boolean): SetGeckoPreference<Boolean> =
        SetGeckoPreference.setBoolPref(name, value, GeckoPreferenceController.PREF_BRANCH_DEFAULT)
}

private suspend fun <T> GeckoResult<T>.awaitLoggingResult(): T? = withTimeout(10_000L) {
    suspendCancellableCoroutine { continuation ->
        accept(
            { value -> if (continuation.isActive) continuation.resume(value) },
            { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error ?: IllegalStateException("Gecko logging request failed"))
                }
            },
        )
    }
}
