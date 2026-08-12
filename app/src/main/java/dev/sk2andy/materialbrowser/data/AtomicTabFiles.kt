package dev.sk2andy.materialbrowser.data

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal class AtomicTabFileDirectory(
    private val directory: File,
    private val extension: String,
) {
    init {
        require(extension.matches(FILE_EXTENSION_PATTERN)) {
            "Tab file extension must contain only lowercase letters and digits."
        }
    }

    fun ensureExists(): Boolean = directory.isDirectory || directory.mkdirs()

    fun fileFor(tabId: String): File? =
        tabArtifactFileName(tabId, extension)?.let { fileName -> File(directory, fileName) }

    fun delete(tabId: String) {
        fileFor(tabId)?.let { file -> AtomicFile(file).delete() }
    }

    fun prune(validTabIds: Set<String>) {
        val validNames = validTabIds.mapNotNull(::fileNameFor).toSet()
        directory.listFiles()
            ?.map { file -> atomicBaseName(file.name) }
            ?.filterNot(validNames::contains)
            ?.toSet()
            ?.forEach { baseName -> AtomicFile(File(directory, baseName)).delete() }
    }

    fun clear() {
        directory.listFiles()?.forEach(File::delete)
    }

    fun clearAndRemoveDirectory() {
        clear()
        directory.delete()
    }

    private fun fileNameFor(tabId: String): String? = tabArtifactFileName(tabId, extension)
}

internal fun tabArtifactFileName(tabId: String, extension: String): String? {
    if (!extension.matches(FILE_EXTENSION_PATTERN)) return null
    return runCatching { "${UUID.fromString(tabId)}.$extension" }.getOrNull()
}

internal inline fun AtomicFile.writeSafely(write: (FileOutputStream) -> Unit): Boolean {
    var output: FileOutputStream? = null
    return try {
        output = startWrite()
        write(output)
        finishWrite(output)
        true
    } catch (_: Exception) {
        output?.let(::failWrite)
        false
    }
}

private fun atomicBaseName(fileName: String): String = when {
    fileName.endsWith(".new") -> fileName.removeSuffix(".new")
    fileName.endsWith(".bak") -> fileName.removeSuffix(".bak")
    else -> fileName
}

private val FILE_EXTENSION_PATTERN = Regex("[a-z0-9]+")
