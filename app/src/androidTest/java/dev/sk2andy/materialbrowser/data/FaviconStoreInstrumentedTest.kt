package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaviconStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = FaviconStore(context)
    private val tabId = UUID.randomUUID().toString()
    private val otherTabId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        store.delete(tabId)
        store.delete(otherTabId)
    }

    @After
    fun tearDown() {
        store.delete(tabId)
        store.delete(otherTabId)
    }

    @Test
    fun preservesTransparentPixelsAcrossStoreInstances() {
        val source = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(16, 16, Color.argb(127, 220, 40, 90))
        }

        assertTrue(store.save(tabId, source))
        val restored = FaviconStore(context).load(tabId)

        assertNotNull(restored)
        assertEquals(32, restored?.width)
        assertEquals(32, restored?.height)
        assertEquals(0, Color.alpha(requireNotNull(restored).getPixel(0, 0)))
        assertEquals(127, Color.alpha(restored.getPixel(16, 16)))
        source.recycle()
        restored.recycle()
    }

    @Test
    fun removesCorruptFaviconDuringLoad() {
        val file = requireNotNull(store.fileFor(tabId))
        require(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        file.writeText("not a png")

        assertNull(store.load(tabId))
        assertFalse(file.exists())
    }

    @Test
    fun pruneKeepsOpenTabAndDeletesOrphanFavicon() {
        val source = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        assertTrue(store.save(tabId, source))
        assertTrue(store.save(otherTabId, source))

        store.prune(setOf(tabId))

        val retained = store.load(tabId)
        assertNotNull(retained)
        assertNull(store.load(otherTabId))
        source.recycle()
        retained?.recycle()
    }
}
