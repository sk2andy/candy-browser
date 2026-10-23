package dev.sk2andy.materialbrowser.feeds

/**
 * Presentation groups only. Article identities, source links and read states remain independent.
 */
internal object FeedSimilarityRules {
    data class Group(val articles: List<FeedArticle>) {
        val id: String
            get() = articles.first().id
    }

    fun groups(articles: List<FeedArticle>): List<Group> {
        val groups = mutableListOf<MutableList<FeedArticle>>()
        val groupWords = mutableListOf<Set<String>>()
        val index = mutableMapOf<String, MutableList<Int>>()
        articles.forEach { article ->
            val words = words(article.title)
            val candidates =
                words
                    .mapNotNull(index::get)
                    .sortedBy(List<Int>::size)
                    .take(2)
                    .flatten()
                    .distinct()
                    .take(MAX_CANDIDATES)
            val matching = candidates.firstOrNull { similar(words, groupWords[it]) }
            if (matching != null) {
                groups[matching].add(article)
            } else {
                val ordinal = groups.size
                groups.add(mutableListOf(article))
                groupWords.add(words)
                words.forEach { index.getOrPut(it) { mutableListOf() }.add(ordinal) }
            }
        }
        return groups.map { Group(it.toList()) }
    }

    private fun words(title: String): Set<String> =
        title
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 1 }
            .take(MAX_TITLE_WORDS)
            .toSet()

    private fun similar(left: Set<String>, right: Set<String>): Boolean {
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        if (minOf(left.size, right.size) < MIN_FUZZY_WORDS) return false
        return left.intersect(right).size.toDouble() / left.union(right).size >= MIN_OVERLAP
    }

    private const val MAX_CANDIDATES = 128
    private const val MAX_TITLE_WORDS = 60
    private const val MIN_FUZZY_WORDS = 5
    private const val MIN_OVERLAP = 0.85
}
