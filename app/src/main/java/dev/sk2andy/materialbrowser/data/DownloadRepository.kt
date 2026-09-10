package dev.sk2andy.materialbrowser.data

import android.app.DownloadManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore

/** Reads Candy-owned downloads from both Android download backends without storing browser data. */
internal class DownloadRepository(
    context: Context,
    private val ownerPackageName: String = context.applicationContext.packageName,
) {
    private val applicationContext = context.applicationContext
    private val manager = applicationContext.getSystemService(DownloadManager::class.java)
    private val resolver = applicationContext.contentResolver

    fun snapshot(): List<DownloadEntry> {
        val systemEntries = querySystemDownloads()
        val mediaEntries = queryMediaStoreDownloads().filterNot { mediaEntry ->
            systemEntries.any { systemEntry -> systemEntry.matchesIndexedMedia(mediaEntry) }
        }
        return (DownloadRuntimeRegistry.snapshot() + systemEntries + mediaEntries)
            .distinctBy(DownloadEntry::id)
            .sortedWith(
                compareByDescending(DownloadEntry::lastModified).thenByDescending(DownloadEntry::id),
            )
    }

    fun clear(entries: Collection<DownloadEntry>): Boolean = runCatching {
        val ids = DownloadHistoryRules.clearableTerminalIds(entries.toList())
        val systemIds = ids.filter { id -> id >= 0L }
        var removedAll = systemIds.isEmpty() ||
            manager.remove(*systemIds.toLongArray()) == systemIds.size
        ids.filter { id -> id < 0L && !DownloadRuntimeRegistry.isRuntimeId(id) }.forEach { id ->
            val runtimeEntryExists = DownloadRuntimeRegistry.containsEntryId(id)
            val removed = resolver.delete(mediaStoreUri(id), null, null)
            removedAll = removedAll && (removed > 0 || runtimeEntryExists)
        }
        DownloadRuntimeRegistry.clear(ids)
        removedAll
    }.getOrDefault(false)

    fun contentUri(entry: DownloadEntry): Uri? = when {
        entry.status != DownloadStatus.Successful -> null
        entry.id >= 0L -> manager.getUriForDownloadedFile(entry.id)
        else -> mediaStoreUri(entry.id)
    }

    private fun querySystemDownloads(): List<DownloadEntry> = runCatching {
        manager.query(DownloadManager.Query())?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.systemDownload()?.let(::add)
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun queryMediaStoreDownloads(): List<DownloadEntry> = runCatching {
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            MEDIA_PROJECTION,
            null,
            null,
            "${MediaStore.Downloads.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) cursor.mediaStoreDownload()?.let(::add)
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun Cursor.systemDownload(): DownloadEntry? {
        val id = long(DownloadManager.COLUMN_ID) ?: return null
        val source = string(DownloadManager.COLUMN_URI).orEmpty().take(MAX_SOURCE_CHARS)
        val name = string(DownloadManager.COLUMN_TITLE)
            ?.takeIf(String::isNotBlank)
            ?.take(MAX_NAME_CHARS)
            ?: Uri.parse(source).lastPathSegment?.take(MAX_NAME_CHARS)
            ?: return null
        val status = when (int(DownloadManager.COLUMN_STATUS)) {
            DownloadManager.STATUS_PENDING -> DownloadStatus.Pending
            DownloadManager.STATUS_RUNNING -> DownloadStatus.Running
            DownloadManager.STATUS_PAUSED -> DownloadStatus.Paused
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Successful
            DownloadManager.STATUS_FAILED -> DownloadStatus.Failed
            else -> DownloadStatus.Cancelled
        }
        return DownloadEntry(
            id = id,
            name = name,
            source = source,
            status = status,
            bytes = long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)?.coerceAtLeast(0L) ?: 0L,
            total = long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES) ?: -1L,
            lastModified = long(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)?.coerceAtLeast(0L)
                ?: 0L,
            mime = string(DownloadManager.COLUMN_MEDIA_TYPE).orEmpty().take(MAX_MIME_CHARS),
        )
    }

    private fun Cursor.mediaStoreDownload(): DownloadEntry? {
        val owner = string(MediaStore.Downloads.OWNER_PACKAGE_NAME)
        if (owner != ownerPackageName) return null
        val mediaId = long(MediaStore.Downloads._ID) ?: return null
        val name = string(MediaStore.Downloads.DISPLAY_NAME)
            ?.takeIf(String::isNotBlank)
            ?.take(MAX_NAME_CHARS)
            ?: return null
        val bytes = long(MediaStore.Downloads.SIZE)?.coerceAtLeast(0L) ?: 0L
        val pending = int(MediaStore.Downloads.IS_PENDING) == 1
        val modifiedSeconds = long(MediaStore.Downloads.DATE_MODIFIED)?.coerceAtLeast(0L) ?: 0L
        return DownloadEntry(
            id = DownloadEntryIds.encodeMediaStoreId(mediaId),
            name = name,
            source = "",
            status = if (pending) DownloadStatus.Cancelled else DownloadStatus.Successful,
            bytes = bytes,
            total = if (pending) -1L else bytes,
            lastModified = modifiedSeconds * MILLIS_PER_SECOND,
            mime = string(MediaStore.Downloads.MIME_TYPE).orEmpty().take(MAX_MIME_CHARS),
        )
    }

    private fun DownloadEntry.matchesIndexedMedia(mediaEntry: DownloadEntry): Boolean =
        name == mediaEntry.name &&
            bytes == mediaEntry.bytes &&
            kotlin.math.abs(lastModified - mediaEntry.lastModified) <= INDEX_MATCH_WINDOW_MILLIS

    private fun Cursor.string(column: String): String? = getColumnIndex(column)
        .takeIf { index -> index >= 0 && !isNull(index) }
        ?.let(::getString)

    private fun Cursor.long(column: String): Long? = getColumnIndex(column)
        .takeIf { index -> index >= 0 && !isNull(index) }
        ?.let(::getLong)

    private fun Cursor.int(column: String): Int? = getColumnIndex(column)
        .takeIf { index -> index >= 0 && !isNull(index) }
        ?.let(::getInt)

    private fun mediaStoreUri(encodedId: Long): Uri = ContentUris.withAppendedId(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        DownloadEntryIds.decodeMediaStoreId(encodedId),
    )

    private companion object {
        const val MAX_NAME_CHARS = 256
        const val MAX_SOURCE_CHARS = 2_048
        const val MAX_MIME_CHARS = 128
        const val MILLIS_PER_SECOND = 1_000L
        const val INDEX_MATCH_WINDOW_MILLIS = 5 * 60 * 1_000L

        val MEDIA_PROJECTION = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
            MediaStore.Downloads.IS_PENDING,
            MediaStore.Downloads.OWNER_PACKAGE_NAME,
        )

    }
}
