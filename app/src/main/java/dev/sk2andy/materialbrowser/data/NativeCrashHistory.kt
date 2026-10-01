package dev.sk2andy.materialbrowser.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.time.Instant

internal class NativeCrashHistory(
    context: Context,
    private val captureStore: NativeCrashHistoryStore,
) {
    private val packageName = context.packageName
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    /** Never retain the protobuf: it also contains memory, log buffers and other sensitive fields. */
    fun collect(
        checkpoint: NativeCrashCaptureCheckpoint,
        beforeMillis: Long,
        append: (String) -> Boolean,
    ) {
        if (checkpoint.captureSinceMillis == null) return
        val exits = runCatching {
            activityManager.getHistoricalProcessExitReasons(packageName, 0, MAX_EXIT_RECORDS)
        }.getOrDefault(emptyList())
        val eligibleExits = exits.filter { exit ->
            exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE &&
                NativeCrashCaptureRules.isBrowserProcess(packageName, exit.processName) &&
                NativeCrashCaptureRules.allows(checkpoint, exit.timestamp, beforeMillis)
        }.sortedBy(ApplicationExitInfo::getTimestamp)
        eligibleExits.forEach { exit ->
            val trace = runCatching {
                exit.traceInputStream?.use { input ->
                    val bytes = input.readNBytes(NativeCrashTraceRules.MAX_INPUT_BYTES + 1)
                    NativeCrashTraceRules.parse(bytes)
                }
            }.getOrNull() ?: return@forEach
            val process = if (exit.processName == packageName) "App" else "BrowserEngine"
            val record = "${Instant.ofEpochMilli(exit.timestamp)} NativeCrash " +
                "process=$process\n$trace"
            if (append(record)) captureStore.markCollected(exit.timestamp)
        }
    }

    private companion object {
        const val MAX_EXIT_RECORDS = 32
    }
}
