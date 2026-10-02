package dev.sk2andy.materialbrowser.browser

data class InlineMediaPlayerSeekSettings(
    val backwardSeconds: Int = 10,
    val forwardSeconds: Int = 10,
)

internal object InlineMediaPlayerSeekSettingsRules {
    val SupportedSeconds = listOf(5, 10, 15, 20, 30, 60)

    fun normalizeSeconds(seconds: Int): Int =
        seconds.takeIf { it in SupportedSeconds } ?: 10

    fun normalize(settings: InlineMediaPlayerSeekSettings): InlineMediaPlayerSeekSettings =
        settings.copy(
            backwardSeconds = normalizeSeconds(settings.backwardSeconds),
            forwardSeconds = normalizeSeconds(settings.forwardSeconds),
        )
}
