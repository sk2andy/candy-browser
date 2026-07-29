package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import dev.sk2andy.materialbrowser.blocking.CandyRuleImport
import dev.sk2andy.materialbrowser.blocking.CandyRulePreview
import dev.sk2andy.materialbrowser.blocking.CandyFilterPresets
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterStudioScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun studioRendersRuleMetadataAndFiltersBySearch() {
        val rule = CandyRule.new(
            action = CandyRuleAction.Block,
            kind = CandyRuleKind.HostPair,
            requestHost = "tracker.example",
            firstPartyHost = "news.example",
            group = "Privacy X-Ray",
        ).copy(hitCount = 7)
        composeRule.setContent {
            MaterialBrowserTheme {
                FilterStudioScreen(
                    rules = listOf(rule),
                    profiles = listOf(BrowserProfile(id = "default", emoji = "🍬")),
                    currentProfileId = "default",
                    currentUrl = "https://news.example",
                    recentDomain = "tracker.example",
                    selectedRuleId = rule.id,
                    onTest = { null },
                    onAdd = { it },
                    onUpdate = { it },
                    onToggle = { _, _ -> },
                    onDelete = {},
                    onParseImport = { dev.sk2andy.materialbrowser.blocking.CandyRuleFormat.parse(it) },
                    onApplyImport = { 0 },
                    onApplySubscription = { _, _ -> 0 },
                    onExport = { "candy-rules:1" },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(FilterStudioTestTags.Screen).assertExists()
        composeRule.onNodeWithText("news.example → tracker.example").assertExists()
        composeRule.onNodeWithTag(FilterStudioTestTags.Body)
            .performScrollToNode(hasTestTag(FilterStudioTestTags.Search))
        composeRule.onNodeWithTag(FilterStudioTestTags.Search).performTextInput("missing")
        composeRule.onNodeWithText("news.example → tracker.example").assertDoesNotExist()
    }

    @Test
    fun emptyStudioExplainsPrimaryAndAdvancedActions() {
        composeRule.setContent {
            MaterialBrowserTheme {
                FilterStudioScreen(
                    rules = emptyList(),
                    profiles = listOf(BrowserProfile(id = "default", emoji = "🍬")),
                    currentProfileId = "default",
                    currentUrl = "https://news.example",
                    recentDomain = null,
                    selectedRuleId = null,
                    onTest = { null },
                    onAdd = { it },
                    onUpdate = { it },
                    onToggle = { _, _ -> },
                    onDelete = {},
                    onParseImport = { dev.sk2andy.materialbrowser.blocking.CandyRuleFormat.parse(it) },
                    onApplyImport = { 0 },
                    onApplySubscription = { _, _ -> 0 },
                    onExport = { "candy-rules:1" },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(FilterStudioTestTags.Add).assertExists()
        composeRule.onNodeWithContentDescription("Import").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Export").assertDoesNotExist()
        composeRule.onNodeWithTag(FilterStudioTestTags.Body)
            .performScrollToNode(hasTestTag(FilterStudioTestTags.BuiltInProtection))
        composeRule.onNodeWithTag(FilterStudioTestTags.BuiltInProtection).assertExists()
        composeRule.onNodeWithTag(FilterStudioTestTags.Body)
            .performScrollToNode(hasTestTag(FilterStudioTestTags.WebList))
        composeRule.onNodeWithTag(FilterStudioTestTags.WebList).assertExists()
        composeRule.onNodeWithTag(FilterStudioTestTags.Body)
            .performScrollToNode(hasTestTag(FilterStudioTestTags.EmptyState))
        composeRule.onNodeWithTag(FilterStudioTestTags.EmptyState).assertExists()
    }

    @Test
    fun officialUblockPresetRequiresExplicitFetchAndScope() {
        composeRule.setContent {
            MaterialBrowserTheme {
                FilterStudioScreen(
                    rules = emptyList(),
                    profiles = listOf(
                        BrowserProfile(id = "default", emoji = "🍬"),
                        BrowserProfile(id = "work", emoji = "💼"),
                    ),
                    currentProfileId = "default",
                    isIncognito = true,
                    currentUrl = "https://news.example",
                    recentDomain = null,
                    selectedRuleId = null,
                    onTest = { null },
                    onAdd = { it },
                    onUpdate = { it },
                    onToggle = { _, _ -> },
                    onDelete = {},
                    onParseImport = CandyRuleImport::parse,
                    onApplyImport = { 0 },
                    onApplySubscription = { _, _ -> 0 },
                    onExport = { "candy-rules:1" },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(FilterStudioTestTags.Body)
            .performScrollToNode(hasTestTag(FilterStudioTestTags.WebList))
        composeRule.onNodeWithTag(FilterStudioTestTags.WebList).performClick()
        composeRule.onNodeWithTag(FilterStudioTestTags.SubscriptionPrivateNote).assertExists()
        composeRule.onNodeWithTag(
            FilterStudioTestTags.SubscriptionPreset,
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNodeWithTag(FilterStudioTestTags.SubscriptionSource)
            .assertTextContains(CandyFilterPresets.UBLOCK_ORIGIN_BASE_URL)
        composeRule.onNodeWithTag(FilterStudioTestTags.SubscriptionScopeCurrent)
            .assertIsSelected()
        composeRule.onNodeWithTag(FilterStudioTestTags.SubscriptionScopeGlobal)
            .performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag(FilterStudioTestTags.SubscriptionConfirm)
            .assertIsNotEnabled()
    }

    @Test
    fun cosmeticRuleExplainsEffectWebsiteAndSelector() {
        composeRule.setContent {
            MaterialBrowserTheme {
                FilterStudioScreen(
                    rules = emptyList(),
                    profiles = listOf(BrowserProfile(id = "default", emoji = "🍬")),
                    currentProfileId = "default",
                    currentUrl = "https://news.example",
                    recentDomain = null,
                    selectedRuleId = null,
                    onTest = { null },
                    onAdd = { it },
                    onUpdate = { it },
                    onToggle = { _, _ -> },
                    onDelete = {},
                    onParseImport = CandyRuleImport::parse,
                    onApplyImport = { 0 },
                    onApplySubscription = { _, _ -> 0 },
                    onExport = { "candy-rules:1" },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(FilterStudioTestTags.Add).performClick()
        composeRule.onNodeWithTag(FilterStudioTestTags.CssAction).performClick()
        composeRule.onNodeWithTag(FilterStudioTestTags.CssExplanation).assertExists()
        composeRule.onNodeWithTag(FilterStudioTestTags.CssSite).assertExists()
        composeRule.onNodeWithTag(FilterStudioTestTags.CssSelector).assertExists()
    }

    @Test
    fun adblockImportRequiresScopeAndSkippedSyntaxConfirmation() {
        val applied = AtomicReference<CandyRulePreview?>()
        composeRule.setContent {
            MaterialBrowserTheme {
                FilterStudioScreen(
                    rules = emptyList(),
                    profiles = listOf(
                        BrowserProfile(id = "default", emoji = "🍬"),
                        BrowserProfile(id = "work", emoji = "💼"),
                    ),
                    currentProfileId = "default",
                    currentUrl = "https://news.example",
                    recentDomain = null,
                    selectedRuleId = null,
                    onTest = { null },
                    onAdd = { it },
                    onUpdate = { it },
                    onToggle = { _, _ -> },
                    onDelete = {},
                    onParseImport = CandyRuleImport::parse,
                    onApplyImport = {
                        applied.set(it)
                        it.rules.size
                    },
                    onApplySubscription = { _, _ -> 0 },
                    onExport = { "candy-rules:1" },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(FilterStudioTestTags.Body)
            .performScrollToNode(hasTestTag(FilterStudioTestTags.ImportOpen))
        composeRule.onNodeWithTag(FilterStudioTestTags.ImportOpen).performClick()
        composeRule.onNodeWithTag(FilterStudioTestTags.ImportInput)
            .performTextInput("||ads.example^\n/path/*")
        composeRule.onNodeWithTag(FilterStudioTestTags.ImportAnalyze).performClick()
        composeRule.onNodeWithTag(FilterStudioTestTags.ImportScopeCurrent).assertIsSelected()
        composeRule.onNodeWithTag(FilterStudioTestTags.ImportConfirm).assertIsNotEnabled()
        composeRule.onNodeWithTag(FilterStudioTestTags.ImportScopeGlobal).performClick()
            .assertIsSelected()
        composeRule.onNodeWithTag(FilterStudioTestTags.ImportSkippedConfirm).performClick()
        composeRule.onNodeWithTag(FilterStudioTestTags.ImportConfirm)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertNull(applied.get()?.rules?.single()?.profileId)
        }
    }
}
