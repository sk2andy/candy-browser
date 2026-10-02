package dev.sk2andy.materialbrowser.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID

/** Local native Gecko files; callers stop native writes before exporting, trimming or clearing. */
internal class GeckoLogStore(private val directory: File) {
    @Synchronized
    fun newCapturePath(): String {
        check(!Files.isSymbolicLink(directory.toPath()))
        check(directory.isDirectory || directory.mkdirs())
        trimSegments(GeckoLoggingRules.MAX_RETAINED_SEGMENTS - 1)
        return File(directory, "capture-${System.currentTimeMillis()}-${UUID.randomUUID()}").absolutePath
    }

    @Synchronized
    fun snapshot(): String {
        val output = ByteArrayOutputStream()
        val files = retainedFiles(GeckoLoggingRules.MAX_RETAINED_SEGMENTS).asReversed()
        for (file in files) {
            val remaining = GeckoLoggingRules.MAX_EXPORT_BYTES - output.size()
            if (remaining <= 0) break
            val header = "\n[Gecko log: ${file.name}]\n".toByteArray(Charsets.UTF_8)
            if (header.size >= remaining) break
            val bytes = fileTailBytes(file, remaining - header.size)
            output.write(header)
            output.write(bytes)
        }
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE)
            .decode(ByteBuffer.wrap(output.toByteArray()))
            .toString()
    }

    @Synchronized
    fun totalBytes(): Long = totalBytes(logFiles())

    @Synchronized
    fun trim() {
        trimSegments(GeckoLoggingRules.MAX_RETAINED_SEGMENTS)
    }

    @Synchronized
    fun clear(): Boolean {
        if (Files.isSymbolicLink(directory.toPath())) return false
        if (!directory.exists()) return true
        if (!directory.isDirectory) return false
        val entries = directory.listFiles() ?: return false
        return entries.fold(true) { deleted, file ->
            val removed = runCatching {
                Files.deleteIfExists(file.toPath())
                true
            }.getOrDefault(false)
            removed && deleted
        }
    }

    private fun trimSegments(limit: Int) {
        val groups = retainedGroups(limit).toMutableList()
        while (groups.size > 1 && totalBytes(groups.flatten()) > GeckoLoggingRules.MAX_EXPORT_BYTES) {
            groups.removeAt(0)
        }
        if (totalBytes(groups.flatten()) > GeckoLoggingRules.MAX_EXPORT_BYTES) {
            trimFileTails(groups.single())
        }
        val retained = groups.flatten().toSet()
        logFiles().filterNot { it in retained }.forEach { file ->
            check(file.delete())
        }
    }

    private fun trimFileTails(files: List<File>) {
        val sortedFiles = files.sortedWith(compareBy<File>(File::length).thenBy(File::getName))
        var remainingBytes = GeckoLoggingRules.MAX_EXPORT_BYTES.toLong()
        sortedFiles.forEachIndexed { index, file ->
            val allocatedBytes = remainingBytes / (sortedFiles.size - index)
            val retainedBytes = minOf(file.length(), allocatedBytes)
            if (file.length() > retainedBytes) retainFileTail(file, retainedBytes)
            remainingBytes -= retainedBytes
        }
    }

    private fun retainFileTail(file: File, retainedBytes: Long) {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
        RandomAccessFile(file, "rw").use { output ->
            val length = output.length()
            var sourcePosition = length - retainedBytes
            var destinationPosition = 0L
            val buffer = ByteArray(64 * 1024)
            while (destinationPosition < retainedBytes) {
                val count = minOf(buffer.size.toLong(), retainedBytes - destinationPosition).toInt()
                output.seek(sourcePosition)
                output.readFully(buffer, 0, count)
                output.seek(destinationPosition)
                output.write(buffer, 0, count)
                sourcePosition += count
                destinationPosition += count
            }
            output.setLength(retainedBytes)
        }
    }

    private fun fileTailBytes(file: File, maxBytes: Int): ByteArray {
        check(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))
        return RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            val count = minOf(length, maxBytes.toLong()).toInt()
            input.seek(length - count)
            ByteArray(count).also { bytes -> input.readFully(bytes) }
        }
    }

    private fun totalBytes(files: List<File>): Long = files.fold(0L) { total, file ->
        val size = file.length().coerceAtLeast(0L)
        if (total > Long.MAX_VALUE - size) Long.MAX_VALUE else total + size
    }

    private fun retainedFiles(limit: Int): List<File> = retainedGroups(limit).flatten()

    private fun retainedGroups(limit: Int): List<List<File>> = logFiles()
        .groupBy { file -> capturePrefixPattern.find(file.name)?.value ?: file.name }
        .entries
        .sortedWith(
            compareBy<Map.Entry<String, List<File>>> { (_, files) -> files.maxOf(File::lastModified) }
                .thenBy { it.key },
        )
        .takeLast(limit)
        .map { (_, files) -> files.sortedBy(File::getName) }

    private fun logFiles(): List<File> {
        if (Files.isSymbolicLink(directory.toPath()) || !directory.isDirectory) return emptyList()
        return directory.listFiles().orEmpty().filter { file ->
            file.name.endsWith(".moz_log") &&
                Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)
        }
    }

    private val capturePrefixPattern = Regex(
        "^capture-[0-9]+-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
    )
}
