package dev.sk2andy.materialbrowser.browser

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SearchEngine(
    val stableId: String,
    val displayName: String,
    private val searchUrl: String,
) {
    Google(
        stableId = "google",
        displayName = "Google",
        searchUrl = "https://www.google.com/search?q=%s",
    ),
    DuckDuckGo(
        stableId = "duckduckgo",
        displayName = "DuckDuckGo",
        searchUrl = "https://duckduckgo.com/?q=%s",
    ),
    Bing(
        stableId = "bing",
        displayName = "Bing",
        searchUrl = "https://www.bing.com/search?q=%s",
    ),
    Brave(
        stableId = "brave",
        displayName = "Brave Search",
        searchUrl = "https://search.brave.com/search?q=%s",
    ),
    Ecosia(
        stableId = "ecosia",
        displayName = "Ecosia",
        searchUrl = "https://www.ecosia.org/search?q=%s",
    ),
    Startpage(
        stableId = "startpage",
        displayName = "Startpage",
        searchUrl = "https://www.startpage.com/sp/search?query=%s",
    ),
    Qwant(
        stableId = "qwant",
        displayName = "Qwant",
        searchUrl = "https://www.qwant.com/?q=%s",
    ),
    Perplexity(
        stableId = "perplexity",
        displayName = "Perplexity",
        searchUrl = "https://www.perplexity.ai/search?q=%s",
    ),
    ChatGPT(
        stableId = "chatgpt",
        displayName = "ChatGPT",
        searchUrl = "https://chatgpt.com/?q=%s",
    ),
    ;

    fun buildSearchUrl(query: String): String = searchUrl.format(query.urlEncoded())

    companion object {
        fun fromStableId(stableId: String?): SearchEngine =
            entries.firstOrNull { it.stableId == stableId } ?: Google
    }
}

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.toString()).replace("+", "%20")
