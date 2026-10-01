package dev.sk2andy.materialbrowser.data

internal class AppLogCrashHandler(
    private val previousHandler: Thread.UncaughtExceptionHandler,
    private val writeCrash: (Throwable) -> Unit,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            runCatching { writeCrash(error) }
        } finally {
            previousHandler.uncaughtException(thread, error)
        }
    }
}
