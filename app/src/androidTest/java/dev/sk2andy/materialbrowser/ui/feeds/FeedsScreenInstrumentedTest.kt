package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.feeds.FeedArticle
import dev.sk2andy.materialbrowser.feeds.FeedImportEntry
import dev.sk2andy.materialbrowser.feeds.FeedImportPreview
import dev.sk2andy.materialbrowser.feeds.FeedImportStatus
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.feeds.FeedState
import dev.sk2andy.materialbrowser.feeds.FeedStore
import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedsScreenInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun directLibraryShowsSavedWebsitesWithoutFeedNavigation() {
        val snapshot = snapshot("website", "A saved website", "profile")
        var opened: ReaderSnapshot? = null
        val repository = FeedRepository.get(context, "ui-test-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedsScreen(
                    repository = repository,
                    library = ReaderLibraryState(snapshots = listOf(snapshot)),
                    libraryOnly = true,
                    onOpenArticle = {},
                    onOpenSnapshot = { opened = it },
                    onOpenOriginal = {},
                    onDownloadArticle = {},
                    onRemoveSnapshot = {},
                    onPlayQueue = {},
                    onImportOpml = {},
                    onExportOpml = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("feed_snapshot_website").performClick()
        composeRule.onNodeWithText(context.getString(R.string.feeds_inbox)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.feeds_sources)).assertDoesNotExist()
        assertEquals(snapshot, opened)
    }

    @Test
    fun libraryTabsAndScrollableFiltersRemainIndependent() {
        val snapshot = snapshot("website", "A saved website", "profile")
        val repository = FeedRepository.get(context, "ui-test-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedsScreen(
                    repository = repository,
                    library = ReaderLibraryState(snapshots = listOf(snapshot)),
                    libraryOnly = true,
                    onOpenArticle = {},
                    onOpenSnapshot = {},
                    onOpenOriginal = {},
                    onDownloadArticle = {},
                    onRemoveSnapshot = {},
                    onPlayQueue = {},
                    onImportOpml = {},
                    onExportOpml = {},
                    onDismiss = {},
                )
            }
        }
        val all = context.getString(R.string.feeds_all)
        val offline = context.getString(R.string.feeds_offline)
        val saved = context.getString(R.string.feeds_starred)
        val filterAll = hasText(all) and hasAnyAncestor(hasTestTag("feeds_library_filters"))
        val filterOffline = hasText(offline) and hasAnyAncestor(hasTestTag("feeds_library_filters"))
        composeRule.onAllNodesWithText(all).assertCountEquals(2)
        composeRule.onNodeWithTag("feeds_library_origin_all").assertIsSelected()
        val rowY = composeRule.onNode(filterAll).fetchSemanticsNode().boundsInRoot.center.y
        composeRule.onNode(filterOffline).performClick().assertIsSelected()
        composeRule.onNodeWithText(saved).performScrollTo()
        assertEquals(
            rowY,
            composeRule.onNodeWithText(saved).fetchSemanticsNode().boundsInRoot.center.y,
            0.5f,
        )
        composeRule.onNodeWithTag("feeds_library_origin_feed").performClick().assertIsSelected()
        composeRule.onNodeWithTag("feed_snapshot_website").assertDoesNotExist()
        composeRule.onNode(filterAll).performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag("feeds_library_origin_feed").assertIsSelected()
        composeRule.onNodeWithTag("feed_snapshot_website").assertDoesNotExist()
        composeRule.onNode(filterOffline).performScrollTo().performClick()
        composeRule.onNodeWithTag("feeds_library_origin_web").performClick().assertIsSelected()
        composeRule.onNode(filterOffline).assertIsSelected()
        composeRule.onNodeWithTag("feed_snapshot_website").assertExists()
        composeRule.onNodeWithTag("feeds_library_origin_all").performClick().assertIsSelected()
        composeRule.onNode(filterAll).performScrollTo().performClick().assertIsSelected()
        composeRule
            .onNodeWithText(context.getString(R.string.feeds_download_saved))
            .assertDoesNotExist()

        composeRule.onNodeWithTag("feeds_search").performTextInput("missing title")
        composeRule.onNodeWithTag("feed_snapshot_website").assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.feeds_clear_search))
            .performClick()
        composeRule.onNodeWithTag("feed_snapshot_website").assertExists()
    }

    @Test
    fun inboxTagsShareScrollableRowWithStatusChips() {
        val profileId = "ui-test-${UUID.randomUUID()}"
        val profiles = BrowserSessionStore(context)
        val savedProfiles = profiles.loadProfiles()
        profiles.saveProfiles(
            savedProfiles.first + BrowserProfile(profileId, "🍬"),
            savedProfiles.second,
        )
        try {
            FeedStore(context, profileId)
                .write(
                    FeedState(
                        articles =
                            listOf(
                                FeedArticle(
                                    "tagged",
                                    emptySet(),
                                    "https://example.org/tagged",
                                    "Tagged article",
                                    tags = setOf("Architecture", "Design", "Technology"),
                                ),
                                FeedArticle(
                                    "other",
                                    emptySet(),
                                    "https://example.org/other",
                                    "Other article",
                                ),
                            )
                    )
                )
            val repository = FeedRepository.get(context, profileId)
            runBlocking { repository.load().getOrThrow() }
            composeRule.setContent {
                MaterialBrowserTheme {
                    FeedsScreen(
                        repository = repository,
                        library = ReaderLibraryState(),
                        onOpenArticle = {},
                        onOpenSnapshot = {},
                        onOpenOriginal = {},
                        onDownloadArticle = {},
                        onRemoveSnapshot = {},
                        onPlayQueue = {},
                        onImportOpml = {},
                        onExportOpml = {},
                        onDismiss = {},
                    )
                }
            }
            val unread = context.getString(R.string.feeds_unread)
            val rowY = composeRule.onNodeWithText(unread).fetchSemanticsNode().boundsInRoot.center.y
            composeRule
                .onNodeWithText("Technology")
                .performScrollTo()
                .performClick()
                .assertIsSelected()
            assertEquals(
                rowY,
                composeRule.onNodeWithText("Technology").fetchSemanticsNode().boundsInRoot.center.y,
                0.5f,
            )
            composeRule.onNodeWithText("Other article").assertDoesNotExist()
            composeRule.onNodeWithText("Tagged article").assertExists()
            val all = context.getString(R.string.feeds_all)
            composeRule.onAllNodesWithText(all).assertCountEquals(1)
            composeRule.onNodeWithText(all).performScrollTo().performClick().assertIsSelected()
            composeRule.onNodeWithText("Other article").assertExists()
        } finally {
            runBlocking { FeedRepository.deleteProfile(context, profileId).getOrThrow() }
            profiles.saveProfiles(savedProfiles.first, savedProfiles.second)
        }
    }

    @Test
    fun legacySnapshotsRequireExplicitExpansion() {
        val legacy = snapshot("legacy", "An earlier Reader save", null)
        val repository = FeedRepository.get(context, "ui-test-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedsScreen(
                    repository = repository,
                    library = ReaderLibraryState(),
                    libraryOnly = true,
                    legacySnapshots = listOf(legacy),
                    onOpenArticle = {},
                    onOpenSnapshot = {},
                    onOpenOriginal = {},
                    onDownloadArticle = {},
                    onRemoveSnapshot = {},
                    onPlayQueue = {},
                    onImportOpml = {},
                    onExportOpml = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithTag("feed_snapshot_legacy").assertDoesNotExist()
        composeRule.onNodeWithTag("feeds_legacy_toggle").performClick()
        composeRule.onNodeWithTag("feed_snapshot_legacy").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.feeds_legacy_note)).assertExists()
    }

    @Test
    fun importWithoutNewSubscriptionsCannotBeConfirmed() {
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedImportDialog(
                    preview =
                        FeedImportPreview(
                            listOf(
                                FeedImportEntry(
                                    "Existing",
                                    "https://example.com/feed",
                                    null,
                                    FeedImportStatus.Existing,
                                ),
                                FeedImportEntry(
                                    "Invalid",
                                    "file:///private",
                                    null,
                                    FeedImportStatus.Invalid,
                                ),
                            )
                        ),
                    onConfirm = { error("Import must be disabled") },
                    onDismiss = {},
                )
            }
        }
        composeRule
            .onNodeWithText(context.getString(R.string.feeds_import_confirm))
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.feeds_import_existing)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.feeds_import_invalid)).assertExists()
    }

    private fun snapshot(id: String, title: String, profile: String?): ReaderSnapshot =
        ReaderSnapshot(
            id = id,
            document =
                ReaderDocument(
                    title,
                    "https://example.com/$id",
                    "Example",
                    listOf(ReaderBlock(ReaderBlockKind.Paragraph, "Saved article text.")),
                ),
            progress = 0.3f,
            savedAtMillis = 1L,
            sourceProfileId = profile,
        )
}
