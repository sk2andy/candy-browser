package dev.sk2andy.materialbrowser.feeds

enum class FeedCatalogKind {
    Blog,
    News,
}

data class FeedCatalogQuery(
    val text: String = "",
    val topic: String? = null,
    val kind: FeedCatalogKind? = null,
    val offset: Int = 0,
)

/** Metadata only. Subscription always previews and validates the publisher's original feed. */
data class FeedCatalogSource(
    val slug: String,
    val title: String,
    val description: String,
    val siteUrl: String,
    val feedUrl: String,
    val language: String,
    val kind: FeedCatalogKind,
)

/** The provider has no global language filter; these are the sources actually loaded. */
data class FeedCatalogPage(val sources: List<FeedCatalogSource>, val nextOffset: Int?)
