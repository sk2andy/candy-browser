package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InactiveTabLifetimeSettingsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fixedChoicesRemainReachableSelectableAndPersisted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = BrowserSessionStore(context)
        val originalLifetime = store.loadInactiveTabLifetime()
        var selected by mutableStateOf(InactiveTabLifetime.Never)
        val labels = mutableMapOf<InactiveTabLifetime, String>()
        composeRule.setContent {
            InactiveTabLifetime.entries.forEach { labels[it] = it.displayName() }
            MaterialBrowserTheme(settings = AppearanceSettings(animationsEnabled = false)) {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = selected,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Grid,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 40,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = true,
                    onInactiveTabLifetimeChanged = {
                        store.saveInactiveTabLifetime(it)
                        selected = it
                    },
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onAddressBarActions = {},
                    onBack = {},
                    section = TabSettingsSection.Lifecycle,
                )
            }
        }
        try {
            InactiveTabLifetime.entries.forEach { lifetime ->
                composeRule.waitForIdle()
                composeRule.onNodeWithText(labels.getValue(lifetime))
                    .performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
                composeRule.runOnIdle {
                    assertEquals(lifetime, selected)
                    assertEquals(lifetime, BrowserSessionStore(context).loadInactiveTabLifetime())
                }
            }
            // Let the last radio ripple and render frame settle before capturing the production page.
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            UiDevice.getInstance(instrumentation).waitForIdle()
            // Capture the actual production page after scrolling through every choice.
            val evidenceDirectory = File(context.getExternalFilesDir(null), "issue348").apply { mkdirs() }
            assertTrue(UiDevice.getInstance(instrumentation)
                .takeScreenshot(File(evidenceDirectory, "tab-lifetimes.png")))
        } finally {
            store.saveInactiveTabLifetime(originalLifetime)
        }
    }
}
