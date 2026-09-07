package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserFeatureMenuRulesTest {
    @Test
    fun `full menu has stable section and action order`() {
        val items = BrowserFeatureMenuRules.items(
            state = BrowserFeatureMenuState(
                hasPage = true,
                canDockAddressBar = true,
                canToggleForceVerticalScrolling = true,
                canToggleForcePageZooming = true,
                canToggleForceSafeArea = true,
                overflowPageActions = listOf(
                    BrowserFeatureMenuAction.ShowTabs,
                    BrowserFeatureMenuAction.NewTab,
                    BrowserFeatureMenuAction.CloseTab,
                ),
            ),
        )

        assertEquals(
            listOf(
                BrowserFeatureMenuAction.Back,
                BrowserFeatureMenuAction.Forward,
                BrowserFeatureMenuAction.Reload,
                BrowserFeatureMenuAction.ToggleFavorite,
                BrowserFeatureMenuAction.TogglePinned,
                BrowserFeatureMenuAction.ShowTabs,
                BrowserFeatureMenuAction.NewTab,
                BrowserFeatureMenuAction.CloseTab,
                BrowserFeatureMenuAction.DuplicateTab,
                BrowserFeatureMenuAction.OpenReader,
                BrowserFeatureMenuAction.TranslatePage,
                BrowserFeatureMenuAction.FindInPage,
                BrowserFeatureMenuAction.Share,
                BrowserFeatureMenuAction.OpenExternal,
                BrowserFeatureMenuAction.Print,
                BrowserFeatureMenuAction.ToggleCookieBannerRemoval,
                BrowserFeatureMenuAction.ToggleForceVerticalScrolling,
                BrowserFeatureMenuAction.ToggleForcePageZooming,
                BrowserFeatureMenuAction.ToggleForceSafeArea,
                BrowserFeatureMenuAction.ToggleAlwaysBlockPopups,
                BrowserFeatureMenuAction.ToggleDesktopView,
                BrowserFeatureMenuAction.ToggleDomainMute,
                BrowserFeatureMenuAction.OpenCandyTrail,
                BrowserFeatureMenuAction.AddSiteCapsule,
                BrowserFeatureMenuAction.Summarize,
                BrowserFeatureMenuAction.SnoozeTab,
                BrowserFeatureMenuAction.DockAddressBar,
                BrowserFeatureMenuAction.OpenSnoozedTabs,
                BrowserFeatureMenuAction.OpenHistory,
                BrowserFeatureMenuAction.OpenSettings,
            ),
            items.map(BrowserFeatureMenuItem::action),
        )
    }

    @Test
    fun `duplicate tab is shared and follows page availability`() {
        val unavailable = BrowserFeatureMenuRules.items(BrowserFeatureMenuState(hasPage = false))
            .single { it.action == BrowserFeatureMenuAction.DuplicateTab }
        val available = BrowserFeatureMenuRules.items(BrowserFeatureMenuState(hasPage = true))
            .single { it.action == BrowserFeatureMenuAction.DuplicateTab }

        assertFalse(unavailable.enabled)
        assertTrue(available.enabled)
    }

    @Test
    fun `loading replaces reload with stop`() {
        val toolbar = BrowserFeatureMenuRules.items(
            state = BrowserFeatureMenuState(isLoading = true),
        ).filter { it.section == BrowserFeatureMenuSection.Toolbar }

        assertEquals(BrowserFeatureMenuAction.Stop, toolbar[2].action)
        assertEquals(BrowserFeatureMenuLabelKey.StopLoading, toolbar[2].labelKey)
        assertTrue(toolbar[2].enabled)
    }

    @Test
    fun `toggle and selectable toolbar commands expose checked state`() {
        val items = BrowserFeatureMenuRules.items(
            state = BrowserFeatureMenuState(
                isDesktopView = true,
                isFavorite = true,
                isPinned = true,
            ),
        )

        val desktop = items.single { it.action == BrowserFeatureMenuAction.ToggleDesktopView }
        val favorite = items.single { it.action == BrowserFeatureMenuAction.ToggleFavorite }
        val pinned = items.single { it.action == BrowserFeatureMenuAction.TogglePinned }
        val share = items.single { it.action == BrowserFeatureMenuAction.Share }
        assertTrue(desktop.checked == true)
        assertTrue(favorite.checked == true)
        assertTrue(pinned.checked == true)
        assertNull(share.checked)
    }

    @Test
    fun `site compatibility group follows current Android presentation rule`() {
        val hidden = BrowserFeatureMenuRules.items(BrowserFeatureMenuState())
        val visible = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(canToggleForcePageZooming = true),
        )

        assertFalse(hidden.any { it.action == BrowserFeatureMenuAction.ToggleCookieBannerRemoval })
        assertTrue(visible.any { it.action == BrowserFeatureMenuAction.ToggleCookieBannerRemoval })
        assertTrue(visible.any { it.action == BrowserFeatureMenuAction.ToggleForcePageZooming })
    }

    @Test
    fun `firefox extensions are Android additive capability`() {
        val common = BrowserFeatureMenuRules.items(BrowserFeatureMenuState())
        val android = BrowserFeatureMenuRules.items(
            state = BrowserFeatureMenuState(),
            capabilities = BrowserFeatureMenuCapabilities(supportsFirefoxExtensions = true),
        )

        assertFalse(common.any { it.action == BrowserFeatureMenuAction.OpenFirefoxExtensions })
        assertTrue(android.any { it.action == BrowserFeatureMenuAction.OpenFirefoxExtensions })
        assertEquals(BrowserFeatureMenuAction.OpenSettings, android.last().action)
    }

    @Test
    fun `topping commands preserve dynamic identity caption and script`() {
        val item = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(
                toppingCommands = listOf(
                    BrowserToppingMenuCommand(
                        scriptId = "reader-tools",
                        commandId = "focus",
                        caption = "Focus mode",
                        scriptName = "Reader Tools",
                    ),
                ),
            ),
        ).single { it.section == BrowserFeatureMenuSection.Toppings }

        assertEquals("topping:reader-tools:focus", item.stableId)
        assertEquals("Focus mode", item.dynamicLabel)
        assertEquals("Reader Tools", item.supportingText)
        assertEquals("reader-tools", item.toppingScriptId)
        assertEquals("focus", item.toppingCommandId)
    }

    @Test
    fun `park-right overflow replaces dock action`() {
        val items = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(
                canDockAddressBar = true,
                overflowPageActions = listOf(BrowserFeatureMenuAction.ParkAddressBarRight),
            ),
        )

        assertTrue(items.any { it.action == BrowserFeatureMenuAction.ParkAddressBarRight })
        assertFalse(items.any { it.action == BrowserFeatureMenuAction.DockAddressBar })
    }
}
