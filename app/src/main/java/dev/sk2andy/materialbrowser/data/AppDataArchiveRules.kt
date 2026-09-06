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
    val websiteState: AppDataArchiveWebsiteState = AppDataArchiveWebsiteState.CandyOwnedOnly,
)

/** Archive contract for browser-engine bytes; Candy-owned records stay portable across engines. */
internal enum class AppDataArchiveWebsiteState {
    CandyOwnedOnly,
    LegacyWebsiteStateExcluded,
}

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
    BrowserEngineMismatch,
    PlatformMismatch,
}

internal object AppDataArchiveRules {
    const val TRANSFER_STATE_DIRECTORY_NAME = "app_data_transfer"
    const val FORMAT_VERSION = 2
    const val LEGACY_FORMAT_VERSION = 1
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
        isAllowedArchiveRelativePath(path) && !isEngineSpecificWebsiteState(path)

    fun isAllowedArchiveRelativePath(path: String): Boolean =
        path != RECALL_DATABASE_RELATIVE_PATH &&
            !path.startsWith("$RECALL_DATABASE_RELATIVE_PATH-")

    /**
     * Gecko and Chromium session/profile bytes are implementation data, not Candy archive data.
     * Keep this exact and narrow: other Candy-owned files under these roots remain portable.
     */
    fun isEngineSpecificWebsiteState(path: String): Boolean =
        path == "no_backup/tab_webview_states" ||
            path.startsWith("no_backup/tab_webview_states/") ||
            path == "no_backup/gecko_session_states" ||
            path.startsWith("no_backup/gecko_session_states/") ||
            path == "files/mozilla" ||
            path.startsWith("files/mozilla/") ||
            path == "app_webview" ||
            path.startsWith("app_webview/") ||
            path.startsWith("app_webview_")

    fun websiteStateFor(
        formatVersion: Int,
        entries: List<AppDataArchiveEntry>,
    ): AppDataArchiveWebsiteState = when {
        formatVersion == LEGACY_FORMAT_VERSION && entries.any { entry ->
            isEngineSpecificWebsiteState(entry.relativePath)
        } -> AppDataArchiveWebsiteState.LegacyWebsiteStateExcluded
        else -> AppDataArchiveWebsiteState.CandyOwnedOnly
    }

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
        if (!isAllowedArchiveRelativePath(relativePath)) return null
        return relativePath
    }

    fun compatibility(
        current: AppDataArchiveEnvironment,
        archive: AppDataArchiveManifest,
    ): AppDataArchiveCompatibility = when {
        current.packageName != archive.packageName ||
            current.appVersionName != archive.appVersionName ||
            current.appVersionCode != archive.appVersionCode -> AppDataArchiveCompatibility.AppMismatch
        archive.websiteState != AppDataArchiveWebsiteState.CandyOwnedOnly &&
            current.webViewVersion != archive.webViewVersion ->
            AppDataArchiveCompatibility.BrowserEngineMismatch
        current.sdkInt != archive.sdkInt -> AppDataArchiveCompatibility.PlatformMismatch
        else -> AppDataArchiveCompatibility.Same
    }

    private val WINDOWS_ABSOLUTE_PATH_PATTERN = Regex("[A-Za-z]:($|/.*)")
    private const val RECALL_DATABASE_RELATIVE_PATH = "no_backup/candy_recall.db"
}
