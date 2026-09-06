package dev.sk2andy.materialbrowser.capsule

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min
import kotlin.math.pow

object CapsuleIconRenderer {
    const val ICON_SIZE = 192

    fun render(
        name: String,
        iconEmoji: String,
        iconColor: CapsuleIconColor,
        favicon: Bitmap?,
        customIcon: Bitmap? = null,
    ): Bitmap {
        val output = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor.backgroundArgb.toInt()
        }
        canvas.drawRoundRect(RectF(8f, 8f, 184f, 184f), 48f, 48f, frame)
        if (customIcon != null && !customIcon.isRecycled) {
            val iconBounds = RectF(25f, 25f, 167f, 167f)
            canvas.save()
            canvas.clipPath(
                Path().apply {
                    addRoundRect(iconBounds, 38f, 38f, Path.Direction.CW)
                },
            )
            canvas.drawBitmap(
                customIcon,
                Rect(0, 0, customIcon.width, customIcon.height),
                iconBounds,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            canvas.restore()
        } else if (favicon != null && !favicon.isRecycled) {
            canvas.drawRoundRect(
                RectF(25f, 25f, 167f, 167f),
                38f,
                38f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = faviconSurfaceColor(favicon)
                },
            )
            val maxWidth = 108f
            val maxHeight = 108f
            val scale = min(maxWidth / favicon.width, maxHeight / favicon.height)
            val width = favicon.width * scale
            val height = favicon.height * scale
            canvas.drawBitmap(
                favicon,
                Rect(0, 0, favicon.width, favicon.height),
                RectF(
                    96f - width / 2f,
                    96f - height / 2f,
                    96f + width / 2f,
                    96f + height / 2f,
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        } else {
            val fallback = iconEmoji.trim().takeIf(String::isNotEmpty)
                ?: name.trim().take(1).uppercase().ifEmpty { "C" }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = iconColor.foregroundArgb.toInt()
                textAlign = Paint.Align.CENTER
                textSize = if (fallback.length <= 2) 72f else 58f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val baseline = 96f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(fallback, 96f, baseline, paint)
        }
        return output
    }

    internal fun faviconSurfaceColor(favicon: Bitmap): Int {
        if (favicon.isRecycled || favicon.width <= 0 || favicon.height <= 0) {
            return LIGHT_FAVICON_SURFACE
        }
        val pixels = IntArray(favicon.width * favicon.height)
        val copied = runCatching {
            favicon.getPixels(pixels, 0, favicon.width, 0, 0, favicon.width, favicon.height)
        }.isSuccess
        if (!copied) return LIGHT_FAVICON_SURFACE
        var alphaTotal = 0.0
        var luminanceTotal = 0.0
        pixels.forEach { pixel ->
            val alpha = Color.alpha(pixel) / 255.0
            if (alpha <= 0.0) return@forEach
            alphaTotal += alpha
            luminanceTotal += relativeLuminance(pixel) * alpha
        }
        if (alphaTotal == 0.0) return LIGHT_FAVICON_SURFACE
        return if (luminanceTotal / alphaTotal >= LIGHT_ICON_LUMINANCE_THRESHOLD) {
            DARK_FAVICON_SURFACE
        } else {
            LIGHT_FAVICON_SURFACE
        }
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) {
                normalized / 12.92
            } else {
                ((normalized + 0.055) / 1.055).pow(2.4)
            }
        }
        return channel(Color.red(color)) * 0.2126 +
            channel(Color.green(color)) * 0.7152 +
            channel(Color.blue(color)) * 0.0722
    }

    internal const val LIGHT_FAVICON_SURFACE = 0xFFFAF7FC.toInt()
    internal const val DARK_FAVICON_SURFACE = 0xFF29252F.toInt()
    private const val LIGHT_ICON_LUMINANCE_THRESHOLD = 0.5
}
