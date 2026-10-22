package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserFeatureMenuRulesTest {
    @Test
    fun `reading destinations require platform support and unread count stays nonnegative`() {
        val state = BrowserFeatureMenuState(canDiscoverFeeds = true, unreadFeedCount = 12)
        val unsupported = BrowserFeatureMenuRules.items(state)
        assertFalse(unsupported.any { it.section == BrowserFeatureMenuSection.Reading })
        assertFalse(unsupported.any { it.action == BrowserFeatureMenuAction.DiscoverFeeds })

        val capabilities = BrowserFeatureMenuCapabilities(supportsFeeds = true, supportsLibrary = true)
        val supported = BrowserFeatureMenuRules.items(state, capabilities)
        assertEquals(12, supported.single { it.action == BrowserFeatureMenuAction.OpenFeeds }.unreadCount)
        assertEquals(
            listOf(BrowserFeatureMenuAction.OpenFeeds, BrowserFeatureMenuAction.OpenLibrary),
            supported.filter { it.section == BrowserFeatureMenuSection.Reading }.map { it.action },
        )
        val noUnread = BrowserFeatureMenuRules.items(state.copy(unreadFeedCount = -1), capabilities)
        assertEquals(0, noUnread.single { it.action == BrowserFeatureMenuAction.OpenFeeds }.unreadCount)
    }

    @Test
    fun `discovery requires a supported page while collection does not`() {
        val capabilities = BrowserFeatureMenuCapabilities(supportsFeeds = true, supportsLibrary = true)
        val blank = BrowserFeatureMenuRules.items(BrowserFeatureMenuState(), capabilities)
        assertFalse(blank.any { it.action == BrowserFeatureMenuAction.DiscoverFeeds })
        assertTrue(blank.single { it.action == BrowserFeatureMenuAction.OpenLibrary }.enabled)
        val page = BrowserFeatureMenuRules.items(BrowserFeatureMenuState(canDiscoverFeeds = true), capabilities)
        assertEquals(BrowserFeatureMenuSection.Page, page.single {
            it.action == BrowserFeatureMenuAction.DiscoverFeeds
        }.section)
    }

    @Test
    fun `reading collection follows page actions and custom workflows are last`() {
        val items = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(toppingCommands = listOf(BrowserToppingMenuCommand("tools", "focus", "Focus", "Tools"))),
            BrowserFeatureMenuCapabilities(supportsFeeds = true, supportsLibrary = true),
        )
        val readingIndex = items.indexOfFirst { it.section == BrowserFeatureMenuSection.Reading }
        assertTrue(items.indexOfLast { it.section == BrowserFeatureMenuSection.Page } < readingIndex)
        assertEquals(BrowserFeatureMenuSection.Toppings, items.last().section)
        val hiddenFeeds = BrowserMenuLayoutRules.visibleItems(
            items,
            BrowserMenuLayoutRules.update(BrowserMenuLayout.Default, BrowserMenuEntry.OpenFeeds, BrowserMenuLocation.Nowhere),
            BrowserMenuSurface.Tab,
        )
        assertFalse(hiddenFeeds.any { it.action == BrowserFeatureMenuAction.OpenFeeds })
        assertTrue(hiddenFeeds.any { it.action == BrowserFeatureMenuAction.OpenLibrary })
    }

    @Test
    fun `status bar scroll action is hidden unless global feature and domain are enabled`() {
        assertFalse(
            BrowserFeatureMenuRules.items(BrowserFeatureMenuState())
                .any { it.action == BrowserFeatureMenuAction.ToggleStatusBarScroll },
        )
        val enabled = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(canToggleStatusBarScroll = true, isStatusBarScrollEnabled = true),
        ).single { it.action == BrowserFeatureMenuAction.ToggleStatusBarScroll }
        assertEquals(true, enabled.checked)
        assertEquals(BrowserFeatureMenuLabelKey.DisableStatusBarScrollForDomain, enabled.labelKey)
        val disabled = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(canToggleStatusBarScroll = true, isStatusBarScrollEnabled = false),
        ).single { it.action == BrowserFeatureMenuAction.ToggleStatusBarScroll }
        assertEquals(false, disabled.checked)
        assertEquals(BrowserFeatureMenuLabelKey.EnableStatusBarScrollForDomain, disabled.labelKey)
    }

    @Test
    fun `page annotations require support and a usable regular page`() {
        val state = BrowserFeatureMenuState(canOpenWebAnnotations = true)
        assertFalse(BrowserFeatureMenuRules.items(state).any { it.action == BrowserFeatureMenuAction.OpenWebAnnotations })
        val capabilities = BrowserFeatureMenuCapabilities(supportsWebAnnotations = true)
        val item = BrowserFeatureMenuRules.items(state, capabilities)
            .single { it.action == BrowserFeatureMenuAction.OpenWebAnnotations }

        assertTrue(item.enabled)
        assertEquals(BrowserFeatureMenuSection.Page, item.section)
        assertEquals(BrowserFeatureMenuLabelKey.WebAnnotations, item.labelKey)
        val unavailable = BrowserFeatureMenuRules.items(BrowserFeatureMenuState(), capabilities)
            .single { it.action == BrowserFeatureMenuAction.OpenWebAnnotations }
        assertFalse(unavailable.enabled)
    }

    @Test
    fun `constellations requires platform support and follows profile availability`() {
        val state = BrowserFeatureMenuState(canOpenConstellations = true)
        assertFalse(BrowserFeatureMenuRules.items(state).any { it.action == BrowserFeatureMenuAction.OpenConstellations })

        val capabilities = BrowserFeatureMenuCapabilities(supportsConstellations = true)
        val items = BrowserFeatureMenuRules.items(state, capabilities)
        val constellation = items.single { it.action == BrowserFeatureMenuAction.OpenConstellations }
        assertTrue(constellation.enabled)
        assertEquals(BrowserFeatureMenuLabelKey.Constellations, constellation.labelKey)
        assertEquals(BrowserFeatureMenuSection.Candy, constellation.section)
        assertEquals(
            items.indexOfFirst { it.action == BrowserFeatureMenuAction.OpenCandyTrail } + 1,
            items.indexOf(constellation),
        )
        val unavailable = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(canOpenConstellations = false), capabilities,
        ).single { it.action == BrowserFeatureMenuAction.OpenConstellations }
        assertFalse(unavailable.enabled)
    }

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
                BrowserFeatureMenuAction.OpenFavorites,
                BrowserFeatureMenuAction.OpenDownloads,
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
    fun `website widget stays hidden without platform capability`() {
        val items = BrowserFeatureMenuRules.items(
            state = BrowserFeatureMenuState(canCreateSiteWidget = true),
        )

        assertFalse(items.any { it.action == BrowserFeatureMenuAction.CreateSiteWidget })
    }

    @Test
    fun `website widget availability follows capture policy`() {
        val capabilities = BrowserFeatureMenuCapabilities(supportsSiteWidgets = true)
        val unavailable = BrowserFeatureMenuRules.items(
            state = BrowserFeatureMenuState(),
            capabilities = capabilities,
        ).single { it.action == BrowserFeatureMenuAction.CreateSiteWidget }
        val available = BrowserFeatureMenuRules.items(
            state = BrowserFeatureMenuState(canCreateSiteWidget = true),
            capabilities = capabilities,
        ).single { it.action == BrowserFeatureMenuAction.CreateSiteWidget }

        assertFalse(unavailable.enabled)
        assertTrue(available.enabled)
        assertEquals(BrowserFeatureMenuSection.Candy, available.section)
        assertEquals(BrowserMenuEntry.CreateSiteWidget, BrowserMenuLayoutRules.entryForAction(available.action))
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
                        documentId = "frame-7",
                    ),
                ),
            ),
        ).single { it.section == BrowserFeatureMenuSection.Toppings }

        assertEquals("topping:reader-tools:focus:frame-7", item.stableId)
        assertEquals("Focus mode", item.dynamicLabel)
        assertEquals("Reader Tools", item.supportingText)
        assertEquals("reader-tools", item.toppingScriptId)
        assertEquals("focus", item.toppingCommandId)
        assertEquals("frame-7", item.toppingDocumentId)
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
