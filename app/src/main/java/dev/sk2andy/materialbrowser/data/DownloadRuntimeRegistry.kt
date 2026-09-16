package dev.sk2andy.materialbrowser.data

import java.util.concurrent.atomic.AtomicInteger

/** Process-only progress for Gecko rows while MediaStore keeps them hidden as pending. */
internal object DownloadRuntimeRegistry {
    private val nextId = AtomicInteger(1)
    private val entries = linkedMapOf<Int, DownloadEntry>()
    private data class Controls(
        val cancel: () -> Unit,
        val pause: (() -> Boolean)?,
        val resume: (() -> Boolean)?,
    )
    private val controls = linkedMapOf<Int, Controls>()

    fun nextTransferId(): Int = nextId.getAndUpdate { value ->
        if (value == Int.MAX_VALUE) 1 else value + 1
    }

    @Synchronized
    fun started(
        id: Int,
        name: String,
        source: String,
        mime: String,
        total: Long,
        startedAt: Long,
        mediaStoreId: Long? = null,
        cancel: (() -> Unit)? = null,
        pause: (() -> Boolean)? = null,
        resume: (() -> Boolean)? = null,
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
            supportsPause = pause != null && resume != null,
            supportsCancel = cancel != null,
        )
        if (cancel != null) controls[id] = Controls(cancel, pause, resume)
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
        controls.remove(id)
    }

    @Synchronized
    fun failed(id: Int, cancelled: Boolean, updatedAt: Long) {
        val current = entries[id] ?: return
        entries[id] = current.copy(
            status = if (cancelled) DownloadStatus.Cancelled else DownloadStatus.Failed,
            lastModified = updatedAt,
        )
        controls.remove(id)
    }

    @Synchronized
    fun paused(id: Int, paused: Boolean, updatedAt: Long) {
        val current = entries[id]?.takeIf { it.status.isActive } ?: return
        entries[id] = current.copy(
            status = if (paused) DownloadStatus.Paused else DownloadStatus.Running,
            lastModified = updatedAt,
        )
    }

    fun cancel(entryId: Long): Boolean {
        val control = synchronized(this) {
            entries.entries.firstOrNull { (_, entry) -> entry.id == entryId && entry.status.isActive }
                ?.key?.let(controls::get)
        } ?: return false
        control.cancel()
        return true
    }

    fun cancelTransfer(transferId: Int): Boolean {
        val control = synchronized(this) {
            entries[transferId]
                ?.takeIf { entry -> entry.status.isActive }
                ?.let { controls[transferId] }
        } ?: return false
        control.cancel()
        return true
    }

    fun togglePause(entryId: Long): Boolean {
        val action = synchronized(this) {
            val (id, entry) = entries.entries.firstOrNull { (_, entry) ->
                entry.id == entryId && entry.status.isActive
            } ?: return false
            val control = controls[id] ?: return false
            if (entry.status == DownloadStatus.Paused) control.resume else control.pause
        } ?: return false
        return action()
    }

    fun togglePauseTransfer(transferId: Int): Boolean {
        val action = synchronized(this) {
            val entry = entries[transferId]?.takeIf { it.status.isActive } ?: return false
            val control = controls[transferId] ?: return false
            if (entry.status == DownloadStatus.Paused) control.resume else control.pause
        } ?: return false
        return action()
    }

    @Synchronized
    fun entryForTransfer(transferId: Int): DownloadEntry? = entries[transferId]

    @Synchronized
    fun activeTransferIds(): Set<Int> = entries.asSequence()
        .filter { (_, entry) -> entry.status.isActive }
        .map { (id, _) -> id }
        .toSet()

    @Synchronized
    fun snapshot(): List<DownloadEntry> = entries.values.toList()

    @Synchronized
    fun clear(ids: Collection<Long>) {
        entries.entries.filter { (_, entry) -> entry.id in ids }.forEach { (id, _) -> controls.remove(id) }
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
