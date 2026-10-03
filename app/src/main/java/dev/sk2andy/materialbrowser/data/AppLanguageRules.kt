package dev.sk2andy.materialbrowser.data

import java.util.Locale

internal object AppLanguageRules {
    fun selectedLocale(languageTag: String, supportedLocales: List<Locale>): Locale? {
        if (languageTag.isBlank()) return null
        val requested = Locale.forLanguageTag(languageTag)
        val script = requested.script.ifEmpty {
            if (requested.language == "zh") {
                if (requested.country in TRADITIONAL_CHINESE_REGIONS) "Hant" else "Hans"
            } else {
                ""
            }
        }
        return supportedLocales.firstOrNull { it == requested }
            ?: supportedLocales.firstOrNull { it.language == requested.language && it.script == script }
            ?: supportedLocales.firstOrNull { it.language == requested.language }
    }

    private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")
}
