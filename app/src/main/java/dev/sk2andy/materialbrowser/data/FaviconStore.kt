package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

class FaviconStore(context: Context) {
    private val files = AtomicTabFileDirectory(
        directory = File(context.noBackupFilesDir, DIRECTORY_NAME),
        extension = FILE_EXTENSION,
    )

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
            !files.ensureExists()
        ) return false
        val target = fileFor(tabId) ?: return false
        return AtomicFile(target).writeSafely { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
        }
    }

    fun delete(tabId: String) = files.delete(tabId)

    fun prune(validTabIds: Set<String>) = files.prune(validTabIds)

    fun clear() = files.clear()

    internal fun fileFor(tabId: String): File? = files.fileFor(tabId)

    private companion object {
        const val DIRECTORY_NAME = "tab_favicons"
        const val FILE_EXTENSION = "png"
        const val PNG_QUALITY = 100
        const val MAX_FILE_SIZE_BYTES = 2L * 1_024L * 1_024L
    }
}

internal const val MAX_FAVICON_BITMAP_DIMENSION = 512
