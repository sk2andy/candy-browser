package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules

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
            val baseUrl = SearchSettingsRules.normalizedSearxngInstanceUrl(searxngInstanceUrl)
                ?: return BLANK_URL
            return "$baseUrl/search?q=${BrowserUrlRules.encodeSearchQuery(query)}"
        }
        val template = if (mode == SearchMode.Ai) aiSearchUrl ?: searchUrl else searchUrl
        return checkNotNull(template).replace(
            oldValue = "%s",
            newValue = BrowserUrlRules.encodeSearchQuery(query),
        )
    }

    companion object {
        fun fromStableId(stableId: String?): SearchEngine =
            entries.firstOrNull { it.stableId == stableId } ?: Google
    }
}

data class SearchSettings(
    val searchEngine: SearchEngine = SearchEngine.Google,
    val searxngInstanceUrl: String = "",
)

object SearchSettingsRules {
    const val MAX_SEARXNG_INSTANCE_URL_LENGTH = 2_048

    fun sanitize(settings: SearchSettings): SearchSettings = settings.copy(
        searxngInstanceUrl = normalizedSearxngInstanceUrl(settings.searxngInstanceUrl).orEmpty(),
    )

    fun normalizedSearxngInstanceUrl(value: String): String? {
        val candidate = value.trim()
        if (
            candidate.isEmpty() ||
            candidate.length > MAX_SEARXNG_INSTANCE_URL_LENGTH ||
            '?' in candidate ||
            '#' in candidate
        ) {
            return null
        }
        return BrowserUrlRules.normalizeHttpUrl(candidate)
            ?.value
            ?.trimEnd('/')
            ?.takeIf(String::isNotEmpty)
    }
}
