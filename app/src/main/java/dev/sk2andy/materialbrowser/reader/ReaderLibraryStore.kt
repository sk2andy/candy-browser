package dev.sk2andy.materialbrowser.reader

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class ReaderLibraryStore internal constructor(private val file: AtomicFile) {
    constructor(context: Context) : this(AtomicFile(File(context.filesDir, FILE_NAME)))

    fun load(isPrivate: Boolean): ReaderLibraryState = synchronized(lock) {
        if (isPrivate) ReaderLibraryState() else read()
    }

    fun updateSettings(settings: ReaderSettings, isPrivate: Boolean) = synchronized(lock) {
        mutate(isPrivate) { ReaderLibraryRules.updateSettings(it, settings, isPrivate = false) }
    }

    fun updateProgress(sourceUrl: String, progress: Float, isPrivate: Boolean) = synchronized(lock) {
        mutate(isPrivate) {
            ReaderLibraryRules.updateProgress(it, sourceUrl, progress, isPrivate = false)
        }
    }

    fun saveSnapshot(
        document: ReaderDocument,
        progress: Float,
        isPrivate: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        sourceProfileId: String? = null,
        preserveExistingProvenance: Boolean = false,
    ): ReaderSnapshot? = synchronized(lock) {
        if (isPrivate) return@synchronized null
        val before = read()
        val existing = before.snapshots.firstOrNull { preserveExistingProvenance && it.document == document }
        if (existing?.isPrivate == true) return@synchronized null
        val snapshot = ReaderSnapshot(
            id = UUID.randomUUID().toString(),
            document = document,
            progress = progress.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
            savedAtMillis = nowMillis,
            sourceProfileId = if (existing != null) existing.sourceProfileId else sourceProfileId?.takeIf {
                it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl)
            },
        )
        val after = ReaderLibraryRules.saveSnapshot(before, snapshot, isPrivate = false)
        if (after != before) write(after)
        snapshot
    }

    fun deleteSnapshot(snapshotId: String, isPrivate: Boolean): Boolean = synchronized(lock) {
        if (isPrivate) return@synchronized false
        val before = read()
        val after = ReaderLibraryRules.deleteSnapshot(before, snapshotId, isPrivate = false)
        if (before == after) return@synchronized false
        write(after)
        true
    }

    fun clear() = synchronized(lock) {
        file.delete()
    }

    private fun mutate(isPrivate: Boolean, transform: (ReaderLibraryState) -> ReaderLibraryState) {
        if (isPrivate) return
        val before = read()
        val after = transform(before)
        if (after != before) write(after)
    }

    private fun read(): ReaderLibraryState {
        if (!file.baseFile.isFile || file.baseFile.length() > MAX_JSON_BYTES) {
            return ReaderLibraryState()
        }
        val bytes = runCatching {
            file.openRead().use { it.readNBytes(MAX_JSON_BYTES + 1) }
        }.getOrNull() ?: return ReaderLibraryState()
        if (bytes.size > MAX_JSON_BYTES) return ReaderLibraryState()
        return ReaderLibraryCodec.decode(bytes.toString(StandardCharsets.UTF_8))
    }

    private fun write(state: ReaderLibraryState) {
        val bounded = ReaderLibraryCodec.bounded(state)
        val bytes = encodedBytes(bounded)
        if (bytes.size > MAX_JSON_BYTES) return
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    private fun encodedBytes(state: ReaderLibraryState): ByteArray =
        ReaderLibraryCodec.encode(state).toByteArray(StandardCharsets.UTF_8)

    private companion object {
        val lock = Any()
        const val FILE_NAME = "reader_library_v1.json"
        const val MAX_JSON_BYTES = ReaderLibraryCodec.MAX_JSON_BYTES
    }
}
