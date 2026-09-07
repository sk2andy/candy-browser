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

    @Synchronized
    fun loadCustom(capsuleId: String): Bitmap? = load(capsuleId, custom = true)

    private fun load(
        capsuleId: String,
        source: Boolean = false,
        custom: Boolean = false,
    ): Bitmap? {
        val id = SiteCapsuleRules.opaqueId(capsuleId) ?: return null
        val target = iconFile(id, source = source, custom = custom)
        if (!target.isFile || target.length() !in 1..MAX_ICON_BYTES) return null
        val bitmap = runCatching {
            AtomicFile(target).openRead().use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return null
        if (bitmap.width in 1..MAX_ICON_SIZE && bitmap.height in 1..MAX_ICON_SIZE) return bitmap
        bitmap.recycle()
        return null
    }

    @Synchronized
    fun save(capsuleId: String, bitmap: Bitmap): Boolean = save(capsuleId, bitmap, source = false)

    @Synchronized
    fun saveSource(capsuleId: String, bitmap: Bitmap): Boolean {
        return saveBoundedSource(capsuleId, bitmap, custom = false)
    }

    @Synchronized
    fun saveCustom(capsuleId: String, bitmap: Bitmap): Boolean {
        return saveBoundedSource(capsuleId, bitmap, custom = true)
    }

    private fun saveBoundedSource(
        capsuleId: String,
        bitmap: Bitmap,
        custom: Boolean,
    ): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
        if (custom && bitmap.width != bitmap.height) return false
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
            save(capsuleId, bounded, source = !custom, custom = custom)
        } finally {
            if (bounded !== bitmap) bounded.recycle()
        }
    }

    private fun save(
        capsuleId: String,
        bitmap: Bitmap,
        source: Boolean = false,
        custom: Boolean = false,
    ): Boolean {
        val id = SiteCapsuleRules.opaqueId(capsuleId) ?: return false
        if (bitmap.isRecycled || bitmap.width !in 1..MAX_ICON_SIZE || bitmap.height !in 1..MAX_ICON_SIZE) {
            return false
        }
        val file = AtomicFile(iconFile(id, source = source, custom = custom))
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
            AtomicFile(iconFile(id, custom = true)).delete()
        }
    }

    @Synchronized
    fun cleanup(knownCapsuleIds: Set<String>) {
        directory.listFiles().orEmpty().forEach { file ->
            val id = when {
                file.name.endsWith("$SOURCE_SUFFIX.png") -> {
                    file.name.removeSuffix("$SOURCE_SUFFIX.png")
                }
                file.name.endsWith("$CUSTOM_SUFFIX.png") -> {
                    file.name.removeSuffix("$CUSTOM_SUFFIX.png")
                }
                file.name.endsWith(".png") -> file.name.removeSuffix(".png")
                else -> null
            }
            if (id == null || id !in knownCapsuleIds) file.delete()
        }
    }

    private fun iconFile(
        id: String,
        source: Boolean = false,
        custom: Boolean = false,
    ): File = File(
        directory,
        when {
            custom -> "$id$CUSTOM_SUFFIX.png"
            source -> "$id$SOURCE_SUFFIX.png"
            else -> "$id.png"
        },
    )

    private companion object {
        const val DIRECTORY_NAME = "site_capsule_icons"
        const val SOURCE_SUFFIX = ".source"
        const val CUSTOM_SUFFIX = ".custom"
        const val MAX_ICON_SIZE = 256
        const val MAX_ICON_BYTES = 256L * 1024L
    }
}
