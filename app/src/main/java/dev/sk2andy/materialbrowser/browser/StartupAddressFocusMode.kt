package dev.sk2andy.materialbrowser.browser

enum class StartupAddressFocusMode(val stableId: String) {
    WhenStartupAnimationDisabled("when_startup_animation_disabled"),
    Always("always"),
    Never("never"),
    ;

    companion object {
        val Default = WhenStartupAnimationDisabled

        fun fromStableId(value: String?): StartupAddressFocusMode =
            entries.firstOrNull { mode -> mode.stableId == value } ?: Default
    }
}
