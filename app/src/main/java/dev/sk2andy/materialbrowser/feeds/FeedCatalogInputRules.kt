package dev.sk2andy.materialbrowser.feeds

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

internal sealed interface FeedCatalogInput {
    data class Address(val url: String) : FeedCatalogInput

    data class Search(val text: String) : FeedCatalogInput

    data object Empty : FeedCatalogInput

    data object Invalid : FeedCatalogInput

    data object PrivateSearch : FeedCatalogInput
}

/** Classifies explicit submissions without fetching, discovering or persisting anything. */
internal object FeedCatalogInputRules {
    const val MAX_ADDRESS_LENGTH = 2048
    const val MAX_DRAFT_LENGTH = MAX_ADDRESS_LENGTH + 1
    private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    fun classify(value: String, isPrivate: Boolean = false): FeedCatalogInput {
        val text = value.trim()
        if (text.isEmpty()) return FeedCatalogInput.Empty
        if (text.length > MAX_ADDRESS_LENGTH || text.any(Char::isISOControl)) {
            return FeedCatalogInput.Invalid
        }
        safeAddress(text)?.let {
            return FeedCatalogInput.Address(it)
        }
        // Never reinterpret a malformed explicit URI as a search or prepend https to its scheme.
        if (
            text.contains("://") ||
                schemePrefix.containsMatchIn(text) && !text.substringBefore(':').contains('.')
        ) {
            return FeedCatalogInput.Invalid
        }
        val authority = text.substringBefore('/').substringBefore('?').substringBefore('#')
        if (text.none(Char::isWhitespace) && authority.contains('.')) {
            return safeAddress("https://$text")?.let { FeedCatalogInput.Address(it) }
                ?: FeedCatalogInput.Invalid
        }
        if (text.length > FeedCatalogRules.MAX_QUERY_LENGTH) return FeedCatalogInput.Invalid
        return if (isPrivate) FeedCatalogInput.PrivateSearch else FeedCatalogInput.Search(text)
    }

    private fun safeAddress(value: String): String? =
        BrowserUriPolicy.normalizeHttpUrl(value)?.let(FeedCatalogRules::safeSourceUrl)
}
