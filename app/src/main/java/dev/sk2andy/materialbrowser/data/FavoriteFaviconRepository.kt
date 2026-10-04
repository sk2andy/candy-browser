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

    fun capture(url: String, bitmap: Bitmap?, forceRefresh: Boolean = false) {
        val snapshot = bitmap
            ?.takeUnless(Bitmap::isRecycled)
            ?.takeIf { icon ->
                icon.width in 1..MAX_FAVICON_BITMAP_DIMENSION &&
                    icon.height in 1..MAX_FAVICON_BITMAP_DIMENSION
            }
            ?.copy(Bitmap.Config.ARGB_8888, false)
        executor.execute {
            val cached = store.load(url)
            val local = listOfNotNull(snapshot, cached).maxByOrNull(Bitmap::minimumDimension)
            val fetched = if (forceRefresh || (local?.minimumDimension() ?: 0) < FaviconPageIconRules.PREFERRED_ICON_DIMENSION) {
                client.fetchFavorite(url)
            } else {
                null
            }
            val candidates = if (forceRefresh) listOfNotNull(fetched, local) else listOfNotNull(local, fetched)
            val best = candidates.maxByOrNull(Bitmap::minimumDimension)
            try {
                if (best != null) store.save(url, best)
            } finally {
                snapshot?.recycle()
                cached?.recycle()
                fetched?.recycle()
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

    fun loadAll(
        urls: List<String>,
        onLoaded: (Map<String, Bitmap>) -> Unit,
    ) {
        val snapshot = urls.toList()
        executor.execute { onLoaded(loadAll(snapshot)) }
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
