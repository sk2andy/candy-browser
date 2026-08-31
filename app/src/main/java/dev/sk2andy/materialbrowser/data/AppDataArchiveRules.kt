package dev.sk2andy.materialbrowser.data

import java.io.File
import java.nio.charset.StandardCharsets

internal data class AppDataArchiveManifest(
    val formatVersion: Int = AppDataArchiveRules.FORMAT_VERSION,
    val packageName: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val webViewVersion: String?,
    val sdkInt: Int,
    val exportedAtEpochMillis: Long,
)

internal data class AppDataArchiveEnvironment(
    val packageName: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val webViewVersion: String?,
    val sdkInt: Int,
)

internal enum class AppDataArchiveCompatibility {
    Same,
    AppMismatch,
    WebViewMismatch,
    PlatformMismatch,
}

internal object AppDataArchiveRules {
    const val TRANSFER_STATE_DIRECTORY_NAME = "app_data_transfer"
    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY_NAME = "manifest.json"
    const val DATA_ENTRY_PREFIX = "data/"
    const val MAX_ENTRY_COUNT = 100_000
    const val MAX_FILE_BYTES = 256L * 1024L * 1024L
    const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 64 * 1024
    const val MAX_RELATIVE_PATH_BYTES = 1_024
    const val MAX_PATH_SEGMENTS = 32
    const val MAX_PATH_SEGMENT_BYTES = 255

    private val excludedTopLevelNames = setOf(
        "cache",
        "code_cache",
        "lib",
        "app_textures",
        TRANSFER_STATE_DIRECTORY_NAME,
    )

    fun shouldExportTopLevel(name: String): Boolean =
        name.isNotEmpty() && name !in excludedTopLevelNames

    fun shouldExportRelativePath(path: String): Boolean =
        path !in excludedRelativePaths

    fun persistentRootNames(dataDirectory: File): Set<String> =
        dataDirectory.list()
            ?.asSequence()
            ?.filter(::shouldExportTopLevel)
            ?.toSet()
            .orEmpty()

    fun isEntryLimitExceeded(entryCount: Int): Boolean =
        entryCount < 0 || entryCount > MAX_ENTRY_COUNT

    fun isFileSizeExceeded(size: Long): Boolean = size < 0L || size > MAX_FILE_BYTES

    fun isTotalSizeExceeded(currentSize: Long, addedSize: Long): Boolean =
        currentSize < 0L ||
            addedSize < 0L ||
            currentSize > MAX_TOTAL_BYTES - addedSize

    fun dataRelativePath(entryName: String, isDirectory: Boolean): String? {
        if (entryName.isEmpty() || '\\' in entryName || '\u0000' in entryName) return null
        if (entryName.startsWith('/') || !entryName.startsWith(DATA_ENTRY_PREFIX)) return null
        if (isDirectory != entryName.endsWith('/')) return null

        val withoutDirectorySuffix = if (isDirectory) entryName.dropLast(1) else entryName
        val relativePath = withoutDirectorySuffix.removePrefix(DATA_ENTRY_PREFIX)
        if (relativePath.isEmpty() || relativePath.startsWith('/')) return null
        if (relativePath.length > MAX_RELATIVE_PATH_BYTES ||
            relativePath.toByteArray(StandardCharsets.UTF_8).size > MAX_RELATIVE_PATH_BYTES
        ) {
            return null
        }
        if (relativePath.matches(WINDOWS_ABSOLUTE_PATH_PATTERN)) return null

        val segments = relativePath.split('/')
        if (segments.size > MAX_PATH_SEGMENTS ||
            segments.any { segment ->
                segment.isEmpty() ||
                    segment == "." ||
                    segment == ".." ||
                    segment.any(Char::isISOControl) ||
                    segment.toByteArray(StandardCharsets.UTF_8).size > MAX_PATH_SEGMENT_BYTES
            }
        ) {
            return null
        }
        if (!shouldExportTopLevel(segments.first())) return null
        if (!shouldExportRelativePath(relativePath)) return null
        return relativePath
    }

    fun compatibility(
        current: AppDataArchiveEnvironment,
        archive: AppDataArchiveManifest,
    ): AppDataArchiveCompatibility = when {
        current.packageName != archive.packageName ||
            current.appVersionName != archive.appVersionName ||
            current.appVersionCode != archive.appVersionCode -> AppDataArchiveCompatibility.AppMismatch
        current.webViewVersion != archive.webViewVersion ->
            AppDataArchiveCompatibility.WebViewMismatch
        current.sdkInt != archive.sdkInt -> AppDataArchiveCompatibility.PlatformMismatch
        else -> AppDataArchiveCompatibility.Same
    }

    private val WINDOWS_ABSOLUTE_PATH_PATTERN = Regex("[A-Za-z]:($|/.*)")
    private val excludedRelativePaths = setOf(
        "no_backup/candy_recall.db",
        "no_backup/candy_recall.db-journal",
        "no_backup/candy_recall.db-shm",
        "no_backup/candy_recall.db-wal",
    )
}
