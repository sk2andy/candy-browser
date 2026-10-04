package dev.sk2andy.materialbrowser.data

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserController
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguagePreferencesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val localeManager = context.getSystemService(LocaleManager::class.java)
    private val originalLocales = localeManager.applicationLocales

    @Before
    fun setUp() {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        BrowserSessionStore(context).apply {
            saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
            saveStartupAnimationEnabled(false)
        }
    }

    @After
    fun tearDown() {
        localeManager.applicationLocales = originalLocales
        clearPreferences()
    }

    @Test
    fun everyNewLanguageResolvesLocalizedLabelsAndFormattedPluralCounts() {
        val locales = AppLanguagePreferences(context).supportedLocales.filter {
            it.language !in setOf("en", "de", "fr", "pt", "es", "pl", "cs")
        }
        assertEquals(22, locales.size)
        val languageLabels = mutableMapOf<String, String>()
        locales.forEach { locale ->
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(locale))
            }
            val localized = context.createConfigurationContext(configuration)
            val languageLabel = localized.getString(R.string.settings_app_language)
            languageLabels[locale.toLanguageTag()] = languageLabel
            assertNotEquals(
                "Language label must be translated for ${locale.toLanguageTag()}",
                "App language",
                languageLabel,
            )
            assertNotEquals(
                "HTTPS warning must be translated for ${locale.toLanguageTag()}",
                "No secure connection available",
                localized.getString(R.string.https_only_warning_title),
            )
            listOf(0, 1, 2, 5, 21).forEach { count ->
                val label = localized.resources.getQuantityString(
                    R.plurals.cd_open_tab_overview_count,
                    count,
                    count,
                )
                assertTrue("Localized count must format for $locale: $label", label.contains("$count"))
            }
        }
        assertNotEquals(
            "Chinese scripts must resolve their own resources",
            languageLabels.getValue("zh-Hans"),
            languageLabels.getValue("zh-Hant"),
        )
    }

    @Test
    fun russianRegionalLocaleResolvesRussianLabelsAndPluralCategories() {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("ru-RU")))
        }
        val localized = context.createConfigurationContext(configuration)
        assertEquals("Язык приложения", localized.getString(R.string.settings_app_language))
        mapOf(
            0 to "Открыть обзор вкладок, 0 вкладок",
            1 to "Открыть обзор вкладок, 1 вкладка",
            2 to "Открыть обзор вкладок, 2 вкладки",
            5 to "Открыть обзор вкладок, 5 вкладок",
            11 to "Открыть обзор вкладок, 11 вкладок",
            21 to "Открыть обзор вкладок, 21 вкладка",
            22 to "Открыть обзор вкладок, 22 вкладки",
            25 to "Открыть обзор вкладок, 25 вкладок",
            101 to "Открыть обзор вкладок, 101 вкладка",
        ).forEach { (count, expected) ->
            assertEquals(
                expected,
                localized.resources.getQuantityString(R.plurals.cd_open_tab_overview_count, count, count),
            )
        }
    }

    @Test
    fun nativeLocaleChangeKeepsActivityAndPrivateTabsAndPersistsAcrossLaunches() {
        val preferences = AppLanguagePreferences(context)
        assertEquals(
            setOf(
                "en", "de", "fr", "pt", "es", "pl", "cs", "ru",
                "zh-Hans", "zh-Hant", "ja", "ko", "nb", "sv", "da", "nl", "lb",
                "sq", "bs", "bg", "el", "hr", "mk", "ro", "sr", "sl", "tr", "th", "vi",
            ),
            preferences.supportedLocales.map { it.toLanguageTag() }.toSet(),
        )
        preferences.setLanguage("pl")
        preferences.setLanguage("unknown")
        assertEquals("pl", preferences.languageTag)
        var privateTabId = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitLanguage(scenario, "pl")
            lateinit var originalActivity: MainActivity
            lateinit var originalController: BrowserController
            var tabIds = emptyList<String>()
            scenario.onActivity { activity ->
                originalActivity = activity
                originalController = activity.browserControllerForTesting()
                originalController.createTab(isIncognito = true)
                privateTabId = originalController.selectedTabId
                tabIds = originalController.tabs.map { it.id }
                assertEquals("Język aplikacji", activity.getString(R.string.settings_app_language))
                AppLanguagePreferences(activity).setLanguage("cs")
            }
            awaitLanguage(scenario, "cs")
            scenario.onActivity { activity ->
                assertSame(originalActivity, activity)
                val controller = activity.browserControllerForTesting()
                assertSame(originalController, controller)
                assertEquals(tabIds, controller.tabs.map { it.id })
                assertEquals(privateTabId, controller.selectedTabId)
                assertTrue(controller.selectedTab.isIncognito)
                assertEquals("Jazyk aplikace", activity.getString(R.string.settings_app_language))
                AppLanguagePreferences(activity).setLanguage("ru")
            }
            awaitLanguage(scenario, "ru")
            scenario.onActivity { activity ->
                assertSame(originalActivity, activity)
                val controller = activity.browserControllerForTesting()
                assertSame(originalController, controller)
                assertEquals(tabIds, controller.tabs.map { it.id })
                assertEquals(privateTabId, controller.selectedTabId)
                assertTrue(controller.selectedTab.isIncognito)
                assertEquals("Язык приложения", activity.getString(R.string.settings_app_language))
            }
            assertEquals("ru", AppLanguagePreferences(context).languageTag)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitLanguage(scenario, "ru")
            assertFalse(
                context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .getString(BrowserSessionStore.KEY_TABS, "").orEmpty().contains(privateTabId),
            )
            scenario.onActivity { activity -> AppLanguagePreferences(activity).setLanguage("") }
            awaitLanguage(scenario, localeManager.systemLocales[0].language)
            assertEquals("", preferences.languageTag)
        }
    }

    private fun awaitLanguage(scenario: ActivityScenario<MainActivity>, language: String) {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        var applied = false
        while (!applied && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                applied = activity.resources.configuration.locales[0].language == language
            }
            if (!applied) SystemClock.sleep(50L)
        }
        assertTrue("App locale must update to $language", applied)
        instrumentation.waitForIdleSync()
    }

    private fun clearPreferences() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
            ReleaseNotesStore.PREFERENCES_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
