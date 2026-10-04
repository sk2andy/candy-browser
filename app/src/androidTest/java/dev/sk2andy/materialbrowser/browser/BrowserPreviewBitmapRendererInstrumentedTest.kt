package dev.sk2andy.materialbrowser.browser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPreviewBitmapRendererInstrumentedTest {
    @Test
    fun tallViewCaptureCropsHeightWithoutCompressingLandmarks() {
        val bitmap = BrowserPreviewBitmapRenderer.render(
            sourceWidthPx = 600,
            targetWidthPx = 480,
            targetHeightPx = 1_440,
        ) { canvas ->
            canvas.drawColor(Color.RED)
            canvas.drawRect(100f, 100f, 200f, 200f, Paint().apply { color = Color.YELLOW })
            canvas.drawRect(0f, 1_900f, 600f, 2_400f, Paint().apply { color = Color.BLUE })
        }
        try {
            assertEquals(480, bitmap.width)
            assertEquals(1_440, bitmap.height)
            assertEquals(Color.YELLOW, bitmap.getPixel(80, 80))
            assertEquals(Color.YELLOW, bitmap.getPixel(159, 159))
            assertEquals(Color.RED, bitmap.getPixel(79, 80))
            assertEquals(Color.RED, bitmap.getPixel(80, 79))
            assertEquals(Color.RED, bitmap.getPixel(160, 159))
            assertEquals(Color.RED, bitmap.getPixel(159, 160))
            assertEquals(Color.RED, bitmap.getPixel(0, 1_439))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun bitmapCapturePreservesGeometryWithDensityAndNonIntegralScale() {
        val source = Bitmap.createBitmap(300, 600, Bitmap.Config.ARGB_8888).apply {
            density = 640
        }
        Canvas(source).apply {
            drawColor(Color.RED)
            drawRect(90f, 120f, 150f, 180f, Paint().apply { color = Color.YELLOW })
            drawRect(0f, 183f, 300f, 600f, Paint().apply { color = Color.BLUE })
        }
        val bitmap = BrowserPreviewBitmapRenderer.render(
            source = source,
            sourceHeightPx = 600,
            targetWidthPx = 200,
            targetHeightPx = 121,
        )
        try {
            assertEquals(200, bitmap.width)
            assertEquals(121, bitmap.height)
            assertEquals(Bitmap.DENSITY_NONE, bitmap.density)
            assertEquals(Color.YELLOW, bitmap.getPixel(61, 81))
            assertEquals(Color.YELLOW, bitmap.getPixel(98, 118))
            assertEquals(Color.RED, bitmap.getPixel(58, 81))
            assertEquals(Color.RED, bitmap.getPixel(61, 78))
            assertEquals(Color.RED, bitmap.getPixel(101, 118))
            assertEquals(Color.RED, bitmap.getPixel(0, 120))
        } finally {
            bitmap.recycle()
            source.recycle()
        }
    }
}
