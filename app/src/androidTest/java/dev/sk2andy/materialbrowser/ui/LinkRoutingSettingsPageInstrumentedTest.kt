package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingRule
import dev.sk2andy.materialbrowser.browser.integration.linkrouting.LinkRoutingTargetKind
import dev.sk2andy.materialbrowser.capsule.SiteCapsule
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

        composeRule.onNodeWithTag(LinkRoutingTestTags.Add).performScrollTo().performClick()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Save).assertIsNotEnabled()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Domain).performTextInput("news.example")
        composeRule.onNodeWithTag(LinkRoutingTestTags.Save).performClick()

        val rule = saved.get().single()
        assertEquals("news.example", rule.domain)
        assertEquals(LinkRoutingTargetKind.Reader, rule.target.kind)
        composeRule.onNodeWithTag(LinkRoutingTestTags.enabled(rule.id))
            .performScrollTo()
            .assertContentDescriptionEquals("news.example")
            .assertIsOn()
            .performClick()
            .assertIsOff()
        assertEquals(false, saved.get().single().enabled)
    }

    @Test
    fun profileAndCapsuleRadiosKeepEligibleChoicesAndReselectingKindKeepsTarget() {
        val saved = AtomicReference<List<LinkRoutingRule>>(emptyList())
        val profiles = listOf(
            BrowserProfile("personal", "🍬"),
            BrowserProfile("work", "💼"),
            BrowserProfile("locked", "🔒", protection = ProfileProtection(ProfileLockTrigger.AppClosed)),
            BrowserProfile("synced", "☁️", syncedDeviceId = "remote"),
        )
        fun capsule(id: String, name: String, profileId: String) = SiteCapsule(
            id = id, name = name, startUrl = "https://example.com", profileId = profileId,
            createdAtMillis = 0, updatedAtMillis = 0,
        )
        composeRule.setContent {
            var rules by remember { mutableStateOf(emptyList<LinkRoutingRule>()) }
            MaterialTheme {
                LinkRoutingSettingsPage(
                    rules = rules,
                    profiles = profiles,
                    capsules = listOf(
                        capsule("news", "News capsule", "personal"),
                        capsule("work-news", "Work capsule", "work"),
                        capsule("locked-news", "Locked capsule", "locked"),
                    ),
                    onRulesChanged = {
                        rules = it
                        saved.set(it)
                    },
                    onBack = {},
                )
            }
        }
        val profileLabel = composeRule.activity.getString(R.string.link_routing_profile)
        val capsuleLabel = composeRule.activity.getString(R.string.link_routing_capsule)
        val editLabel = composeRule.activity.getString(R.string.link_routing_edit)
        composeRule.onNodeWithTag(LinkRoutingTestTags.Add).performScrollTo().performClick()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Domain).performTextInput("news.example")
        dialogChoice(profileLabel).performScrollTo().performClick()
        dialogChoice("💼").performScrollTo().performClick().assertIsSelected()
        dialogChoice(profileLabel).performScrollTo().performClick()
        dialogChoice("💼").performScrollTo().assertIsSelected()
        dialogChoice("🔒").assertDoesNotExist()
        dialogChoice("☁️").assertDoesNotExist()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Save).performClick()
        val firstRule = saved.get().single()
        assertEquals(LinkRoutingTargetKind.Profile, firstRule.target.kind)
        assertEquals("work", firstRule.target.id)

        composeRule.onNodeWithContentDescription(editLabel).performScrollTo().performClick()
        dialogChoice("💼").performScrollTo().assertIsSelected()
        dialogChoice(capsuleLabel).performScrollTo().performClick()
        dialogChoice("Work capsule").performScrollTo().performClick().assertIsSelected()
        dialogChoice(capsuleLabel).performScrollTo().performClick()
        dialogChoice("Work capsule").performScrollTo().assertIsSelected()
        dialogChoice("Locked capsule").assertDoesNotExist()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Save).performClick()
        val updated = saved.get().single()
        assertEquals(firstRule.id, updated.id)
        assertEquals(firstRule.domain, updated.domain)
        assertEquals(LinkRoutingTargetKind.Capsule, updated.target.kind)
        assertEquals("work-news", updated.target.id)
        composeRule.onNodeWithContentDescription(editLabel).performScrollTo().performClick()
        dialogChoice("Work capsule").performScrollTo().assertIsSelected()
    }

    @Test
    fun unavailableTargetPreventsSaveAndReaderRestoresValidChoice() {
        composeRule.setContent {
            MaterialTheme {
                LinkRoutingSettingsPage(
                    rules = emptyList(), profiles = emptyList(), capsules = emptyList(),
                    onRulesChanged = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithTag(LinkRoutingTestTags.Add).performScrollTo().performClick()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Domain).performTextInput("news.example")
        for (label in listOf(R.string.link_routing_profile, R.string.link_routing_capsule)) {
            dialogChoice(composeRule.activity.getString(label))
                .performScrollTo().performClick()
            composeRule.onNodeWithTag(LinkRoutingTestTags.Save).assertIsNotEnabled()
        }
        dialogChoice(composeRule.activity.getString(R.string.link_routing_reader))
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag(LinkRoutingTestTags.Save).assertIsEnabled()
    }

    private fun dialogChoice(label: String) =
        composeRule.onNode(hasText(label) and hasAnyAncestor(isDialog()))
}
