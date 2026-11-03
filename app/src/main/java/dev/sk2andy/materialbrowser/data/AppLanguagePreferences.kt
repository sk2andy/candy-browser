package dev.sk2andy.materialbrowser.data

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

internal class AppLanguagePreferences(context: Context) {
    private val localeManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)
    } else {
        null
    }

    val supportedLocales: List<Locale> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LocaleConfig(context).supportedLocales?.let { locales ->
            List(locales.size()) { index -> locales[index] }
        }.orEmpty()
    } else {
        emptyList()
    }

    val languageTag: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager?.applicationLocales?.get(0)?.toLanguageTag().orEmpty()
        } else {
            ""
        }

    fun setLanguage(languageTag: String) {
        if (languageTag.isNotEmpty() && supportedLocales.none { it.toLanguageTag() == languageTag }) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager?.applicationLocales = LocaleList.forLanguageTags(languageTag)
        }
    }
}
