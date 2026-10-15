package dev.sk2andy.materialbrowser

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Binder
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import dev.sk2andy.materialbrowser.browser.documents.CandyDocumentId
import dev.sk2andy.materialbrowser.browser.documents.CandyDocumentRules
import dev.sk2andy.materialbrowser.browser.documents.CandyDocumentsRepository
import dev.sk2andy.materialbrowser.browser.documents.ReaderPdfExporter
import java.io.File
import java.io.FileNotFoundException

/** User-selected, read-only access through Android's system document picker. */
class CandyDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor = asCandy {
        MatrixCursor(projection ?: ROOT_PROJECTION).apply {
            row(mapOf(
                Root.COLUMN_ROOT_ID to CandyDocumentRules.ROOT,
                Root.COLUMN_DOCUMENT_ID to CandyDocumentRules.ROOT,
                Root.COLUMN_TITLE to getContextOrThrow().getString(R.string.app_name),
                Root.COLUMN_SUMMARY to getContextOrThrow().getString(R.string.documents_root_summary),
                Root.COLUMN_MIME_TYPES to "*/*",
                Root.COLUMN_FLAGS to Root.FLAG_LOCAL_ONLY,
                Root.COLUMN_ICON to R.mipmap.ic_launcher,
            ))
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = asCandy {
        MatrixCursor(projection ?: DOCUMENT_PROJECTION).apply { includeDocument(documentId) }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = asCandy {
        MatrixCursor(projection ?: DOCUMENT_PROJECTION).apply {
            when (CandyDocumentRules.parse(parentDocumentId)) {
                CandyDocumentId.Root -> {
                    includeDocument(CandyDocumentRules.READER)
                    includeDocument(CandyDocumentRules.DOWNLOADS)
                }
                CandyDocumentId.Reader -> repository().articles().forEach { article ->
                    row(articleRow(article.id, article.document.title, article.savedAtMillis))
                }
                CandyDocumentId.Downloads -> repository().downloads().forEach { download ->
                    row(downloadRow(download.id, download.name, download.mime, download.bytes, download.lastModified))
                }
                else -> throw FileNotFoundException("Unknown Candy directory")
            }
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = asCandy {
        val parent = CandyDocumentRules.parse(parentDocumentId)
        val child = CandyDocumentRules.parse(documentId)
        when (parent) {
            CandyDocumentId.Root -> child != null && child != CandyDocumentId.Root && exists(documentId)
            CandyDocumentId.Reader -> child is CandyDocumentId.Article && exists(documentId)
            CandyDocumentId.Downloads -> child is CandyDocumentId.Download && exists(documentId)
            else -> false
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor = asCandy {
        if (mode != "r") throw FileNotFoundException("Candy documents are read-only")
        signal?.throwIfCanceled()
        val repository = repository()
        when (val id = CandyDocumentRules.parse(documentId)) {
            is CandyDocumentId.Article -> {
                val article = repository.articles().firstOrNull { it.id == id.snapshotId }
                    ?: throw FileNotFoundException("Article unavailable")
                val file = File.createTempFile("candy-reader-", ".pdf", getContextOrThrow().cacheDir)
                try {
                    file.outputStream().use { ReaderPdfExporter.write(article.document, it, signal) }
                    signal?.throwIfCanceled()
                    if (article !in repository.articles()) throw FileNotFoundException("Article unavailable")
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } finally {
                    // Open descriptors remain readable; no reusable plaintext export stays in cache.
                    file.delete()
                }
            }
            is CandyDocumentId.Download -> {
                val download = repository.downloads().firstOrNull { it.id == id.downloadId }
                    ?: throw FileNotFoundException("Download unavailable")
                val uri = repository.downloadUri(download) ?: throw FileNotFoundException("Download unavailable")
                getContextOrThrow().contentResolver.openFileDescriptor(uri, "r", signal)
                    ?: throw FileNotFoundException("Download unavailable")
            }
            else -> throw FileNotFoundException("Unknown Candy document")
        }
    }

    private fun MatrixCursor.includeDocument(documentId: String) {
        val values = when (val id = CandyDocumentRules.parse(documentId)) {
            CandyDocumentId.Root -> directoryRow(documentId, getContextOrThrow().getString(R.string.app_name))
            CandyDocumentId.Reader -> directoryRow(documentId, getContextOrThrow().getString(R.string.documents_reader_folder))
            CandyDocumentId.Downloads -> directoryRow(documentId, getContextOrThrow().getString(R.string.documents_downloads_folder))
            is CandyDocumentId.Article -> repository().articles().firstOrNull { it.id == id.snapshotId }?.let {
                articleRow(it.id, it.document.title, it.savedAtMillis)
            }
            is CandyDocumentId.Download -> repository().downloads().firstOrNull { it.id == id.downloadId }?.let {
                downloadRow(it.id, it.name, it.mime, it.bytes, it.lastModified)
            }
            else -> null
        } ?: throw FileNotFoundException("Candy document unavailable")
        row(values)
    }

    private fun exists(documentId: String): Boolean = when (val id = CandyDocumentRules.parse(documentId)) {
        CandyDocumentId.Reader, CandyDocumentId.Downloads -> true
        is CandyDocumentId.Article -> repository().articles().any { it.id == id.snapshotId }
        is CandyDocumentId.Download -> repository().downloads().any { it.id == id.downloadId }
        else -> false
    }

    private fun directoryRow(id: String, title: String): Map<String, Any?> = mapOf(
        Document.COLUMN_DOCUMENT_ID to id,
        Document.COLUMN_DISPLAY_NAME to title,
        Document.COLUMN_MIME_TYPE to Document.MIME_TYPE_DIR,
        Document.COLUMN_FLAGS to 0,
    )

    private fun articleRow(id: String, title: String, modified: Long): Map<String, Any?> = mapOf(
        Document.COLUMN_DOCUMENT_ID to CandyDocumentRules.articleId(id),
        Document.COLUMN_DISPLAY_NAME to CandyDocumentRules.pdfName(title),
        Document.COLUMN_MIME_TYPE to "application/pdf",
        Document.COLUMN_LAST_MODIFIED to modified,
        Document.COLUMN_FLAGS to 0,
    )

    private fun downloadRow(id: Long, title: String, mime: String, bytes: Long, modified: Long): Map<String, Any?> = mapOf(
        Document.COLUMN_DOCUMENT_ID to CandyDocumentRules.downloadId(id),
        Document.COLUMN_DISPLAY_NAME to title.filterNot(Char::isISOControl),
        Document.COLUMN_MIME_TYPE to mime.ifBlank { "application/octet-stream" },
        Document.COLUMN_SIZE to bytes,
        Document.COLUMN_LAST_MODIFIED to modified,
        Document.COLUMN_FLAGS to 0,
    )

    private fun MatrixCursor.row(values: Map<String, Any?>) {
        addRow(columnNames.map(values::get).toTypedArray())
    }

    private fun repository(): CandyDocumentsRepository = CandyDocumentsRepository(getContextOrThrow())

    private fun getContextOrThrow() = requireNotNull(context).applicationContext

    private inline fun <T> asCandy(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        return try { block() } finally { Binder.restoreCallingIdentity(token) }
    }

    private companion object {
        val ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY,
            Root.COLUMN_MIME_TYPES, Root.COLUMN_FLAGS, Root.COLUMN_ICON,
        )
        val DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS,
        )
    }
}
