package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException

class TabPreviewStore(context: Context) {
    private val files = AtomicTabFileDirectory(
        directory = File(context.noBackupFilesDir, DIRECTORY_NAME),
        extension = FILE_EXTENSION,
    )
    private val legacyDirectory = File(context.noBackupFilesDir, LEGACY_DIRECTORY_NAME)

    init {
        deleteDirectory(legacyDirectory)
    }

    fun load(
        tabId: String,
        targetWidthPx: Int = TabPreviewCaptureRules.COMPACT_TARGET_WIDTH_PX,
    ): Bitmap? {
        if (targetWidthPx !in 1..TabPreviewCaptureRules.MAX_TARGET_WIDTH_PX) return null
        var sampleSize = 1
        lateinit var dimensions: TabPreviewBitmapDimensions
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
                    bounds.outWidth !in 1..TabPreviewCaptureRules.MAX_BITMAP_DIMENSION ||
                    bounds.outHeight !in 1..TabPreviewCaptureRules.MAX_BITMAP_DIMENSION ||
                    bounds.outWidth.toLong() * bounds.outHeight > TabPreviewCaptureRules.MAX_BITMAP_PIXELS
                ) {
                    atomicFile.delete()
                    return null
                }
                dimensions = requireNotNull(TabPreviewCaptureRules.resolveBitmapDimensions(
                    sourceWidthPx = bounds.outWidth,
                    sourceHeightPx = bounds.outHeight,
                    targetWidthPx = targetWidthPx,
                    maximumTargetHeightPx = if (targetWidthPx <= TabPreviewCaptureRules.COMPACT_TARGET_WIDTH_PX) {
                        TabPreviewCaptureRules.maximumTargetHeightPx(targetWidthPx)
                    } else {
                        TabPreviewCaptureRules.MAX_BITMAP_DIMENSION
                    },
                ))
                sampleSize = minOf(
                    TabPreviewCaptureRules.decodeSampleSize(bounds.outWidth, dimensions.widthPx),
                    TabPreviewCaptureRules.decodeSampleSize(bounds.outHeight, dimensions.heightPx),
                )
            }
            atomicFile.openRead().use { input ->
                BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
        } catch (_: FileNotFoundException) {
            return null
        } catch (_: Exception) {
            null
        }
        if (
            bitmap == null ||
            bitmap.width !in 1..TabPreviewCaptureRules.MAX_BITMAP_DIMENSION ||
            bitmap.height !in 1..TabPreviewCaptureRules.MAX_BITMAP_DIMENSION
        ) {
            bitmap?.recycle()
            atomicFile.delete()
            return null
        }
        if (dimensions.widthPx == bitmap.width && dimensions.heightPx == bitmap.height) return bitmap
        return try {
            Bitmap.createScaledBitmap(
                bitmap,
                dimensions.widthPx,
                dimensions.heightPx,
                true,
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun save(tabId: String, bitmap: Bitmap): Boolean {
        if (
            bitmap.isRecycled ||
            bitmap.width !in 1..TabPreviewCaptureRules.MAX_BITMAP_DIMENSION ||
            bitmap.height !in 1..TabPreviewCaptureRules.MAX_BITMAP_DIMENSION ||
            bitmap.width.toLong() * bitmap.height > TabPreviewCaptureRules.MAX_BITMAP_PIXELS ||
            !files.ensureExists()
        ) return false
        val target = fileFor(tabId) ?: return false
        return AtomicFile(target).writeSafely { output ->
            check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, WEBP_QUALITY, output))
        }
    }

    fun delete(tabId: String) = files.delete(tabId)

    fun prune(validTabIds: Set<String>) = files.prune(validTabIds)

    fun clear() {
        files.clearAndRemoveDirectory()
        deleteDirectory(legacyDirectory)
    }

    internal fun fileFor(tabId: String): File? = files.fileFor(tabId)

    private companion object {
        const val DIRECTORY_NAME = "tab_previews_v2"
        const val LEGACY_DIRECTORY_NAME = "tab_previews"
        const val FILE_EXTENSION = "webp"
        const val WEBP_QUALITY = 100
        const val MAX_FILE_SIZE_BYTES = 12L * 1_024L * 1_024L
    }
}

private fun deleteDirectory(directory: File) {
    directory.listFiles()?.forEach(File::delete)
    directory.delete()
}
