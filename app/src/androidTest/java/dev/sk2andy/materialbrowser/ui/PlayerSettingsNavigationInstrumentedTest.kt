package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.PageTranslationProvider
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.browser.LinkLongPressAction
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerMode
import dev.sk2andy.materialbrowser.browser.InlineMediaPlayerSeekSettings
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.File
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
            listOf(R.string.settings_home_tabs_title, R.string.settings_group_tab_lifecycle),
            listOf(R.string.settings_home_tabs_title, R.string.settings_group_tab_lifecycle,
                R.string.settings_closed_tab_undo_title),
            listOf(R.string.settings_home_tabs_title, R.string.profile_actions_title),
            listOf(R.string.settings_home_gestures_title, R.string.settings_group_link_interactions),
            listOf(R.string.settings_home_gestures_title, R.string.settings_group_link_interactions,
                R.string.settings_link_long_press_action),
            listOf(R.string.settings_home_gestures_title, R.string.settings_group_link_interactions,
                R.string.settings_link_peek_swipe_action),
            listOf(R.string.settings_home_gestures_title, R.string.settings_group_link_interactions,
                R.string.settings_link_peek_actions_title),
            listOf(R.string.settings_home_gestures_title, R.string.settings_address_bar_actions_title),
            listOf(R.string.settings_home_gestures_title, R.string.settings_address_bar_actions_title,
                R.string.settings_address_bar_long_press_title),
            listOf(R.string.settings_home_gestures_title, R.string.settings_address_bar_actions_title,
                R.string.settings_address_bar_actions_title),
            listOf(R.string.settings_home_gestures_title, R.string.settings_address_bar_actions_title,
                R.string.settings_menu_actions_title),
            listOf(R.string.settings_home_gestures_title, R.string.settings_section_gestures),
            listOf(R.string.settings_section_browser, R.string.settings_browser_group_websites,
                R.string.settings_translations_title),
            listOf(R.string.settings_section_browser, R.string.settings_browser_group_websites,
                R.string.link_routing_title),
            listOf(R.string.settings_appearance_title, R.string.settings_colors_title),
            listOf(R.string.settings_appearance_title, R.string.settings_surfaces_shapes_title),
            listOf(R.string.settings_appearance_title, R.string.settings_address_bar_style),
            listOf(R.string.settings_section_browser, R.string.settings_browser_group_links,
                R.string.settings_external_link_preview_title),
            listOf(R.string.settings_section_browser, R.string.settings_browser_group_links,
                R.string.settings_external_app_links_title),
            listOf(R.string.settings_player_title, R.string.settings_inline_media_player_title),
        )
        paths.forEach { path ->
            listOf(false, true).forEach { systemBack ->
                var phase = "opening route"
                try {
                    path.forEach(::openDestination)
                    phase = "returning to parent"
                    if (systemBack) {
                        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
                        advanceRoute()
                    } else {
                        headerBack()
                    }
                    phase = "checking parent"
                    assertPageHeader(path[path.lastIndex - 1])
                    phase = "returning home"
                    repeat(path.size - 1) { headerBack() }
                    assertHome()
                } catch (failure: AssertionError) {
                    val directory = File(context.getExternalFilesDir(null), "settings-navigation-diagnostics")
                        .apply { mkdirs() }
                    val route = path.joinToString(" → ") { context.getString(it) }
                    File(directory, "failed-route.txt").writeText(
                        "Route: $route\nSystem Back: $systemBack\nPhase: $phase\n${failure.message}\n" +
                            composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = 30),
                    )
                    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .takeScreenshot(File(directory, "failed-route.png"))
                    throw AssertionError("Route: $route; system Back: $systemBack; phase: $phase", failure)
                }
            }
        }
    }

    @Test
    fun movedSettingsPersistAndRemainSelectedAfterReopeningPages() {
        val store = BrowserSessionStore(context)
        val profilesTarget = !store.loadProfilesEnabled()
        val dockingTarget = !store.loadAddressBarDockingEnabled()
        val translationTarget = if (store.loadPageTranslationProvider() == PageTranslationProvider.Kagi) {
            PageTranslationProvider.Yandex
        } else {
            PageTranslationProvider.Kagi
        }
        openSettings()
        openDestination(R.string.settings_section_browser)
        openDestination(R.string.settings_browser_group_websites)
        openDestination(R.string.settings_translations_title)
        composeRule.onNodeWithText(translationTarget.displayName)
            .performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(translationTarget, store.loadPageTranslationProvider()) }
        repeat(3) { headerBack() }
        assertHome()

        openDestination(R.string.settings_home_tabs_title)
        openDestination(R.string.settings_group_tab_lifecycle)
        // Never exercises the persisted selection without pruning existing tabs.
        composeRule.onNodeWithText(context.getString(R.string.tab_lifetime_never))
            .performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(InactiveTabLifetime.Never, store.loadInactiveTabLifetime()) }
        headerBack()
        openDestination(R.string.profile_actions_title)
        composeRule.onNodeWithText(context.getString(R.string.settings_profiles_title))
            .performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(profilesTarget, store.loadProfilesEnabled()) }
        repeat(2) { headerBack() }
        assertHome()

        openDestination(R.string.settings_home_gestures_title)
        openDestination(R.string.settings_section_gestures)
        composeRule.onNodeWithTag(TabSettingsTestTags.AddressBarDocking)
            .performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(dockingTarget, store.loadAddressBarDockingEnabled()) }
        repeat(2) { headerBack() }
        assertHome()

        openDestination(R.string.settings_section_browser)
        openDestination(R.string.settings_browser_group_websites)
        openDestination(R.string.settings_translations_title)
        composeRule.onNodeWithText(translationTarget.displayName).performScrollTo().assertIsSelected()
        repeat(3) { headerBack() }
        openDestination(R.string.settings_home_tabs_title)
        openDestination(R.string.settings_group_tab_lifecycle)
        composeRule.onNodeWithText(context.getString(R.string.tab_lifetime_never))
            .performScrollTo().assertIsSelected()
        headerBack()
        openDestination(R.string.profile_actions_title)
        val profiles = composeRule.onNodeWithText(context.getString(R.string.settings_profiles_title))
            .performScrollTo()
        if (profilesTarget) profiles.assertIsOn() else profiles.assertIsOff()
        repeat(2) { headerBack() }
        openDestination(R.string.settings_home_gestures_title)
        openDestination(R.string.settings_section_gestures)
        val docking = composeRule.onNodeWithTag(TabSettingsTestTags.AddressBarDocking).performScrollTo()
        if (dockingTarget) docking.assertIsOn() else docking.assertIsOff()
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            assertEquals(translationTarget, controller.pageTranslationProvider)
            assertEquals(InactiveTabLifetime.Never, controller.inactiveTabLifetime)
            assertEquals(profilesTarget, controller.profilesEnabled)
            assertEquals(dockingTarget, controller.isAddressBarDockingEnabled)
        }
        repeat(2) { headerBack() }
        assertHome()
    }

    private fun assertPageHeader(title: Int) {
        // Layout-only containers are flattened in semantics, so a repeated section heading
        // can also be a Back-button sibling. It is explicitly marked Heading; the page title
        // is not. Keep a unique exact-title match instead of choosing a node by position.
        composeRule.onNode(
            hasText(context.getString(title)) and
                !SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and
                hasAnySibling(hasTestTag("settings_page_back")),
        ).assertIsDisplayed()
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
        // The two action editors own custom headers rather than SettingsPage's tagged button.
        // Identify those buttons within their existing page tags; still require one labeled,
        // clickable Back beside a title. Two actual headers remain an assertion failure.
        val pageIdentity = hasTestTag("settings_page_back") or
            hasAnyAncestor(hasTestTag(LinkPeekActionEditorTestTags.Page)) or
            hasAnyAncestor(hasTestTag(AddressBarActionEditorTestTags.Page))
        composeRule.onNode(
            pageIdentity and hasClickAction() and
                hasContentDescription(context.getString(R.string.action_back)) and
                hasAnySibling(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)),
        )
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
    }

    private fun advanceRoute() {
        // Android Back dispatch runs independently of the paused Compose clock. Drain it,
        // then compose/place the new destination before advancing its transition.
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
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
