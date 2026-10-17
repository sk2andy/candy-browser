package dev.sk2andy.materialbrowser.data

/** Filesystem-safe naming shared by Android downloads and WebKit's suggested-filename adapter. */
object DownloadFileNameRules {
    const val ANDROID_PACKAGE_MIME_TYPE = "application/vnd.android.package-archive"
    private val invalidFileNameCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}\\p{Cf}]")
    private const val MAX_FILE_NAME_LENGTH = 120

    fun fileName(candidate: String, mimeType: String): String {
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
        ANDROID_PACKAGE_MIME_TYPE -> "apk"
        else -> null
    }
}
