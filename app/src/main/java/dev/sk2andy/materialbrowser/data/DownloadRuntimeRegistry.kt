package dev.sk2andy.materialbrowser.data

/** Process-only progress for Gecko rows while MediaStore keeps them hidden as pending. */
internal object DownloadRuntimeRegistry {
    private val entries = linkedMapOf<Int, DownloadEntry>()

    @Synchronized
    fun started(
        id: Int,
        name: String,
        source: String,
        mime: String,
        total: Long,
        startedAt: Long,
        mediaStoreId: Long? = null,
    ) {
        entries[id] = DownloadEntry(
            id = mediaStoreId?.let(DownloadEntryIds::encodeMediaStoreId) ?: encodeRuntimeId(id),
            name = name,
            source = source,
            status = DownloadStatus.Running,
            bytes = 0L,
            total = total,
            lastModified = startedAt,
            mime = mime,
        )
    }

    @Synchronized
    fun progress(id: Int, bytes: Long, total: Long, updatedAt: Long) {
        val current = entries[id] ?: return
        entries[id] = current.copy(
            bytes = bytes.coerceAtLeast(0L),
            total = total,
            lastModified = updatedAt,
        )
    }

    @Synchronized
    fun completed(id: Int) {
        entries.remove(id)
    }

    @Synchronized
    fun failed(id: Int, cancelled: Boolean, updatedAt: Long) {
        val current = entries[id] ?: return
        entries[id] = current.copy(
            status = if (cancelled) DownloadStatus.Cancelled else DownloadStatus.Failed,
            lastModified = updatedAt,
        )
    }

    @Synchronized
    fun snapshot(): List<DownloadEntry> = entries.values.toList()

    @Synchronized
    fun clear(ids: Collection<Long>) {
        entries.entries.removeAll { (_, entry) -> entry.id in ids }
    }

    @Synchronized
    fun containsEntryId(id: Long): Boolean = entries.values.any { entry -> entry.id == id }

    fun isRuntimeId(id: Long): Boolean = id <= RUNTIME_ID_MAX

    private fun encodeRuntimeId(id: Int): Long = Long.MIN_VALUE + id.toLong()
    private const val RUNTIME_ID_MAX = Long.MIN_VALUE + Int.MAX_VALUE.toLong()
}

internal object DownloadEntryIds {
    fun encodeMediaStoreId(id: Long): Long = -id - 1L

    fun decodeMediaStoreId(encodedId: Long): Long = -encodedId - 1L
}
