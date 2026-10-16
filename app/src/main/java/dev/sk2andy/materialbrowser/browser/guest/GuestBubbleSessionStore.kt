package dev.sk2andy.materialbrowser.browser.guest

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Durable owner lock. Process death never grants access to the owner's browser. */
internal class GuestBubbleSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.noBackupFilesDir, GuestBubbleRules.SESSION_DIRECTORY_NAME)
    private val marker = File(directory, "session")
    private val atomicMarker = AtomicFile(marker)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun observeEnabled(onChanged: (Boolean) -> Unit): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY_ENABLED) onChanged(isEnabled())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChanged(isEnabled())
        return AutoCloseable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setEnabled(enabled: Boolean): Boolean = withGuard(onFailure = false) {
        if (!enabled && readSession() != null) return@withGuard false
        preferences.edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    fun state(): GuestBubbleSession? = withGuard(onFailure = GuestBubbleRules.recoverySession) {
        readSession()
    }

    fun isLocked(): Boolean = state() != null

    fun start(): Boolean = withGuard(onFailure = false) {
        if (!isEnabled() || readSession() != null) return@withGuard false
        writeSession(GuestBubbleSession(UUID.randomUUID().toString(), GuestBubblePhase.Active))
    }

    /** Call before the first WebView in this guest process; repeated claims preserve its session. */
    fun claimGuestProcess(): Boolean {
        if (!GuestBubbleRules.isGuestProcess(appContext.packageName, Application.getProcessName())) {
            return false
        }
        val processId = Process.myPid()
        val startTicks = processStartTicks(processId) ?: return false
        return withGuard(onFailure = false) {
            val session = readSession() ?: return@withGuard false
            if (session.phase != GuestBubblePhase.Active) return@withGuard false
            if (session.guestProcessId == processId && session.guestProcessStartTicks == startTicks) {
                return@withGuard true
            }
            val previousId = session.guestProcessId
            if (previousId != null && processMatches(previousId, session.guestProcessStartTicks)) {
                return@withGuard false
            }
            if (!eraseGuestData()) return@withGuard false
            writeSession(session.copy(guestProcessId = processId, guestProcessStartTicks = startTicks))
        }
    }

    /** Only the guest's successful biometric callback may request this transition. */
    fun beginClosing(): Boolean {
        if (!GuestBubbleRules.isGuestProcess(appContext.packageName, Application.getProcessName())) {
            return false
        }
        return withGuard(onFailure = false) {
            val session = readSession() ?: return@withGuard false
            if (session.guestProcessId != Process.myPid() ||
                session.guestProcessStartTicks != processStartTicks(Process.myPid())
            ) {
                return@withGuard false
            }
            writeSession(session.copy(phase = GuestBubblePhase.Closing))
        }
    }

    /** Runs outside the WebView process, so no late engine write can recreate erased guest data. */
    fun completeCleanup(): Boolean {
        if (Application.getProcessName() != appContext.packageName + GuestBubbleRules.CLEANUP_PROCESS_SUFFIX) {
            return false
        }
        return synchronized(cleanupMonitor) {
            try {
                val session = state() ?: return@synchronized true
                if (session.phase != GuestBubblePhase.Closing) return@synchronized false
                if (!stopGuestProcesses(session) || !eraseGuestData()) return@synchronized false
                withGuard(onFailure = false) {
                    if (readSession() != session) return@withGuard false
                    atomicMarker.delete()
                    !markerExists()
                }
            } catch (_: SecurityException) {
                false
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
        }
    }

    private fun stopGuestProcesses(session: GuestBubbleSession): Boolean {
        val processIds = appContext.getSystemService(ActivityManager::class.java)
            .runningAppProcesses.orEmpty()
            .filter { GuestBubbleRules.isGuestProcess(appContext.packageName, it.processName) }
            .mapTo(mutableSetOf()) { it.pid }
        session.guestProcessId?.let(processIds::add)
        val identities = mutableListOf<Pair<Int, Long>>()
        processIds.forEach { processId ->
            if (!File("/proc/$processId").exists()) return@forEach
            val command = processCommand(processId) ?: return false
            if (!GuestBubbleRules.isGuestProcess(appContext.packageName, command)) return@forEach
            if (processId == Process.myPid()) return false
            val startTicks = processStartTicks(processId) ?: return false
            identities += processId to startTicks
            Process.killProcess(processId)
        }
        val deadline = SystemClock.uptimeMillis() + PROCESS_EXIT_TIMEOUT_MILLIS
        while (identities.any { (processId, startTicks) -> processMatches(processId, startTicks) }) {
            if (SystemClock.uptimeMillis() >= deadline) return false
            Thread.sleep(PROCESS_EXIT_POLL_MILLIS)
        }
        return true
    }

    private fun processMatches(processId: Int, startTicks: Long?): Boolean {
        if (!File("/proc/$processId").exists()) return false
        val actualStartTicks = processStartTicks(processId) ?: return true
        return startTicks == null || actualStartTicks == startTicks
    }

    private fun eraseGuestData(): Boolean = try {
        listOf(
            File(appContext.applicationInfo.dataDir, "app_webview_${GuestBubbleRules.WEBVIEW_DATA_SUFFIX}"),
            // Chromium AwBrowserProcess passes webview_<suffix> as PathUtils' cache subdirectory.
            File(appContext.cacheDir, "webview_${GuestBubbleRules.WEBVIEW_DATA_SUFFIX}"),
        ).all(::deleteTree)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Never follow symbolic links while deleting the engine-owned directory. */
    private fun deleteTree(file: File): Boolean {
        val path = file.toPath()
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            val children = file.listFiles() ?: return false
            if (!children.all(::deleteTree)) return false
        }
        return file.delete() && !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
    }

    private fun readSession(): GuestBubbleSession? {
        if (!markerExists()) return null
        return try {
            atomicMarker.openRead().use { input ->
                val bytes = input.readNBytes(GuestBubbleRules.MAX_MARKER_BYTES + 1)
                if (bytes.size > GuestBubbleRules.MAX_MARKER_BYTES) {
                    GuestBubbleRules.recoverySession
                } else {
                    GuestBubbleRules.decodeMarker(bytes.toString(Charsets.UTF_8))
                        ?: GuestBubbleRules.recoverySession
                }
            }
        } catch (_: IOException) {
            GuestBubbleRules.recoverySession
        }
    }

    private fun writeSession(session: GuestBubbleSession): Boolean {
        val output = atomicMarker.startWrite()
        return try {
            output.write(GuestBubbleRules.encodeMarker(session).toByteArray(Charsets.UTF_8))
            atomicMarker.finishWrite(output)
            readSession() == session
        } catch (error: IOException) {
            atomicMarker.failWrite(output)
            false
        }
    }

    private fun markerExists(): Boolean = marker.exists() ||
        File(directory, "session.bak").exists() || File(directory, "session.new").exists()

    private fun <T> withGuard(onFailure: T, action: () -> T): T = synchronized(markerMonitor) {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return@synchronized onFailure
            FileChannel.open(
                File(directory, "session.guard").toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            ).use { channel -> channel.lock().use { action() } }
        } catch (_: IOException) {
            onFailure
        } catch (_: SecurityException) {
            onFailure
        }
    }

    private fun processStartTicks(processId: Int): Long? = runCatching {
        File("/proc/$processId/stat").readText().substringAfterLast(") ").split(' ')[19].toLong()
    }.getOrNull()

    private fun processCommand(processId: Int): String? = runCatching {
        File("/proc/$processId/cmdline").readText().substringBefore('\u0000')
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "guest_bubble"
        const val KEY_ENABLED = "enabled"
        const val PROCESS_EXIT_TIMEOUT_MILLIS = 3_000L
        const val PROCESS_EXIT_POLL_MILLIS = 25L
        val markerMonitor = Any()
        val cleanupMonitor = Any()
    }
}
