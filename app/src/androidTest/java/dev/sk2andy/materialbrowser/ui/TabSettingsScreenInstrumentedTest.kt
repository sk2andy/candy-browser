package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import java.util.concurrent.atomic.AtomicReference

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
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isTabButtonVisible = true,
                    isAddressBarDockingEnabled = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onTabButtonVisibleChanged = {},
                    onAddressBarDockingEnabledChanged = {},
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
        val overviewMode = AtomicReference<TabOverviewMode?>()
        val stackFolderMode = AtomicReference<TabOverviewMode?>()
        var currentOverviewMode by mutableStateOf(TabOverviewMode.Hero)
        var currentStackFolderMode by mutableStateOf(TabOverviewMode.Grid)
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = currentOverviewMode,
                    tabStackFolderMode = currentStackFolderMode,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isTabButtonVisible = true,
                    isAddressBarDockingEnabled = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = { mode ->
                        currentOverviewMode = mode
                        overviewMode.set(mode)
                    },
                    onTabStackFolderModeChanged = { mode ->
                        currentStackFolderMode = mode
                        stackFolderMode.set(mode)
                    },
                    onTabListStartsAtBottomChanged = listStartsAtBottom::set,
                    onAutomaticTabSortingEnabledChanged = automaticSorting::set,
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onTabButtonVisibleChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_hero))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_list))
            .assertExists()
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_grid))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.tab_overview_mode_hero))
            .assertExists()
            .performClick()
        composeRule.onNodeWithTag(TabSettingsTestTags.ListStartsAtBottom)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag(TabSettingsTestTags.AutomaticSorting)
            .assertIsEnabled()
            .performClick()

        assertTrue(automaticSorting.get())
        assertTrue(listStartsAtBottom.get())
        assertTrue(overviewMode.get() == TabOverviewMode.List)
        assertTrue(stackFolderMode.get() == TabOverviewMode.Hero)
    }

    @Test
    fun addressBarDockingSwitchCanDisableParking() {
        var enabled by mutableStateOf(true)
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isTabButtonVisible = true,
                    isAddressBarDockingEnabled = enabled,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onTabButtonVisibleChanged = {},
                    onAddressBarDockingEnabledChanged = { enabled = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(TabSettingsTestTags.AddressBarDocking)
            .performScrollTo()
            .performClick()

        assertFalse(enabled)
    }
}
