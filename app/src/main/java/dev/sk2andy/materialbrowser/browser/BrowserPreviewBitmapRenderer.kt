package dev.sk2andy.materialbrowser.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/** Preview bounds crop content; one scale preserves the renderer's pixel geometry. */
internal object BrowserPreviewBitmapRenderer {
    fun render(
        sourceWidthPx: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
        draw: (Canvas) -> Unit,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        bitmap.density = Bitmap.DENSITY_NONE
        try {
            val canvas = Canvas(bitmap)
            val scale = targetWidthPx.toFloat() / sourceWidthPx
            canvas.scale(scale, scale)
            draw(canvas)
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    fun render(
        source: Bitmap,
        sourceHeightPx: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): Bitmap = render(source.width, targetWidthPx, targetHeightPx) { canvas ->
        val bounds = Rect(0, 0, source.width, sourceHeightPx)
        // Explicit pixel rectangles bypass BitmapDrawable/Canvas density scaling.
        canvas.drawBitmap(source, bounds, bounds, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
