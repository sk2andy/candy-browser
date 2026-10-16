package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.guest.GuestBubbleSessionStore
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestBubbleSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tutorialRequiresExplicitActivationAndCancelLeavesDisabled() {
        val enabledChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            var enabled by remember { mutableStateOf(false) }
            MaterialBrowserTheme {
                GuestBubbleSettingsControl(
                    enabled = enabled,
                    available = true,
                    summary = "Long-press profile + to start",
                    onEnabledChanged = {
                        enabledChanges += it
                        enabled = it
                    },
                )
            }
        }

        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Toggle).performClick()
        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Tutorial).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(emptyList<Boolean>(), enabledChanges) }
        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Cancel).performClick()
        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Tutorial).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(emptyList<Boolean>(), enabledChanges) }

        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Toggle).performClick()
        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Activate).performClick()
        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Tutorial).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf(true), enabledChanges) }

        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Toggle).performClick()
        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Tutorial).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf(true, false), enabledChanges) }
    }

    @Test
    fun unavailableGuestBubbleCannotOpenTutorialOrChangePreference() {
        val changes = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                GuestBubbleSettingsControl(
                    enabled = false,
                    available = false,
                    summary = "Enable profiles first",
                    onEnabledChanged = { changes.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Toggle).assertIsNotEnabled()
        composeRule.onNodeWithTag(GuestBubbleSettingsTestTags.Tutorial).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, changes.get()) }
    }

    @Test
    fun preferenceChangesUpdateGuestLongPressWhileOverviewRemainsComposed() {
        val store = GuestBubbleSessionStore(ApplicationProvider.getApplicationContext())
        val wasEnabled = store.isEnabled()
        val guestStarts = AtomicInteger()
        val profile = BrowserProfile(id = "candy", emoji = "🍬")
        try {
            assertTrue(store.setEnabled(false))
            composeRule.setContent {
                val enabled by rememberGuestBubbleEnabled(store)
                MaterialBrowserTheme {
                    ProfileSwitcher(
                        profiles = listOf(profile),
                        activeProfileId = profile.id,
                        enabled = true,
                        onSelect = {},
                        onLongClick = {},
                        onAdd = {},
                        onAddLongClick = if (enabled) {
                            { guestStarts.incrementAndGet() }
                        } else {
                            null
                        },
                    )
                }
            }

            val addNode = composeRule.onNodeWithTag(ProfileSwitcherTestTags.Add)
            addNode.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
            composeRule.runOnIdle { assertTrue(store.setEnabled(true)) }
            addNode.performTouchInput { longClick() }
            composeRule.runOnIdle { assertEquals(1, guestStarts.get()) }
            composeRule.runOnIdle { assertTrue(store.setEnabled(false)) }
            addNode.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        } finally {
            store.setEnabled(wasEnabled)
        }
    }

}
