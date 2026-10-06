package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TabPreviewRepository private constructor(context: Context) {
    private val store = TabPreviewStore(context.applicationContext)
    private val captureLock = Any()
    private var pendingCaptureCount = 0
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "tab-preview-io")
    }

    fun restore(validTabIds: Set<String>, onLoaded: (String, Bitmap) -> Unit) {
        executor.execute {
            store.prune(validTabIds)
            validTabIds.forEach { tabId ->
                store.load(tabId)?.let { bitmap -> onLoaded(tabId, bitmap) }
            }
        }
    }

    fun save(tabId: String, bitmap: Bitmap) {
        if (
            bitmap.isRecycled ||
            bitmap.width.toLong() * bitmap.height > TabPreviewCaptureRules.MAX_BITMAP_PIXELS
        ) return
        bitmap.copy(Bitmap.Config.ARGB_8888, false)?.let { snapshot -> saveCapture(tabId, snapshot) }
    }

    /** Takes ownership of the full-resolution capture, including when the bounded queue is full. */
    fun saveCapture(tabId: String, bitmap: Bitmap) {
        val accepted = synchronized(captureLock) {
            if (pendingCaptureCount >= MAX_PENDING_CAPTURES) false else {
                pendingCaptureCount++
                true
            }
        }
        if (!accepted) {
            bitmap.recycle()
            return
        }
        executor.execute {
            try {
                store.save(tabId, bitmap)
            } finally {
                bitmap.recycle()
                synchronized(captureLock) { pendingCaptureCount-- }
            }
        }
    }

    fun loadRestorationPreview(tabId: String, onLoaded: (Bitmap?) -> Unit) {
        executor.execute {
            onLoaded(store.load(tabId, targetWidthPx = TabPreviewCaptureRules.MAX_TARGET_WIDTH_PX))
        }
    }

    fun delete(tabId: String) {
        executor.execute { store.delete(tabId) }
    }

    fun clear() {
        executor.execute(store::clear)
    }

    fun flush(): Boolean = executor.awaitIdle()

    companion object {
        private const val MAX_PENDING_CAPTURES = 2

        @Volatile
        private var instance: TabPreviewRepository? = null

        fun get(context: Context): TabPreviewRepository = instance ?: synchronized(this) {
            instance ?: TabPreviewRepository(context).also { instance = it }
        }
    }
}
