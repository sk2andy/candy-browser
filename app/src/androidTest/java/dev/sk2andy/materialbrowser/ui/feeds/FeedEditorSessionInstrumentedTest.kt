package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.feeds.FeedFolder
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedEditorSessionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun lateFolderSaveDoesNotCloseAnotherFolderDraft() {
        checkLateFolderSave(secondFolder = "b")
    }

    @Test
    fun lateFolderSaveDoesNotCloseReopenedSameFolderDraft() {
        checkLateFolderSave(secondFolder = "a")
    }

    private fun checkLateFolderSave(secondFolder: String) {
        val profileId = "editor-session-${UUID.randomUUID()}"
        val profiles = BrowserSessionStore(context)
        val savedProfiles = profiles.loadProfiles()
        profiles.saveProfiles(
            savedProfiles.first + BrowserProfile(profileId, "🍬"),
            savedProfiles.second,
        )
        try {
            val repository = FeedRepository.get(context, profileId)
            runBlocking { repository.load().getOrThrow() }
            val pending = mutableListOf<suspend () -> Result<Unit>>()
            composeRule.setContent {
                MaterialBrowserTheme {
                    FeedSourcesScreen(
                        state =
                            FeedState(
                                folders =
                                    listOf(FeedFolder("a", "Folder A"), FeedFolder("b", "Folder B"))
                            ),
                        repository = repository,
                        mutate = { pending += it },
                        onAdd = {},
                        onOpenSource = {},
                        onNotificationPermission = {},
                    )
                }
            }
            composeRule.onNodeWithTag("feeds_folder_edit_a").performScrollTo().performClick()
            composeRule.onNodeWithTag("feeds_name_input").performTextReplacement("Submitted change")
            composeRule.onNodeWithText(context.getString(R.string.feeds_done)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.feeds_cancel)).performClick()

            composeRule
                .onNodeWithTag("feeds_folder_edit_$secondFolder")
                .performScrollTo()
                .performClick()
            composeRule
                .onNodeWithTag("feeds_name_input")
                .performTextReplacement("Keep this newer draft")
            // Complete the real repository mutation only after a new editor session is visible.
            runBlocking { pending.single().invoke().getOrThrow() }
            composeRule.waitForIdle()
            composeRule
                .onNodeWithTag("feeds_name_input")
                .assertTextContains("Keep this newer draft")
        } finally {
            runBlocking { FeedRepository.deleteProfile(context, profileId).getOrThrow() }
            profiles.saveProfiles(savedProfiles.first, savedProfiles.second)
        }
    }
}
