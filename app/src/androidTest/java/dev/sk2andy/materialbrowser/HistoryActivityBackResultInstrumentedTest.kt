package dev.sk2andy.materialbrowser

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowsingHistoryRepository
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.ui.HistoryScreenTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class HistoryActivityBackResultInstrumentedTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test
    fun committedDeletionPublishesResultBeforeNativeActivityBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BrowserSessionStore(context)
        val repository = BrowsingHistoryRepository.get(context)
        val previous = repository.snapshot()
        val entry =
            HistoryEntry(
                url = "https://history-back.example/article",
                title = "History back result",
                lastVisitedAt = System.currentTimeMillis(),
                profileId = store.loadProfiles().second,
            )
        store.saveHistory(listOf(entry))
        try {
            ActivityScenario.launchActivityForResult<HistoryActivity>(
                    Intent(context, HistoryActivity::class.java)
                )
                .use { scenario ->
                    composeRule.onNodeWithTag(HistoryScreenTestTags.select(entry)).performClick()
                    composeRule.onNodeWithTag(HistoryScreenTestTags.DeleteSelected).performClick()
                    composeRule.waitUntil(5_000) { repository.snapshot().isEmpty() }
                    composeRule.waitForIdle()
                    scenario.onActivity { activity ->
                        assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
                        activity.onBackPressedDispatcher.onBackPressed()
                    }
                    assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
                }
        } finally {
            store.saveHistory(previous)
        }
    }
}
