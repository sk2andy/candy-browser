package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID

class FaviconStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)

    fun load(tabId: String): Bitmap? {
        val file = fileFor(tabId) ?: return null
        val atomicFile = AtomicFile(file)
        val bitmap = try {
            atomicFile.openRead().use { input ->
                if (atomicFile.baseFile.length() !in 1..MAX_FILE_SIZE_BYTES) {
                    atomicFile.delete()
                    return null
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                if (
                    bounds.outWidth !in 1..MAX_FAVICON_BITMAP_DIMENSION ||
                    bounds.outHeight !in 1..MAX_FAVICON_BITMAP_DIMENSION
                ) {
                    atomicFile.delete()
                    return null
                }
            }
            atomicFile.openRead().use(BitmapFactory::decodeStream)
        } catch (_: FileNotFoundException) {
            return null
        } catch (_: Exception) {
            null
        }
        if (
            bitmap == null ||
            bitmap.width !in 1..MAX_FAVICON_BITMAP_DIMENSION ||
            bitmap.height !in 1..MAX_FAVICON_BITMAP_DIMENSION
        ) {
            bitmap?.recycle()
            atomicFile.delete()
            return null
        }
        bitmap.prepareToDraw()
        return bitmap
    }

    fun save(tabId: String, bitmap: Bitmap): Boolean {
        if (
            bitmap.isRecycled ||
            bitmap.width !in 1..MAX_FAVICON_BITMAP_DIMENSION ||
            bitmap.height !in 1..MAX_FAVICON_BITMAP_DIMENSION ||
            (!directory.exists() && !directory.mkdirs())
        ) return false
        val target = fileFor(tabId) ?: return false
        val atomicFile = AtomicFile(target)
        var output: FileOutputStream? = null
        return try {
            output = atomicFile.startWrite()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
            atomicFile.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let(atomicFile::failWrite)
            false
        }
    }

    fun delete(tabId: String) {
        val target = fileFor(tabId) ?: return
        AtomicFile(target).delete()
    }

    fun prune(validTabIds: Set<String>) {
        val validNames = validTabIds.mapNotNull(::faviconFileName).toSet()
        val orphanBaseNames = directory.listFiles()
            ?.map { file -> faviconAtomicBaseName(file.name) }
            ?.filterNot(validNames::contains)
            ?.toSet()
            .orEmpty()
        orphanBaseNames.forEach { baseName ->
            AtomicFile(File(directory, baseName)).delete()
        }
    }

    fun clear() {
        directory.listFiles()?.forEach(File::delete)
    }

    internal fun fileFor(tabId: String): File? = faviconFileName(tabId)?.let { File(directory, it) }

    private companion object {
        const val DIRECTORY_NAME = "tab_favicons"
        const val PNG_QUALITY = 100
        const val MAX_FILE_SIZE_BYTES = 2L * 1_024L * 1_024L
    }
}

internal const val MAX_FAVICON_BITMAP_DIMENSION = 512

internal fun faviconFileName(tabId: String): String? = runCatching {
    "${UUID.fromString(tabId)}.png"
}.getOrNull()

private fun faviconAtomicBaseName(fileName: String): String = when {
    fileName.endsWith(".new") -> fileName.removeSuffix(".new")
    fileName.endsWith(".bak") -> fileName.removeSuffix(".bak")
    else -> fileName
}
