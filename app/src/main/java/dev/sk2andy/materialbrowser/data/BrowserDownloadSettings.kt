package dev.sk2andy.materialbrowser.data

data class BrowserDownloadSettings(
    val managerMode: DownloadManagerMode = DownloadManagerMode.BuiltIn,
    val externalManagerId: String? = null,
    val shareSessionDataWithOneDm: Boolean = false,
    val downloadSubdirectory: String? = null,
) {
    fun normalized(): BrowserDownloadSettings {
        val safeManagerId = externalManagerId
            ?.takeIf { it.isNotBlank() && it.length <= MAX_EXTERNAL_MANAGER_ID_LENGTH }
            ?.takeIf { value -> value.none(Char::isISOControl) }
        val safeMode = if (managerMode == DownloadManagerMode.External && safeManagerId == null) {
            DownloadManagerMode.BuiltIn
        } else {
            managerMode
        }
        return copy(
            managerMode = safeMode,
            externalManagerId = safeManagerId,
            downloadSubdirectory = DownloadDirectoryRules.normalizedSubdirectory(
                downloadSubdirectory,
            ),
        )
    }

    companion object {
        private const val MAX_EXTERNAL_MANAGER_ID_LENGTH = 512
    }
}

internal object DownloadDirectoryRules {
    private const val DOWNLOADS_DIRECTORY = "Download"
    private const val MAX_SUBDIRECTORY_LENGTH = 240

    fun normalizedSubdirectory(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.replace('\\', '/')
            ?.trim('/')
            ?.takeIf(String::isNotEmpty)
            ?: return null
        if (normalized.length > MAX_SUBDIRECTORY_LENGTH) return null
        val segments = normalized.split('/').map(String::trim)
        if (
            segments.any { segment ->
                segment.isBlank() ||
                    segment == "." ||
                    segment == ".." ||
                    segment.any(Char::isISOControl)
            }
        ) {
            return null
        }
        return segments.joinToString("/")
    }

    fun isValidSubdirectoryInput(value: String): Boolean =
        value.isBlank() || normalizedSubdirectory(value) != null

    fun mediaStoreRelativePath(subdirectory: String?): String = normalizedSubdirectory(subdirectory)
        ?.let { relativePath -> "$DOWNLOADS_DIRECTORY/$relativePath" }
        ?: DOWNLOADS_DIRECTORY

    fun downloadManagerFilePath(subdirectory: String?, fileName: String): String =
        normalizedSubdirectory(subdirectory)
            ?.let { relativePath -> "$relativePath/$fileName" }
            ?: fileName
}

enum class DownloadManagerMode(val stableId: String) {
    BuiltIn("built_in"),
    AskEveryTime("ask_every_time"),
    External("external");

    companion object {
        fun fromStableId(value: String?): DownloadManagerMode =
            entries.firstOrNull { it.stableId == value } ?: BuiltIn
    }
}
