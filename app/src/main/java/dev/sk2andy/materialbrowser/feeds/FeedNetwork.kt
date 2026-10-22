package dev.sk2andy.materialbrowser.feeds

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Feed requests deliberately carry no browser cookies or authentication. */
data class FeedNetworkResponse(val url: String, val text: String)

object FeedNetwork {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    suspend fun fetch(url: String): String = fetchResponse(url).text

    suspend fun fetchResponse(url: String): FeedNetworkResponse =
        withContext(Dispatchers.IO) {
            var current = requireNotNull(FeedRules.safeUrl(url)) { "Invalid feed URL" }
            repeat(6) { redirect ->
                ensureActive()
                client
                    .newCall(
                        Request.Builder()
                            .url(current)
                            .header(
                                "Accept",
                                "application/atom+xml, application/rss+xml, application/xml, text/xml, text/html;q=0.8",
                            )
                            .header("User-Agent", "Candy Feeds")
                            .build()
                    )
                    .execute()
                    .use { response ->
                        if (response.code in 300..399) {
                            if (redirect == 5) throw IOException("Too many redirects")
                            current =
                                FeedParser.resolve(current, response.header("Location").orEmpty())
                                    .ifBlank { throw IOException("Invalid redirect") }
                        } else {
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            val body = response.body ?: throw IOException("Empty response")
                            if (body.contentLength() > FeedRules.MAX_FEED_BYTES)
                                throw IOException("Feed too large")
                            val bytes =
                                body.byteStream().use { input ->
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        if (output.size() + count > FeedRules.MAX_FEED_BYTES)
                                            throw IOException("Feed too large")
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                }
                            ensureActive()
                            return@withContext FeedNetworkResponse(
                                current,
                                bytes.toString(
                                    body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                                ),
                            )
                        }
                    }
            }
            throw IOException("Too many redirects")
        }
}
