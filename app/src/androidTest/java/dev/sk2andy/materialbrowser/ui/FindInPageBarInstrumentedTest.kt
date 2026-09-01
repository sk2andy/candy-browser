package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FindInPageBarInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun autoFocusInputNavigationAndCloseAreExposed() {
        var query by mutableStateOf("")
        val nextCount = mutableIntStateOf(0)
        var closed by mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                FindInPageBar(
                    query = query,
                    onQueryChange = { query = it },
                    matchText = "1/3",
                    isCounting = false,
                    canNavigate = query.isNotEmpty(),
                    focusNonce = 1,
                    autoFocus = true,
                    placeholder = "Find",
                    queryContentDescription = "Query",
                    countingContentDescription = "Counting",
                    previousMatchContentDescription = "Previous",
                    nextMatchContentDescription = "Next",
                    closeContentDescription = "Close",
                    onPreviousMatch = {},
                    onNextMatch = { nextCount.intValue++ },
                    onClose = { closed = true },
                )
            }
        }

        composeRule.onNodeWithTag(FindInPageBarTestTags.Query)
            .assertIsFocused()
            .performTextInput("Candy")
        composeRule.onNodeWithTag(FindInPageBarTestTags.Next)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(FindInPageBarTestTags.Close).performClick()

        composeRule.runOnIdle {
            assertEquals("Candy", query)
            assertEquals(1, nextCount.intValue)
            assertTrue(closed)
        }
    }
}
