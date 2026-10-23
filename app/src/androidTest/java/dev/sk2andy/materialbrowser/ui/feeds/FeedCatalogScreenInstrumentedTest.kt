package dev.sk2andy.materialbrowser.ui.feeds

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.feeds.FeedCatalogKind
import dev.sk2andy.materialbrowser.feeds.FeedCatalogPage
import dev.sk2andy.materialbrowser.feeds.FeedCatalogQuery
import dev.sk2andy.materialbrowser.feeds.FeedCatalogSource
import dev.sk2andy.materialbrowser.feeds.FeedRepository
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedCatalogScreenInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun allSixTopicsFitWithoutScrollingAtDefaultTextSize() {
        val repository = FeedRepository.get(context, "catalog-fit-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme { FeedCatalogScreen(repository, {}, {}, {}) }
        }
        val viewport =
            composeRule.onNodeWithTag("feed_catalog_results").fetchSemanticsNode().boundsInRoot
        feedCatalogTopics.forEach { topic ->
            val card =
                composeRule.onNodeWithTag("feed_catalog_topic_${topic.slug}").assertIsDisplayed()
            val bounds = card.fetchSemanticsNode().boundsInRoot
            assertTrue(
                "${topic.slug} must fit entirely without scrolling",
                bounds.top >= viewport.top && bounds.bottom <= viewport.bottom,
            )
        }
    }

    @Test
    fun androidLanguageChoicesHaveVisibleNamesAndExcludeTheRootLocale() {
        val choices = FeedCatalogLanguageRules.choices(java.util.Locale.GERMAN)
        assertTrue(choices.isNotEmpty())
        assertTrue(choices.all { it.label.isNotBlank() && it.nativeLabel.isNotBlank() })
        assertTrue(choices.none { java.util.Locale.forLanguageTag(it.code).language.isBlank() })
    }

    @Test
    fun typingDoesNotSearchAndPreviewUsesOriginalFeed() {
        val requests = mutableListOf<FeedCatalogQuery>()
        var opened = ""
        val repository = FeedRepository.get(context, "catalog-ui-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedCatalogScreen(
                    repository,
                    onPreview = { opened = it },
                    onManual = {},
                    onDismiss = {},
                    searchCatalog = { query ->
                        requests += query
                        Result.success(
                            FeedCatalogPage(listOf(source("one", "A source", "en")), null)
                        )
                    },
                )
            }
        }
        composeRule.onNodeWithTag("feed_catalog_search").performTextInput("art")
        composeRule.runOnIdle { assertEquals(0, requests.size) }
        composeRule.onNodeWithTag("feed_catalog_search").performImeAction()
        composeRule
            .onNodeWithTag("feed_catalog_results")
            .performScrollToNode(hasTestTag("feed_catalog_source_one"))
        composeRule.onNodeWithTag("feed_catalog_source_one").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(FeedCatalogQuery(text = "art")), requests)
            assertEquals("https://publisher.example/one/feed.xml", opened)
        }
    }

    @Test
    fun catalogOmitsUnsupportedMetadataFiltersAndManualEntryKeepsDraft() {
        val requests = mutableListOf<FeedCatalogQuery>()
        var manualOpened = false
        val repository = FeedRepository.get(context, "catalog-ui-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedCatalogScreen(
                    repository,
                    {},
                    { manualOpened = true },
                    {},
                    searchCatalog = { query ->
                        requests += query
                        Result.success(FeedCatalogPage(emptyList(), null))
                    },
                )
            }
        }
        composeRule.onNodeWithTag("feed_catalog_search").performTextInput("not submitted")
        composeRule.onNodeWithTag("feed_catalog_languages").assertDoesNotExist()
        composeRule.onNodeWithTag("feed_catalog_kind_Blog").assertDoesNotExist()
        composeRule.onNodeWithTag("feed_catalog_manual").performClick()
        composeRule.onNodeWithTag("feed_catalog_search").assertTextContains("not submitted")
        composeRule.runOnIdle {
            assertEquals(0, requests.size)
            assertTrue(manualOpened)
        }
    }

    @Test
    fun privateCatalogHasNoSearchOrNetworkAndKeepsManualEntry() {
        var searches = 0
        var manual = false
        val repository =
            FeedRepository.get(context, "catalog-ui-${UUID.randomUUID()}", isPrivate = true)
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedCatalogScreen(
                    repository,
                    {},
                    { manual = true },
                    {},
                    searchCatalog = {
                        searches++
                        Result.success(FeedCatalogPage(emptyList(), null))
                    },
                )
            }
        }
        composeRule.onNodeWithTag("feed_catalog_search").assertDoesNotExist()
        composeRule.onNodeWithTag("feed_catalog_screen").assertIsDisplayed()
        composeRule
            .onNodeWithTag("feed_catalog_results")
            .performScrollToNode(hasTestTag("feed_catalog_manual"))
        composeRule.onNodeWithTag("feed_catalog_manual").performClick()
        composeRule.runOnIdle {
            assertEquals(0, searches)
            assertEquals(true, manual)
        }
    }

    @Test
    fun retryKeepsTheSameSubmittedQuery() {
        val requests = mutableListOf<FeedCatalogQuery>()
        val repository = FeedRepository.get(context, "catalog-ui-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedCatalogScreen(
                    repository,
                    {},
                    {},
                    {},
                    searchCatalog = { query ->
                        requests += query
                        if (requests.size == 1) Result.failure(java.io.IOException())
                        else
                            Result.success(
                                FeedCatalogPage(listOf(source("two", "A fresh voice", "ar")), null)
                            )
                    },
                )
            }
        }
        composeRule.onNodeWithTag("feed_catalog_search").performTextInput("science")
        composeRule.onNodeWithTag("feed_catalog_submit").performClick()
        composeRule
            .onNodeWithTag("feed_catalog_results")
            .performScrollToNode(hasTestTag("feed_catalog_retry"))
        composeRule.onNodeWithTag("feed_catalog_retry").performClick()
        composeRule
            .onNodeWithTag("feed_catalog_results")
            .performScrollToNode(hasTestTag("feed_catalog_source_two"))
        composeRule.runOnIdle {
            assertEquals(
                listOf(FeedCatalogQuery(text = "science"), FeedCatalogQuery(text = "science")),
                requests,
            )
        }
    }

    @Test
    fun duplicateOnlyPageKeepsAdvancingProviderCursor() {
        val requests = mutableListOf<FeedCatalogQuery>()
        val repository = FeedRepository.get(context, "catalog-ui-${UUID.randomUUID()}")
        composeRule.setContent {
            MaterialBrowserTheme {
                FeedCatalogScreen(
                    repository,
                    {},
                    {},
                    {},
                    searchCatalog = { query ->
                        requests += query
                        Result.success(
                            when (query.offset) {
                                0 ->
                                    FeedCatalogPage(listOf(source("one", "First source", "en")), 16)
                                16 ->
                                    FeedCatalogPage(listOf(source("one", "First source", "en")), 32)
                                else ->
                                    FeedCatalogPage(
                                        listOf(source("two", "Later source", "ja")),
                                        null,
                                    )
                            }
                        )
                    },
                )
            }
        }
        composeRule
            .onNodeWithTag("feed_catalog_results")
            .performScrollToNode(hasTestTag("feed_catalog_topic_science"))
        composeRule.onNodeWithTag("feed_catalog_topic_science").performClick()
        repeat(2) {
            composeRule
                .onNodeWithTag("feed_catalog_results")
                .performScrollToNode(hasTestTag("feed_catalog_more"))
            composeRule.onNodeWithTag("feed_catalog_more").performClick()
            composeRule.waitForIdle()
        }
        composeRule
            .onNodeWithTag("feed_catalog_results")
            .performScrollToNode(hasTestTag("feed_catalog_source_two"))
        composeRule.runOnIdle { assertEquals(listOf(0, 16, 32), requests.map { it.offset }) }
    }

    @Test
    fun recreationRestoresSubmittedTopicWithoutSubmittingDraft() {
        val restoration = StateRestorationTester(composeRule)
        val requests = mutableListOf<FeedCatalogQuery>()
        val repository = FeedRepository.get(context, "catalog-ui-${UUID.randomUUID()}")
        restoration.setContent {
            MaterialBrowserTheme {
                FeedCatalogScreen(
                    repository,
                    {},
                    {},
                    {},
                    searchCatalog = { query ->
                        requests += query
                        Result.success(
                            FeedCatalogPage(listOf(source("japanese", "日本語の科学", "ja")), null)
                        )
                    },
                )
            }
        }
        composeRule
            .onNodeWithTag("feed_catalog_results")
            .performScrollToNode(hasTestTag("feed_catalog_topic_science"))
        composeRule.onNodeWithTag("feed_catalog_topic_science").performClick()
        composeRule.onNodeWithTag("feed_catalog_search").performTextInput("not submitted")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitUntil { requests.size == 2 }
        composeRule.onNodeWithTag("feed_catalog_search").assertTextContains("not submitted")
        composeRule
            .onNodeWithTag("feed_catalog_results")
            .performScrollToNode(hasTestTag("feed_catalog_source_japanese"))
        composeRule.onNodeWithTag("feed_catalog_source_japanese").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(FeedCatalogQuery(topic = "science"), requests.last()) }
    }

    @Test
    fun recreationWithOnlyTypedInputDoesNotStartSearch() {
        val restoration = StateRestorationTester(composeRule)
        var requests = 0
        val repository = FeedRepository.get(context, "catalog-ui-${UUID.randomUUID()}")
        restoration.setContent {
            MaterialBrowserTheme {
                FeedCatalogScreen(
                    repository,
                    {},
                    {},
                    {},
                    searchCatalog = {
                        requests++
                        Result.success(FeedCatalogPage(emptyList(), null))
                    },
                )
            }
        }
        composeRule.onNodeWithTag("feed_catalog_search").performTextInput("still a draft")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("feed_catalog_search").assertTextContains("still a draft")
        composeRule.runOnIdle { assertEquals(0, requests) }
    }

    private fun source(slug: String, title: String, language: String) =
        FeedCatalogSource(
            slug,
            title,
            "Independent voices",
            "https://publisher.example/",
            "https://publisher.example/$slug/feed.xml",
            language,
            FeedCatalogKind.Blog,
        )
}
