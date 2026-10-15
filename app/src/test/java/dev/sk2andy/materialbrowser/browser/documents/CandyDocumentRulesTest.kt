package dev.sk2andy.materialbrowser.browser.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CandyDocumentRulesTest {
    @Test
    fun `only canonical opaque document ids are accepted`() {
        val id = "b1402bc1-87d0-450b-b6eb-1430d6e278d9"
        assertEquals(CandyDocumentId.Article(id), CandyDocumentRules.parse("reader:$id"))
        assertEquals(CandyDocumentId.Download(-43L), CandyDocumentRules.parse("download:-43"))
        listOf("reader:../reader_library_v1.json", "download:01", "download:-0", "download:../../1", "file:/data/app", "reader:1-1-1-1-1").forEach {
            assertNull(it, CandyDocumentRules.parse(it))
        }
    }

    @Test
    fun `export names have one safe pdf suffix`() {
        assertEquals("Candy article.pdf", CandyDocumentRules.pdfName("/\\\n"))
        assertEquals("Report.pdf", CandyDocumentRules.pdfName("Report.pdf"))
        assertEquals("News title.pdf", CandyDocumentRules.pdfName("News/ title\n"))
    }
}
