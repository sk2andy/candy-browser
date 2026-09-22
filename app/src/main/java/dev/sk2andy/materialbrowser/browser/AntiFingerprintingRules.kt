package dev.sk2andy.materialbrowser.browser

internal object AntiFingerprintingRules {
    private val androidIdentityPattern = Regex(
        """Linux;\s*Android\s+[^)]*""",
        RegexOption.IGNORE_CASE,
    )
    private val webViewTokenPattern = Regex(
        """(?:^|;)\s*wv(?:;|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val chromeVersionPattern = Regex(
        """Chrome/(\d+)\.\d+\.\d+\.\d+""",
        RegexOption.IGNORE_CASE,
    )

    fun reduceUserAgent(userAgent: String): String = userAgent
        .replace(androidIdentityPattern) { match ->
            val webViewSuffix = if (webViewTokenPattern.containsMatchIn(match.value)) "; wv" else ""
            "Linux; Android 10; K$webViewSuffix"
        }
        .replace(chromeVersionPattern, "Chrome/$1.0.0.0")
}
