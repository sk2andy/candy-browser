package dev.sk2andy.materialbrowser.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileCreationSheetInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createsProfileWithSelectedIconAndIsolationMode() {
        val createdProfile = AtomicReference<Pair<String, Boolean>?>()
        composeRule.setContent {
            MaterialTheme {
                EmojiPickerSheet(
                    visible = true,
                    creatingProfile = true,
                    isolationSupported = true,
                    selectedEmoji = null,
                    onCreate = { emoji, isolationEnabled ->
                        createdProfile.set(emoji to isolationEnabled)
                    },
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        val createButton = composeRule.onNodeWithText(
            context.getString(R.string.action_create_profile),
        )
        createButton.assertIsNotEnabled()

        composeRule.onNodeWithText("💼").performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_profile_isolation_title),
        ).performClick()
        createButton.assertIsEnabled().performClick()

        assertEquals("💼" to true, createdProfile.get())
    }

    @Test
    fun isolationAndCreateButtonStayOutsideScrollableIcons() {
        composeRule.setContent {
            MaterialTheme {
                EmojiPickerSheet(
                    visible = true,
                    creatingProfile = true,
                    isolationSupported = true,
                    selectedEmoji = null,
                    onCreate = { _, _ -> },
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        val isolationBounds = composeRule
            .onNodeWithTag(ProfileCreationTestTags.Isolation)
            .fetchSemanticsNode()
            .boundsInRoot
        val titleBounds = composeRule
            .onNodeWithText(context.getString(R.string.add_profile_title))
            .fetchSemanticsNode()
            .boundsInRoot
        val sheetBounds = composeRule
            .onNodeWithTag(ProfileCreationTestTags.Sheet)
            .fetchSemanticsNode()
            .boundsInRoot
        val iconScrollBounds = composeRule
            .onNodeWithTag(ProfileCreationTestTags.IconScroll)
            .fetchSemanticsNode()
            .boundsInRoot
        val createButton = composeRule.onNodeWithTag(ProfileCreationTestTags.CreateButton)
        val buttonBoundsBeforeScroll = createButton.fetchSemanticsNode().boundsInRoot

        val displayHeight = context.resources.displayMetrics.heightPixels.toFloat()
        val maxButtonBottomGap = context.resources.displayMetrics.density * 64f
        val createButtonScreenBounds = screenBoundsForText(
            context.getString(R.string.action_create_profile),
        )
        assertTrue(
            "sheetHeight=${sheetBounds.height}, displayHeight=$displayHeight",
            sheetBounds.height <= displayHeight * 0.66f + 1f,
        )
        assertTrue(
            "buttonBottom=${createButtonScreenBounds.bottom}, " +
                "displayHeight=$displayHeight, maxGap=$maxButtonBottomGap",
            displayHeight - createButtonScreenBounds.bottom <= maxButtonBottomGap + 1f,
        )
        assertTrue(isolationBounds.bottom < titleBounds.top)
        assertTrue(isolationBounds.bottom < iconScrollBounds.top)
        assertTrue(iconScrollBounds.bottom <= buttonBoundsBeforeScroll.top)

        composeRule.onNodeWithText("📅").performScrollTo()
        composeRule.waitForIdle()

        val isolationBoundsAfterScroll = composeRule
            .onNodeWithTag(ProfileCreationTestTags.Isolation)
            .fetchSemanticsNode()
            .boundsInRoot
        val buttonBoundsAfterScroll = createButton.fetchSemanticsNode().boundsInRoot
        assertEquals(isolationBounds.top, isolationBoundsAfterScroll.top, 0.5f)
        assertEquals(isolationBounds.bottom, isolationBoundsAfterScroll.bottom, 0.5f)
        assertEquals(buttonBoundsBeforeScroll.top, buttonBoundsAfterScroll.top, 0.5f)
        assertEquals(buttonBoundsBeforeScroll.bottom, buttonBoundsAfterScroll.bottom, 0.5f)
    }

    private fun screenBoundsForText(text: String): Rect {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending += checkNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow,
        )
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.text?.toString() == text) {
                return Rect().also(node::getBoundsInScreen)
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        error("No accessibility node found with text: $text")
    }
}
