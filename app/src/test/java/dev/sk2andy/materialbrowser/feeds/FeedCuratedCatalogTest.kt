package dev.sk2andy.materialbrowser.feeds

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedCuratedCatalogTest {
    private fun catalog() = FeedCuratedCatalog {
        File("src/main/assets/feed_catalog/plenary.json").readText()
    }

    @Test
    fun `publisher names match before descriptions and case does not matter`() {
        val results = catalog().search(FeedCatalogQuery(text = "BBC"))
        assertTrue(results.sources.isNotEmpty())
        assertTrue(results.sources.first().title.contains("BBC", ignoreCase = true))
        assertEquals(results, catalog().search(FeedCatalogQuery(text = "bbc")))
    }

    @Test
    fun `absent publisher does not produce unrelated recommendations`() {
        assertTrue(catalog().search(FeedCatalogQuery(text = "Golem")).sources.isEmpty())
    }

    @Test
    fun `bundled catalog paginates all unique sources without network metadata`() {
        val catalog = catalog()
        val sources = mutableListOf<FeedCatalogSource>()
        var offset: Int? = 0
        do {
            val page = catalog.search(FeedCatalogQuery(offset = requireNotNull(offset)))
            sources += page.sources
            offset = page.nextOffset
        } while (offset != null)
        assertEquals(832, sources.size)
        assertEquals(sources.size, sources.map { it.feedUrl }.distinct().size)
        assertTrue(sources.all { it.kind == null && it.language.isBlank() })
    }

    @Test
    fun `topics and international publisher names remain searchable`() {
        assertTrue(catalog().search(FeedCatalogQuery(topic = "science")).sources.isNotEmpty())
        assertTrue(catalog().search(FeedCatalogQuery(text = "Коммерсантъ")).sources.isNotEmpty())
        assertTrue(catalog().search(FeedCatalogQuery(text = "朝日")).sources.isNotEmpty())
    }

    @Test
    fun `publisher domain is searchable even when feed is hosted elsewhere`() {
        val catalog = FeedCuratedCatalog {
            """{"sources":[
              {"slug":"publisher","title":"A daily journal","description":"News",
               "feedUrl":"https://feeds.example.com/journal","siteUrl":"https://www.publisher.example/",
               "topics":[],"tags":[]},
              {"slug":"mention","title":"Another journal","description":"Articles about publisher.example",
               "feedUrl":"https://another.example/feed","siteUrl":"", "topics":[],"tags":[]}
            ]}"""
        }
        assertEquals(
            listOf("publisher", "mention"),
            catalog.search(FeedCatalogQuery(text = "publisher.example")).sources.map { it.slug },
        )
        assertEquals(
            "publisher",
            catalog.search(FeedCatalogQuery(text = "publisher")).sources.first().slug,
        )
    }

    @Test
    fun `asset is read once and bad offsets are rejected`() {
        var reads = 0
        val catalog = FeedCuratedCatalog {
            reads++
            File("src/main/assets/feed_catalog/plenary.json").readText()
        }
        catalog.search(FeedCatalogQuery(text = "BBC"))
        catalog.search(FeedCatalogQuery(topic = "science"))
        assertEquals(1, reads)
        assertTrue(runCatching { catalog.search(FeedCatalogQuery(offset = -1)) }.isFailure)
    }
}
