package dev.sk2andy.materialbrowser.capsule

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.min

object CapsuleIconRenderer {
    const val ICON_SIZE = 192

    fun render(
        name: String,
        profileEmoji: String,
        favicon: Bitmap?,
    ): Bitmap {
        val output = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                ICON_SIZE.toFloat(),
                ICON_SIZE.toFloat(),
                intArrayOf(Color.rgb(255, 126, 169), Color.rgb(130, 91, 255)),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(RectF(8f, 8f, 184f, 184f), 48f, 48f, frame)
        canvas.drawRoundRect(
            RectF(25f, 25f, 167f, 167f),
            38f,
            38f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
        )
        if (favicon != null && !favicon.isRecycled) {
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
            val fallback = profileEmoji.trim().takeIf(String::isNotEmpty)
                ?: name.trim().take(1).uppercase().ifEmpty { "C" }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(71, 45, 116)
                textAlign = Paint.Align.CENTER
                textSize = if (fallback.length <= 2) 72f else 58f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val baseline = 96f - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(fallback, 96f, baseline, paint)
        }
        return output
    }
}
