package dev.sk2andy.materialbrowser.feeds

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class FeedCatalogHttpException(val statusCode: Int) : IOException("HTTP $statusCode")

/** Catalog reads never follow redirects or share browser cookies/authentication. */
internal object FeedCatalogNetwork {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    suspend fun fetch(address: String): String = suspendCancellableCoroutine { continuation ->
        val url = address.toHttpUrlOrNull()
        require(
            url != null &&
                url.scheme == "https" &&
                url.host == "rssamplifier.com" &&
                url.port == 443 &&
                url.username.isEmpty() &&
                url.password.isEmpty() &&
                url.encodedPath.startsWith("/api/")
        ) {
            "Invalid catalog endpoint"
        }
        val call =
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Candy Feeds")
                    .build()
            )
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!response.isSuccessful)
                                throw FeedCatalogHttpException(response.code)
                            val body = response.body ?: throw IOException("Empty catalog response")
                            if (body.contentLength() > FeedRules.MAX_FEED_BYTES)
                                throw IOException("Catalog response too large")
                            val output = ByteArrayOutputStream()
                            body.byteStream().use { input ->
                                val buffer = ByteArray(8192)
                                while (continuation.isActive) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > FeedRules.MAX_FEED_BYTES)
                                        throw IOException("Catalog response too large")
                                    output.write(buffer, 0, count)
                                }
                            }
                            if (continuation.isActive)
                                continuation.resume(output.toString(Charsets.UTF_8.name()))
                        } catch (error: IOException) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            }
        )
    }
}
