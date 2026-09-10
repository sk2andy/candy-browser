package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowsingFavoritesRules
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.data.FavoriteMutation
import dev.sk2andy.materialbrowser.data.FavoriteUndoRules
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchDeleteUndoAndOpenFavorite() {
        val alpha = favorite("https://alpha.example/", "Alpha")
        val beta = favorite("https://beta.example/guide", "Beta Guide")
        val opened = AtomicReference<FavoriteEntry?>()
        composeRule.setContent {
            var favorites by remember { mutableStateOf(listOf(alpha, beta)) }
            var revision by remember { mutableLongStateOf(0L) }
            MaterialBrowserTheme {
                FavoritesScreen(
                    favorites = favorites,
                    onDeleteFavorite = { target, onComplete ->
                        val before = favorites
                        val updated = BrowsingFavoritesRules.remove(before, target)
                        if (updated == before) {
                            onComplete(null)
                        } else {
                            favorites = updated
                            onComplete(
                                FavoriteMutation(
                                    before = before,
                                    applied = updated,
                                    added = false,
                                    revision = ++revision,
                                ),
                            )
                        }
                    },
                    onUndoDelete = { mutation ->
                        FavoriteUndoRules.restore(favorites, revision, mutation)?.let { restored ->
                            revision++
                            favorites = restored
                        }
                    },
                    onOpenFavorite = opened::set,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(alpha.title).assertIsDisplayed()
        composeRule.onNodeWithText(beta.title).assertIsDisplayed()
        composeRule.onNodeWithTag(FavoritesScreenTestTags.SearchField).performTextInput("beta")
        composeRule.onNodeWithText(alpha.title).assertDoesNotExist()
        composeRule.onNodeWithText(beta.title).assertIsDisplayed()

        composeRule.onNodeWithTag(FavoritesScreenTestTags.delete(beta.url)).performClick()
        composeRule.onNodeWithText(beta.title).assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.action_undo)).performClick()
        composeRule.onNodeWithText(beta.title).assertIsDisplayed().performClick()
        assertEquals(beta, opened.get())

        composeRule.onNodeWithTag(FavoritesScreenTestTags.SearchField).performTextClearance()
        composeRule.onNodeWithText(alpha.title).assertIsDisplayed()
    }

    private fun favorite(url: String, title: String) = FavoriteEntry(
        url = url,
        title = title,
        addedAt = System.currentTimeMillis(),
    )
}
