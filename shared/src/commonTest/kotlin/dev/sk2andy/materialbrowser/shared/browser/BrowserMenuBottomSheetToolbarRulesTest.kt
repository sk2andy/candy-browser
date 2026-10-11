package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserMenuBottomSheetToolbarRulesTest {
    @Test
    fun `default slots preserve current page navigation toolbar`() {
        assertEquals(
            listOf(
                BrowserMenuEntry.Back,
                BrowserMenuEntry.Forward,
                BrowserMenuEntry.Reload,
                BrowserMenuEntry.Favorite,
                BrowserMenuEntry.Pin,
            ),
            BrowserMenuLayout.Default.bottomSheetToolbarEntries,
        )
    }

    @Test
    fun `all individual tab actions are eligible but grouped and tab switcher actions are excluded`() {
        val available = BrowserMenuBottomSheetToolbarRules.availableEntries()

        assertTrue(BrowserMenuEntry.DesktopView in available)
        assertTrue(BrowserMenuEntry.OpenDownloads in available)
        assertTrue(BrowserMenuEntry.OpenFirefoxExtensions in available)
        assertTrue(BrowserMenuEntry.ShowTabs in available)
        assertTrue(available.all { it.supportsTab })
        assertFalse(BrowserMenuEntry.ToppingCommands in available)
        assertFalse(BrowserMenuEntry.FirefoxPageActions in available)
        assertFalse(BrowserMenuEntry.CloseAllTabs in available)
    }

    @Test
    fun `selecting occupied action swaps positions and empty choice removes only target`() {
        val swapped = BrowserMenuBottomSheetToolbarRules.update(
            BrowserMenuLayout.Default,
            slotIndex = 0,
            entry = BrowserMenuEntry.Pin,
        )
        val cleared = BrowserMenuBottomSheetToolbarRules.update(swapped, slotIndex = 2, entry = null)

        assertEquals(
            listOf(
                BrowserMenuEntry.Pin,
                BrowserMenuEntry.Forward,
                BrowserMenuEntry.Reload,
                BrowserMenuEntry.Favorite,
                BrowserMenuEntry.Back,
            ),
            swapped.bottomSheetToolbarEntries,
        )
        assertEquals(
            listOf(
                BrowserMenuEntry.Pin,
                BrowserMenuEntry.Forward,
                null,
                BrowserMenuEntry.Favorite,
                BrowserMenuEntry.Back,
            ),
            cleared.bottomSheetToolbarEntries,
        )
    }

    @Test
    fun `selection preserves presentation and action locations`() {
        val layout = BrowserMenuLayoutRules.update(
            BrowserMenuLayout(
                presentation = BrowserMenuPresentation.BottomSheet,
                bottomSheetContentLayout = BrowserMenuBottomSheetContentLayout.SwipePages,
            ),
            BrowserMenuEntry.Share,
            BrowserMenuLocation.Nowhere,
        )
        val updated = BrowserMenuBottomSheetToolbarRules.update(layout, slotIndex = 4, entry = BrowserMenuEntry.Share)

        assertEquals(layout.locations, updated.locations)
        assertEquals(layout.presentation, updated.presentation)
        assertEquals(layout.bottomSheetContentLayout, updated.bottomSheetContentLayout)
        assertEquals(BrowserMenuEntry.Share, updated.bottomSheetToolbarEntries[4])
    }

    @Test
    fun `normalization bounds slots and clears duplicates unsupported entries and missing slots`() {
        assertEquals(
            listOf(BrowserMenuEntry.Share, null, null, BrowserMenuEntry.DesktopView, null),
            BrowserMenuBottomSheetToolbarRules.normalize(
                listOf(
                    BrowserMenuEntry.Share,
                    BrowserMenuEntry.Share,
                    BrowserMenuEntry.ToppingCommands,
                    BrowserMenuEntry.DesktopView,
                    BrowserMenuEntry.CloseAllTabs,
                    BrowserMenuEntry.Back,
                ),
            ),
        )
        assertEquals(
            listOf(BrowserMenuEntry.Share, null, null, null, null),
            BrowserMenuBottomSheetToolbarRules.normalize(listOf(BrowserMenuEntry.Share)),
        )
    }

    @Test
    fun `invalid index or grouped action leaves current selection unchanged`() {
        val expected = BrowserMenuLayoutRules.normalize(BrowserMenuLayout.Default)

        assertEquals(expected, BrowserMenuBottomSheetToolbarRules.update(expected, -1, BrowserMenuEntry.Share))
        assertEquals(expected, BrowserMenuBottomSheetToolbarRules.update(expected, 5, BrowserMenuEntry.Share))
        assertEquals(expected, BrowserMenuBottomSheetToolbarRules.update(expected, 0, BrowserMenuEntry.ToppingCommands))
    }

    @Test
    fun `missing wire value restores defaults but explicit empty value keeps empty toolbar`() {
        assertEquals(
            BrowserMenuBottomSheetToolbarRules.DefaultEntries,
            BrowserMenuLayoutRules.fromWireValues(emptyMap()).bottomSheetToolbarEntries,
        )
        assertEquals(
            listOf(null, null, null, null, null),
            BrowserMenuLayoutRules.fromWireValues(mapOf("bottom_sheet_toolbar_entries" to ""))
                .bottomSheetToolbarEntries,
        )
    }

    @Test
    fun `wire values round trip empty slots and discard unknown duplicate and overflow values`() {
        val decoded = BrowserMenuLayoutRules.fromWireValues(
            mapOf("bottom_sheet_toolbar_entries" to "share,,future_action,share,desktop_view,back,forward"),
        )

        assertEquals(
            listOf(BrowserMenuEntry.Share, null, null, null, BrowserMenuEntry.DesktopView),
            decoded.bottomSheetToolbarEntries,
        )
        val encoded = BrowserMenuLayoutRules.toWireValues(decoded)
        assertEquals("share,,,,desktop_view", encoded["bottom_sheet_toolbar_entries"])
        assertEquals(decoded, BrowserMenuLayoutRules.fromWireValues(encoded))
    }

    @Test
    fun `selected address actions can be added before normal menu visibility filtering`() {
        val entries = listOf(
            BrowserMenuEntry.CloseTab,
            BrowserMenuEntry.OpenDownloads,
            BrowserMenuEntry.ShowTabs,
            BrowserMenuEntry.NewTab,
            BrowserMenuEntry.ShowTabs,
        )

        assertEquals(
            listOf(
                BrowserFeatureMenuAction.CloseTab,
                BrowserFeatureMenuAction.ShowTabs,
                BrowserFeatureMenuAction.NewTab,
            ),
            BrowserMenuBottomSheetToolbarRules.additionalPageActions(entries),
        )
    }
}
