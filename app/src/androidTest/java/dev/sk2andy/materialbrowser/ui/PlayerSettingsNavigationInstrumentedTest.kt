package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerSeekSettings
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class PlayerSettingsNavigationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val savedPreferences = listOf(
        BrowserSessionStore.PREFERENCES_NAME,
        GestureOnboardingStore.PREFERENCES_NAME,
        ReleaseNotesStore.PREFERENCES_NAME,
    ).associateWith { name -> context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap() }

    init {
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        BrowserSessionStore(context).apply {
            saveVideoAutoplayBlocked(false)
            saveInlineMediaPlayerMode(InlineMediaPlayerMode.Default)
            saveInlineMediaPlayerSeekSettings(InlineMediaPlayerSeekSettings())
            saveLinkLongPressAction(LinkLongPressAction.LinkPeek)
        }
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        composeRule.activityRule.scenario.close()
        restorePreferences()
    }

    @Test
    fun homePlayerRoutePersistsChangesAndBothBackActionsReturnHome() {
        openSettings()
        openDestination(R.string.settings_player_title)

        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayer).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekBackward)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekForward)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        composeRule.onNodeWithTag(PlayerSettingsTestTags.VideoAutoplay)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayer)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.onNodeWithText(context.getString(R.string.settings_inline_media_player_mode_disabled))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.mainClock.advanceTimeBy(300L)
        headerBack()
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekBackward).assertIsNotEnabled()
        val store = BrowserSessionStore(context)
        assertEquals(InlineMediaPlayerSeekSettings(15, 30), store.loadInlineMediaPlayerSeekSettings())
        assertEquals(InlineMediaPlayerMode.Disabled, store.loadInlineMediaPlayerMode())
        assertTrue(store.loadVideoAutoplayBlocked())

        headerBack()
        assertHome()
        openDestination(R.string.settings_section_browser)
        openDestination(R.string.settings_browser_group_websites)
        composeRule.onNodeWithTag(BrowserSettingsTestTags.ScrollBar).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayer).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerSettingsTestTags.VideoAutoplay).assertDoesNotExist()
        headerBack()
        composeRule.onNode(
            hasText(context.getString(R.string.settings_section_browser)) and !hasClickAction(),
        ).assertIsDisplayed()
        headerBack()
        assertHome()
        openDestination(R.string.settings_player_title)
        composeRule.onNodeWithTag(PlayerSettingsTestTags.InlineMediaPlayerSeekForward).assertIsNotEnabled()
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            assertEquals(InlineMediaPlayerSeekSettings(15, 30), controller.inlineMediaPlayerSeekSettings)
            assertEquals(InlineMediaPlayerMode.Disabled, controller.inlineMediaPlayerMode)
            assertTrue(controller.isVideoAutoplayBlocked)
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        advanceRoute()
        assertHome()
    }

    @Test
    fun expandedPagesHaveTheSameHeaderAndSystemBackParent() {
        openSettings()
        val paths = listOf(
            listOf(R.string.settings_home_tabs_title, R.string.settings_tab_stack_folder_mode),
            listOf(R.string.settings_home_tabs_title, R.string.settings_closed_tab_undo_title),
            listOf(R.string.settings_home_gestures_title, R.string.settings_link_long_press_action),
            listOf(R.string.settings_home_gestures_title, R.string.settings_link_peek_swipe_action),
            listOf(R.string.settings_appearance_title, R.string.settings_colors_title),
            listOf(R.string.settings_appearance_title, R.string.settings_surfaces_shapes_title),
            listOf(R.string.settings_appearance_title, R.string.settings_address_bar_style),
            listOf(R.string.settings_section_browser, R.string.settings_browser_group_links,
                R.string.settings_external_link_preview_title),
            listOf(R.string.settings_player_title, R.string.settings_inline_media_player_title),
        )
        paths.forEach { path ->
            listOf(false, true).forEach { systemBack ->
                path.forEach(::openDestination)
                if (systemBack) {
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
                    advanceRoute()
                } else {
                    headerBack()
                }
                composeRule.onAllNodes(
                    hasText(context.getString(path[path.lastIndex - 1])) and !hasClickAction(),
                ).onFirst().assertExists()
                repeat(path.size - 1) { headerBack() }
                assertHome()
            }
        }
    }

    private fun openSettings() {
        val closeAddress = context.getString(R.string.cd_close_address_input)
        if (composeRule.onAllNodesWithContentDescription(closeAddress).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription(closeAddress).performClick()
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_more_options)).performClick()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings)
            .assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
    }

    private fun openDestination(title: Int) {
        composeRule.onNode(hasText(context.getString(title)) and hasClickAction())
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
    }

    private fun headerBack() {
        composeRule.onNodeWithTag("settings_page_back")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
    }

    private fun advanceRoute() {
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
    }

    private fun assertHome() {
        val title = hasText(context.getString(R.string.settings_title)) and !hasClickAction()
        composeRule.waitUntil(timeoutMillis = 10_000L) { composeRule.onNode(title).isDisplayed() }
        composeRule.onNode(title).assertIsDisplayed()
    }

    private fun restorePreferences() {
        savedPreferences.forEach { (name, original) ->
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = preferences.edit()
            // Remove only keys created by this test, never clear the existing preference file.
            (preferences.all.keys - original.keys).forEach(editor::remove)
            original.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            assertTrue(editor.commit())
            assertEquals(original, preferences.all)
        }
    }
}
