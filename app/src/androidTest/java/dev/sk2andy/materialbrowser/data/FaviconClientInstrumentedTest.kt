package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaviconClientInstrumentedTest {
    @Test
    fun favoriteFetchUsesLargestDeclaredSameOriginIconInsteadOfSmallDefault() {
        val requestedPaths = mutableListOf<String>()
        val smallIcon = png(16)
        val largeIcon = png(144)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    synchronized(requestedPaths) { requestedPaths += request.path.orEmpty() }
                    return when (request.path) {
                        "/" -> if (request.getHeader("User-Agent").orEmpty().contains("Android")) {
                            MockResponse().setResponseCode(302)
                                .setHeader("Location", "https://m.youtube.com/")
                        } else MockResponse().setBody(
                            """<html><head>
                                <link rel="icon" href="/icon-32.png" sizes="32x32">
                                <link rel="icon" href="/icon-144.png" sizes="144x144">
                            </head></html>""".trimIndent(),
                        )
                        "/icon-144.png" -> MockResponse().setBody(Buffer().write(largeIcon))
                        "/favicon.ico", "/icon-32.png" ->
                            MockResponse().setBody(Buffer().write(smallIcon))
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            start()
        }
        try {
            val bitmap = FaviconClient().fetchFavorite(server.url("/private-path?q=secret").toString())

            assertNotNull(bitmap)
            assertEquals(144, bitmap?.width)
            assertEquals(144, bitmap?.height)
            assertEquals(listOf("/", "/icon-144.png"), requestedPaths)
            bitmap?.recycle()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun favoriteFetchRejectsThirdPartyLinksAndRedirectsThenKeepsSmallFallback() {
        val thirdParty = MockWebServer().apply { start() }
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/" -> MockResponse().setBody(
                        """<link rel="icon" href="${thirdParty.url("/tracking.png")}" sizes="256x256">
                            <link rel="icon" href="/redirect.png" sizes="144x144">""".trimIndent(),
                    )
                    "/redirect.png" -> MockResponse().setResponseCode(302)
                        .setHeader("Location", thirdParty.url("/tracking.png"))
                    "/favicon.ico" -> MockResponse().setBody(Buffer().write(png(16)))
                    else -> MockResponse().setResponseCode(404)
                }
            }
            start()
        }
        try {
            val bitmap = FaviconClient().fetchFavorite(server.url("/private?q=secret").toString())

            assertEquals(16, bitmap?.width)
            assertEquals(0, thirdParty.requestCount)
            assertEquals(3, server.requestCount)
            bitmap?.recycle()
        } finally {
            server.shutdown()
            thirdParty.shutdown()
        }
    }

    private fun png(size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
