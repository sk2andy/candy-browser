package dev.sk2andy.materialbrowser.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {
    @Test
    fun `rss preserves article identity and removes executable content`() {
        val preview =
            FeedParser.parse(
                """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel>
            <title>News &amp; notes</title><link>https://example.org</link>
            <item><title>First</title><link>/articles/1</link><description>Summary</description>
            <content:encoded><![CDATA[<p>Useful words</p><script>secret()</script>]]></content:encoded>
            <pubDate>Wed, 21 Oct 2026 10:00:00 GMT</pubDate></item>
            <item><title>Unsafe</title><link>javascript:alert(1)</link></item>
            </channel></rss>
        """
                    .trimIndent(),
                "https://example.org/feed",
            )
        assertEquals("News & notes", preview.title)
        assertEquals(1, preview.articles.size)
        val article = preview.articles.single()
        assertEquals("https://example.org/articles/1", article.url)
        assertEquals("Useful words", article.content)
        assertTrue(article.publishedAtMillis > 0)
    }

    @Test
    fun `atom reads alternate links and xhtml text`() {
        val preview =
            FeedParser.parse(
                """
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Atom</title>
            <entry><title>Entry</title><link rel="self" href="https://example.org/api/1"/>
            <link rel="alternate" href="https://example.org/1"/>
            <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>First paragraph</p><p>Second</p></div></content>
            <updated>2026-10-21T10:00:00Z</updated></entry></feed>
        """
                    .trimIndent(),
                "https://example.org/feed",
            )
        assertEquals("https://example.org/1", preview.articles.single().url)
        assertEquals("First paragraph Second", preview.articles.single().content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects external entities`() {
        FeedParser.parse(
            """<!DOCTYPE rss [<!ENTITY external SYSTEM "file:///etc/passwd">]><rss><channel><title>&external;</title></channel></rss>""",
            "https://example.org/feed",
        )
    }

    @Test
    fun `discovery only accepts feed alternate links`() {
        val candidates =
            FeedParser.discover(
                """
            <link rel="alternate" type="application/rss+xml" title="News" href="/feed?a=1&amp;b=2">
            <link rel="stylesheet" type="application/rss+xml" href="/wrong">
            <link rel="alternate" type="application/atom+xml" href="javascript:alert(1)">
        """
                    .trimIndent(),
                "https://example.org/page",
            )
        assertEquals(listOf(FeedCandidate("https://example.org/feed?a=1&b=2", "News")), candidates)
    }

    @Test
    fun `repeated import recognizes duplicate urls and preserves folders`() {
        val state =
            FeedState(
                sources =
                    listOf(
                        FeedSource("id", "https://example.org/feed", "News", folderId = "folder")
                    ),
                folders = listOf(FeedFolder("folder", "Research & notes")),
            )
        val xml = FeedOpml.export(state)
        val entries = FeedOpml.preview(xml, state.sources).entries
        assertEquals(FeedImportStatus.Existing, entries.single().status)
        assertEquals("Research & notes", entries.single().folder)
        assertFalse(xml.contains("Research & notes"))
    }

    @Test
    fun `atom resolves inherited xml base and ignores non permalink rss identifiers`() {
        val atom =
            FeedParser.parse(
                """<feed xml:base="https://articles.example.org/base/"><entry xml:base="news/"><title>Relative</title><link href="one"/></entry></feed>""",
                "https://example.org/feed",
            )
        assertEquals("https://articles.example.org/base/news/one", atom.articles.single().url)
        val rss =
            FeedParser.parse(
                """<rss><channel><item><title>Opaque identifier</title><guid isPermaLink="false">https://example.org/id</guid></item></channel></rss>""",
                "https://example.org/feed",
            )
        assertTrue(rss.articles.isEmpty())
    }
}
