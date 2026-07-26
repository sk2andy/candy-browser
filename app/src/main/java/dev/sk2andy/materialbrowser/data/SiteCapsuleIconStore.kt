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
    fun load(capsuleId: String): Bitmap? {
        val id = SiteCapsuleRules.opaqueId(capsuleId) ?: return null
        val target = File(directory, "$id.png")
        if (!target.isFile || target.length() !in 1..MAX_ICON_BYTES) return null
        return runCatching {
            AtomicFile(target).openRead().use(BitmapFactory::decodeStream)
        }.getOrNull()?.takeIf { bitmap ->
            bitmap.width in 1..MAX_ICON_SIZE && bitmap.height in 1..MAX_ICON_SIZE
        }
    }

    @Synchronized
    fun save(capsuleId: String, bitmap: Bitmap): Boolean {
        val id = SiteCapsuleRules.opaqueId(capsuleId) ?: return false
        if (bitmap.isRecycled || bitmap.width !in 1..MAX_ICON_SIZE || bitmap.height !in 1..MAX_ICON_SIZE) {
            return false
        }
        val file = AtomicFile(File(directory, "$id.png"))
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
            AtomicFile(File(directory, "$id.png")).delete()
        }
    }

    @Synchronized
    fun cleanup(knownCapsuleIds: Set<String>) {
        directory.listFiles().orEmpty().forEach { file ->
            val id = file.name.removeSuffix(".png")
            if (!file.name.endsWith(".png") || id !in knownCapsuleIds) file.delete()
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "site_capsule_icons"
        const val MAX_ICON_SIZE = 256
        const val MAX_ICON_BYTES = 256L * 1024L
    }
}
