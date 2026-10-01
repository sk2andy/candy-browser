package dev.sk2andy.materialbrowser.data

import android.content.SharedPreferences
import android.util.AtomicFile
import java.io.File

/** Capture cutoff is durable before private browsing starts; corrupt/missing state rejects history. */
internal class NativeCrashHistoryStore(
    directory: File,
    private val preferences: SharedPreferences,
) {
    private val file = AtomicFile(File(directory, "native-capture.txt"))
    private var checkpoint = initialCheckpoint()

    @Synchronized
    fun snapshot(): NativeCrashCaptureCheckpoint = checkpoint

    @Synchronized
    fun clear(): Boolean {
        checkpoint = checkpoint.copy(captureSinceMillis = null)
        val revoked = preferences.edit().putBoolean(KEY_REVOKED, true).commit()
        file.delete()
        return revoked && !file.baseFile.exists() && !File("${file.baseFile.path}.bak").exists()
    }

    @Synchronized
    fun reset(allowed: Boolean, nowMillis: Long): Boolean = save(
        NativeCrashCaptureRules.reset(checkpoint, allowed, nowMillis),
    )

    @Synchronized
    fun markCollected(timestampMillis: Long): Boolean = save(
        checkpoint.copy(
            collectedThroughMillis = maxOf(checkpoint.collectedThroughMillis, timestampMillis),
        ),
    )

    private fun save(next: NativeCrashCaptureCheckpoint): Boolean {
        checkpoint = next
        return runCatching {
            // Independent durable revocation keeps an old eligible file harmless if replacement fails.
            check(
                preferences.edit()
                    .putBoolean(KEY_REVOKED, true)
                    .putLong(KEY_CUTOFF, next.collectedThroughMillis)
                    .commit(),
            )
            val output = file.startWrite()
            try {
                val text = "$FORMAT\n${next.captureSinceMillis ?: "blocked"}\n" +
                    "${next.collectedThroughMillis}\n"
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
                file.finishWrite(output)
                check(load() == next)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
            check(preferences.edit().putBoolean(KEY_REVOKED, false).commit())
            true
        }.getOrElse {
            checkpoint = next.copy(captureSinceMillis = null)
            // Invalidate a stale eligible checkpoint if atomic persistence failed.
            file.delete()
            false
        }
    }

    private fun isRevoked(): Boolean = runCatching {
        preferences.getBoolean(KEY_REVOKED, false)
    }.getOrDefault(true)

    private fun initialCheckpoint(): NativeCrashCaptureCheckpoint {
        val loaded = load()
        val cutoff = runCatching { preferences.getLong(KEY_CUTOFF, 0L) }
            .getOrDefault(Long.MAX_VALUE)
            .coerceAtLeast(0L)
        return loaded.copy(
            captureSinceMillis = loaded.captureSinceMillis.takeUnless { isRevoked() },
            collectedThroughMillis = maxOf(loaded.collectedThroughMillis, cutoff),
        )
    }

    private fun load(): NativeCrashCaptureCheckpoint = runCatching {
        val lines = file.openRead().use { input ->
            input.readNBytes(128).toString(Charsets.UTF_8).lines()
        }
        require(lines.size == 4 && lines[0] == FORMAT && lines[3].isEmpty())
        val since = if (lines[1] == "blocked") null else lines[1].toLong().also { require(it >= 0L) }
        val through = lines[2].toLong().also { require(it >= (since ?: 0L)) }
        NativeCrashCaptureCheckpoint(since, through)
    }.getOrDefault(NativeCrashCaptureCheckpoint())

    companion object {
        const val PREFERENCES_NAME = "app_logging_capture"
        private const val FORMAT = "CandyNativeCapture1"
        private const val KEY_REVOKED = "native_capture_revoked"
        private const val KEY_CUTOFF = "native_capture_cutoff"
    }
}
