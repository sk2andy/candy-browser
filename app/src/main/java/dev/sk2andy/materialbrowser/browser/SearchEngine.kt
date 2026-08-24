package dev.sk2andy.materialbrowser.browser

enum class SearchMode {
    Web,
    Ai,
}

enum class SearchEngine(
    val stableId: String,
    val displayName: String,
    private val searchUrl: String?,
    private val aiSearchUrl: String? = null,
) {
    Google(
        stableId = "google",
        displayName = "Google",
        searchUrl = "https://www.google.com/search?q=%s",
        aiSearchUrl = "https://www.google.com/ai?q=%s",
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
    Kagi(
        stableId = "kagi",
        displayName = "Kagi",
        searchUrl = "https://kagi.com/search?q=%s",
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
    SearXNG(
        stableId = "searxng",
        displayName = "SearXNG",
        searchUrl = null,
    ),
    ;

    val supportsAiSearch: Boolean
        get() = aiSearchUrl != null

    fun buildSearchUrl(
        query: String,
        mode: SearchMode = SearchMode.Web,
        searxngInstanceUrl: String = "",
    ): String {
        if (this == SearXNG) {
            return SearxngRules.buildSearchUrl(searxngInstanceUrl, query)
                ?: BLANK_URL
        }
        val template = if (mode == SearchMode.Ai) aiSearchUrl ?: searchUrl else searchUrl
        checkNotNull(template)
        return template.format(query.urlEncoded())
    }

    companion object {
        fun fromStableId(stableId: String?): SearchEngine =
            entries.firstOrNull { it.stableId == stableId } ?: Google
    }
}
