package dev.sk2andy.materialbrowser.browser

enum class ExternalAppLinkHandling(val stableId: String) {
    Automatic("automatic"),
    AskEveryTime("ask_every_time"),
    ;

    companion object {
        val Default = Automatic

        fun fromStableId(value: String?): ExternalAppLinkHandling =
            entries.firstOrNull { handling -> handling.stableId == value } ?: Default
    }
}

data class ExternalAppPrompt(
    val id: Long,
    val destination: String,
)
