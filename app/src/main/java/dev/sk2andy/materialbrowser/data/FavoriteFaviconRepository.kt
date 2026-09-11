package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.graphics.Bitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class FavoriteFaviconRepository private constructor(context: Context) {
    private val store = FavoriteFaviconStore(context.applicationContext)
    private val client = FaviconClient()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "favorite-favicon-io")
    }

    fun capture(url: String, bitmap: Bitmap?) {
        val snapshot = bitmap
            ?.takeUnless(Bitmap::isRecycled)
            ?.takeIf { icon ->
                icon.width in 1..MAX_FAVICON_BITMAP_DIMENSION &&
                    icon.height in 1..MAX_FAVICON_BITMAP_DIMENSION
            }
            ?.copy(Bitmap.Config.ARGB_8888, false)
        executor.execute {
            if (snapshot != null) {
                try {
                    store.save(url, snapshot)
                } finally {
                    snapshot.recycle()
                }
                return@execute
            }
            val fetched = client.fetch(url) ?: return@execute
            try {
                store.save(url, fetched)
            } finally {
                fetched.recycle()
            }
        }
    }

    fun loadAll(urls: List<String>): Map<String, Bitmap> {
        val bitmapsByCacheId = mutableMapOf<String, Bitmap?>()
        val loadedByUrl = mutableMapOf<String, Bitmap>()
        urls.forEach { url ->
            val cacheId = FaviconFetchRules.cacheId(url) ?: return@forEach
            if (!bitmapsByCacheId.containsKey(cacheId)) {
                bitmapsByCacheId[cacheId] = store.load(url)
            }
            bitmapsByCacheId[cacheId]?.let { bitmap ->
                loadedByUrl[url] = bitmap
            }
        }
        return loadedByUrl
    }

    fun prune(validUrls: Set<String>) {
        val snapshot = validUrls.toSet()
        executor.execute { store.prune(snapshot) }
    }

    fun flush(): Boolean = executor.awaitIdle()

    companion object {
        @Volatile
        private var instance: FavoriteFaviconRepository? = null

        fun get(context: Context): FavoriteFaviconRepository = instance ?: synchronized(this) {
            instance ?: FavoriteFaviconRepository(context).also { instance = it }
        }
    }
}
