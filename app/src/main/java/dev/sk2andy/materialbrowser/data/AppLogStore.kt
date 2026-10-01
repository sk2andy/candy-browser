package dev.sk2andy.materialbrowser.data

import java.io.File
import java.io.FileOutputStream

/** Two bounded files, shared by the background writer, export and synchronous crash handler. */
internal class AppLogStore(private val directory: File) {
    private val currentFile = File(directory, "current.txt")
    private val previousFile = File(directory, "previous.txt")
    private var enabled = false
    private var generation = 0L

    @Synchronized
    fun setEnabled(value: Boolean): Boolean {
        if (enabled != value) generation++
        enabled = value
        return if (value) true else clear()
    }

    @Synchronized
    fun captureGeneration(): Long? = generation.takeIf { enabled }

    @Synchronized
    fun invalidatePendingWrites() {
        generation++
    }

    @Synchronized
    fun append(record: String, expectedGeneration: Long, sync: Boolean = false): Boolean {
        if (!enabled || generation != expectedGeneration) return false
        val bytes = record.toByteArray(Charsets.UTF_8)
        if (bytes.size > AppLogRules.MAX_RECORD_BYTES) return false
        return runCatching {
            check(directory.isDirectory || directory.mkdirs())
            if (currentFile.length() + bytes.size > AppLogRules.MAX_FILE_BYTES) {
                check(!previousFile.exists() || previousFile.delete())
                check(currentFile.renameTo(previousFile))
            }
            FileOutputStream(currentFile, true).use { output ->
                output.write(bytes)
                if (sync) output.fd.sync()
            }
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun snapshot(): String = listOf(previousFile, currentFile).joinToString("") { file ->
        if (!file.isFile) return@joinToString ""
        file.inputStream().use { input ->
            input.readNBytes(AppLogRules.MAX_FILE_BYTES).toString(Charsets.UTF_8)
        }
    }

    @Synchronized
    fun clear(): Boolean {
        generation++
        val previousDeleted = !previousFile.exists() || previousFile.delete()
        val currentDeleted = !currentFile.exists() || currentFile.delete()
        return previousDeleted && currentDeleted
    }
}
