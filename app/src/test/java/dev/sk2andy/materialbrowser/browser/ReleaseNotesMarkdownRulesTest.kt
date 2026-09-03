package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesMarkdownRulesTest {
    @Test
    fun `supported release notes become structured content`() {
        val document = ReleaseNotesMarkdownRules.parse(
            markdown = """
                # Candy Sync is here

                Move between **Candy Browser** and your `desktop`.

                ![Synced device](https://raw.githubusercontent.com/sk2andy/candy-browser/v0.32/docs/screenshots/candy-sync-device-profile.png)

                ## Your tabs, everywhere

                - Open tabs
                - Close tabs

                Learn more in [Cross-device sync](https://github.com/sk2andy/candy-browser/blob/v0.32/README.md#cross-device-sync).

                > Everything is end-to-end encrypted.
            """.trimIndent(),
            versionName = "0.32",
        )

        assertNotNull(document)
        requireNotNull(document)
        assertEquals("Candy Sync is here", document.title.text)
        assertTrue(document.blocks[1] is ReleaseNotesBlock.Paragraph)
        assertEquals(
            "release-notes-images/candy-sync-device-profile.png",
            (document.blocks[2] as ReleaseNotesBlock.Image).assetPath,
        )
        assertTrue(document.blocks.any { block -> block is ReleaseNotesBlock.ListItems })
        assertTrue(document.blocks.any { block -> block is ReleaseNotesBlock.Quote })
    }

    @Test
    fun `inline formatting keeps readable text and safe link metadata`() {
        val inline = ReleaseNotesMarkdownRules.parseInline(
            "Use **bold**, *italic*, `code`, and [docs](https://example.com/docs).",
        )

        assertEquals("Use bold, italic, code, and docs.", inline.text)
        assertEquals(
            listOf(
                ReleaseNotesInlineStyle.Bold,
                ReleaseNotesInlineStyle.Italic,
                ReleaseNotesInlineStyle.Code,
                ReleaseNotesInlineStyle.Link,
            ),
            inline.decorations.map(ReleaseNotesDecoration::style),
        )
        assertEquals("https://example.com/docs", inline.decorations.last().target)
    }

    @Test
    fun `unsafe or version-mismatched screenshots reject the document`() {
        listOf(
            "http://example.com/screenshot.png",
            "https://raw.githubusercontent.com/sk2andy/candy-browser/v0.31/docs/screenshots/screenshot.png",
            "https://raw.githubusercontent.com/sk2andy/candy-browser/v0.32/docs/screenshots/../secret.png",
        ).forEach { target ->
            assertNull(
                ReleaseNotesMarkdownRules.parse(
                    markdown = "# Title\n\n![Screenshot]($target)",
                    versionName = "0.32",
                ),
            )
        }
    }

    @Test
    fun `document must start with a level-one heading`() {
        assertNull(
            ReleaseNotesMarkdownRules.parse(
                markdown = "Intro first\n\n# Late title",
                versionName = "0.32",
            ),
        )
        assertNull(
            ReleaseNotesMarkdownRules.parse(
                markdown = "## Wrong level",
                versionName = "0.32",
            ),
        )
    }

    @Test
    fun `unclosed code block and oversized input are rejected`() {
        assertNull(
            ReleaseNotesMarkdownRules.parse(
                markdown = "# Title\n\n```kotlin\nval unfinished = true",
                versionName = "0.32",
            ),
        )
        assertNull(
            ReleaseNotesMarkdownRules.parse(
                markdown = "# ${"x".repeat(ReleaseNotesMarkdownRules.MAX_MARKDOWN_BYTES)}",
                versionName = "0.32",
            ),
        )
    }
}
