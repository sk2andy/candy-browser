package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer

internal class FaviconClient {
    fun fetch(pageUrl: String): Bitmap? {
        var iconUrl = FaviconFetchRules.originIconUrl(pageUrl) ?: return null
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = runCatching {
                URI(iconUrl).toURL().openConnection() as HttpURLConnection
            }.getOrNull() ?: return null
            val redirect = try {
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "image/*")
                connection.setRequestProperty("Accept-Encoding", "identity")
                val responseCode = connection.responseCode
                if (responseCode in REDIRECT_RESPONSE_CODES) {
                    if (redirectCount == MAX_REDIRECTS) return null
                    FaviconFetchRules.allowedRedirect(
                        iconUrl = iconUrl,
                        location = connection.getHeaderField("Location") ?: return null,
                    ) ?: return null
                } else {
                    if (responseCode !in 200..299) return null
                    val declaredLength = connection.contentLengthLong
                    if (declaredLength !in -1..MAX_FILE_SIZE_BYTES.toLong()) return null
                    val encoded = connection.inputStream.use { input ->
                        input.readNBytes(MAX_FILE_SIZE_BYTES + 1)
                    }
                    if (encoded.isEmpty() || encoded.size > MAX_FILE_SIZE_BYTES) return null
                    return decode(encoded)
                }
            } catch (_: Exception) {
                return null
            } finally {
                connection.disconnect()
            }
            iconUrl = redirect
        }
        return null
    }

    private fun decode(encoded: ByteArray): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(ByteBuffer.wrap(encoded)),
        ) { decoder, info, _ ->
            require(
                info.size.width in 1..MAX_FAVICON_BITMAP_DIMENSION &&
                    info.size.height in 1..MAX_FAVICON_BITMAP_DIMENSION,
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
        }
    }.getOrNull()

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 4_000
        const val READ_TIMEOUT_MILLIS = 6_000
        const val MAX_FILE_SIZE_BYTES = 2 * 1_024 * 1_024
        const val MAX_REDIRECTS = 3
        val REDIRECT_RESPONSE_CODES = setOf(301, 302, 303, 307, 308)
    }
}
