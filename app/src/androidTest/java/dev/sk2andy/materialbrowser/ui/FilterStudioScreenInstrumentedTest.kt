package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.blocking.CandyRule
import dev.sk2andy.materialbrowser.blocking.CandyRuleAction
import dev.sk2andy.materialbrowser.blocking.CandyRuleKind
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
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
        composeRule.onNodeWithTag(FilterStudioTestTags.Search).performTextInput("missing")
        composeRule.onNodeWithText("news.example → tracker.example").assertDoesNotExist()
    }
}
