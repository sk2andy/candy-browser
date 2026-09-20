package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteFolderIconStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun importedImageSurvivesSourceRemovalAndStoreRecreation() {
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "$id.png")
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val store = FavoriteFolderIconStore(context)
        val imported = store.import(id, Uri.fromFile(source))
        assertNotNull(imported)
        assertEquals(256, imported!!.width)
        imported.recycle()
        source.delete()
        val restored = FavoriteFolderIconStore(context).load(id)
        assertNotNull(restored)
        assertEquals(Color.MAGENTA, restored!!.getPixel(0, 0))
        restored.recycle()
        store.prune(emptySet())
        assertNull(store.load(id))
    }

    @Test
    fun malformedUploadDoesNotReplaceExistingIcon() {
        val id = UUID.randomUUID().toString()
        val source = File(context.cacheDir, "$id.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val store = FavoriteFolderIconStore(context)
        store.import(id, Uri.fromFile(source))!!.recycle()
        source.writeText("invalid image")
        assertNull(store.import(id, Uri.fromFile(source)))
        val restored = store.load(id)!!
        assertEquals(Color.BLUE, restored.getPixel(0, 0))
        restored.recycle()
        source.delete()
        store.prune(emptySet())
    }
}
