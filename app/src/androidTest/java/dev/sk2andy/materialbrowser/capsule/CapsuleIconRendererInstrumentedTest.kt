package dev.sk2andy.materialbrowser.capsule

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleIconRendererInstrumentedTest {
    @Test
    fun selectedColorRepaintsIconTileAroundFavicon() {
        val favicon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val pink = CapsuleIconRenderer.render(
            name = "Mail",
            iconEmoji = "📬",
            iconColor = CapsuleIconColor.Pink,
            favicon = favicon,
        )
        val sky = CapsuleIconRenderer.render(
            name = "Mail",
            iconEmoji = "📬",
            iconColor = CapsuleIconColor.Sky,
            favicon = favicon,
        )

        assertEquals(CapsuleIconColor.Pink.backgroundArgb.toInt(), pink.getPixel(16, 96))
        assertEquals(CapsuleIconColor.Sky.backgroundArgb.toInt(), sky.getPixel(16, 96))
        assertEquals(Color.RED, pink.getPixel(96, 96))
        assertEquals(Color.RED, sky.getPixel(96, 96))

        favicon.recycle()
        pink.recycle()
        sky.recycle()
    }

    @Test
    fun lightAndDarkTransparentFaviconsGetContrastingNeutralSurfaces() {
        val lightFavicon = transparentFavicon(Color.WHITE)
        val darkFavicon = transparentFavicon(Color.BLACK)
        val lightIcon = CapsuleIconRenderer.render(
            name = "Light",
            iconEmoji = "☀️",
            iconColor = CapsuleIconColor.Pink,
            favicon = lightFavicon,
        )
        val darkIcon = CapsuleIconRenderer.render(
            name = "Dark",
            iconEmoji = "🌙",
            iconColor = CapsuleIconColor.Pink,
            favicon = darkFavicon,
        )

        assertEquals(CapsuleIconRenderer.DARK_FAVICON_SURFACE, lightIcon.getPixel(30, 96))
        assertEquals(CapsuleIconRenderer.LIGHT_FAVICON_SURFACE, darkIcon.getPixel(30, 96))
        assertEquals(Color.WHITE, lightIcon.getPixel(96, 96))
        assertEquals(Color.BLACK, darkIcon.getPixel(96, 96))
        assertEquals(CapsuleIconColor.Pink.backgroundArgb.toInt(), lightIcon.getPixel(16, 96))
        assertEquals(CapsuleIconColor.Pink.backgroundArgb.toInt(), darkIcon.getPixel(16, 96))

        lightFavicon.recycle()
        darkFavicon.recycle()
        lightIcon.recycle()
        darkIcon.recycle()
    }

    private fun transparentFavicon(color: Int): Bitmap =
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).drawRect(8f, 8f, 24f, 24f, Paint().apply { this.color = color })
        }
}
