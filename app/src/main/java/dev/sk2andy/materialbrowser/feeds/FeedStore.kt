package dev.sk2andy.materialbrowser.feeds

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.UUID
import org.json.JSONObject

/** Repository mutex serializes this store. Offline generations are immutable after publication. */
internal class FeedStore(context: Context, profileId: String) {
    private val profileKey = FeedRules.identity(profileId)
    private val file = AtomicFile(File(context.filesDir, "feeds/$profileKey.json"))
    private val documents = File(context.filesDir, "feeds/documents/$profileKey")

    fun delete() {
        file.delete()
        val documentsDeleted = !documents.exists() || documents.deleteRecursively()
        if (exists(file) || !documentsDeleted) throw IOException("Could not remove feed data")
    }

    fun read(): FeedState {
        if (!exists(file)) return FeedState(loaded = true)
        val created = mutableListOf<String>()
        var published = false
        try {
            val restored =
                file.openRead().use { input ->
                    if (input.channel.size() > MAX_LEGACY_BYTES)
                        throw IOException("Feed storage too large")
                    FeedJsonStream.read(input.reader(Charsets.UTF_8)) { document ->
                        saveDocument(document).also(created::add)
                    }
                }
            val checked =
                restored.copy(
                    articles =
                        restored.articles.map { article ->
                            val reference = article.offlineDocumentId
                            if (reference != null && !documentExists(reference)) {
                                article.copy(
                                    offlineDocumentId = null,
                                    offlineStatus = FeedOfflineStatus.Failed,
                                )
                            } else article
                        }
                )
            val committed =
                if (created.isNotEmpty() || checked != restored) write(checked, checked)
                else checked
            published = true
            pruneDocuments(committed)
            return committed
        } catch (error: Throwable) {
            if (!published) created.forEach(::deleteDocument)
            throw error
        }
    }

    /** A metadata commit only references documents whose atomic writes already succeeded. */
    fun write(state: FeedState, previous: FeedState? = null): FeedState {
        val projectedBytes = offlineBytes(state)
        val oldBytes = previous?.let(::offlineBytes) ?: referencedBytes(state)
        if (
            projectedBytes > state.settings.offlineBudgetMb * 1024L * 1024L &&
                projectedBytes > oldBytes
        ) {
            throw IOException("Offline storage full")
        }
        val created = mutableListOf<String>()
        var committed = false
        try {
            val normalized =
                state.copy(
                    articles =
                        state.articles.map { article ->
                            val document = article.offlineDocument
                            if (document != null) {
                                require(FeedRules.identity(document.sourceUrl) == article.id) {
                                    "Article identity mismatch"
                                }
                                val reference = saveDocument(document).also(created::add)
                                article.copy(
                                    offlineDocument = null,
                                    offlineDocumentId = reference,
                                    offlineStatus = FeedOfflineStatus.Ready,
                                )
                            } else {
                                article.offlineDocumentId?.let { reference ->
                                    if (!documentExists(reference))
                                        throw IOException("Offline document missing")
                                }
                                article
                            }
                        }
                )
            writeMetadata(normalized)
            committed = true
            pruneDocuments(normalized)
            return normalized
        } catch (error: Throwable) {
            if (!committed) created.forEach(::deleteDocument)
            throw error
        }
    }

    fun loadDocument(article: FeedArticle): ReaderDocument? {
        val reference = article.offlineDocumentId ?: return article.offlineDocument
        val atomic = documentFile(reference)
        val bytes =
            atomic.openRead().use { input ->
                if (input.channel.size() > MAX_DOCUMENT_BYTES)
                    throw IOException("Offline document too large")
                input.readNBytes(MAX_DOCUMENT_BYTES + 1)
            }
        if (bytes.size > MAX_DOCUMENT_BYTES) throw IOException("Offline document too large")
        val document =
            FeedCodec.readDocument(JSONObject(bytes.toString(Charsets.UTF_8)))
                ?: throw IOException("Invalid offline document")
        if (FeedRules.identity(document.sourceUrl) != article.id)
            throw IOException("Offline article identity mismatch")
        return document
    }

