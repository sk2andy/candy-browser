package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isTabButtonVisible = true,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
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
}
