package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import android.util.AtomicFile
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
class TabPreviewStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = TabPreviewStore(context)
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
    fun savesLoadsAndDeletesPreviewAcrossStoreInstances() {
        val source = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(30, 90, 180))
        }

        assertTrue(store.save(tabId, source))
        val restored = TabPreviewStore(context).load(tabId)

        assertNotNull(restored)
        assertEquals(48, restored?.width)
        assertEquals(72, restored?.height)
        store.delete(tabId)
        assertNull(TabPreviewStore(context).load(tabId))

        source.recycle()
        restored?.recycle()
    }

    @Test
    fun removesCorruptPreviewDuringLoad() {
        val file = requireNotNull(store.fileFor(tabId))
        require(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        file.writeText("not a webp")

        assertNull(store.load(tabId))
        assertFalse(file.exists())
    }

    @Test
    fun pruneKeepsOpenTabAndDeletesOrphanPreview() {
        val source = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(60, 120, 210))
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

    @Test
    fun recoversPreviousPreviewAfterInterruptedAtomicWrite() {
        val source = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(50, 130, 200))
        }
        assertTrue(store.save(tabId, source))
        val atomicFile = AtomicFile(requireNotNull(store.fileFor(tabId)))
        atomicFile.startWrite().use { interrupted ->
            interrupted.write("partial replacement".toByteArray())
            interrupted.fd.sync()
        }

        store.prune(setOf(tabId))
        val recovered = store.load(tabId)

        assertNotNull(recovered)
        assertEquals(40, recovered?.width)
        assertEquals(60, recovered?.height)
        source.recycle()
        recovered?.recycle()
    }
}