    private fun writeMetadata(state: FeedState) {
        file.baseFile.parentFile?.mkdirs()
        val output = file.startWrite()
        try {
            val writer =
                OutputStreamWriter(
                    BoundedOutputStream(output, FeedCodec.MAX_STORE_BYTES),
                    Charsets.UTF_8,
                )
            FeedCodec.write(state, writer)
            writer.flush()
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    private fun saveDocument(document: ReaderDocument): String {
        val bytes = encodedDocument(document)
        val reference = UUID.randomUUID().toString()
        documents.mkdirs()
        val atomic = documentFile(reference)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
        return reference
    }

    private fun encodedDocument(document: ReaderDocument): ByteArray {
        require(FeedRules.safeUrl(document.sourceUrl) != null) { "Invalid offline document URL" }
        require(document.title.length <= 1_000 && document.siteName.length <= 500) {
            "Offline document metadata too large"
        }
        require(
            document.blocks.size <= 600 &&
                document.blocks.sumOf { it.text.length.toLong() } <= 500_000
        ) {
            "Offline document text too large"
        }
        require(document.blocks.sumOf { it.links.size } <= 500) { "Too many offline links" }
        require(
            document.blocks.all { block ->
                block.text.length <= 12_000 &&
                    block.links.all { it.label.length <= 1_000 && it.url.length <= 2_048 }
            }
        ) {
            "Offline document block too large"
        }
        val bytes = FeedCodec.document(document).toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_DOCUMENT_BYTES) throw IOException("Offline document too large")
        return bytes
    }

    private fun offlineBytes(state: FeedState): Long {
        val references =
            state.articles
                .filter { it.offlineDocument == null }
                .mapNotNull { it.offlineDocumentId }
                .toSet()
        return references.sumOf(::documentLength) +
            state.articles.sumOf {
                it.offlineDocument?.let(::encodedDocument)?.size?.toLong() ?: 0L
            }
    }

    private fun referencedBytes(state: FeedState): Long =
        state.articles.mapNotNull { it.offlineDocumentId }.toSet().sumOf(::documentLength)

    private fun documentLength(reference: String): Long {
        val atomic = documentFile(reference)
        val existing = atomic.baseFile.takeIf(File::exists) ?: File("${atomic.baseFile.path}.bak")
        return existing.length()
    }

    private fun documentExists(reference: String): Boolean =
        FeedCodec.validDocumentId(reference) &&
            exists(documentFile(reference)) &&
            documentLength(reference) in 1..MAX_DOCUMENT_BYTES.toLong()

    private fun documentFile(reference: String): AtomicFile {
        require(FeedCodec.validDocumentId(reference)) { "Invalid offline document reference" }
        return AtomicFile(File(documents, "$reference.json"))
    }

    private fun deleteDocument(reference: String) = documentFile(reference).delete()

    /** Best-effort orphan cleanup; a crash/failure is retried on the next successful read/write. */
    private fun pruneDocuments(state: FeedState) {
        val retained =
            state.articles
                .mapNotNull { it.offlineDocumentId }
                .flatMapTo(mutableSetOf()) { listOf("$it.json", "$it.json.bak") }
        documents.listFiles()?.forEach { candidate ->
            if (candidate.name !in retained && !candidate.delete()) {
                Log.w("CandyFeeds", "Could not remove unreferenced offline generation")
            }
        }
    }

    private fun exists(atomic: AtomicFile): Boolean =
        atomic.baseFile.exists() || File("${atomic.baseFile.path}.bak").exists()

    private class BoundedOutputStream(output: OutputStream, private val limit: Int) :
        FilterOutputStream(output) {
        private var count = 0L

        override fun write(value: Int) {
            checkLimit(1)
            out.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            checkLimit(length)
            out.write(buffer, offset, length)
        }

        private fun checkLimit(length: Int) {
            if (count + length > limit) throw IOException("Feed metadata storage full")
            count += length
        }
    }

    private companion object {
        const val MAX_LEGACY_BYTES = 270L * 1024L * 1024L
        const val MAX_DOCUMENT_BYTES = 4 * 1024 * 1024
    }
}
