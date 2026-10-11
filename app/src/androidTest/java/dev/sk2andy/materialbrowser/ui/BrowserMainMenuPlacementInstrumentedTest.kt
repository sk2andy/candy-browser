package dev.sk2andy.materialbrowser.ui

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMainMenuPlacementInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bottomMenuLeavesTriggerUncoveredAndTriggerTapDismissesIt() {
        showMenu(topAnchor = false)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val anchor = requireNotNull(device.findObject(By.desc("placement-anchor")))
        val anchorBounds = anchor.visibleBounds
        composeRule.onNodeWithContentDescription("Open placement menu").performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
        val title = requireNotNull(device.wait(Until.findObject(By.text(menuTitle())), 5_000))
        assertTrue(title.visibleBounds.bottom < anchorBounds.top)

        device.click(anchorBounds.centerX(), anchorBounds.centerY())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
    }

    @Test
    fun topMenuOpensBelowTriggerWhenItFits() {
        showMenu(topAnchor = true)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val anchor = requireNotNull(device.findObject(By.desc("placement-anchor")))
        val anchorBounds = anchor.visibleBounds
        composeRule.onNodeWithContentDescription("Open placement menu").performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
        val title = requireNotNull(device.wait(Until.findObject(By.text(menuTitle())), 5_000))
        assertTrue(title.visibleBounds.top >= anchorBounds.bottom)
    }

    @Test
    fun menuTakesFocusAndBackReturnsItToActivity() {
        showMenu(topAnchor = false)
        composeRule.onNodeWithContentDescription("Open placement menu").performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertFalse(activity.window.decorView.hasWindowFocus())
        }
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()
        composeRule.activityRule.scenario.onActivity { activity ->
            assertTrue(activity.window.decorView.hasWindowFocus())
        }
    }

    @Test
    fun settingsRemainReachableAndReopeningStartsAtTop() {
        showMenu(topAnchor = false, menuScreenHeightDp = null)
        composeRule.onNodeWithContentDescription("Open placement menu").performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Settings)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Menu).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Open placement menu").performClick()
        composeRule.onNodeWithText(menuTitle()).assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserMainMenuTestTags.Favorite).assertIsDisplayed()
    }

    private fun menuTitle(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.browser_menu_title)

    private fun showMenu(topAnchor: Boolean, menuScreenHeightDp: Int? = 450) {
        composeRule.setContent {
            val configuration = LocalConfiguration.current
            val menuConfiguration = remember(configuration, menuScreenHeightDp) {
                Configuration(configuration).apply {
                    if (menuScreenHeightDp != null) screenHeightDp = menuScreenHeightDp
                }
            }
            CompositionLocalProvider(LocalConfiguration provides menuConfiguration) {
                MaterialBrowserTheme {
                    var expanded by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxSize().padding(24.dp, 20.dp)) {
                        Box(
                            Modifier.align(
                                if (topAnchor) Alignment.TopEnd else Alignment.BottomEnd,
                            ).size(48.dp).semantics { contentDescription = "placement-anchor" },
                        ) {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.MoreVert, "Open placement menu")
                            }
                            BrowserMainMenu(
                                expanded = expanded,
                                backdropSource = null,
                                onDismissRequest = { expanded = false },
                                pageSubtitle = "developer.android.com",
                                canGoBack = true,
                                canGoForward = true,
                                isLoading = false,
                                canToggleFavorite = true,
                                isFavorite = false,
                                isPinned = false,
                                canUsePageActions = true,
                                canOpenReader = true,
                                canTranslatePage = true,
                                canToggleDomainMute = true,
                                isDomainMuted = false,
                                canToggleAlwaysBlockPopups = true,
                                isAlwaysBlockPopupsEnabled = false,
                                canToggleDesktopView = true,
                                isDesktopView = false,
                                canToggleCookieBannerRemoval = true,
                                isCookieBannerRemovalEnabled = false,
                                canToggleForceVerticalScrolling = true,
                                isForceVerticalScrollingEnabled = false,
                                canToggleForcePageZooming = true,
                                isForcePageZoomingEnabled = false,
                                canToggleForceSafeArea = true,
                                isForceSafeAreaEnabled = false,
                                canAddSiteCapsule = true,
                                canSnooze = true,
                                snoozedTabCount = 0,
                                onBack = {},
                                onForward = {},
                                onReloadOrStop = {},
                                onToggleFavorite = {},
                                onTogglePinned = {},
                                onShare = {},
                                onOpenExternal = {},
                                onPrint = {},
                                onOpenReader = {},
                                onTranslate = {},
                                onDomainMutedChange = {},
                                onAlwaysBlockPopupsChange = {},
                                onDesktopViewChange = {},
                                onCookieBannerRemovalEnabledChange = {},
                                onForceVerticalScrollingChange = {},
                                onForcePageZoomingChange = {},
                                onForceSafeAreaChange = {},
                                onOpenCandyTrail = {},
                                onAddSiteCapsule = {},
                                onSummarize = {},
                                onSnooze = {},
                                onSnoozedTabs = {},
                                onDockAddressBar = {},
                                onHistory = {},
                                onSettings = {},
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}
