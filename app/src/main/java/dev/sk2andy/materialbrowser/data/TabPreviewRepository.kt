package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TabPreviewRepository private constructor(context: Context) {
    private val store = TabPreviewStore(context.applicationContext)
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
        executor.execute { store.save(tabId, bitmap) }
    }

    fun delete(tabId: String) {
        executor.execute { store.delete(tabId) }
    }

    fun clear() {
        executor.execute(store::clear)
    }

    companion object {
        @Volatile
        private var instance: TabPreviewRepository? = null

        fun get(context: Context): TabPreviewRepository = instance ?: synchronized(this) {
            instance ?: TabPreviewRepository(context).also { instance = it }
        }
    }
}
