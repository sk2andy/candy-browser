package dev.sk2andy.materialbrowser.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderExtractionContractTest {
    @Test
    fun `sanitization keeps semantic blocks and only safe links`() {
        val result = ReaderExtractionContract.sanitize(
            ReaderExtractionPayload(
                title = "  Example\u0000 story  ",
                sourceUrl = "https://news.example/story",
                siteName = "Example News",
                blocks = listOf(
                    ReaderExtractionBlock("heading", "A meaningful heading", 2),
                    ReaderExtractionBlock(
                        "paragraph",
                        "A long readable paragraph with enough useful article text to satisfy the extraction contract and remain pleasant to read.",
                        links = listOf(
                            ReaderExtractionLink("Background", "https://docs.example/context"),
                            ReaderExtractionLink("Attack", "javascript:alert(1)"),
                            ReaderExtractionLink("Credentials", "https://user:secret@docs.example/"),
                        ),
                    ),
                ),
            ),
        ) as ReaderExtractionResult.Success

        assertEquals("Example story", result.document.title)
        assertEquals(ReaderBlockKind.Heading, result.document.blocks.first().kind)
        assertEquals(2, result.document.blocks.first().level)
        assertEquals(
            listOf(ReaderLink("Background", "https://docs.example/context")),
            result.document.blocks.last().links,
        )
    }

    @Test
    fun `non-http source and thin content are rejected`() {
        val unsafe = ReaderExtractionContract.sanitize(
            ReaderExtractionPayload("Title", "file:///private/page", null, emptyList()),
        )
        val thin = ReaderExtractionContract.sanitize(
            ReaderExtractionPayload(
                "Title",
                "https://example.com",
                null,
                listOf(ReaderExtractionBlock("paragraph", "Too short")),
            ),
        )

        assertEquals(
            ReaderExtractionResult.Failure(ReaderExtractionFailure.UnsupportedPage),
            unsafe,
        )
        assertEquals(
            ReaderExtractionResult.Failure(ReaderExtractionFailure.EmptyArticle),
            thin,
        )
    }

    @Test
    fun `extraction script clones page and returns plain json without html execution`() {
        val script = ReaderExtractionScript.javascript

        assertTrue(script.contains("cloneNode(true)"))
        assertTrue(script.contains("script,style,noscript"))
        assertTrue(script.contains("JSON.stringify"))
        assertTrue(script.contains("totalChars >= 500000"))
        assertTrue(script.contains("500 - totalLinks"))
        assertTrue(script.contains("replace(/[\\u0000-\\u001f\\u007f]+/g"))
        assertTrue(script.contains("replace(/\\s+/g"))
        assertFalse(script.contains("\\\\u0000"))
        assertFalse(script.contains("\\\\s+"))
        assertFalse(script.contains("innerHTML"))
        assertFalse(script.contains("document.write"))
    }
}
