package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.capsule.SiteCapsuleRules
import java.io.File

class SiteCapsuleIconStore(context: Context) {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    @Synchronized
    fun load(capsuleId: String): Bitmap? = load(capsuleId, source = false)

    @Synchronized
    fun loadSource(capsuleId: String): Bitmap? = load(capsuleId, source = true)

    private fun load(capsuleId: String, source: Boolean): Bitmap? {
        val id = SiteCapsuleRules.opaqueId(capsuleId) ?: return null
        val target = iconFile(id, source)
        if (!target.isFile || target.length() !in 1..MAX_ICON_BYTES) return null
        return runCatching {
            AtomicFile(target).openRead().use(BitmapFactory::decodeStream)
        }.getOrNull()?.takeIf { bitmap ->
            bitmap.width in 1..MAX_ICON_SIZE && bitmap.height in 1..MAX_ICON_SIZE
        }
    }

    @Synchronized
    fun save(capsuleId: String, bitmap: Bitmap): Boolean = save(capsuleId, bitmap, source = false)

    @Synchronized
    fun saveSource(capsuleId: String, bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
        val scale = minOf(
            1f,
            MAX_ICON_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat(),
        )
        val bounded = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        return try {
            save(capsuleId, bounded, source = true)
        } finally {
            if (bounded !== bitmap) bounded.recycle()
        }
    }

    private fun save(capsuleId: String, bitmap: Bitmap, source: Boolean): Boolean {
        val id = SiteCapsuleRules.opaqueId(capsuleId) ?: return false
        if (bitmap.isRecycled || bitmap.width !in 1..MAX_ICON_SIZE || bitmap.height !in 1..MAX_ICON_SIZE) {
            return false
        }
        val file = AtomicFile(iconFile(id, source))
        val stream = file.startWrite()
        return try {
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            if (!compressed) error("Capsule icon PNG encoding failed")
            file.finishWrite(stream)
            if (file.baseFile.length() > MAX_ICON_BYTES) {
                file.delete()
                false
            } else {
                true
            }
        } catch (_: Throwable) {
            file.failWrite(stream)
            false
        }
    }

    @Synchronized
    fun delete(capsuleId: String) {
        SiteCapsuleRules.opaqueId(capsuleId)?.let { id ->
            AtomicFile(iconFile(id, source = false)).delete()
            AtomicFile(iconFile(id, source = true)).delete()
        }
    }

    @Synchronized
    fun cleanup(knownCapsuleIds: Set<String>) {
        directory.listFiles().orEmpty().forEach { file ->
            val id = file.name
                .removeSuffix(".png")
                .removeSuffix(SOURCE_SUFFIX)
            if (!file.name.endsWith(".png") || id !in knownCapsuleIds) file.delete()
        }
    }

    private fun iconFile(id: String, source: Boolean): File = File(
        directory,
        if (source) "$id$SOURCE_SUFFIX.png" else "$id.png",
    )

    private companion object {
        const val DIRECTORY_NAME = "site_capsule_icons"
        const val SOURCE_SUFFIX = ".source"
        const val MAX_ICON_SIZE = 256
        const val MAX_ICON_BYTES = 256L * 1024L
    }
}
