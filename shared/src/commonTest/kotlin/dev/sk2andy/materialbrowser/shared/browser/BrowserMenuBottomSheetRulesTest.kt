package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserMenuBottomSheetRulesTest {
    @Test
    fun `shortcuts move reader next to libraries without duplicating actions`() {
        val items = BrowserFeatureMenuRules.items(BrowserFeatureMenuState())
        val sections = BrowserMenuBottomSheetRules.sections(items)

        assertEquals(
            listOf(
                BrowserFeatureMenuAction.OpenDownloads,
                BrowserFeatureMenuAction.OpenReader,
                BrowserFeatureMenuAction.OpenFavorites,
                BrowserFeatureMenuAction.OpenHistory,
                BrowserFeatureMenuAction.OpenSettings,
            ),
            sections.shortcuts.map { it.action },
        )
        val allItems = sections.shortcuts + sections.pageActions + sections.pageControls + sections.footer
        assertEquals(items.size, allItems.size)
        assertEquals(items.toSet(), allItems.toSet())
        assertFalse(sections.pageActions.any { it.action == BrowserFeatureMenuAction.OpenReader })
        assertTrue(sections.pageActions.any { it.action == BrowserFeatureMenuAction.DuplicateTab })
        assertTrue(sections.pageActions.any { it.action == BrowserFeatureMenuAction.OpenSnoozedTabs })
    }

    @Test
    fun `sections preserve filtered actions availability and private restrictions`() {
        val items = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(hasPage = true, canToggleFavorite = false, canSnooze = false),
        ).filterNot { it.action == BrowserFeatureMenuAction.OpenDownloads }
        val sections = BrowserMenuBottomSheetRules.sections(items)

        assertFalse(sections.shortcuts.any { it.action == BrowserFeatureMenuAction.OpenDownloads })
        assertFalse(sections.footer.first { it.action == BrowserFeatureMenuAction.ToggleFavorite }.enabled)
        assertFalse(sections.pageActions.first { it.action == BrowserFeatureMenuAction.SnoozeTab }.enabled)
        assertTrue(sections.pageControls.all { it.kind == BrowserFeatureMenuItemKind.Toggle })
        assertTrue(sections.footer.all { it.section == BrowserFeatureMenuSection.Toolbar })
    }

    @Test
    fun `empty menu produces empty regions`() {
        val sections = BrowserMenuBottomSheetRules.sections(emptyList())

        assertTrue(sections.shortcuts.isEmpty())
        assertTrue(sections.pageActions.isEmpty())
        assertTrue(sections.pageControls.isEmpty())
        assertTrue(sections.footer.isEmpty())
        assertEquals(listOf(null, null, null, null, null), sections.footerSlots)
        assertEquals(listOf(null, null, null, null, null), sections.shortcutSlots)
    }

    @Test
    fun `custom footer removes chosen shortcuts and toggles from middle without duplication`() {
        val items = BrowserFeatureMenuRules.items(
            BrowserFeatureMenuState(canToggleDesktopView = true, isDesktopView = true),
        )
        val sections = BrowserMenuBottomSheetRules.sections(
            items,
            toolbarEntries = listOf(
                BrowserMenuEntry.OpenDownloads,
                BrowserMenuEntry.DesktopView,
                null,
                BrowserMenuEntry.Share,
                BrowserMenuEntry.Pin,
            ),
        )

        assertEquals(
            listOf(
                BrowserFeatureMenuAction.OpenDownloads,
                BrowserFeatureMenuAction.ToggleDesktopView,
                null,
                BrowserFeatureMenuAction.Share,
                BrowserFeatureMenuAction.TogglePinned,
            ),
            sections.footerSlots.map { it?.action },
        )
        assertEquals(null, sections.shortcutSlots[0])
        assertFalse(sections.pageControls.any { it.action == BrowserFeatureMenuAction.ToggleDesktopView })
        assertTrue(sections.footer.first { it.action == BrowserFeatureMenuAction.ToggleDesktopView }.checked == true)
        assertTrue(sections.pageActions.any { it.action == BrowserFeatureMenuAction.Back })
        assertTrue(sections.pageActions.any { it.action == BrowserFeatureMenuAction.ToggleFavorite })
        val allItems = sections.shortcuts + sections.pageActions + sections.pageControls + sections.footer
        assertEquals(items.size, allItems.size)
        assertEquals(items.toSet(), allItems.toSet())
    }

    @Test
    fun `hidden selected actions leave empty footer slots and keep visible slots in place`() {
        val layout = BrowserMenuLayoutRules.update(
            BrowserMenuLayout.Default,
            BrowserMenuEntry.Share,
            BrowserMenuLocation.Nowhere,
        )
        val items = BrowserMenuLayoutRules.visibleItems(
            BrowserFeatureMenuRules.items(BrowserFeatureMenuState()),
            layout,
            BrowserMenuSurface.Tab,
        )
        val sections = BrowserMenuBottomSheetRules.sections(
            items,
            toolbarEntries = listOf(BrowserMenuEntry.Share, null, BrowserMenuEntry.Back),
        )

        assertEquals(
            listOf(null, null, BrowserFeatureMenuAction.Back, null, null),
            sections.footerSlots.map { it?.action },
        )
        assertFalse(sections.pageActions.any { it.action == BrowserFeatureMenuAction.Share })
        assertFalse(sections.footer.single().enabled)
    }

    @Test
    fun `reload slot uses current stop action and disabled selected controls keep availability`() {
        val items = BrowserFeatureMenuRules.items(BrowserFeatureMenuState(isLoading = true))
        val sections = BrowserMenuBottomSheetRules.sections(
            items,
            toolbarEntries = listOf(BrowserMenuEntry.Reload, BrowserMenuEntry.DesktopView),
        )

        assertEquals(BrowserFeatureMenuAction.Stop, sections.footerSlots[0]?.action)
        assertTrue(sections.footerSlots[0]?.enabled == true)
        assertFalse(sections.footerSlots[1]?.enabled == true)
        assertFalse(sections.pageControls.any { it.action == BrowserFeatureMenuAction.ToggleDesktopView })
    }
}
