package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 32)
class AppLanguagePreferencesLegacyInstrumentedTest {
    @Test
    fun usesSystemLanguageWithoutAppLanguageSettings() {
        val preferences = AppLanguagePreferences(InstrumentationRegistry.getInstrumentation().targetContext)

        assertTrue(preferences.supportedLocales.isEmpty())
        assertEquals("", preferences.languageTag)
        preferences.setLanguage("de")
        assertEquals("", preferences.languageTag)
    }
}
