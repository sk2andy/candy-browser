package dev.sk2andy.materialbrowser.data

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class BrowserDownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String,
    val userAgent: String? = null,
    val cookies: String? = null,
)

object BrowserDownloadRequestFactory {
    fun create(
        url: String,
        contentDisposition: String? = null,
        mimeType: String? = null,
        userAgent: String? = null,
        cookies: String? = null,
    ): BrowserDownloadRequest? {
        if (!SafeDownloadValues.isHttpUrl(url)) return null
        val safeMimeType = SafeDownloadValues.mimeType(mimeType)
        return BrowserDownloadRequest(
            url = url,
            fileName = SafeDownloadValues.fileName(url, contentDisposition, safeMimeType),
            mimeType = safeMimeType,
            userAgent = SafeDownloadValues.header(userAgent),
            cookies = SafeDownloadValues.header(cookies, MAX_COOKIE_LENGTH),
        )
    }

    private const val MAX_COOKIE_LENGTH = 16_384
}

internal object SafeDownloadValues {
    private val invalidFileNameCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]")
    private val mimeTypePattern = Regex("^[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+*-]+$")
    private const val MAX_FILE_NAME_LENGTH = 120

    fun isHttpUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        (uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null
    }.getOrDefault(false)

    fun mimeType(value: String?): String {
        val candidate = value
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return candidate.takeIf(mimeTypePattern::matches) ?: "application/octet-stream"
    }

    fun header(value: String?, maxLength: Int = 4_096): String? = value
        ?.takeIf { it.isNotBlank() && it.length <= maxLength && '\r' !in it && '\n' !in it }

    fun fileName(url: String, contentDisposition: String?, mimeType: String): String {
        val candidate = contentDispositionFileName(contentDisposition)
            ?: urlFileName(url)
            ?: "download"
        val sanitized = candidate
            .replace(invalidFileNameCharacters, "_")
            .trim()
            .trim('.')
            .ifEmpty { "download" }
        val extension = extensionForMimeType(mimeType)
        val withExtension = if (extension != null && !hasExtension(sanitized)) {
            "$sanitized.$extension"
        } else {
            sanitized
        }
        if (withExtension.length <= MAX_FILE_NAME_LENGTH) return withExtension
        val suffix = withExtension.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() && it.length <= 10 }
            ?.let { ".$it" }
            .orEmpty()
        return withExtension.take(MAX_FILE_NAME_LENGTH - suffix.length).trimEnd() + suffix
    }

    private fun contentDispositionFileName(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val encoded = Regex("filename\\*\\s*=\\s*UTF-8'[^']*'([^;]+)", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
        if (!encoded.isNullOrBlank()) {
            return runCatching {
                URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8.name())
            }.getOrNull()
        }
        return Regex("filename\\s*=\\s*(?:\"([^\"]+)\"|([^;]+))", RegexOption.IGNORE_CASE)
            .find(value)
            ?.let { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }
            ?.trim()
    }

    private fun urlFileName(value: String): String? = runCatching {
        URI(value).rawPath
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?.let { URLDecoder.decode(it.replace("+", "%2B"), StandardCharsets.UTF_8.name()) }
    }.getOrNull()

    private fun hasExtension(value: String): Boolean {
        val extension = value.substringAfterLast('.', missingDelimiterValue = "")
        return extension.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9+_-]{0,9}$"))
    }

    private fun extensionForMimeType(mimeType: String): String? = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/avif" -> "avif"
        "image/svg+xml" -> "svg"
        "application/pdf" -> "pdf"
        "text/plain" -> "txt"
        "text/html" -> "html"
        "application/json" -> "json"
        else -> null
    }
}
