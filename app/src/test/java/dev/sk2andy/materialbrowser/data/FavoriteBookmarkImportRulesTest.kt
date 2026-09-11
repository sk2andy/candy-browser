package dev.sk2andy.materialbrowser.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteBookmarkImportRulesTest {
    @Test
    fun `parser reads Netscape anchors attributes timestamps and entities`() {
        val source = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
            <DL><p>
                <DT><A HREF="https://Example.com:443/guide?one=1&amp;two=2"
                    ADD_DATE="1700000000">Candy &amp; Friends</A>
                <DT><a add_date='broken' href='http://docs.example/path'>
                    <span>Docs</span> &#x1F36C;
                </a>
            </DL><p>
        """.trimIndent()

        val result = FavoriteBookmarkImportRules.parse(
            source = source,
            importedAtMillis = 42L,
        ) as FavoriteBookmarkParseResult.Parsed

        assertEquals(
            listOf(
                FavoriteEntry(
                    url = "https://Example.com:443/guide?one=1&two=2",
                    title = "Candy & Friends",
                    addedAt = 1_700_000_000_000L,
                ),
                FavoriteEntry(
                    url = "http://docs.example/path",
                    title = "Docs 🍬",
                    addedAt = 42L,
                ),
            ),
            result.favorites,
        )
    }

    @Test
    fun `parser rejects unrelated HTML`() {
        assertEquals(
            FavoriteBookmarkParseResult.InvalidFormat,
            FavoriteBookmarkImportRules.parse(
                source = "<html><a href=\"https://example.com\">Example</a></html>",
                importedAtMillis = 1L,
            ),
        )
    }

    @Test
    fun `parser skips commented and scripted anchors without hiding later bookmarks`() {
        val invalidAnchors = "<A>ignored</A>".repeat(10_001)
        val source = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <!-- <A HREF="https://comment.example/">Comment</A> -->
            <script><A HREF="https://script.example/">Script</A></script>
            $invalidAnchors
            <A HREF=https://real.example/>Real</A>
        """.trimIndent()

        val result = FavoriteBookmarkImportRules.parse(
            source = source,
            importedAtMillis = 1L,
        ) as FavoriteBookmarkParseResult.Parsed

        assertEquals(listOf("https://real.example/"), result.favorites.map(FavoriteEntry::url))
    }

    @Test
    fun `nested anchor replaces malformed outer anchor`() {
        val result = FavoriteBookmarkImportRules.parse(
            source = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <A>broken <A HREF=https://real.example/>Real</A>
            """.trimIndent(),
            importedAtMillis = 1L,
        ) as FavoriteBookmarkParseResult.Parsed

        assertEquals(listOf("https://real.example/"), result.favorites.map(FavoriteEntry::url))
    }

    @Test
    fun `merge keeps existing favorites and rejects duplicates unsafe URLs and long URLs`() {
        val existing = favorite("https://example.com/guide#old", "Existing", 1L)
        val longUrl = "https://long.example/" + "a".repeat(9_000)
        val parsed = FavoriteBookmarkImportRules.parse(
            source = """
                <!DOCTYPE NETSCAPE-Bookmark-file-1>
                <DL><p>
                    <DT><A HREF="https://Example.com:443/guide#new">Duplicate</A>
                    <DT><A HREF="https://user:secret@private.example/">Credentials</A>
                    <DT><A HREF="javascript:alert(1)">Script</A>
                    <DT><A HREF="$longUrl">Long</A>
                    <DT><A HREF="https://new.example/path">New favorite</A>
                </DL><p>
            """.trimIndent(),
            importedAtMillis = 2L,
        ) as FavoriteBookmarkParseResult.Parsed

        val result = FavoriteBookmarkImportRules.merge(
            current = listOf(existing),
            imported = parsed.favorites,
        )

        assertEquals(1, result.importedCount)
        assertEquals(3, result.skippedCount)
        assertEquals(
            listOf("https://new.example/path", existing.url),
            result.favorites.map(FavoriteEntry::url),
        )
    }

    @Test
    fun `merge uses host title and reports favorite limit`() {
        val current = listOf(favorite("https://existing.example/", "Existing", 1L))
        val imported = listOf(
            favorite("https://one.example/", "", 2L),
            favorite("https://two.example/", "Two", 3L),
        )

        val result = FavoriteBookmarkImportRules.merge(
            current = current,
            imported = imported,
            limit = 2,
        )

        assertEquals(1, result.importedCount)
        assertEquals(1, result.skippedCount)
        assertTrue(result.limitReached)
        assertEquals("one.example", result.favorites.first().title)
        assertEquals(current.first(), result.favorites.last())
    }

    @Test
    fun `reader rejects invalid UTF-8`() {
        val result = FavoriteBookmarkImportReader.read(
            ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)),
        )

        assertEquals(FavoriteBookmarkFileResult.InvalidUtf8, result)
    }

    private fun favorite(url: String, title: String, addedAt: Long) = FavoriteEntry(
        url = url,
        title = title,
        addedAt = addedAt,
    )
}
