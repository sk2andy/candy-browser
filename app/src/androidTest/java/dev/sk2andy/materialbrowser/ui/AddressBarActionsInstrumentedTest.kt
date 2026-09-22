package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.AddressBarAction
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarActionsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeActionInvokesHomeCallback() {
        var homeInvocations = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                AddressBarActionButton(
                    action = AddressBarAction.Home,
                    state = actionState(),
                    callbacks = actionCallbacks(onHome = { homeInvocations++ }),
                )
            }
        }

        composeRule.onNodeWithTag(AddressBarActionTestTags.action(AddressBarAction.Home))
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, homeInvocations) }
    }

    private fun actionState() = AddressBarActionState(
        tabCount = 1,
        isLoading = false,
        canGoBack = false,
        canGoForward = false,
        canToggleFavorite = false,
        isFavorite = false,
        isPinned = false,
        canToggleDesktopView = false,
        isDesktopView = false,
        canToggleForceVerticalScrolling = false,
        isForceVerticalScrollingEnabled = false,
        canUsePageActions = false,
        canOpenReader = false,
        canCloseTab = true,
        canParkRight = false,
        newTabPulseScale = 1f,
    )

    private fun actionCallbacks(onHome: () -> Unit) = AddressBarActionCallbacks(
        onTabs = {},
        onToggleFavorite = {},
        onTogglePinned = {},
        onDesktopViewChange = {},
        onForceVerticalScrollingChange = {},
        onReaderStudio = {},
        onFindInPage = {},
        onShare = {},
        onPrint = {},
        onNewTab = {},
        onHome = onHome,
        onReloadOrStop = {},
        onCloseTab = {},
        onBack = {},
        onForward = {},
        onParkRight = {},
        onNewTabButtonBounds = {},
    )
}
