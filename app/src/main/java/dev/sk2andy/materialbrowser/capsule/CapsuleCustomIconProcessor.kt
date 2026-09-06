package dev.sk2andy.materialbrowser.capsule

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.nio.ByteBuffer

object CapsuleCustomIconProcessor {
    const val OUTPUT_SIZE = 192

    fun decodeCandidate(contentResolver: ContentResolver, uri: Uri): Bitmap? {
        val mimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
        if (mimeType != null && !mimeType.startsWith("image/")) return null
        return decodeCatchingMemoryFailure {
            val encoded = contentResolver.openInputStream(uri)?.use { input ->
                input.readNBytes((MAX_SOURCE_BYTES + 1L).toInt())
            } ?: return null
            if (encoded.size.toLong() > MAX_SOURCE_BYTES) return null
            val source = ImageDecoder.createSource(ByteBuffer.wrap(encoded))
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
            }.takeIf(::isValidCandidate)
        }
    }

    fun crop(bitmap: Bitmap, crop: CapsuleIconCrop): Bitmap? {
        if (!isValidCandidate(bitmap)) return null
        val layout = CapsuleIconCropRules.layout(
            imageWidth = bitmap.width.toFloat(),
            imageHeight = bitmap.height.toFloat(),
            viewportSize = OUTPUT_SIZE.toFloat(),
            crop = crop,
        ) ?: return null
        return runCatching {
            Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888).also { output ->
                Canvas(output).drawBitmap(
                    bitmap,
                    Rect(0, 0, bitmap.width, bitmap.height),
                    RectF(
                        layout.left,
                        layout.top,
                        layout.left + layout.width,
                        layout.top + layout.height,
                    ),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
            }
        }.getOrNull()
    }

    private fun isValidCandidate(bitmap: Bitmap): Boolean =
        !bitmap.isRecycled &&
            bitmap.width in 1..MAX_BITMAP_DIMENSION &&
            bitmap.height in 1..MAX_BITMAP_DIMENSION

    private const val MAX_BITMAP_DIMENSION = 2_048
    private const val MAX_SOURCE_DIMENSION = 32_768
    private const val MAX_SOURCE_PIXELS = 67_108_864L
    private const val MAX_SOURCE_BYTES = 32L * 1_024L * 1_024L
}

private inline fun <T> decodeCatchingMemoryFailure(block: () -> T): T? = try {
    block()
} catch (_: Exception) {
    null
} catch (_: OutOfMemoryError) {
    null
}
