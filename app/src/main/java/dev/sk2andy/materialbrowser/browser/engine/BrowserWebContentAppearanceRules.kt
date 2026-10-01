package dev.sk2andy.materialbrowser.browser.engine

import dev.sk2andy.materialbrowser.data.AppearanceSettings

internal object BrowserWebContentAppearanceRules {
    /** Resolve System before forwarding to engines with a separate native night-mode cache. */
    fun colorScheme(
        settings: AppearanceSettings,
        systemDark: Boolean,
    ): BrowserWebContentColorScheme = if (settings.usesDarkColors(systemDark)) {
        BrowserWebContentColorScheme.Dark
    } else {
        BrowserWebContentColorScheme.Light
    }
}
