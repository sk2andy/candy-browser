package dev.sk2andy.materialbrowser.browser.documents

import java.util.UUID

internal sealed interface CandyDocumentId {
    data object Root : CandyDocumentId
    data object Reader : CandyDocumentId
    data object Downloads : CandyDocumentId
    data class Article(val snapshotId: String) : CandyDocumentId
    data class Download(val downloadId: Long) : CandyDocumentId
}

internal object CandyDocumentRules {
    const val ROOT = "candy"
    const val READER = "reader"
    const val DOWNLOADS = "downloads"

    fun parse(id: String): CandyDocumentId? = when (id) {
        ROOT -> CandyDocumentId.Root
        READER -> CandyDocumentId.Reader
        DOWNLOADS -> CandyDocumentId.Downloads
        else -> when {
            id.startsWith("reader:") -> id.removePrefix("reader:").let { value ->
                runCatching { UUID.fromString(value).toString() }.getOrNull()
                    ?.takeIf { it == value }?.let(CandyDocumentId::Article)
            }
            id.startsWith("download:") -> id.removePrefix("download:").let { value ->
                value.toLongOrNull()?.takeIf {
                    it.toString() == value && it > Long.MIN_VALUE + Int.MAX_VALUE.toLong()
                }?.let(CandyDocumentId::Download)
            }
            else -> null
        }
    }

    fun articleId(id: String): String = "reader:$id"

    fun downloadId(id: Long): String = "download:$id"

    fun pdfName(title: String): String = title
        .filterNot { it.isISOControl() || it == '/' || it == '\\' }
        .trim().take(120).ifBlank { "Candy article" }.removeSuffix(".pdf") + ".pdf"
}
