package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSwitcherInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesSelectionEditAndAddActionsWithConsistentTouchTargets() {
        val selectedProfile = AtomicReference<String>()
        val editedProfile = AtomicReference<String>()
        val addClicks = AtomicInteger()
        val candy = BrowserProfile(id = "candy", emoji = "🍬")
        val work = BrowserProfile(id = "work", emoji = "💼")

        composeRule.setContent {
            MaterialBrowserTheme {
                ProfileSwitcher(
                    profiles = listOf(candy, work),
                    activeProfileId = candy.id,
                    enabled = true,
                    onSelect = selectedProfile::set,
                    onLongClick = editedProfile::set,
                    onAdd = addClicks::incrementAndGet,
                )
            }
        }

        val candyNode = composeRule.onNodeWithTag(
            ProfileSwitcherTestTags.profile(candy.id),
        )
        val workNode = composeRule.onNodeWithTag(
            ProfileSwitcherTestTags.profile(work.id),
        )
        val addNode = composeRule.onNodeWithTag(ProfileSwitcherTestTags.Add)

        candyNode.assertIsSelected()
        workNode.assertIsNotSelected().performClick()
        candyNode.performTouchInput { longClick() }
        addNode.performClick()

        assertEquals(work.id, selectedProfile.get())
        assertEquals(candy.id, editedProfile.get())
        assertEquals(1, addClicks.get())

        val minimumTouchTargetPx = composeRule.density.density * 48f
        val touchBounds = listOf(
            "candy" to candyNode,
            "work" to workNode,
            "add" to addNode,
        ).map { (label, node) ->
            label to node.fetchSemanticsNode().touchBoundsInRoot
        }
        touchBounds.forEach { (label, bounds) ->
            assertTrue("$label width=${bounds.width}", bounds.width >= minimumTouchTargetPx)
            assertTrue("$label height=${bounds.height}", bounds.height >= minimumTouchTargetPx)
        }
        touchBounds.forEach { (label, bounds) ->
            val widthDp = bounds.width / composeRule.density.density
            val heightDp = bounds.height / composeRule.density.density
            assertEquals("$label width", 52f, widthDp, 0.75f)
            assertEquals("$label height", 52f, heightDp, 0.75f)
        }
    }

    @Test
    fun scrollsActiveProfileIntoView() {
        val profiles = List(8) { index ->
            BrowserProfile(id = "profile-$index", emoji = "${index + 1}️⃣")
        }
        val activeProfile = profiles.last()

        composeRule.setContent {
            MaterialBrowserTheme {
                ProfileSwitcher(
                    profiles = profiles,
                    activeProfileId = activeProfile.id,
                    enabled = true,
                    onSelect = {},
                    onLongClick = {},
                    onAdd = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag(ProfileSwitcherTestTags.profile(activeProfile.id))
            .assertIsDisplayed()
    }

    @Test
    fun disabledSwitcherBlocksAllActions() {
        val actionCount = AtomicInteger()
        val profile = BrowserProfile(id = "candy", emoji = "🍬")

        composeRule.setContent {
            MaterialBrowserTheme {
                ProfileSwitcher(
                    profiles = listOf(profile),
                    activeProfileId = profile.id,
                    enabled = false,
                    onSelect = { actionCount.incrementAndGet() },
                    onLongClick = { actionCount.incrementAndGet() },
                    onAdd = actionCount::incrementAndGet,
                )
            }
        }

        val profileNode = composeRule.onNodeWithTag(
            ProfileSwitcherTestTags.profile(profile.id),
        )
        val addNode = composeRule.onNodeWithTag(ProfileSwitcherTestTags.Add)
        profileNode.assertIsNotEnabled().performTouchInput { click() }
        profileNode.performTouchInput { longClick() }
        addNode.assertIsNotEnabled().performTouchInput { click() }

        assertEquals(0, actionCount.get())
    }
}
