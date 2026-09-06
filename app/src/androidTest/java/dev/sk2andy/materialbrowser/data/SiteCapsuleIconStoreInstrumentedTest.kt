package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun renderedIconAndSourceFaviconRoundTripIndependently() {
        val rendered = bitmap(Color.BLUE)
        val source = bitmap(Color.RED, size = 512)

        store.save(CAPSULE_ID, rendered)
        assertNull(store.loadSource(CAPSULE_ID))
        store.saveSource(CAPSULE_ID, source)

        val restoredRendered = requireNotNull(store.load(CAPSULE_ID))
        val restoredSource = requireNotNull(store.loadSource(CAPSULE_ID))
        assertEquals(Color.BLUE, restoredRendered.getPixel(0, 0))
        assertEquals(Color.RED, restoredSource.getPixel(0, 0))
        assertEquals(256, restoredSource.width)
        assertEquals(256, restoredSource.height)

        store.delete(CAPSULE_ID)
        assertNull(store.load(CAPSULE_ID))
        assertNull(store.loadSource(CAPSULE_ID))

        rendered.recycle()
        source.recycle()
        restoredRendered.recycle()
        restoredSource.recycle()
    }

    private fun bitmap(color: Int, size: Int = 24): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private companion object {
        const val CAPSULE_ID = "04a74ad8-7533-460c-bfbf-a135968940d5"
    }
}
