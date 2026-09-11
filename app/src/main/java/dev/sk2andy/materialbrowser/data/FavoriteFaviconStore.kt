package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

internal class FavoriteFaviconStore(context: Context) {
    private val files = AtomicTabFileDirectory(
        directory = File(context.noBackupFilesDir, DIRECTORY_NAME),
        extension = FILE_EXTENSION,
    )

    fun load(url: String): Bitmap? {
        val file = fileFor(url) ?: return null
        val atomicFile = AtomicFile(file)
        val bitmap = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            atomicFile.openRead().use { input ->
                if (atomicFile.baseFile.length() !in 1..MAX_FILE_SIZE_BYTES) {
                    atomicFile.delete()
                    return null
                }
                BitmapFactory.decodeStream(input, null, bounds)
                if (
                    bounds.outWidth !in 1..MAX_FAVICON_BITMAP_DIMENSION ||
                    bounds.outHeight !in 1..MAX_FAVICON_BITMAP_DIMENSION
                ) {
                    atomicFile.delete()
                    return null
                }
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            atomicFile.openRead().use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
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

    fun save(url: String, bitmap: Bitmap): Boolean {
        if (
            bitmap.isRecycled ||
            bitmap.width !in 1..MAX_FAVICON_BITMAP_DIMENSION ||
            bitmap.height !in 1..MAX_FAVICON_BITMAP_DIMENSION ||
            !files.ensureExists()
        ) return false
        val target = fileFor(url) ?: return false
        return AtomicFile(target).writeSafely { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output))
        }
    }

    fun prune(validUrls: Set<String>) {
        files.prune(validUrls.mapNotNull(FaviconFetchRules::cacheId).toSet())
    }

    private fun fileFor(url: String): File? =
        FaviconFetchRules.cacheId(url)?.let(files::fileFor)

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sampleSize = 1
        while (
            width / sampleSize > MAX_RENDERED_FAVICON_DIMENSION ||
            height / sampleSize > MAX_RENDERED_FAVICON_DIMENSION
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private companion object {
        const val DIRECTORY_NAME = "favorite_favicons"
        const val FILE_EXTENSION = "png"
        const val PNG_QUALITY = 100
        const val MAX_FILE_SIZE_BYTES = 2L * 1_024L * 1_024L
        const val MAX_RENDERED_FAVICON_DIMENSION = 128
    }
}
