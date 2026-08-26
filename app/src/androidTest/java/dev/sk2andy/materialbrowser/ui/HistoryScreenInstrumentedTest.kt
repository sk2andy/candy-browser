package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.data.HistoryClearRequest
import dev.sk2andy.materialbrowser.data.HistoryEntry
import dev.sk2andy.materialbrowser.data.HistoryRecordingMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profileChipsSupportMultipleProfilesAndSearchCombinedHistory() {
        val now = System.currentTimeMillis()
        val profiles = listOf(
            BrowserProfile(id = "personal", emoji = "🏠"),
            BrowserProfile(id = "work", emoji = "💼"),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                HistoryScreen(
                    profiles = profiles,
                    activeProfileId = "personal",
                    history = listOf(
                        HistoryEntry(
                            url = "https://home.example/",
                            title = "Personal page",
                            lastVisitedAt = now,
                            profileId = "personal",
                        ),
                        HistoryEntry(
                            url = "https://work.example/guide",
                            title = "Work guide",
                            lastVisitedAt = now - 1,
                            profileId = "work",
                        ),
                    ),
                    recordingMode = HistoryRecordingMode.Enabled,
                    onRecordingModeChange = {},
                    onDeleteEntries = {},
                    onClearHistory = {},
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Personal page").assertIsDisplayed()
        composeRule.onNodeWithText("Work guide").assertDoesNotExist()

        composeRule.onNodeWithTag(HistoryScreenTestTags.profile("work"))
            .assertIsNotSelected()
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithText("Personal page").assertIsDisplayed()
        composeRule.onNodeWithText("Work guide").assertIsDisplayed()

        composeRule.onNodeWithTag(HistoryScreenTestTags.Search).performClick()
        composeRule.onNodeWithTag(HistoryScreenTestTags.SearchField).performTextInput("work")
        composeRule.onNodeWithText("Personal page").assertDoesNotExist()
        composeRule.onNodeWithText("Work guide").assertIsDisplayed()

        composeRule.onNodeWithTag(HistoryScreenTestTags.Search).performClick()
        composeRule.onNodeWithText("Personal page").assertIsDisplayed()
    }

    @Test
    fun disablingHistoryAlsoDisablesClearOnExit() {
        composeRule.setContent {
            MaterialBrowserTheme {
                var mode by remember { mutableStateOf(HistoryRecordingMode.ClearOnExit) }
                HistoryScreen(
                    profiles = listOf(BrowserProfile(id = "personal", emoji = "🏠")),
                    activeProfileId = "personal",
                    history = emptyList(),
                    recordingMode = mode,
                    onRecordingModeChange = { mode = it },
                    onDeleteEntries = {},
                    onClearHistory = {},
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearOnExit).assertIsOn()
        composeRule.onNodeWithTag(HistoryScreenTestTags.SaveHistory)
            .assertIsOn()
            .performClick()
            .assertIsOff()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearOnExit)
            .assertIsOff()
            .assertIsNotEnabled()
    }

    @Test
    fun clearDialogSupportsProfileMultiselectAndInclusiveDateTimeBounds() {
        val personalTime = 1_772_323_200_000L
        val workTime = personalTime + 60_000L
        val request = AtomicReference<HistoryClearRequest?>()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialBrowserTheme {
                HistoryScreen(
                    profiles = listOf(
                        BrowserProfile(id = "personal", emoji = "🏠"),
                        BrowserProfile(id = "work", emoji = "💼"),
                    ),
                    activeProfileId = "personal",
                    history = listOf(
                        HistoryEntry(
                            url = "https://personal.example/",
                            title = "Personal",
                            lastVisitedAt = personalTime,
                            profileId = "personal",
                        ),
                        HistoryEntry(
                            url = "https://work.example/",
                            title = "Work",
                            lastVisitedAt = workTime,
                            profileId = "work",
                        ),
                    ),
                    recordingMode = HistoryRecordingMode.Enabled,
                    onRecordingModeChange = {},
                    onDeleteEntries = {},
                    onClearHistory = request::set,
                    onOpenEntry = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(HistoryScreenTestTags.Clear).performClick()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearDialog).assertIsDisplayed()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearSince)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearDatePicker).assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearTimePicker).assertIsDisplayed()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearTimePicker).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearUntil)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearDatePicker).assertIsDisplayed()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearTimePicker).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag(HistoryScreenTestTags.clearProfile("personal"))
            .assertIsSelected()
        composeRule.onNodeWithTag(HistoryScreenTestTags.clearProfile("work"))
            .assertIsNotSelected()
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag(HistoryScreenTestTags.ClearConfirm).performClick()

        assertEquals(setOf("personal", "work"), request.get()?.profileIds)
        assertTrue(request.get()!!.sinceInclusiveMillis <= personalTime)
        assertTrue(request.get()!!.untilExclusiveMillis > workTime)
    }
}
