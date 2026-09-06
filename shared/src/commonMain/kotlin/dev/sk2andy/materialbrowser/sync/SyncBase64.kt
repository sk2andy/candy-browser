package dev.sk2andy.materialbrowser.sync

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object SyncBase64 {
    private val pattern = Regex("[A-Za-z0-9_-]+")
    private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun encode(value: ByteArray): String = codec.encode(value)

    @Throws(Exception::class)
    fun decode(value: String, expectedBytes: Int? = null, maxBytes: Int = 524_288): ByteArray {
        require(value.isNotEmpty() && value.length <= ((maxBytes * 4L + 2L) / 3L).toInt())
        require(pattern.matches(value) && !value.contains('='))
        val decoded = runCatching { codec.decode(value) }
            .getOrElse { throw IllegalArgumentException("Invalid base64url") }
        require(decoded.size <= maxBytes)
        if (expectedBytes != null) require(decoded.size == expectedBytes)
        return decoded
    }
}
