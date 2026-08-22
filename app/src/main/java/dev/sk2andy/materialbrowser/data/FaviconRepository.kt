package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaviconRepository private constructor(context: Context) {
    private val store = FaviconStore(context.applicationContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "tab-favicon-io")
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
            bitmap.width !in 1..MAX_FAVICON_BITMAP_DIMENSION ||
            bitmap.height !in 1..MAX_FAVICON_BITMAP_DIMENSION
        ) return
        val snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return
        executor.execute {
            try {
                store.save(tabId, snapshot)
            } finally {
                snapshot.recycle()
            }
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
        @Volatile
        private var instance: FaviconRepository? = null

        fun get(context: Context): FaviconRepository = instance ?: synchronized(this) {
            instance ?: FaviconRepository(context).also { instance = it }
        }
    }
}
