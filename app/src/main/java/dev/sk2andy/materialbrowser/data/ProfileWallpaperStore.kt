package dev.sk2andy.materialbrowser.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.AtomicFile
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperRules
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import java.io.File
import java.io.FileNotFoundException
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

class ProfileWallpaperStore(context: Context) {
    private val directory = File(context.filesDir, DIRECTORY_NAME)

    fun decodeCandidate(contentResolver: ContentResolver, uri: Uri): Bitmap? =
        decodeCatchingMemoryFailure {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                check(
                    width in 1..MAX_SOURCE_DIMENSION &&
                        height in 1..MAX_SOURCE_DIMENSION &&
                        width.toLong() * height.toLong() <= MAX_SOURCE_PIXELS,
                )
                val scale = minOf(
                    1f,
                    MAX_BITMAP_DIMENSION.toFloat() / maxOf(width, height).toFloat(),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                )
                decoder.setOnPartialImageListener { false }
            }.takeIf(::isValidBitmap)
        }

    fun load(profileId: String, wallpaperTarget: ProfileWallpaperTarget): Bitmap? =
        synchronized(FILE_LOCK) {
            val target = fileFor(profileId, wallpaperTarget) ?: return null
            if (!target.isFile) return null
            val atomicFile = AtomicFile(target)
            val bitmap = try {
                atomicFile.openRead().use { input ->
                    if (atomicFile.baseFile.length() !in 1..MAX_FILE_SIZE_BYTES) {
                        atomicFile.delete()
                        return null
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, bounds)
                    if (
                        bounds.outWidth !in 1..MAX_BITMAP_DIMENSION ||
                        bounds.outHeight !in 1..MAX_BITMAP_DIMENSION
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
            } catch (_: OutOfMemoryError) {
                null
            }
            if (bitmap == null || !isValidBitmap(bitmap)) {
                bitmap?.recycle()
                atomicFile.delete()
                return null
            }
            bitmap.prepareToDraw()
            return bitmap
        }

    fun save(
        profileId: String,
        wallpaperTarget: ProfileWallpaperTarget,
        bitmap: Bitmap,
    ): Boolean = synchronized(FILE_LOCK) {
        if (!isValidBitmap(bitmap) || (!directory.isDirectory && !directory.mkdirs())) return false
        val target = fileFor(profileId, wallpaperTarget) ?: return false
        val atomicFile = AtomicFile(target)
        val saved = atomicFile.writeSafely { output ->
            val boundedOutput = BoundedOutputStream(output, MAX_FILE_SIZE_BYTES)
            check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, boundedOutput))
            check(boundedOutput.writtenByteCount > 0L)
            boundedOutput.flush()
        }
        val valid = saved && target.length() in 1..MAX_FILE_SIZE_BYTES
        if (valid && ProfileWallpaperTarget.entries.all { candidate ->
                fileFor(profileId, candidate)?.isFile == true
            }
        ) {
            legacyFileFor(profileId)?.let { AtomicFile(it).delete() }
        }
        return valid
    }

    fun delete(profileId: String, wallpaperTarget: ProfileWallpaperTarget) =
        synchronized(FILE_LOCK) {
            fileFor(profileId, wallpaperTarget)?.let { AtomicFile(it).delete() }
        }

    fun delete(profileId: String) = synchronized(FILE_LOCK) {
        fileNamesFor(profileId).forEach { fileName ->
            AtomicFile(File(directory, fileName)).delete()
        }
    }

    fun cleanup(
        configuredWallpapers: Set<Pair<String, ProfileWallpaperTarget>>,
    ) = synchronized(FILE_LOCK) {
        val configuredProfileIds = configuredWallpapers.mapTo(hashSetOf()) { it.first }
        val validNames = buildSet {
            configuredWallpapers.forEach { (profileId, wallpaperTarget) ->
                fileFor(profileId, wallpaperTarget)?.name?.let(::add)
            }
            configuredProfileIds.forEach { profileId ->
                legacyFileFor(profileId)?.name?.let(::add)
            }
        }
        directory.listFiles().orEmpty().forEach { file ->
            val baseName = when {
                file.name.endsWith(".new") -> file.name.removeSuffix(".new")
                file.name.endsWith(".bak") -> file.name.removeSuffix(".bak")
                else -> file.name
            }
            if (baseName !in validNames) AtomicFile(File(directory, baseName)).delete()
        }
    }

    fun migrateLegacy(profileIds: Set<String>) = synchronized(FILE_LOCK) {
        profileIds.forEach profile@{ profileId ->
            val legacy = legacyFileFor(profileId) ?: return@profile
            val atomicLegacy = AtomicFile(legacy)
            val validLegacy = try {
                atomicLegacy.openRead().use { }
                atomicLegacy.baseFile.length() in 1..MAX_FILE_SIZE_BYTES
            } catch (_: FileNotFoundException) {
                false
            }
            if (!validLegacy) {
                atomicLegacy.delete()
                return@profile
            }
            ProfileWallpaperTarget.entries.forEach target@{ wallpaperTarget ->
                val target = fileFor(profileId, wallpaperTarget) ?: return@target
                if (target.isFile) return@target
                AtomicFile(target).writeSafely { output ->
                    atomicLegacy.openRead().use { input -> input.copyTo(output) }
                }
            }
            if (ProfileWallpaperTarget.entries.all { wallpaperTarget ->
                    fileFor(profileId, wallpaperTarget)?.let { target ->
                        target.length() in 1..MAX_FILE_SIZE_BYTES
                    } == true
                }
            ) {
                atomicLegacy.delete()
            }
        }
    }

    internal fun fileFor(profileId: String, wallpaperTarget: ProfileWallpaperTarget): File? =
        profileHash(profileId)?.let { hash ->
            File(directory, "$hash.${wallpaperTarget.wireValue}.$FILE_EXTENSION")
        }

    internal fun legacyFileFor(profileId: String): File? = profileHash(profileId)
        ?.let { hash -> File(directory, "$hash.$FILE_EXTENSION") }

    private fun fileNamesFor(profileId: String): List<String> = profileHash(profileId)
        ?.let { hash ->
            buildList {
                add("$hash.$FILE_EXTENSION")
                ProfileWallpaperTarget.entries.forEach { target ->
                    add("$hash.${target.wireValue}.$FILE_EXTENSION")
                }
            }
        }
        .orEmpty()

    private fun profileHash(profileId: String): String? = profileId
        .takeIf(ProfileWallpaperRules::isSupportedLocalProfileId)
        ?.let(::sha256)

    private fun isValidBitmap(bitmap: Bitmap): Boolean =
        !bitmap.isRecycled &&
            bitmap.width in 1..MAX_BITMAP_DIMENSION &&
            bitmap.height in 1..MAX_BITMAP_DIMENSION

    private companion object {
        const val DIRECTORY_NAME = "profile_wallpapers"
        const val FILE_EXTENSION = "webp"
        const val WEBP_QUALITY = 88
        const val MAX_BITMAP_DIMENSION = 2_560
        const val MAX_SOURCE_DIMENSION = 32_768
        const val MAX_SOURCE_PIXELS = 268_435_456L
        const val MAX_FILE_SIZE_BYTES = 16L * 1_024L * 1_024L
        val FILE_LOCK = Any()
    }
}

private inline fun <T> decodeCatchingMemoryFailure(block: () -> T): T? = try {
    block()
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}

private class BoundedOutputStream(
    output: OutputStream,
    private val maximumBytes: Long,
) : FilterOutputStream(output) {
    var writtenByteCount = 0L
        private set

    override fun write(value: Int) {
        reserve(1)
        out.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        reserve(length)
        out.write(buffer, offset, length)
    }

    private fun reserve(byteCount: Int) {
        if (byteCount < 0 || writtenByteCount + byteCount > maximumBytes) {
            throw IOException("Wallpaper exceeds encoded size limit")
        }
        writtenByteCount += byteCount
    }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
