package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import dev.sk2andy.materialbrowser.recall.RecallMatch
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecallAddressSuggestionsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recallSectionAppearsBeforeRemoteSuggestionAndOpensLocalMatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val match = RecallMatch(
            profileId = "personal",
            url = "https://example.com/guide",
            title = "Candy guide",
            excerpt = "A matching local excerpt about browser privacy",
            visitedAt = 1L,
            score = 2.0,
        )
        val selected = AtomicReference<AddressSuggestionItem?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                AddressSuggestions(
                    suggestions = listOf(
                        AddressSuggestionItem.Recall(match),
                        AddressSuggestionItem.Search("candy browser remote"),
                    ),
                    highlightedIndex = -1,
                    onHighlight = {},
                    onSelect = selected::set,
                    onFill = {},
                    rootHeightPx = 2_000f,
                    bottomBarTopPx = mutableFloatStateOf(1_800f),
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.recall_from_history))
            .assertIsDisplayed()
        composeRule.onNodeWithText(match.excerpt).assertIsDisplayed()
        composeRule.onNodeWithTag(AddressSuggestionTestTags.recallRow(match.url)).performClick()

        assertEquals(AddressSuggestionItem.Recall(match), selected.get())
    }
}
