package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionRules
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchShortcutsInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val github = SearchShortcut("gh", "https://github.com/search?q=%s")
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity {
            controller?.destroy()
            context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test
    fun editedAndRemovedShortcutsSurviveStoreReload() {
        val store = BrowserSessionStore(context)
        store.saveSearchShortcuts(listOf(github.copy(keyword = "!GH")))
        assertEquals(listOf(github), BrowserSessionStore(context).loadSearchShortcuts())
        val edited = SearchShortcutRules.update(
            store.loadSearchShortcuts(), "gh", "code", "https://github.com/search?type=code&q=%s",
        )!!
        store.saveSearchShortcuts(edited)
        assertEquals(edited, BrowserSessionStore(context).loadSearchShortcuts())
        store.saveSearchShortcuts(emptyList())
        assertEquals(emptyList<SearchShortcut>(), BrowserSessionStore(context).loadSearchShortcuts())
    }

    @Test
    fun activeShortcutOffersSearchCompletionsAndKeepsNavigationScoped() {
        activityRule.scenario.onActivity { activity ->
            controller = BrowserController(activity).also { browser ->
                browser.updateSearchShortcuts(listOf(github))
                assertEquals(
                    listOf(AddressSuggestionItem.Search("compose android")),
                    browser.addressSuggestionItems("!gh comp", searchQueries = listOf("compose android")),
                )
                assertEquals(emptyList<AddressSuggestionItem>(), browser.addressSuggestionItems("!gh "))
                assertEquals(
                    "https://github.com/search?q=compose%20android",
                    AddressResolver.resolve("!gh compose android", searchEngine = SearchEngine.Google,
                        searchShortcuts = browser.searchShortcuts),
                )
                val query = SearchShortcutRules.match("!gh compose", browser.searchShortcuts)!!.query
                assertFalse(SearchSuggestionRules.shouldRequest(query, SearchSuggestionProvider.Google, isIncognito = true))
            }
        }
    }
}
