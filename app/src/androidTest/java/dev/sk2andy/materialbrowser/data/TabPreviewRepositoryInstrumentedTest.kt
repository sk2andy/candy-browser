package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabPreviewRepositoryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = TabPreviewRepository.get(context)
    private val store = TabPreviewStore(context)

    @Test
    fun ownedCaptureIsReleasedAfterWriteWhileRegularSavePreservesCallerBitmap() {
        val ownedId = UUID.randomUUID().toString()
        val sharedId = UUID.randomUUID().toString()
        val owned = bitmap()
        val shared = bitmap()
        try {
            repository.saveCapture(ownedId, owned)
            repository.save(sharedId, shared)
            assertTrue(repository.flush())

            assertTrue(owned.isRecycled)
            assertFalse(shared.isRecycled)
            val restoredOwned = store.load(ownedId)
            val restoredShared = store.load(sharedId)
            assertNotNull(restoredOwned)
            assertNotNull(restoredShared)
            restoredOwned?.recycle()
            restoredShared?.recycle()
        } finally {
            if (!owned.isRecycled) owned.recycle()
            shared.recycle()
            store.delete(ownedId)
            store.delete(sharedId)
        }
    }

    @Test
    fun boundedCaptureQueueReleasesRejectedCaptureInsteadOfAccumulatingImages() {
        val anchorId = UUID.randomUUID().toString()
        val ids = List(3) { UUID.randomUUID().toString() }
        val anchor = bitmap()
        val captures = List(3) { bitmap() }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            assertTrue(store.save(anchorId, anchor))
            repository.loadRestorationPreview(anchorId) { loaded ->
                try {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS))
                } finally {
                    loaded?.recycle()
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            captures.forEachIndexed { index, image -> repository.saveCapture(ids[index], image) }

            assertFalse(captures[0].isRecycled)
            assertFalse(captures[1].isRecycled)
            assertTrue(captures[2].isRecycled)
            release.countDown()
            assertTrue(repository.flush())
            assertTrue(captures.all(Bitmap::isRecycled))
            val first = store.load(ids[0])
            val second = store.load(ids[1])
            assertNotNull(first)
            assertNotNull(second)
            assertNull(store.load(ids[2]))
            first?.recycle()
            second?.recycle()
        } finally {
            release.countDown()
            assertTrue(repository.flush())
            captures.filterNot(Bitmap::isRecycled).forEach(Bitmap::recycle)
            anchor.recycle()
            (ids + anchorId).forEach(store::delete)
        }
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(48, 72, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.BLUE)
    }
}
