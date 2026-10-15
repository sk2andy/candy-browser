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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertFalse
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
import dev.sk2andy.materialbrowser.reader.ReaderSpeechController
import dev.sk2andy.materialbrowser.shared.ui.ReaderStudioScreen as SharedReaderStudioScreen
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
        composeRule.onNodeWithTag(ReaderStudioTestTags.Back).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertExists()
        composeRule.onAllNodesWithText("Reader test article").assertCountEquals(1)
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
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

        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Save).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.FontSegmented).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.AlignmentSegmented).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.ThemeSegmented).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.SpeechTransport).performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithText(context.getString(R.string.reader_theme_paper)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reader_theme_night)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.reader_alignment_start),
        )
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.reader_alignment_justified),
        )
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
            .assertIsSelected()
    }

    @Test
    fun hiddenControlsBackAndSheetCancellationKeepArticleVisible() {
        var dismissed = false
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderStudioScreen(
                    result = ReaderExtractionResult.Success(document()),
                    sourceUrl = "https://example.com/article",
                    isPrivate = false,
                    repository = repository,
                    onRetry = {},
                    onDismiss = { dismissed = true },
                    onOpenOriginal = {},
                    onOpenLink = {},
                )
            }
        }
        val url = composeRule.onNodeWithTag(ReaderStudioTestTags.Url)
        assertFalse(url.fetchSemanticsNode().config.contains(SemanticsActions.SetText))
        url.performTouchInput { click() }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Sheet).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Toggle).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        Espresso.pressBack()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Sheet).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Hide).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).performTouchInput {
            click()
            swipeUp()
        }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertDoesNotExist()
        Espresso.pressBack()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Url).assertTextEquals("example.com")
        assertFalse(dismissed)
        Espresso.pressBack()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun articleScrollSwitchesBetweenDomainAndFullUrlWithSettingsAvailable() {
        val article = document().copy(blocks = List(50) { document().blocks.last() })
        composeRule.setContent {
            MaterialBrowserTheme {
                ReaderStudioScreen(
                    result = ReaderExtractionResult.Success(article),
                    sourceUrl = article.sourceUrl,
                    isPrivate = true,
                    repository = repository,
                    onRetry = {},
                    onDismiss = {},
                    onOpenOriginal = {},
                    onOpenLink = {},
                )
            }
        }
        val url = composeRule.onNodeWithTag(ReaderStudioTestTags.Url)
        url.assertTextEquals("https://example.com/article")
        composeRule.onNodeWithTag(ReaderStudioTestTags.Toggle).assertDoesNotExist()
        val expandedWidth = composeRule.onNodeWithTag(ReaderStudioTestTags.Pill)
            .fetchSemanticsNode().boundsInRoot.width
        repeat(2) {
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).performTouchInput { swipeUp() }
            url.assertTextEquals("example.com")
            val compactWidth = composeRule.onNodeWithTag(ReaderStudioTestTags.Pill)
                .fetchSemanticsNode().boundsInRoot.width
            assertTrue(compactWidth < expandedWidth)
            composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
            composeRule.onNodeWithTag(ReaderStudioTestTags.Sheet).assertIsDisplayed()
            Espresso.pressBack()
            url.assertTextEquals("example.com")
            composeRule.onNodeWithTag(ReaderStudioTestTags.Article).performTouchInput { swipeDown() }
            url.assertTextEquals("https://example.com/article")
        }
    }

    @Test
    fun sharedScreenWithoutPlatformBackRestoresHiddenControlsThenDismisses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var dismissed = false
        composeRule.setContent {
            MaterialBrowserTheme {
                SharedReaderStudioScreen(
                    result = ReaderExtractionResult.Success(document()),
                    sourceUrl = "https://example.com/article",
                    isPrivate = false,
                    repository = repository,
                    resources = AndroidReaderStudioResources,
                    speechFactory = { ReaderSpeechController(context) },
                    onRetry = {},
                    onDismiss = { dismissed = true },
                    onOpenOriginal = {},
                    onOpenLink = {},
                )
            }
        }

        val back = composeRule.onNodeWithTag(ReaderStudioTestTags.Back).assertIsDisplayed()
        val title = composeRule.onNodeWithTag(ReaderStudioTestTags.ArticleTitle)
        assertTrue(title.fetchSemanticsNode().boundsInRoot.top >= back.fetchSemanticsNode().boundsInRoot.bottom)
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Hide).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Back).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Pill).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Url).assertTextEquals("example.com")
        composeRule.runOnIdle { assertFalse(dismissed) }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Settings).performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Library).performScrollTo().performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertDoesNotExist()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Back).performClick()
        composeRule.onNodeWithTag(ReaderStudioTestTags.Article).assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(dismissed) }
        composeRule.onNodeWithTag(ReaderStudioTestTags.Back).performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
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
