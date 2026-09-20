package dev.sk2andy.materialbrowser.browser

enum class InlineMediaPlayerMode(val stableId: String) {
    ButtonFullscreen("button_fullscreen"),
    ButtonInlineAndFullscreen("button_inline_and_fullscreen"),
    AlwaysForFullscreen("always_for_fullscreen"),
    Automatic("automatic"),
    ;

    companion object {
        val Default = ButtonInlineAndFullscreen

        fun fromStableId(value: String?): InlineMediaPlayerMode =
            entries.firstOrNull { mode -> mode.stableId == value } ?: Default
    }
}

internal object InlineMediaPlayerModeRules {
    fun detectsInlineVideo(mode: InlineMediaPlayerMode): Boolean =
        showsOpenButton(mode) || startsWhenVideoDetected(mode)

    fun showsOpenButton(mode: InlineMediaPlayerMode): Boolean = when (mode) {
        InlineMediaPlayerMode.ButtonFullscreen,
        InlineMediaPlayerMode.ButtonInlineAndFullscreen,
        -> true
        InlineMediaPlayerMode.AlwaysForFullscreen,
        InlineMediaPlayerMode.Automatic,
        -> false
    }

    fun buttonStartsFullscreen(mode: InlineMediaPlayerMode): Boolean =
        mode == InlineMediaPlayerMode.ButtonFullscreen

    fun supportsInlinePresentation(mode: InlineMediaPlayerMode): Boolean = when (mode) {
        InlineMediaPlayerMode.ButtonInlineAndFullscreen,
        InlineMediaPlayerMode.Automatic,
        -> true
        InlineMediaPlayerMode.ButtonFullscreen,
        InlineMediaPlayerMode.AlwaysForFullscreen,
        -> false
    }

    fun replacesWebsiteFullscreen(mode: InlineMediaPlayerMode): Boolean =
        mode == InlineMediaPlayerMode.AlwaysForFullscreen

    fun startsWhenVideoDetected(mode: InlineMediaPlayerMode): Boolean =
        mode == InlineMediaPlayerMode.Automatic
}
