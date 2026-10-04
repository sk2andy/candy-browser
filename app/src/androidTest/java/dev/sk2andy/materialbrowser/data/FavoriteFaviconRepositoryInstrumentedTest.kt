package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteFaviconRepositoryInstrumentedTest {
    @Test
    fun explicitCaptureUpgradesExistingSmallFavoriteCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FavoriteFaviconStore(context)
        val repository = FavoriteFaviconRepository.get(context)
        val sharp = icon(144)
        val small = icon(16)
        val png = ByteArrayOutputStream().use { output ->
            check(sharp.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/" -> MockResponse().setBody("""<link rel="icon" href="/sharp.png" sizes="144x144">""")
                    "/sharp.png" -> MockResponse().setBody(Buffer().write(png))
                    else -> MockResponse().setResponseCode(404)
                }
            }
            start()
        }
        val url = server.url("/favorite?q=secret").toString()
        try {
            assertTrue(repository.flush())
            assertTrue(store.save(url, small))

            repository.capture(url, bitmap = null, forceRefresh = true)
            assertTrue(repository.flush())
            val restored = store.load(url)

            assertEquals(144, restored?.width)
            assertEquals(144, restored?.height)
            assertEquals(2, server.requestCount)
            restored?.recycle()
        } finally {
            sharp.recycle()
            small.recycle()
            store.prune(emptySet())
            server.shutdown()
        }
    }

    @Test
    fun explicitRefreshReplacesEquallySharpIconContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FavoriteFaviconStore(context)
        val repository = FavoriteFaviconRepository.get(context)
        val cached = icon(144)
        val replacement = icon(144).apply { eraseColor(Color.BLUE) }
        val png = ByteArrayOutputStream().use { output ->
            check(replacement.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/" -> MockResponse().setBody("""<link rel="icon" href="/sharp.png" sizes="144x144">""")
                    "/sharp.png" -> MockResponse().setBody(Buffer().write(png))
                    else -> MockResponse().setResponseCode(404)
                }
            }
            start()
        }
        val url = server.url("/favorite").toString()
        try {
            assertTrue(repository.flush())
            assertTrue(store.save(url, cached))

            repository.capture(url, bitmap = null, forceRefresh = true)
            assertTrue(repository.flush())
            val restored = store.load(url)

            assertEquals(144, restored?.width)
            assertEquals(Color.BLUE, restored?.getPixel(72, 72))
            assertEquals(2, server.requestCount)
            restored?.recycle()
        } finally {
            cached.recycle()
            replacement.recycle()
            store.prune(emptySet())
            server.shutdown()
        }
    }

    @Test
    fun smallTabIconDoesNotReplaceSharperFavoriteCacheOrTriggerNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FavoriteFaviconStore(context)
        val repository = FavoriteFaviconRepository.get(context)
        val server = MockWebServer().apply { start() }
        val url = server.url("/favorite").toString()
        val sharp = icon(144)
        val small = icon(16)
        try {
            assertTrue(repository.flush())
            assertTrue(store.save(url, sharp))

            repository.capture(url, small)
            assertTrue(repository.flush())
            val restored = store.load(url)

            assertEquals(144, restored?.width)
            assertEquals(144, restored?.height)
            assertEquals(0, server.requestCount)
            restored?.recycle()
        } finally {
            sharp.recycle()
            small.recycle()
            store.prune(emptySet())
            server.shutdown()
        }
    }

    private fun icon(size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
}
