package dev.sk2andy.materialbrowser.browser.gecko

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoFileUploadStagerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mediaContentUriBecomesReadableFileForGeckoAndIsRemovedWithTab() {
        val contents = byteArrayOf(1, 2, 3, 4)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "candy-upload.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        }
        val mediaUri = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        )
        try {
            context.contentResolver.openOutputStream(mediaUri)?.use { it.write(contents) }
            val stager = GeckoFileUploadStager(context)
            stager.clearOrphans()

            val staged = stager.stage(arrayOf(mediaUri))
            assertNotNull(staged)
            val upload = requireNotNull(staged)
            val file = requireNotNull(upload.uris.single().path)
            assertEquals("file", upload.uris.single().scheme)
            assertEquals("candy-upload.png", java.io.File(file).name)
            assertArrayEquals(contents, java.io.File(file).readBytes())

            stager.retain("tab-a", upload)
            stager.release("tab-a")
            assertFalse(java.io.File(file).exists())
        } finally {
            context.contentResolver.delete(mediaUri, null, null)
        }
    }
}
