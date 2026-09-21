package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.sk2andy.materialbrowser.browser.FileChooserRules
import java.io.File
import java.util.UUID

/** GeckoView's file prompt needs readable file paths, not arbitrary document-provider URIs. */
internal class GeckoFileUploadStager(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val root = File(context.applicationContext.cacheDir, CACHE_DIRECTORY)
    private val retained = mutableMapOf<String, MutableList<File>>()

    fun clearOrphans() {
        root.listFiles()?.forEach(File::deleteRecursively)
    }

    fun stage(uris: Array<Uri>): StagedUpload? {
        if (uris.isEmpty() || uris.size > FileChooserRules.MAX_SELECTED_FILES) return null
        val directory = File(root, UUID.randomUUID().toString())
        if (!directory.mkdirs()) return null
        return runCatching {
            var totalBytes = root.walkTopDown()
                .filter(File::isFile)
                .sumOf(File::length)
            val files = uris.mapIndexed { index, uri ->
                val slot = File(directory, index.toString())
                check(slot.mkdir())
                val file = File(slot, safeName(uri))
                val input = resolver.openInputStream(uri) ?: error("Selected file is unreadable")
                input.use { source ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(COPY_BUFFER_SIZE)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            check(totalBytes <= MAX_CACHE_BYTES && root.usableSpace > MIN_FREE_BYTES)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                Uri.fromFile(file)
            }
            StagedUpload(directory, files.toTypedArray())
        }.getOrElse {
            directory.deleteRecursively()
            null
        }
    }

    fun retain(tabId: String, upload: StagedUpload) {
        retained.getOrPut(tabId, ::mutableListOf).add(upload.directory)
    }

    fun release(tabId: String) {
        retained.remove(tabId)?.forEach(File::deleteRecursively)
    }

    fun releaseAll() {
        retained.keys.toList().forEach(::release)
    }

    private fun safeName(uri: Uri): String {
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        val name = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeLast(MAX_NAME_LENGTH)
            ?.map { char ->
                if (char.isLetterOrDigit() || char == '.' || char == '-' || char == '_' || char == ' ') {
                    char
                } else {
                    '_'
                }
            }
            ?.joinToString("")
            ?.trim('.', ' ')
            ?.takeIf(String::isNotBlank)
        if (name != null) return name
        val extension = resolver.getType(uri)
            ?.substringAfter('/', missingDelimiterValue = "")
            ?.lowercase()
            ?.let { subtype ->
                when (subtype) {
                    "jpeg" -> "jpg"
                    "svg+xml" -> "svg"
                    "quicktime" -> "mov"
                    else -> subtype.takeIf { value ->
                        value.length in 1..12 && value.all(Char::isLetterOrDigit)
                    }
                }
            }
        return if (extension == null) "upload" else "upload.$extension"
    }

    internal data class StagedUpload(val directory: File, val uris: Array<Uri>) {
        fun delete() {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "gecko-file-uploads"
        const val MAX_NAME_LENGTH = 120
        const val COPY_BUFFER_SIZE = 64 * 1024
        const val MAX_CACHE_BYTES = 1_073_741_824L
        const val MIN_FREE_BYTES = 32L * 1024 * 1024
    }
}
