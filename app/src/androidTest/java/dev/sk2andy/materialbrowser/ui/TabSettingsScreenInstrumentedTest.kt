package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class TabSettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun residentTabSliderUpdatesDisplayedLimit() {
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Hero,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isTabButtonVisible = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onTabButtonVisibleChanged = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.resources.getQuantityString(
                R.plurals.settings_resident_tab_limit_summary,
                10,
                10,
            ),
        ).assertExists()
        composeRule.onNodeWithTag(TabSettingsTestTags.ResidentTabLimit)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(15f)
            }
        composeRule.onNodeWithText(
            context.resources.getQuantityString(
                R.plurals.settings_resident_tab_limit_summary,
                15,
                15,
            ),
        ).assertExists()
    }

    @Test
    fun tabOrderingOptionsExposeModeAndCallbacks() {
        val listStartsAtBottom = AtomicBoolean()
        val automaticSorting = AtomicBoolean()
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Hero,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isTabButtonVisible = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabListStartsAtBottomChanged = listStartsAtBottom::set,
                    onAutomaticTabSortingEnabledChanged = automaticSorting::set,
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onTabButtonVisibleChanged = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabSettingsTestTags.ListStartsAtBottom)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(TabSettingsTestTags.AutomaticSorting)
            .assertIsEnabled()
            .performClick()

        assertTrue(automaticSorting.get())
        assertFalse(listStartsAtBottom.get())
    }
}
