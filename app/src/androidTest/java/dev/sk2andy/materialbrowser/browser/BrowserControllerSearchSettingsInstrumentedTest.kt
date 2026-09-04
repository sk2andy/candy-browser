package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.HistoryEntry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerSearchSettingsInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun disablingHistorySuggestionsHidesStoredHistoryAndPersistsChoice() {
        activityRule.scenario.onActivity { activity ->
            BrowserSessionStore(activity).saveHistory(
                listOf(
                    HistoryEntry(
                        url = "https://history.example/",
                        title = "History match",
                        lastVisitedAt = 1L,
                    ),
                ),
            )
            controller = BrowserController(activity).also { browserController ->
                assertTrue(browserController.addressSuggestions("history").isNotEmpty())

                browserController.updateHistorySuggestionsEnabled(false)

                assertFalse(browserController.isHistorySuggestionsEnabled)
                assertTrue(browserController.addressSuggestions("history").isEmpty())
                assertFalse(BrowserSessionStore(activity).loadHistorySuggestionsEnabled())
            }
        }
    }
}
