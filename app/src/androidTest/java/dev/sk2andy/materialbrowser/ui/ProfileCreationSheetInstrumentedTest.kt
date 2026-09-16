package dev.sk2andy.materialbrowser.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.ProfileWallpaperTarget
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
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
    private val profileEmojis by lazy {
        context.assets.open("candy_sync_device_icons_v1.json").use { input ->
            SyncDeviceIconCatalog.decode(input.bufferedReader().readText()).icons.map { it.emoji }
        }
    }

    @Test
    fun createsProfileWithSelectedIconAndIsolationMode() {
        val createdProfile = AtomicReference<ProfileCreationSubmission?>()
        composeRule.setContent {
            MaterialTheme {
                EmojiPickerSheet(
                    visible = true,
                    creatingProfile = true,
                    isolationSupported = true,
                    profileProtectionSupported = true,
                    emojis = profileEmojis,
                    selectedEmoji = null,
                    onCreate = { emoji, isolationEnabled, options ->
                        createdProfile.set(
                            ProfileCreationSubmission(
                                emoji,
                                isolationEnabled,
                                options.protection,
                                options.wallpaperTargets,
                            ),
                        )
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
        composeRule.onNodeWithTag(ProfileCreationOptionTestTags.Protection)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.action_save)).performClick()
        composeRule.onNodeWithTag(ProfileCreationOptionTestTags.NewTabWallpaper)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(ProfileCreationOptionTestTags.TabSwitcherWallpaper)
            .assertIsDisplayed()
            .performClick()
        createButton.assertIsEnabled().performClick()

        val submission = requireNotNull(createdProfile.get())
        assertEquals("💼", submission.emoji)
        assertTrue(submission.isolationEnabled)
        assertTrue(submission.protection != null)
        assertEquals(ProfileWallpaperTarget.entries.toSet(), submission.wallpaperTargets)
    }

    @Test
    fun creationOptionsScrollWhileCreateButtonStaysFixed() {
        composeRule.setContent {
            MaterialTheme {
                EmojiPickerSheet(
                    visible = true,
                    creatingProfile = true,
                    isolationSupported = true,
                    profileProtectionSupported = true,
                    emojis = profileEmojis,
                    selectedEmoji = null,
                    onCreate = { _, _, _ -> },
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
        val protectionBounds = composeRule
            .onNodeWithTag(ProfileCreationOptionTestTags.Protection)
            .fetchSemanticsNode()
            .boundsInRoot
        val newTabWallpaperBounds = composeRule
            .onNodeWithTag(ProfileCreationOptionTestTags.NewTabWallpaper)
            .fetchSemanticsNode()
            .boundsInRoot
        val tabSwitcherWallpaperBounds = composeRule
            .onNodeWithTag(ProfileCreationOptionTestTags.TabSwitcherWallpaper)
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
            sheetBounds.height <= displayHeight * 0.9f + 1f,
        )
        assertTrue(
            "buttonBottom=${createButtonScreenBounds.bottom}, " +
                "displayHeight=$displayHeight, maxGap=$maxButtonBottomGap",
            displayHeight - createButtonScreenBounds.bottom <= maxButtonBottomGap + 1f,
        )
        assertTrue(iconScrollBounds.height > 0f)
        assertTrue(iconScrollBounds.top <= isolationBounds.top)
        assertTrue(isolationBounds.bottom <= protectionBounds.top)
        assertTrue(protectionBounds.bottom <= newTabWallpaperBounds.top)
        assertTrue(newTabWallpaperBounds.bottom <= tabSwitcherWallpaperBounds.top)
        assertTrue(tabSwitcherWallpaperBounds.bottom < titleBounds.top)
        assertTrue(titleBounds.bottom <= iconScrollBounds.bottom)
        assertTrue(iconScrollBounds.bottom <= buttonBoundsBeforeScroll.top)

        composeRule.onNodeWithText("📅").performScrollTo()
        composeRule.waitForIdle()

        val iconScrollBoundsAfterScroll = composeRule
            .onNodeWithTag(ProfileCreationTestTags.IconScroll)
            .fetchSemanticsNode()
            .boundsInRoot
        val buttonBoundsAfterScroll = createButton.fetchSemanticsNode().boundsInRoot
        assertEquals(iconScrollBounds.top, iconScrollBoundsAfterScroll.top, 0.5f)
        assertEquals(iconScrollBounds.bottom, iconScrollBoundsAfterScroll.bottom, 0.5f)
        assertEquals(buttonBoundsBeforeScroll.top, buttonBoundsAfterScroll.top, 0.5f)
        assertEquals(buttonBoundsBeforeScroll.bottom, buttonBoundsAfterScroll.bottom, 0.5f)
    }

    private data class ProfileCreationSubmission(
        val emoji: String,
        val isolationEnabled: Boolean,
        val protection: ProfileProtection?,
        val wallpaperTargets: Set<ProfileWallpaperTarget>,
    )

    private fun screenBoundsForText(text: String): Rect {
        var resolvedBounds: Rect? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                ?.let { root -> findTextBounds(root, text) }
                ?.also { resolvedBounds = it } != null
        }
        return checkNotNull(resolvedBounds)
    }

    private fun findTextBounds(root: AccessibilityNodeInfo, text: String): Rect? {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending += root
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.text?.toString() == text) {
                return Rect().also(node::getBoundsInScreen)
            }
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return null
    }
}
