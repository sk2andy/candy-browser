package dev.sk2andy.materialbrowser.ui

import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.StatusBarScrollAccessibilityContract
import dev.sk2andy.materialbrowser.R
import androidx.activity.ComponentActivity
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.TabOverviewMode
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuAction
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserFeatureMenuState
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuEntry
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayoutRules
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLocation
import dev.sk2andy.materialbrowser.shared.browser.BrowserMenuSurface
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusBarScrollAcceptanceInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun productionTabSettingsCanScrollEnableAndConfigureTarget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BrowserSessionStore(context)
        val originalFeatureEnabled = store.loadStatusBarScrollEnabled()
        val targetWidthPercent = mutableIntStateOf(20)
        store.saveStatusBarScrollEnabled(true)
        try {
        val enabled = mutableStateOf(false)
        val targetWidthPreviewEvents = mutableListOf<Pair<Int, Boolean>>()
        var openedAccessibilitySettings = false
        composeRule.setContent {
            MaterialBrowserTheme {
                TabsAndGesturesSettingsPage(
                    inactiveTabLifetime = InactiveTabLifetime.Never,
                    residentTabLimit = 10,
                    tabOverviewMode = TabOverviewMode.Hero,
                    tabStackFolderMode = TabOverviewMode.Grid,
                    tabListStartsAtBottom = false,
                    automaticTabSortingEnabled = false,
                    dismissResistancePercent = 50,
                    profilesEnabled = true,
                    isAddressBarDockingEnabled = false,
                    isStatusBarScrollEnabled = enabled.value,
                    statusBarScrollTargetWidthPercent = targetWidthPercent.intValue,
                    onInactiveTabLifetimeChanged = {},
                    onResidentTabLimitChanged = {},
                    onTabOverviewModeChanged = {},
                    onTabStackFolderModeChanged = {},
                    onTabListStartsAtBottomChanged = {},
                    onAutomaticTabSortingEnabledChanged = {},
                    onDismissResistancePercentChanged = {},
                    onProfilesEnabledChanged = {},
                    onAddressBarDockingEnabledChanged = {},
                    onStatusBarScrollEnabledChanged = { enabled.value = it },
                    onStatusBarScrollTargetWidthPercentChanged = { targetWidthPercent.intValue = it },
                    onStatusBarScrollTargetWidthPreview = { width, finished ->
                        targetWidthPreviewEvents += width to finished
                        context.sendBroadcast(
                            Intent(StatusBarScrollAccessibilityContract.ACTION_PREVIEW_TARGET)
                                .setPackage(context.packageName)
                                .putExtra(StatusBarScrollAccessibilityContract.EXTRA_TARGET_WIDTH_PERCENT, width)
                                .putExtra(StatusBarScrollAccessibilityContract.EXTRA_PREVIEW_FINISHED, finished),
                        )
                    },
                    onAddressBarActions = {},
                    onOpenStatusBarScrollAccessibilitySettings = {
                        openedAccessibilitySettings = true
                    },
                    onBack = {},
                )
            }
        }

        val toggle = composeRule.onNodeWithTag(TabSettingsTestTags.StatusBarScrollEnabled)
        toggle.performScrollTo()
        toggle.assertIsDisplayed()
        val widthSlider = composeRule.onNodeWithTag(TabSettingsTestTags.StatusBarScrollWidth)
        widthSlider.performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        widthSlider.performTouchInput {
            click(Offset(width * 0.92f, height / 2f))
        }
        assertTrue("Disabled width control must not emit preview events", targetWidthPreviewEvents.isEmpty())
        assertEquals("Disabled width control must retain its saved value", 20, targetWidthPercent.intValue)
        toggle.performClick()
        composeRule.waitForIdle()
        assertEquals(true, enabled.value)
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.settings_status_bar_scroll_accessibility_dialog_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.settings_status_bar_scroll_accessibility_open),
        ).performClick()
        assertEquals(true, openedAccessibilitySettings)

        composeRule.waitForIdle()
        widthSlider
            .performScrollTo()
            .assertIsDisplayed()
            .performTouchInput {
                click(Offset(width * 0.92f, height / 2f))
            }
        composeRule.waitForIdle()
        assertTrue("Target width callback should report an adjustable percentage", targetWidthPercent.intValue > 20)
        assertTrue(targetWidthPreviewEvents.any { !it.second })
        assertTrue(targetWidthPreviewEvents.any { it.second })

        val configuredWidthPercent = targetWidthPercent.intValue
        val lastPreviewEventCount = targetWidthPreviewEvents.size
        toggle.performScrollTo().performClick()
        composeRule.waitForIdle()
        widthSlider.performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        widthSlider.performTouchInput {
            click(Offset(width * 0.92f, height / 2f))
        }
        assertTrue(
            "Disabling the feature must stop width preview callbacks",
            targetWidthPreviewEvents.size == lastPreviewEventCount,
        )
        assertEquals(
            "Disabling the feature must retain the configured width",
            configuredWidthPercent,
            targetWidthPercent.intValue,
        )
        composeRule.onNodeWithText(
            composeRule.activity.getString(
                R.string.settings_status_bar_scroll_width_summary,
                targetWidthPercent.intValue,
            ),
        ).assertIsDisplayed()

        val screenshotDirectory = File(
            requireNotNull(composeRule.activity.getExternalFilesDir(null)),
            "issue323-acceptance",
        ).apply { mkdirs() }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(File(screenshotDirectory, "status-bar-scroll-settings.png"))
        } finally {
            context.sendBroadcast(
                Intent(StatusBarScrollAccessibilityContract.ACTION_PREVIEW_TARGET)
                    .setPackage(context.packageName)
                    .putExtra(
                        StatusBarScrollAccessibilityContract.EXTRA_TARGET_WIDTH_PERCENT,
                        targetWidthPercent.intValue,
                    )
                    .putExtra(StatusBarScrollAccessibilityContract.EXTRA_PREVIEW_FINISHED, true),
            )
            store.saveStatusBarScrollEnabled(originalFeatureEnabled)
        }
    }

    @Test
    fun productionMenuSettingsHideStatusBarEntryWhenFeatureIsOff() {
        val enabled = mutableStateOf(false)
        composeRule.setContent {
            MaterialBrowserTheme {
                BrowserMenuSettingsPage(
                    layout = dev.sk2andy.materialbrowser.shared.browser.BrowserMenuLayout.Default,
                    statusBarScrollEnabled = enabled.value,
                    onLocationChanged = { _, _ -> },
                    onBack = {},
                )
            }
        }
        val title = composeRule.activity.getString(R.string.settings_status_bar_scroll_title)
        composeRule.onNodeWithText(title).assertDoesNotExist()

        composeRule.runOnUiThread { enabled.value = true }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(title).performScrollTo().assertIsDisplayed()

        val screenshotDirectory = File(
            requireNotNull(composeRule.activity.getExternalFilesDir(null)),
            "issue323-acceptance",
        ).apply { mkdirs() }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .takeScreenshot(File(screenshotDirectory, "status-bar-scroll-menu-settings.png"))
    }

    @Test
    fun menuActionCanBeHiddenPersistedAndRestoredIndependentlyOfFeatureAndDomainState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BrowserSessionStore(context)
        val originalLayout = store.loadBrowserMenuLayout()
        val originalFeatureEnabled = store.loadStatusBarScrollEnabled()
        val layoutState = mutableStateOf(BrowserMenuLayoutRules.normalize(originalLayout))
        val featureEnabledState = mutableStateOf(true)
        try {
            // The action is default-visible and the global feature is enabled, while the
            // per-domain state is disabled. Hiding the action is a separate menu-layout change.
            store.saveStatusBarScrollEnabled(true)
            composeRule.setContent {
                MaterialBrowserTheme {
                    BrowserMenuSettingsPage(
                        layout = layoutState.value,
                        statusBarScrollEnabled = featureEnabledState.value,
                        onLocationChanged = { entry, location ->
                            layoutState.value = BrowserMenuLayoutRules.update(layoutState.value, entry, location)
                            store.saveBrowserMenuLayout(layoutState.value)
                        },
                        onBack = {},
                    )
                }
            }
            val actionRow = composeRule.onNodeWithTag(
                dev.sk2andy.materialbrowser.shared.ui.settings.BrowserMenuSettingsTestTags.entry(
                    BrowserMenuEntry.StatusBarScroll,
                ),
            )
            actionRow.performScrollTo().assertIsDisplayed().performClick()
            composeRule.onNodeWithText(
                composeRule.activity.getString(R.string.settings_menu_location_nowhere),
            ).performClick()
            composeRule.waitForIdle()

            val restoredHiddenLayout = BrowserSessionStore(context).loadBrowserMenuLayout()
            assertEquals(
                BrowserMenuLocation.Nowhere,
                BrowserMenuLayoutRules.location(restoredHiddenLayout, BrowserMenuEntry.StatusBarScroll),
            )
            val globallyEnabledItems = BrowserFeatureMenuRules.items(
                BrowserFeatureMenuState(canToggleStatusBarScroll = true, isStatusBarScrollEnabled = false),
            )
            assertTrue(globallyEnabledItems.any { it.action == BrowserFeatureMenuAction.ToggleStatusBarScroll })
            assertTrue(
                BrowserMenuLayoutRules.visibleItems(
                    globallyEnabledItems,
                    restoredHiddenLayout,
                    BrowserMenuSurface.Tab,
                ).none { it.action == BrowserFeatureMenuAction.ToggleStatusBarScroll },
            )

            val restoredVisibleLayout = BrowserMenuLayoutRules.update(
                restoredHiddenLayout,
                BrowserMenuEntry.StatusBarScroll,
                BrowserMenuLocation.Tab,
            )
            store.saveBrowserMenuLayout(restoredVisibleLayout)
            val roundTrippedVisibleLayout = BrowserSessionStore(context).loadBrowserMenuLayout()
            assertTrue(
                BrowserMenuLayoutRules.visibleItems(
                    globallyEnabledItems,
                    roundTrippedVisibleLayout,
                    BrowserMenuSurface.Tab,
                ).any { it.action == BrowserFeatureMenuAction.ToggleStatusBarScroll },
            )

            val globallyDisabledItems = BrowserFeatureMenuRules.items(
                BrowserFeatureMenuState(canToggleStatusBarScroll = false, isStatusBarScrollEnabled = false),
            )
            assertTrue(globallyDisabledItems.none { it.action == BrowserFeatureMenuAction.ToggleStatusBarScroll })
            composeRule.runOnUiThread { featureEnabledState.value = false }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(
                composeRule.activity.getString(R.string.settings_status_bar_scroll_title),
            ).assertDoesNotExist()
        } finally {
            store.saveBrowserMenuLayout(originalLayout)
            store.saveStatusBarScrollEnabled(originalFeatureEnabled)
        }
    }

    @Test
    fun perDomainPreferenceSurvivesStoreReconstruction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val writer = BrowserSessionStore(context)
        val original = writer.loadStatusBarScrollDisabledDomains()
        val profileId = "issue323-acceptance-${System.currentTimeMillis()}"
        val expected = original + (profileId to setOf("example.com"))
        try {
            writer.saveStatusBarScrollDisabledDomains(expected)
            val reconstructed = BrowserSessionStore(context).loadStatusBarScrollDisabledDomains()
            assertEquals(setOf("example.com"), reconstructed[profileId])
        } finally {
            writer.saveStatusBarScrollDisabledDomains(original)
        }
    }
}
