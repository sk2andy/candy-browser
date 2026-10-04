package dev.sk2andy.materialbrowser.data

internal object FaviconPageIconRules {
    const val PREFERRED_ICON_DIMENSION = 128
    private const val MAX_LINKS = 64
    private const val MAX_CANDIDATES = 3
    private const val MAX_URL_CHARS = 8 * 1_024
    private val ignoredContent = Regex(
        """<!--[\s\S]*?(?:-->|$)|<(script|style)\b[^>]*>[\s\S]*?(?:</\1\s*>|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val links = Regex("""<link\s+([^>]+)>""", RegexOption.IGNORE_CASE)
    private val sizes = Regex("""(?:^|\s)(\d+)x(\d+)(?=\s|$)""", RegexOption.IGNORE_CASE)

    fun originPageUrl(pageUrl: String): String? =
        FaviconFetchRules.originIconUrl(pageUrl)?.removeSuffix("favicon.ico")

    fun candidates(pageUrl: String, source: String): List<String> {
        val originUrl = originPageUrl(pageUrl) ?: return emptyList()
        return links.findAll(ignoredContent.replace(source, ""))
            .take(MAX_LINKS)
            .mapNotNull { link ->
                val attributes = link.groupValues[1]
                val rel = attribute(attributes, "rel")?.lowercase()
                    ?.split(Regex("""\s+"""))
                    ?: return@mapNotNull null
                val touchIcon = rel.any { token ->
                    token == "apple-touch-icon" || token == "apple-touch-icon-precomposed"
                }
                if (!touchIcon && "icon" !in rel) return@mapNotNull null
                val href = attribute(attributes, "href")?.replace("&amp;", "&")
                    ?.takeIf { it.isNotBlank() && it.length <= MAX_URL_CHARS }
                    ?: return@mapNotNull null
                val url = FaviconFetchRules.allowedRedirect(originUrl, href) ?: return@mapNotNull null
                val dimension = sizes.findAll(attribute(attributes, "sizes").orEmpty())
                    .mapNotNull dimension@{ size ->
                        val width = size.groupValues[1].toIntOrNull() ?: return@dimension null
                        val height = size.groupValues[2].toIntOrNull() ?: return@dimension null
                        minOf(width, height).takeIf { it in 1..MAX_FAVICON_BITMAP_DIMENSION }
                    }
                    .maxOrNull()
                    ?: if (touchIcon) PREFERRED_ICON_DIMENSION else 0
                Candidate(url, dimension)
            }
            .sortedByDescending(Candidate::dimension)
            .distinctBy(Candidate::url)
            .take(MAX_CANDIDATES)
            .map(Candidate::url)
            .toList()
    }

    private fun attribute(attributes: String, name: String): String? {
        val pattern = Regex(
            """(?:^|\s)$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
            RegexOption.IGNORE_CASE,
        )
        val match = pattern.find(attributes) ?: return null
        return match.groupValues.drop(1).firstOrNull(String::isNotEmpty)
    }

    private data class Candidate(val url: String, val dimension: Int)
}
