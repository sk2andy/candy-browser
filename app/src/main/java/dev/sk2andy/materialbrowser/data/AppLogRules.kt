package dev.sk2andy.materialbrowser.data

import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap

internal enum class AppLogEvent {
    LoggingEnabled,
    ProcessStarted,
    BrowserStarted,
    UncaughtException,
    ExtensionInstallStarted,
    ExtensionInstallSucceeded,
    ExtensionInstallFailed,
    ExtensionReadFailed,
    ExtensionMutationFailed,
    GeckoRendererCrashed,
    GeckoRendererKilled,
    SystemRendererGone,
    SystemDownloadResponseReceived,
    SystemDownloadRequested,
    SystemDownloadBlobHelper,
    SystemDownloadPlatform,
    SystemDownloadStarted,
    SystemDownloadCompleted,
    SystemDownloadInvalidRequest,
    SystemDownloadNetworkFailed,
    SystemDownloadHttpFailed,
    SystemDownloadStorageFailed,
    SystemDownloadCancelled,
    SystemDownloadHelperSetupFailed,
    SystemDownloadHelperLoadFailed,
    SystemDownloadBridgeReplyFailed,
    SystemDownloadBridgeError,
    SystemDownloadTimedOut,
    SystemDownloadSinkOpenFailed,
    SystemDownloadSinkWriteFailed,
    SystemDownloadSinkCommitFailed,
    SystemDownloadEnqueueFailed,
    AddressBarAutoParkedForVisibleControl,
    AddressBarAutoParkedForFocusedInput,
    AddressBarManuallyUnparked,
}

/** Only code metadata enters logs. Exception messages, file paths and browser input never do. */
internal object AppLogRules {
    const val MAX_FILE_BYTES = 256 * 1024
    const val MAX_RECORD_BYTES = 16 * 1024
    private const val MAX_CAUSES = 4
    private const val MAX_FRAMES = 40
    private val CODE_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$.<>-]{0,255}")

    fun render(event: AppLogEvent, timestampMillis: Long, error: Throwable? = null): String =
        buildString {
            append(Instant.ofEpochMilli(timestampMillis))
            append(' ')
            appendLine(event.name)
            val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            var cause = error
            repeat(MAX_CAUSES) {
                val current = cause ?: return@buildString
                if (!seen.add(current)) return@buildString
                append("  exception ")
                appendLine(current.javaClass.name)
                current.stackTrace.take(MAX_FRAMES).forEach { frame ->
                    val validMethod = CODE_NAME.matches(frame.methodName) ||
                        frame.methodName == "<init>" || frame.methodName == "<clinit>"
                    if (CODE_NAME.matches(frame.className) && validMethod) {
                        append("    at ")
                        append(frame.className)
                        append('.')
                        append(frame.methodName)
                        append(':')
                        appendLine(frame.lineNumber)
                    }
                }
                cause = current.cause
            }
        }.take(MAX_RECORD_BYTES / 4 - 1).trimEnd() + "\n"
}
