package dev.sk2andy.materialbrowser.feeds

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedSimilarityRulesTest {
    @Test
    fun punctuationAndCaseDifferencesGroupWithoutRemovingOriginalArticles() {
        val first = article("1", "Candy brings a calmer reading experience")
        val second = article("2", "CANDY: brings a calmer reading experience!")
        val groups = FeedSimilarityRules.groups(listOf(first, second))
        assertEquals(1, groups.size)
        assertEquals(listOf(first, second), groups.single().articles)
        assertEquals(
            setOf("https://example.com/1", "https://example.com/2"),
            groups.single().articles.map { it.url }.toSet(),
        )
    }

    @Test
    fun relatedLongHeadlinesGroupButShortAmbiguousOnesStayIndependent() {
        val articles =
            listOf(
                article(
                    "1",
                    "New Candy browser release brings improved reading tools for everyone",
                ),
                article(
                    "2",
                    "New Candy browser release brings improved reading tools for everyone today",
                ),
                article("3", "Market rises"),
                article("4", "Market falls"),
            )
        assertEquals(listOf(2, 1, 1), FeedSimilarityRules.groups(articles).map { it.articles.size })
    }

    @Test
    fun emptyTitlesStaySeparateAndInputOrderIsPreserved() {
        val articles =
            listOf(article("1", ""), article("2", ""), article("3", "Different headline"))
        assertEquals(
            articles.map { it.id },
            FeedSimilarityRules.groups(articles).flatMap { it.articles }.map { it.id },
        )
    }

    private fun article(id: String, title: String) =
        FeedArticle(id, setOf("source-$id"), "https://example.com/$id", title)
}
