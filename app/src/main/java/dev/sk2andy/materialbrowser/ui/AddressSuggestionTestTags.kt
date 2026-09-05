package dev.sk2andy.materialbrowser.ui

internal object AddressSuggestionTestTags {
    fun searchRow(query: String): String = "address_search_suggestion:$query"
    fun fillSearch(query: String): String = "address_search_suggestion_fill:$query"
    fun recallRow(url: String): String = "address_recall_suggestion:$url"
}
