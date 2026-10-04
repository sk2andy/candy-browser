package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.sk2andy.materialbrowser.BuildConfig
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object AppLogging {
    private val writer = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(128),
        { task -> Thread(task, "CandyAppLogs").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    @Volatile
    private var store: AppLogStore? = null

    private var nativeCaptureStore: NativeCrashHistoryStore? = null
    private var nativeHistory: NativeCrashHistory? = null

    @Volatile
    private var privateBrowsingActive = false

    private val privateOwners = mutableSetOf<Any>()

    @Synchronized
    fun initialize(context: Context) {
        if (store != null) return
        val appContext = context.applicationContext
        val directory = File(appContext.noBackupFilesDir, "app_logs")
        store = AppLogStore(directory)
        nativeCaptureStore = NativeCrashHistoryStore(
            directory,
            appContext.getSharedPreferences(NativeCrashHistoryStore.PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
        nativeHistory = NativeCrashHistory(appContext, requireNotNull(nativeCaptureStore))
        val previousCapture = requireNotNull(nativeCaptureStore).snapshot()
        val startedAtMillis = System.currentTimeMillis()
        setEnabled(BrowserSessionStore(appContext).loadDeveloperSettings().appLoggingEnabled)
        val currentStore = requireNotNull(store)
        currentStore.captureGeneration()?.let { generation ->
            writer.execute {
                collectNativeCrashes(previousCapture, startedAtMillis, currentStore, generation)
            }
        }
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (previousHandler != null) {
            Thread.setDefaultUncaughtExceptionHandler(
                AppLogCrashHandler(previousHandler) { error ->
                    record(AppLogEvent.UncaughtException, error, sync = true)
                },
            )
        }
        record(AppLogEvent.ProcessStarted)
    }

    @Synchronized
    fun setPrivateBrowsingActive(active: Boolean, owner: Any = this) {
        if (active) privateOwners += owner else privateOwners -= owner
        val anyPrivateOwner = privateOwners.isNotEmpty()
        if (anyPrivateOwner == privateBrowsingActive) return
        if (anyPrivateOwner) store?.invalidatePendingWrites()
        if (store?.captureGeneration() != null) {
            nativeCaptureStore?.reset(
                allowed = !anyPrivateOwner,
                nowMillis = System.currentTimeMillis(),
            )
        }
        privateBrowsingActive = anyPrivateOwner
    }

    @Synchronized
    fun setEnabled(enabled: Boolean): Boolean {
        val result = store?.setEnabled(enabled) ?: return false
        val nativeResult = if (enabled) {
            nativeCaptureStore?.reset(!privateBrowsingActive, System.currentTimeMillis()) ?: true
        } else {
            nativeCaptureStore?.reset(allowed = false, nowMillis = System.currentTimeMillis()) ?: true
        }
        if (enabled) record(AppLogEvent.LoggingEnabled)
        return result && nativeResult
    }

    fun record(event: AppLogEvent, error: Throwable? = null, sync: Boolean = false) {
        val currentStore = store ?: return
        if (privateBrowsingActive) return
        val generation = currentStore.captureGeneration() ?: return
        val record = runCatching { AppLogRules.render(event, System.currentTimeMillis(), error) }
            .getOrNull() ?: return
        if (sync) {
            currentStore.append(record, generation, sync = true)
        } else {
            runCatching {
                writer.execute {
                    if (!privateBrowsingActive) currentStore.append(record, generation)
                    if (event == AppLogEvent.GeckoRendererCrashed ||
                        event == AppLogEvent.GeckoRendererKilled ||
                        event == AppLogEvent.SystemRendererGone
                    ) {
                        nativeCaptureStore?.snapshot()?.let { checkpoint ->
                            collectNativeCrashes(
                                checkpoint,
                                System.currentTimeMillis(),
                                currentStore,
                                generation,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Debug Logcat uses the same sanitized payload and private-owner gate as opt-in app logs. */
    @Synchronized
    fun recordDiagnostic(event: AppLogEvent, isPrivate: Boolean, error: Throwable? = null) {
        if (isPrivate || privateBrowsingActive) return
        if (BuildConfig.DEBUG) {
            runCatching {
                Log.d("CandyDownload", AppLogRules.render(event, System.currentTimeMillis(), error))
            }
        }
        record(event, error)
    }

    @Synchronized
    fun clear(): Boolean {
        val result = store?.clear() ?: true
        val nativeResult = if (store?.captureGeneration() != null) {
            nativeCaptureStore?.reset(!privateBrowsingActive, System.currentTimeMillis()) ?: true
        } else {
            nativeCaptureStore?.reset(allowed = false, nowMillis = System.currentTimeMillis()) ?: true
        }
        return result && nativeResult
    }

    private fun collectNativeCrashes(
        checkpoint: NativeCrashCaptureCheckpoint,
        beforeMillis: Long,
        currentStore: AppLogStore,
        generation: Long,
    ) {
        if (privateBrowsingActive || currentStore.captureGeneration() != generation) return
        nativeHistory?.collect(checkpoint, beforeMillis) { record ->
            !privateBrowsingActive && currentStore.append(record, generation)
        }
    }

    /** Caller owns Dispatchers.IO and reports failures; sharing remains an explicit user action. */
    fun export(context: Context, uri: Uri, diagnostics: String): Boolean = runCatching {
        val logs = writer.submit<String> {
            val currentStore = store
            val generation = currentStore?.captureGeneration()
            if (currentStore != null && generation != null) {
                nativeCaptureStore?.snapshot()?.let { checkpoint ->
                    collectNativeCrashes(
                        checkpoint,
                        System.currentTimeMillis(),
                        currentStore,
                        generation,
                    )
                }
            }
            currentStore?.snapshot().orEmpty()
        }
            .get(5L, TimeUnit.SECONDS)
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write("$diagnostics\n\nCandy app logs\n$logs".toByteArray(Charsets.UTF_8))
        } ?: return false
        true
    }.getOrDefault(false)
}
