package dev.sk2andy.materialbrowser.browser.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAnnotationMarkdownRulesTest {
    @Test
    fun `notes render without release heading and keep structured blocks`() {
        val blocks = WebAnnotationMarkdownRules.parse(
            """
                A **saved** thought.

                ## Next steps
                - Read *again*
                2. Compare results

                > Remember this

                ```kotlin
                val label = "[literal](https://example.com)"
                ```
            """.trimIndent(),
        )

        assertEquals("A saved thought.", (blocks[0] as WebAnnotationMarkdownBlock.Paragraph).content.text)
        assertEquals(2, (blocks[1] as WebAnnotationMarkdownBlock.Heading).level)
        assertEquals("•", (blocks[2] as WebAnnotationMarkdownBlock.ListItem).marker)
        assertEquals("2.", (blocks[3] as WebAnnotationMarkdownBlock.ListItem).marker)
        assertEquals("Remember this", (blocks[4] as WebAnnotationMarkdownBlock.Quote).content.text)
        assertEquals(
            "val label = \"[literal](https://example.com)\"",
            (blocks[5] as WebAnnotationMarkdownBlock.Code).content,
        )
    }

    @Test
    fun `inline formatting retains valid clickable HTTP and HTTPS destinations`() {
        val inline = WebAnnotationMarkdownRules.parseInline(
            "**Bold** *italic* `code` [**Docs**](https://example.com/wiki/Candy_(browser)) [Local](http://localhost:8080/a#note)",
        )
        assertEquals("Bold italic code Docs Local", inline.text)
        assertEquals(
            listOf("https://example.com/wiki/Candy_(browser)", "http://localhost:8080/a#note"),
            inline.decorations.filter { it.style == WebAnnotationMarkdownStyle.Link }.map { it.target },
        )
        assertTrue(inline.decorations.any {
            it.style == WebAnnotationMarkdownStyle.Bold && inline.text.substring(it.start, it.end) == "Docs"
        })
        assertEquals(
            listOf("Bold", "italic", "code", "Docs", "Docs", "Local"),
            inline.decorations.map { inline.text.substring(it.start, it.end) },
        )
    }

    @Test
    fun `unsafe malformed and relative destinations never become links`() {
        for (destination in listOf(
            "javascript:alert(1)",
            "data:text/html,hi",
            "file:///etc/passwd",
            "https://user:secret@example.com/",
            "https://example.com/has space",
            "https:///missing-host",
            "/relative",
            "#section",
        )) {
            val markdown = "[Read]($destination)"
            val inline = WebAnnotationMarkdownRules.parseInline(markdown)
            assertEquals(markdown, inline.text)
            assertFalse(inline.decorations.any { it.style == WebAnnotationMarkdownStyle.Link })
        }
    }

    @Test
    fun `unfinished markup and HTML remain readable without effects`() {
        val note = "```kotlin\nUnclosed **bold\n<script>alert(1)</script>\n![Remote](https://example.com/image.png)"
        assertEquals(note, WebAnnotationMarkdownRules.plainText(note))
        assertFalse(WebAnnotationMarkdownRules.parseInline("`[Link](https://example.com)`").decorations.any {
            it.style == WebAnnotationMarkdownStyle.Link
        })
        assertEquals("**literal**", WebAnnotationMarkdownRules.parseInline("\\*\\*literal\\*\\*").text)
    }

    @Test
    fun `preview strips markdown and note parsing stays bounded`() {
        assertEquals(
            "Title\n• Bold\nDocs\ncode",
            WebAnnotationMarkdownRules.plainText("# Title\n- **Bold**\n\n[Docs](https://example.com)\n\n```\ncode\n```"),
        )
        assertEquals("x".repeat(4_000), WebAnnotationMarkdownRules.plainText("x".repeat(8_000)))
        assertTrue(WebAnnotationMarkdownRules.parse(" \n\t").isEmpty())
    }

    @Test
    fun `nested and combined emphasis consume shared closing runs`() {
        for (markdown in listOf("**bold *italic***", "__bold _italic___")) {
            val inline = WebAnnotationMarkdownRules.parseInline(markdown)
            assertEquals("bold italic", inline.text)
            assertEquals(
                listOf("italic"),
                inline.decorations.filter { it.style == WebAnnotationMarkdownStyle.Italic }
                    .map { inline.text.substring(it.start, it.end) },
            )
            assertEquals(
                listOf("bold italic"),
                inline.decorations.filter { it.style == WebAnnotationMarkdownStyle.Bold }
                    .map { inline.text.substring(it.start, it.end) },
            )
        }
        for (markdown in listOf("***both***", "___both___")) {
            val inline = WebAnnotationMarkdownRules.parseInline(markdown)
            assertEquals("both", inline.text)
            assertEquals(setOf(WebAnnotationMarkdownStyle.Bold, WebAnnotationMarkdownStyle.Italic), inline.decorations.map { it.style }.toSet())
        }
        assertEquals("italic bold", WebAnnotationMarkdownRules.parseInline("*italic **bold***").text)
    }

    @Test
    fun `escaped emphasis markers never close active formatting`() {
        val inline = WebAnnotationMarkdownRules.parseInline("**escaped \\** still bold**")
        assertEquals("escaped ** still bold", inline.text)
        assertEquals(listOf(WebAnnotationMarkdownStyle.Bold), inline.decorations.map { it.style })
        assertEquals("italic * remains", WebAnnotationMarkdownRules.parseInline("*italic \\* remains*").text)
    }
}
