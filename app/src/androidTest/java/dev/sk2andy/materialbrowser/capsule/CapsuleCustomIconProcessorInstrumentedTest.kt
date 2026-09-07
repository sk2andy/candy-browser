package dev.sk2andy.materialbrowser.capsule

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleCustomIconProcessorInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun decoderAcceptsRealImageContentAndRejectsMalformedContent() {
        val validFile = java.io.File(context.cacheDir, "capsule_custom_icon_valid.png")
        val invalidFile = java.io.File(context.cacheDir, "capsule_custom_icon_invalid.png")
        val source = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888)
        validFile.outputStream().use { output ->
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        invalidFile.writeText("not an image")

        val decoded = CapsuleCustomIconProcessor.decodeCandidate(
            context.contentResolver,
            Uri.fromFile(validFile),
        )

        assertEquals(512, decoded?.width)
        assertEquals(128, decoded?.height)
        assertNull(
            CapsuleCustomIconProcessor.decodeCandidate(
                context.contentResolver,
                Uri.fromFile(invalidFile),
            ),
        )

        source.recycle()
        decoded?.recycle()
        validFile.delete()
        invalidFile.delete()
    }

    @Test
    fun cropProducesBoundedSquareFromSelectedSourceRegion() {
        val source = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            for (x in 200 until width) {
                for (y in 0 until height) setPixel(x, y, Color.BLUE)
            }
        }

        val cropped = requireNotNull(
            CapsuleCustomIconProcessor.crop(source, CapsuleIconCrop()),
        )

        assertEquals(CapsuleCustomIconProcessor.OUTPUT_SIZE, cropped.width)
        assertEquals(CapsuleCustomIconProcessor.OUTPUT_SIZE, cropped.height)
        assertEquals(Color.RED, cropped.getPixel(24, 96))
        assertEquals(Color.BLUE, cropped.getPixel(168, 96))

        source.recycle()
        cropped.recycle()
    }
}
