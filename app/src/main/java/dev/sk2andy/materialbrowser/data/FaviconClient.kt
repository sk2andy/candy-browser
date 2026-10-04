package dev.sk2andy.materialbrowser.data

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer

internal class FaviconClient {
    fun fetchFavorite(pageUrl: String): Bitmap? {
        val originUrl = FaviconPageIconRules.originPageUrl(pageUrl) ?: return null
        val page = fetchBytes(originUrl, "text/html", MAX_PAGE_PREFIX_BYTES, allowPrefix = true)
            ?.toString(Charsets.UTF_8)
        var best: Bitmap? = null
        FaviconPageIconRules.candidates(pageUrl, page.orEmpty()).forEach { iconUrl ->
            val icon = fetchBytes(iconUrl, "image/*", MAX_FILE_SIZE_BYTES)?.let(::decode)
                ?: return@forEach
            if (icon.minimumDimension() > (best?.minimumDimension() ?: 0)) {
                best?.recycle()
                best = icon
            } else {
                icon.recycle()
            }
            if (requireNotNull(best).minimumDimension() >= FaviconPageIconRules.PREFERRED_ICON_DIMENSION) {
                return best
            }
        }
        val fallback = fetch(pageUrl)
        if ((fallback?.minimumDimension() ?: 0) > (best?.minimumDimension() ?: 0)) {
            best?.recycle()
            return fallback
        }
        fallback?.recycle()
        return best
    }

    fun fetch(pageUrl: String): Bitmap? = FaviconFetchRules.originIconUrl(pageUrl)
        ?.let { iconUrl -> fetchBytes(iconUrl, "image/*", MAX_FILE_SIZE_BYTES) }
        ?.let(::decode)

    private fun fetchBytes(
        initialUrl: String,
        accept: String,
        maxBytes: Int,
        allowPrefix: Boolean = false,
    ): ByteArray? {
        var iconUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = runCatching {
                URI(iconUrl).toURL().openConnection() as HttpURLConnection
            }.getOrNull() ?: return null
            val redirect = try {
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", accept)
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
                    if (!allowPrefix && declaredLength !in -1..maxBytes.toLong()) return null
                    val encoded = connection.inputStream.use { input ->
                        input.readNBytes(if (allowPrefix) maxBytes else maxBytes + 1)
                    }
                    if (encoded.isEmpty() || encoded.size > maxBytes) return null
                    return encoded
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
        const val USER_AGENT = "CandyBrowser-Favicon/1.0"
        const val CONNECT_TIMEOUT_MILLIS = 4_000
        const val READ_TIMEOUT_MILLIS = 6_000
        const val MAX_FILE_SIZE_BYTES = 2 * 1_024 * 1_024
        const val MAX_PAGE_PREFIX_BYTES = 256 * 1_024
        const val MAX_REDIRECTS = 3
        val REDIRECT_RESPONSE_CODES = setOf(301, 302, 303, 307, 308)
    }
}

internal fun Bitmap.minimumDimension(): Int = minOf(width, height)
