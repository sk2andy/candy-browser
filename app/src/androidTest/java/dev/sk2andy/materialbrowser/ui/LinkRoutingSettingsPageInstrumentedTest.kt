package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingRule
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingTargetKind
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkRoutingSettingsPageInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addingReaderRuleRequiresDomainAndPersistsEnabledToggle() {
        val saved = AtomicReference<List<LinkRoutingRule>>(emptyList())
        composeRule.setContent {
            var rules by remember { mutableStateOf(emptyList<LinkRoutingRule>()) }
            MaterialTheme {
                LinkRoutingSettingsPage(
                    rules = rules,
                    profiles = listOf(BrowserProfile("personal", "🍬")),
                    capsules = emptyList(),
                    onRulesChanged = {
                        rules = it
                        saved.set(it)
                    },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(LinkRoutingTestTags.Add).performClick()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Save).assertIsNotEnabled()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Domain).performTextInput("news.example")
        composeRule.onNodeWithTag(LinkRoutingTestTags.Save).performClick()

        val rule = saved.get().single()
        assertEquals("news.example", rule.domain)
        assertEquals(LinkRoutingTargetKind.Reader, rule.target.kind)
        composeRule.onNodeWithTag(LinkRoutingTestTags.enabled(rule.id)).performClick()
        assertEquals(false, saved.get().single().enabled)
    }
}
