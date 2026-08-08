package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderExtractionResult
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import dev.sk2andy.materialbrowser.reader.ReaderLibraryRepository
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.After
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderStudioScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val store = ReaderLibraryStore(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )
    private val repository = ReaderLibraryRepository.get(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun articleShowsSemanticsControlsAndPrivateBoundary() {
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderStudioScreen(
                    result = ReaderExtractionResult.Success(document()),
                    sourceUrl = "https://example.com/article",
                    isPrivate = true,
                    repository = repository,
                    onRetry = {},
                    onDismiss = {},
                    onOpenOriginal = {},
                    onOpenLink = {},
                )
            }
        }

        composeRule.onNodeWithTag(ReaderStudioTestTags.Screen).assertExists()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertExists()
        composeRule.onAllNodesWithText("Reader test article").assertCountEquals(2)
        composeRule.onNodeWithTag(ReaderStudioTestTags.PrivateNotice).assertExists()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Save).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Library).assertDoesNotExist()
    }

    @Test
    fun controlsRemainDisplayedAtLargeFontScale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                MaterialBrowserTheme {
                    ReaderStudioScreen(
                        result = ReaderExtractionResult.Success(document()),
                        sourceUrl = "https://example.com/article",
                        isPrivate = false,
                        repository = repository,
                        onRetry = {},
                        onDismiss = {},
                        onOpenOriginal = {},
                        onOpenLink = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(ReaderStudioTestTags.Save).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.FontSegmented).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.AlignmentSegmented).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.ThemeSegmented).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.SpeechTransport).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.SpeechPlay).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.SpeechPause).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.SpeechStop).assertIsDisplayed()
        val controlCenters = listOf(
            ReaderStudioTestTags.FontSegmented,
            ReaderStudioTestTags.AlignmentSegmented,
            ReaderStudioTestTags.Save,
        ).map { tag ->
            composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center.y
        }
        assertTrue(controlCenters.max() - controlCenters.min() < 2f)
        composeRule.onNodeWithText(context.getString(R.string.reader_theme_paper)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_theme_night)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.reader_alignment_start),
        )
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.reader_alignment_justified),
        )
            .assertIsDisplayed()
            .performClick()
            .assertIsSelected()
    }

    private fun document() = ReaderDocument(
        title = "Reader test article",
        sourceUrl = "https://example.com/article",
        siteName = "Example",
        blocks = listOf(
            ReaderBlock(
                ReaderBlockKind.Heading,
                "A useful heading",
                level = 2,
            ),
            ReaderBlock(
                ReaderBlockKind.Paragraph,
                "A readable paragraph that remains local to the device and is rendered only as Compose text.",
            ),
        ),
    )
}
