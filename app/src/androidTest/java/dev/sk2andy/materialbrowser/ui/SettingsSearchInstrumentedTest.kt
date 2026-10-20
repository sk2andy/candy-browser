package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SettingsSearchInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val savedPreferences = listOf(
        BrowserSessionStore.PREFERENCES_NAME,
        GestureOnboardingStore.PREFERENCES_NAME,
        ReleaseNotesStore.PREFERENCES_NAME,
    ).associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }

    init {
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        BrowserSessionStore(context).saveScrollBarEnabled(false)
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        composeRule.activityRule.scenario.close()
        restorePreferences()
    }

    @Test
    fun movedSettingsSearchRetainsTargetsAndBothBackActionsRestoreQuery() {
        openSettings()
        val cases = listOf(
            Triple("settings_resident_tab_limit", R.string.settings_resident_tab_limit, TabSettingsTestTags.ResidentTabLimit),
            Triple("settings_address_bar_docking_title", R.string.settings_address_bar_docking_title, TabSettingsTestTags.AddressBarDocking),
            Triple("settings_external_link_preview_title", R.string.settings_external_link_preview_title, BrowserSettingsTestTags.ExternalLinkPreview),
            Triple("link_routing_title", R.string.link_routing_title, LinkRoutingTestTags.Add),
        )
        cases.forEach { (id, label, tag) ->
            listOf(false, true).forEach { systemBack ->
                val query = context.getString(label)
                search(query)
                composeRule.onNodeWithTag(SettingsSearchTestTags.result(id))
                    .performSemanticsAction(SemanticsActions.OnClick) { it() }
                advanceRoute()
                composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                if (systemBack) {
                    device.pressBack()
                    advanceRoute()
                } else {
                    headerBack()
                }
                assertQuery(query)
                composeRule.onNodeWithTag(SettingsSearchTestTags.result(id)).assertIsDisplayed()
            }
        }
        listOf(
            "settings_profiles_title" to R.string.settings_profiles_title,
            "settings_translation_provider" to R.string.settings_translation_provider,
            "settings_auto_close_tabs" to R.string.settings_auto_close_tabs,
        ).forEach { (id, label) ->
            val query = context.getString(label)
            search(query)
            composeRule.onNodeWithTag(SettingsSearchTestTags.result(id))
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            advanceRoute()
            when (id) {
                "settings_profiles_title" -> composeRule.onNodeWithText(query).performScrollTo().assertIsDisplayed()
                "settings_translation_provider" -> composeRule.onNodeWithText("Google Translate").performScrollTo().assertIsDisplayed()
                else -> composeRule.onNodeWithText(context.getString(R.string.tab_lifetime_never)).performScrollTo().assertIsDisplayed()
            }
            headerBack()
            assertQuery(query)
        }
    }

    @Test
    fun menuStyleSearchOpensRadioPageAndBackRestoresSearch() {
        openSettings()
        val query = context.getString(R.string.settings_menu_presentation_title)
        search(query)
        val result = composeRule.onNodeWithTag(SettingsSearchTestTags.result("settings_menu_presentation_title"))
        result.performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
        composeRule.onNodeWithTag(
            dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSelectionTestTags.presentation(
                dev.sk2andy.materialbrowser.shared.browser.BrowserMenuPresentation.BottomSheet,
            ),
        ).performScrollTo().assertIsDisplayed()
        headerBack()
        assertQuery(query)
        result.assertIsDisplayed()
    }

    @Test
    fun localizedSearchOpensVisibleOptionAndPersistsItsChange() {
        openSettings()
        composeRule.onNodeWithTag(SettingsSearchTestTags.Query)
            .assertHeightIsEqualTo(56.dp)
            .assert(hasText(context.getString(R.string.settings_search_hint)))
        captureScreenshot("issue253-home.png")
        val query = context.getString(R.string.settings_scroll_bar_title)
        search(query)
        val result = composeRule.onNodeWithTag(
            SettingsSearchTestTags.result("settings_scroll_bar_title"),
        )
        result.assertIsDisplayed()
            .assert(hasText(query))
            .assert(hasText(context.getString(R.string.settings_section_browser)))
        captureScreenshot("issue253-search.png")

        result.performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
        composeRule.onNodeWithTag(BrowserSettingsTestTags.ScrollBar).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsSearchTestTags.Query).assertDoesNotExist()
        captureScreenshot("issue253-target.png")
        composeRule.onNodeWithTag(BrowserSettingsTestTags.ScrollBar)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        assertTrue(BrowserSessionStore(context).loadScrollBarEnabled())
        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.browserControllerForTesting().isScrollBarEnabled)
        }

        headerBack()
        assertQuery(query)
        result.assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.history_clear_search))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
        assertQuery("")
        result.assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.settings_section_browser))
            .assertIsDisplayed()
    }

    @Test
    fun unmatchedSearchShowsFeedbackAndBackClearsBeforeClosingSettings() {
        openSettings()
        search("candy-no-such-setting-253")
        composeRule.onNodeWithTag(SettingsSearchTestTags.Empty)
            .assertIsDisplayed()
            .assert(hasText(context.getString(R.string.settings_search_empty)))
        composeRule.onNodeWithText(context.getString(R.string.settings_section_browser))
            .assertDoesNotExist()

        device.pressBack()
        advanceRoute()
        assertQuery("")
        composeRule.onNodeWithTag(SettingsSearchTestTags.Empty).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.settings_section_browser))
            .assertIsDisplayed()

        device.pressBack()
        advanceRoute()
        composeRule.onNodeWithTag(SettingsSearchTestTags.Query).assertDoesNotExist()
        openSettings()
        assertQuery("")
        composeRule.onNodeWithTag(SettingsSearchTestTags.Empty).assertDoesNotExist()
    }

    @Test
    fun headerBackClearsFocusedSearchAndDeepCustomOptionIsVisible() {
        openSettings()
        val query = context.getString(R.string.settings_tab_dismiss_resistance)
        val field = composeRule.onNodeWithTag(SettingsSearchTestTags.Query)
        field.performClick()
        field.performTextReplacement(query)
        field.assertIsFocused()
        advanceRoute()
        headerBack()
        assertQuery("")
        composeRule.onNodeWithTag(SettingsSearchTestTags.Query).assertIsNotFocused()
        assertKeyboardHidden()

        search(query)
        composeRule.onNodeWithTag(
            SettingsSearchTestTags.result("settings_tab_dismiss_resistance"),
        ).performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
        composeRule.onNodeWithText(query).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsSearchTestTags.Query).assertDoesNotExist()
        assertKeyboardHidden()
        device.pressBack()
        advanceRoute()
        assertQuery(query)
        composeRule.onNodeWithTag(SettingsSearchTestTags.Query).assertIsNotFocused()
    }

    @Test
    fun localizedChoiceAndProviderNamesFindTheirConfiguration() {
        openSettings()
        search("AMOLED")
        composeRule.onNodeWithTag(SettingsSearchTestTags.result("settings_appearance_mode"))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
        composeRule.onNodeWithTag(AppearanceSettingsTestTags.AppearanceMode).assertIsDisplayed()
        headerBack()
        search("DuckDuckGo")
        composeRule.onNodeWithTag(SettingsSearchTestTags.result("settings_search_engine"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsSearchTestTags.result("settings_search_suggestions"))
            .assertExists()
    }

    @Test
    fun privateSearchIsBoundedAndDiscardedOnActivityRecreation() {
        // Exercise private behavior only when the existing device permission already allows it.
        // Test setup must not alter permission grants.
        assumeTrue(
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
        waitForBrowserChrome()
        composeRule.activityRule.scenario.onActivity { activity ->
            val controller = activity.browserControllerForTesting()
            controller.createTab(isIncognito = true)
            assertTrue(controller.selectedTab.isIncognito)
        }
        openSettings()
        val input = "private-search-253 ".repeat(20)
        search(input)
        val query = input.take(SettingsSearchRules.MAX_QUERY_LENGTH)
        assertQuery(query)
        val preferences = context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
        assertTrue(preferences.all.values.none { it.toString().contains("private-search-253") })
        composeRule.mainClock.autoAdvance = true
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        openSettings()
        assertQuery("")
    }

    private fun openSettings() {
        composeRule.mainClock.autoAdvance = true
        waitForBrowserChrome()
        val closeAddress = context.getString(R.string.cd_close_address_input)
        if (composeRule.onAllNodesWithContentDescription(closeAddress).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription(closeAddress).performClick()
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_more_options)).performClick()
        // Let the menu finish appearing before pausing the clock for route assertions.
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings).isDisplayed()
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings)
            .assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
        val title = hasText(context.getString(R.string.settings_title)) and !hasClickAction()
        composeRule.waitUntil(timeoutMillis = 10_000L) { composeRule.onNode(title).isDisplayed() }
        composeRule.onNodeWithTag(SettingsSearchTestTags.Query).assertIsDisplayed()
    }

    private fun waitForBrowserChrome() {
        // MainActivity installs its lifecycle-aware recomposer before its first composition.
        // A resumed activity alone does not guarantee that browser chrome is available yet.
        val startupControls = listOf(
            context.getString(R.string.cd_close_address_input),
            context.getString(R.string.cd_more_options),
        )
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            // Startup may focus the address editor, which replaces the More button.
            startupControls.any { description ->
                composeRule.onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty() &&
                    composeRule.onNodeWithContentDescription(description).isDisplayed()
            }
        }
    }

    private fun search(query: String) {
        val field = composeRule.onNodeWithTag(SettingsSearchTestTags.Query)
        field.performClick()
        field.performTextReplacement(query)
        field.performImeAction()
        advanceRoute()
        assertKeyboardHidden()
    }

    private fun headerBack() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_back))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        advanceRoute()
    }

    private fun assertQuery(query: String) {
        composeRule.onNodeWithTag(SettingsSearchTestTags.Query).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(query)),
        )
    }

    private fun assertKeyboardHidden() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }
    }

    private fun advanceRoute() {
        // Android Back dispatch runs independently of the paused Compose clock. Drain it,
        // then compose/place the new destination before advancing its transition.
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        // Scroll requests start after the target has been placed and received a frame.
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
    }

    private fun captureScreenshot(name: String) {
        val directory = requireNotNull(context.getExternalFilesDir(null))
        assertTrue("Could not capture $name", device.takeScreenshot(File(directory, name)))
    }

    private fun restorePreferences() {
        savedPreferences.forEach { (name, original) ->
            val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = preferences.edit()
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
