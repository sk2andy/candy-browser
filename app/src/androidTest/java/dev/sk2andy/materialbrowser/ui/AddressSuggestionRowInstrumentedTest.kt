package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.commands.AddressSuggestionItem
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun searchSelectionReportsBoundsWhileFillOnlyFills() {
        val suggestion = AddressSuggestionItem.Search("candy browser")
        val selected = AtomicReference<AddressSuggestionItem?>()
        val filled = AtomicReference<AddressSuggestionItem?>()
        val bounds = AtomicReference<Rect?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                val bottomBarTopPx = remember { mutableFloatStateOf(1_800f) }
                Box(Modifier.fillMaxSize()) {
                    AddressSuggestions(
                        suggestions = listOf(suggestion),
                        highlightedIndex = -1,
                        onHighlight = {},
                        onSelect = selected::set,
                        onFill = filled::set,
                        rootHeightPx = 2_000f,
                        bottomBarTopPx = bottomBarTopPx,
                        animationsEnabled = false,
                        onSelectionBounds = { item, rect ->
                            assertEquals(suggestion, item)
                            bounds.set(rect)
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressSuggestionTestTags.searchRow(suggestion.query))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(AddressSuggestionTestTags.fillSearch(suggestion.query))
            .performClick()
        assertEquals(suggestion, filled.get())
        assertNull(selected.get())
        assertNull(bounds.get())

        composeRule.onNodeWithTag(AddressSuggestionTestTags.searchRow(suggestion.query))
            .performClick()
        assertEquals(suggestion, selected.get())
        assertTrue(bounds.get()?.isEmpty == false)
    }

    @Test
    fun launchEffectFinishesAfterAnimating() {
        val finished = AtomicInteger()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialBrowserTheme {
                AddressSuggestionLaunchEffect(
                    origin = Rect(20f, 100f, 320f, 160f),
                    onFinished = { finished.incrementAndGet() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(0, finished.get()) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.runOnIdle { assertEquals(1, finished.get()) }
    }
}
