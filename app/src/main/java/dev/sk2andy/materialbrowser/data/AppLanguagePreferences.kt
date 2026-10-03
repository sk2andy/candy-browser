package dev.sk2andy.materialbrowser.data

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

internal class AppLanguagePreferences(context: Context) {
    private val localeManager = context.getSystemService(LocaleManager::class.java)

    val supportedLocales: List<Locale> = LocaleConfig(context).supportedLocales?.let { locales ->
        List(locales.size()) { index -> locales[index] }
    }.orEmpty()

    val languageTag: String
        get() = localeManager.applicationLocales[0]?.toLanguageTag().orEmpty()

    fun setLanguage(languageTag: String) {
        if (languageTag.isNotEmpty() && supportedLocales.none { it.toLanguageTag() == languageTag }) {
            return
        }
        localeManager.applicationLocales = LocaleList.forLanguageTags(languageTag)
    }
}
