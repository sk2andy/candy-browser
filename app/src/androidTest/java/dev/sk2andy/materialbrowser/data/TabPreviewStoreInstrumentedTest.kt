package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
    fun sharpPixelPreviewKeepsLosslessPixelsWhileDefaultLoadUsesSmallThumbnail() {
        val source = Bitmap.createBitmap(1_080, 2_410, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK; strokeWidth = 1f }
        for (x in 0 until source.width step 2) canvas.drawLine(x + 0.5f, 0f, x + 0.5f, 2_410f, paint)
        try {
            assertTrue(store.save(tabId, source))
            val sharp = requireNotNull(store.load(tabId, targetWidthPx = 1_280))
            val thumbnail = requireNotNull(store.load(tabId))
            try {
                assertEquals(1_080, sharp.width)
                assertEquals(2_410, sharp.height)
                assertEquals(10_411_200, sharp.byteCount)
                assertEquals(source.getPixel(100, 1_000), sharp.getPixel(100, 1_000))
                assertEquals(source.getPixel(101, 1_000), sharp.getPixel(101, 1_000))
                assertEquals(480, thumbnail.width)
                assertEquals(1_071, thumbnail.height)
                assertTrue(thumbnail.byteCount <= 2_100_000)
            } finally {
                sharp.recycle()
                thumbnail.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    @Test
    fun tallPreviewDecodesSmallThumbnailWithoutStretchingOrPermanentFullSizeAllocation() {
        val source = Bitmap.createBitmap(614, 3_840, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(store.save(tabId, source))
            val thumbnail = requireNotNull(store.load(tabId))
            val sharp = requireNotNull(store.load(tabId, targetWidthPx = 1_280))
            try {
                assertEquals(230, thumbnail.width)
                assertEquals(1_440, thumbnail.height)
                assertTrue(thumbnail.byteCount <= 2_764_800)
                assertTrue(kotlin.math.abs(thumbnail.width / thumbnail.height.toDouble() - 614.0 / 3_840) < 0.001)
                assertEquals(614, sharp.width)
                assertEquals(3_840, sharp.height)
                assertTrue(sharp.byteCount <= 12_000_000)
            } finally {
                thumbnail.recycle()
                sharp.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    @Test
    fun legacySmallPreviewIsNeverUpscaledIntoFalseDetail() {
        val source = Bitmap.createBitmap(480, 1_000, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(store.save(tabId, source))
            val sharp = requireNotNull(store.load(tabId, targetWidthPx = 1_280))
            try {
                assertEquals(480, sharp.width)
                assertEquals(1_000, sharp.height)
            } finally {
                sharp.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    @Test
    fun rejectsOversizedEncodedPreviewBeforeDecodingItsPixels() {
        val source = Bitmap.createBitmap(2_048, 2_048, Bitmap.Config.ARGB_8888)
        try {
            assertFalse(store.save(tabId, source))
            val file = requireNotNull(store.fileFor(tabId))
            require(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
            file.outputStream().use { source.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }

            assertNull(store.load(tabId, targetWidthPx = 1_280))
            assertFalse(file.exists())
        } finally {
            source.recycle()
        }
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

    @Test
    fun removesLegacyPreviewDirectoryOnUpgradeAndClear() {
        val legacyDirectory = File(context.noBackupFilesDir, "tab_previews")
        assertTrue(legacyDirectory.mkdirs() || legacyDirectory.isDirectory)
        val legacyPreview = File(legacyDirectory, "legacy.webp")
        legacyPreview.writeText("old preview")

        TabPreviewStore(context)

        assertFalse(legacyDirectory.exists())

        assertTrue(legacyDirectory.mkdirs() || legacyDirectory.isDirectory)
        legacyPreview.writeText("old preview")
        store.clear()

        assertFalse(legacyDirectory.exists())
    }
}
