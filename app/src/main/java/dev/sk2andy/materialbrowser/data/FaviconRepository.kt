package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class FaviconRepository private constructor(context: Context) {
    private val store = FaviconStore(context.applicationContext)
    private val client = FaviconClient()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "tab-favicon-io")
    }
    private val fetchExecutor: ExecutorService = ThreadPoolExecutor(
        2,
        2,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(8),
        { task -> Thread(task, "tab-favicon-fetch") },
        ThreadPoolExecutor.AbortPolicy(),
    )

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

    fun fetch(
        pageUrl: String,
        shouldFetch: () -> Boolean,
        onLoaded: (Bitmap?) -> Unit,
    ): Boolean = try {
        fetchExecutor.execute { onLoaded(if (shouldFetch()) client.fetch(pageUrl) else null) }
        true
    } catch (_: RejectedExecutionException) {
        false
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
