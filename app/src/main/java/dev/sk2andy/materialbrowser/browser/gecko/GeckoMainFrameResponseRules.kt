package dev.sk2andy.materialbrowser.browser.gecko

internal data class GeckoMainFrameResponse(
    val url: String,
    val statusCode: Int,
    val navigationGeneration: Int,
    val isCloudflareChallenge: Boolean = false,
)

internal object GeckoMainFrameResponseRules {
    fun resolve(
        url: String,
        statusCode: Int,
        navigationGeneration: Int,
        isCloudflareChallenge: Boolean = false,
    ): GeckoMainFrameResponse? {
        val safeUrl = url.take(MAX_URL_CHARS)
        if (!safeUrl.startsWith("http://") && !safeUrl.startsWith("https://")) return null
        if (statusCode !in MIN_HTTP_STATUS..MAX_HTTP_STATUS) return null
        if (navigationGeneration < 0) return null
        return GeckoMainFrameResponse(
            url = safeUrl,
            statusCode = statusCode,
            navigationGeneration = navigationGeneration,
            isCloudflareChallenge = isCloudflareChallenge && safeUrl.startsWith("https://"),
        )
    }

    private const val MIN_HTTP_STATUS = 100
    private const val MAX_HTTP_STATUS = 599
    private const val MAX_URL_CHARS = 8 * 1_024
}
