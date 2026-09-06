package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteCapsuleIconStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = SiteCapsuleIconStore(context)

    @Before
    fun setUp() = store.delete(CAPSULE_ID)

    @After
    fun tearDown() = store.delete(CAPSULE_ID)

    @Test
    fun renderedFaviconAndCustomSourcesRoundTripIndependently() {
        val rendered = bitmap(Color.BLUE)
        val source = bitmap(Color.RED, size = 512)
        val custom = bitmap(Color.GREEN, size = 192)

        store.save(CAPSULE_ID, rendered)
        assertNull(store.loadSource(CAPSULE_ID))
        store.saveSource(CAPSULE_ID, source)
        store.saveCustom(CAPSULE_ID, custom)

        val restoredRendered = requireNotNull(store.load(CAPSULE_ID))
        val restoredSource = requireNotNull(store.loadSource(CAPSULE_ID))
        val restoredCustom = requireNotNull(store.loadCustom(CAPSULE_ID))
        assertEquals(Color.BLUE, restoredRendered.getPixel(0, 0))
        assertEquals(Color.RED, restoredSource.getPixel(0, 0))
        assertEquals(256, restoredSource.width)
        assertEquals(256, restoredSource.height)
        assertEquals(Color.GREEN, restoredCustom.getPixel(0, 0))
        assertEquals(192, restoredCustom.width)

        store.delete(CAPSULE_ID)
        assertNull(store.load(CAPSULE_ID))
        assertNull(store.loadSource(CAPSULE_ID))
        assertNull(store.loadCustom(CAPSULE_ID))

        rendered.recycle()
        source.recycle()
        custom.recycle()
        restoredRendered.recycle()
        restoredSource.recycle()
        restoredCustom.recycle()
    }

    @Test
    fun cleanupKeepsAllKnownVariantsAndDeletesOrphanVariants() {
        val orphanId = "d56094fd-e8fb-49d9-aac8-9b85a99c759f"
        val bitmap = bitmap(Color.MAGENTA)
        store.save(CAPSULE_ID, bitmap)
        store.saveSource(CAPSULE_ID, bitmap)
        store.saveCustom(CAPSULE_ID, bitmap)
        store.save(orphanId, bitmap)
        store.saveSource(orphanId, bitmap)
        store.saveCustom(orphanId, bitmap)

        store.cleanup(setOf(CAPSULE_ID))

        val rendered = requireNotNull(store.load(CAPSULE_ID))
        val source = requireNotNull(store.loadSource(CAPSULE_ID))
        val custom = requireNotNull(store.loadCustom(CAPSULE_ID))
        assertEquals(Color.MAGENTA, rendered.getPixel(0, 0))
        assertEquals(Color.MAGENTA, source.getPixel(0, 0))
        assertEquals(Color.MAGENTA, custom.getPixel(0, 0))
        assertNull(store.load(orphanId))
        assertNull(store.loadSource(orphanId))
        assertNull(store.loadCustom(orphanId))

        store.delete(orphanId)
        bitmap.recycle()
        rendered.recycle()
        source.recycle()
        custom.recycle()
    }

    @Test
    fun customSourceRejectsNonSquareBitmap() {
        val bitmap = Bitmap.createBitmap(192, 96, Bitmap.Config.ARGB_8888)

        assertFalse(store.saveCustom(CAPSULE_ID, bitmap))
        assertNull(store.loadCustom(CAPSULE_ID))

        bitmap.recycle()
    }

    private fun bitmap(color: Int, size: Int = 24): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private companion object {
        const val CAPSULE_ID = "04a74ad8-7533-460c-bfbf-a135968940d5"
    }
}
