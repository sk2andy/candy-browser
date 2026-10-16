package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.constellations.ConstellationDocument
import dev.sk2andy.materialbrowser.constellations.ConstellationSnapshot
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConstellationScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun profileOptInAndModelDownloadRequireSeparateExplicitActions() {
        val snapshot = mutableStateOf(ConstellationSnapshot(profileId = "personal"))
        var enabled = 0
        var downloads = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                ConstellationScreen(
                    profileName = "Personal",
                    snapshot = snapshot.value,
                    onEnable = { enabled++ },
                    onDisable = {},
                    onDownloadModel = { downloads++ },
                    onOpenPage = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(ConstellationTestTags.Enable).performClick()
        composeRule.runOnIdle {
            assertEquals(1, enabled)
            assertEquals(0, downloads)
            snapshot.value = snapshot.value.copy(enabled = true)
        }
        composeRule.onNodeWithTag(ConstellationTestTags.Download).performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(1, downloads) }
    }

    @Test
    fun pageListOpensOriginalAndProfileSwitchRemovesOldDetails() {
        val personal = page("personal", "Local AI", "https://local.example")
        val other = page("work", "Work secret", "https://secret.example")
        val snapshot = mutableStateOf(
            ConstellationSnapshot(
                profileId = "personal",
                enabled = true,
                modelReady = true,
                documents = listOf(personal, other),
            ),
        )
        var opened: String? = null
        composeRule.setContent {
            MaterialBrowserTheme {
                ConstellationScreen(
                    profileName = snapshot.value.profileId,
                    snapshot = snapshot.value,
                    onEnable = {},
                    onDisable = {},
                    onDownloadModel = {},
                    onOpenPage = { opened = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ConstellationTestTags.Graph))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Work secret").assertDoesNotExist()
        composeRule.onNodeWithTag(ConstellationTestTags.Body)
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(ConstellationTestTags.page(personal.url)))
        composeRule.onNodeWithTag(ConstellationTestTags.page(personal.url)).performClick()
        composeRule.onNodeWithTag(ConstellationTestTags.OpenPage).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(personal.url, opened) }

        composeRule.runOnIdle {
            snapshot.value = ConstellationSnapshot(profileId = "work", enabled = true, modelReady = true)
        }
        composeRule.onNodeWithTag(ConstellationTestTags.OpenPage).assertDoesNotExist()
        composeRule.onNodeWithText("Local AI").assertDoesNotExist()
        val emptyMessage = composeRule.activity.getString(R.string.constellations_empty)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(emptyMessage)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(emptyMessage).assertIsDisplayed()
    }

    @Test
    fun failedOptInIsVisibleWhileProfileRemainsDisabled() {
        composeRule.setContent {
            MaterialBrowserTheme {
                ConstellationScreen(
                    profileName = "Personal",
                    snapshot = ConstellationSnapshot(profileId = "personal", error = true),
                    onEnable = {},
                    onDisable = {},
                    onDownloadModel = {},
                    onOpenPage = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.constellations_error))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ConstellationTestTags.Enable).assertIsDisplayed()
    }

    @Test
    fun localSearchNavigatesMatchesWithoutOpeningPageAndResetsForAnotherProfile() {
        val first = page("personal", "Café local", "https://a.example")
        val second = page("personal", "Café privé", "https://b.example")
        val foreign = page("work", "Café work secret", "https://work.example")
        val snapshot = mutableStateOf(
            ConstellationSnapshot(
                profileId = "personal",
                enabled = true,
                modelReady = true,
                documents = listOf(first, second, foreign),
            ),
        )
        composeRule.setContent {
            MaterialBrowserTheme {
                ConstellationScreen(
                    profileName = snapshot.value.profileId,
                    snapshot = snapshot.value,
                    onEnable = {},
                    onDisable = {},
                    onDownloadModel = {},
                    onOpenPage = {},
                    onDismiss = {},
                )
            }
        }
        waitForSearchField()
        composeRule.onNodeWithTag(ConstellationTestTags.Search)
            .performScrollTo().performTextReplacement("CAFE")
        composeRule.onNodeWithTag(ConstellationTestTags.Search).performImeAction()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.constellations_search_position, 1, 2))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ConstellationTestTags.SearchFocus).assertTextEquals("Café local")
        composeRule.onNodeWithTag(ConstellationTestTags.SearchNext).performScrollTo().performClick()
        composeRule.onNodeWithTag(ConstellationTestTags.SearchFocus).assertTextEquals("Café privé")
        composeRule.onNodeWithTag(ConstellationTestTags.SearchPrevious).performClick()
        composeRule.onNodeWithTag(ConstellationTestTags.SearchFocus).assertTextEquals("Café local")
        composeRule.onNodeWithTag(ConstellationTestTags.OpenPage).assertDoesNotExist()
        composeRule.onNodeWithText("Café work secret").assertDoesNotExist()

        composeRule.onNodeWithTag(ConstellationTestTags.Search).performScrollTo()
        composeRule.onNodeWithTag(ConstellationTestTags.SearchClear).performClick()
        assertSearchQuery("")
        composeRule.onNodeWithTag(ConstellationTestTags.SearchFocus).assertDoesNotExist()
        composeRule.onNodeWithTag(ConstellationTestTags.Search)
            .performTextReplacement("never matches")
        composeRule.onNodeWithTag(ConstellationTestTags.Search).performImeAction()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.constellations_search_no_results))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ConstellationTestTags.Body)
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(ConstellationTestTags.Graph))
        composeRule.onNodeWithTag(ConstellationTestTags.Graph).assertIsDisplayed()

        composeRule.runOnIdle {
            snapshot.value = ConstellationSnapshot(
                profileId = "work", enabled = true, modelReady = true, documents = listOf(foreign),
            )
        }
        waitForSearchField()
        assertSearchQuery("")
        composeRule.onNodeWithTag(ConstellationTestTags.SearchFocus).assertDoesNotExist()
        composeRule.onNodeWithText("Café local").assertDoesNotExist()
    }

    private fun waitForSearchField() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(ConstellationTestTags.Search))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSearchQuery(query: String) {
        composeRule.onNodeWithTag(ConstellationTestTags.Search).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(query)),
        )
    }

    private fun page(profileId: String, title: String, url: String) = ConstellationDocument(
        profileId = profileId,
        url = url,
        title = title,
        excerpt = "Saved article text",
        visitedAt = 1L,
        embedding = listOf(1f, 0f),
    )
}
