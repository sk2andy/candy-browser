package dev.sk2andy.materialbrowser

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingHistoryRepository
import dev.sk2andy.materialbrowser.data.HistoryEntry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HistoryActivityInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<HistoryActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        BrowserSessionStore(context).saveHistory(
            listOf(
                HistoryEntry(
                    url = "https://activity.example/",
                    title = "Activity history",
                    lastVisitedAt = System.currentTimeMillis(),
                ),
            ),
        )
        composeRule.activityRule.scenario.recreate()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun displaysPersistedHistoryInDedicatedActivity() {
        composeRule.onNodeWithText(context.getString(R.string.history_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Activity history").assertIsDisplayed()
    }

    @Test
    fun refreshesHistoryAfterReturningToForeground() {
        composeRule.onNodeWithText("Activity history").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            BrowsingHistoryRepository.get(activity).clear()
        }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.onNodeWithText("Activity history").assertDoesNotExist()
    }
}
