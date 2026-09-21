package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.util.UUID

/** App-owned copies: folder icons never depend on a document provider retaining access. */
internal class FavoriteFolderIconStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val files = AtomicTabFileDirectory(
        directory = File(applicationContext.noBackupFilesDir, "favorite_folder_icons"),
        extension = "png",
    )

    fun load(folderId: String): Bitmap? {
        val file = files.fileFor(cacheId(folderId)) ?: return null
        return runCatching {
            AtomicFile(file).openRead().use { input -> decode(input.readBytesBounded()) }
        }.getOrNull()
    }

    fun loadAll(folderIds: Collection<String>): Map<String, Bitmap> = buildMap {
        folderIds.distinct().forEach { id -> load(id)?.let { put(id, it) } }
    }

    fun import(folderId: String, uri: Uri): Bitmap? {
        val bitmap = runCatching {
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                decode(input.readBytesBounded())
            }
        }.getOrNull() ?: return null
        if (!save(folderId, bitmap)) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    fun save(folderId: String, bitmap: Bitmap): Boolean {
        val target = files.fileFor(cacheId(folderId)) ?: return false
        if (bitmap.isRecycled || !files.ensureExists()) return false
        return AtomicFile(target).writeSafely { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    fun delete(folderId: String) {
        files.delete(cacheId(folderId))
    }

    fun prune(validFolderIds: Set<String>) {
        files.prune(validFolderIds.map(::cacheId).toSet())
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_DIMENSION || bounds.outHeight !in 1..MAX_DIMENSION) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (bounds.outWidth / inSampleSize > ICON_SIZE || bounds.outHeight / inSampleSize > ICON_SIZE) {
                inSampleSize *= 2
            }
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.apply { prepareToDraw() }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun cacheId(folderId: String): String =
        UUID.nameUUIDFromBytes(folderId.toByteArray(Charsets.UTF_8)).toString()

    private companion object {
        const val MAX_BYTES = 8 * 1_024 * 1_024
        const val MAX_DIMENSION = 8_192
        const val ICON_SIZE = 256
    }
}
