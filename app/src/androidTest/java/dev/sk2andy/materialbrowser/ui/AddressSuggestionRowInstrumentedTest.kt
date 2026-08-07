package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressSuggestionRowInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rowOpensWhileTrailingArrowOnlyFills() {
        val opens = AtomicInteger()
        val fills = AtomicInteger()
        val query = "candy browser"
        composeRule.setContent {
            MaterialBrowserTheme {
                SearchSuggestionRow(
                    query = query,
                    highlighted = false,
                    onHighlight = {},
                    onClick = opens::incrementAndGet,
                    onFill = fills::incrementAndGet,
                )
            }
        }

        composeRule.onNodeWithTag(AddressSuggestionTestTags.fillSearch(query))
            .assertHasClickAction()
            .performClick()

        assertEquals(0, opens.get())
        assertEquals(1, fills.get())

        composeRule.onNodeWithTag(AddressSuggestionTestTags.searchRow(query))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, opens.get())
        assertEquals(1, fills.get())
    }
}
