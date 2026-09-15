package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserMenuLayoutRulesTest {
    @Test
    fun `default layout preserves current surfaces`() {
        val layout = BrowserMenuLayout.Default

        assertTrue(
            BrowserMenuLayoutRules.isVisible(
                layout,
                BrowserMenuEntry.Share,
                BrowserMenuSurface.Tab,
            ),
        )
        assertTrue(
            BrowserMenuLayoutRules.isVisible(
                layout,
                BrowserMenuEntry.Share,
                BrowserMenuSurface.TabSwitcher,
            ),
        )
        assertTrue(
            BrowserMenuLayoutRules.isVisible(
                layout,
                BrowserMenuEntry.Back,
                BrowserMenuSurface.Tab,
            ),
        )
        assertFalse(
            BrowserMenuLayoutRules.isVisible(
                layout,
                BrowserMenuEntry.Back,
                BrowserMenuSurface.TabSwitcher,
            ),
        )
        assertTrue(
            BrowserMenuLayoutRules.isVisible(
                layout,
                BrowserMenuEntry.CloseAllTabs,
                BrowserMenuSurface.TabSwitcher,
            ),
        )
    }

    @Test
    fun `allowed locations follow supported surfaces`() {
        assertEquals(
            listOf(
                BrowserMenuLocation.Nowhere,
                BrowserMenuLocation.Tab,
                BrowserMenuLocation.TabSwitcher,
                BrowserMenuLocation.Both,
            ),
            BrowserMenuLayoutRules.allowedLocations(BrowserMenuEntry.Share),
        )
        assertEquals(
            listOf(BrowserMenuLocation.Nowhere, BrowserMenuLocation.Tab),
            BrowserMenuLayoutRules.allowedLocations(BrowserMenuEntry.Back),
        )
        assertEquals(
            listOf(BrowserMenuLocation.Nowhere, BrowserMenuLocation.TabSwitcher),
            BrowserMenuLayoutRules.allowedLocations(BrowserMenuEntry.TabStacks),
        )
    }

    @Test
    fun `invalid stored placement falls back to entry default`() {
        val layout = BrowserMenuLayoutRules.fromWireValues(
            mapOf(BrowserMenuEntry.Back.stableId to BrowserMenuLocation.Both.stableId),
        )

        assertEquals(
            BrowserMenuLocation.Tab,
            BrowserMenuLayoutRules.location(layout, BrowserMenuEntry.Back),
        )
    }

    @Test
    fun `wire values ignore unknown entries and round trip known locations`() {
        val decoded = BrowserMenuLayoutRules.fromWireValues(
            mapOf(
                BrowserMenuEntry.Share.stableId to BrowserMenuLocation.Nowhere.stableId,
                BrowserMenuEntry.CloseAllTabs.stableId to BrowserMenuLocation.Nowhere.stableId,
                "future_action" to BrowserMenuLocation.Both.stableId,
            ),
        )
        val encoded = BrowserMenuLayoutRules.toWireValues(decoded)

        assertEquals(BrowserMenuLocation.Nowhere.stableId, encoded[BrowserMenuEntry.Share.stableId])
        assertEquals(
            BrowserMenuLocation.Nowhere.stableId,
            encoded[BrowserMenuEntry.CloseAllTabs.stableId],
        )
        assertFalse("future_action" in encoded)
    }

    @Test
    fun `tab item filtering maps reload and stop to one setting`() {
        val layout = BrowserMenuLayoutRules.update(
            BrowserMenuLayout.Default,
            BrowserMenuEntry.Reload,
            BrowserMenuLocation.Nowhere,
        )
        val items = listOf(
            BrowserFeatureMenuItem(
                stableId = "stop",
                action = BrowserFeatureMenuAction.Stop,
                labelKey = BrowserFeatureMenuLabelKey.StopLoading,
                section = BrowserFeatureMenuSection.Toolbar,
                kind = BrowserFeatureMenuItemKind.Command,
                enabled = true,
            ),
            BrowserFeatureMenuItem(
                stableId = "share",
                action = BrowserFeatureMenuAction.Share,
                labelKey = BrowserFeatureMenuLabelKey.Share,
                section = BrowserFeatureMenuSection.Page,
                kind = BrowserFeatureMenuItemKind.Command,
                enabled = true,
            ),
        )

        assertEquals(
            listOf(BrowserFeatureMenuAction.Share),
            BrowserMenuLayoutRules.visibleItems(items, layout, BrowserMenuSurface.Tab)
                .map(BrowserFeatureMenuItem::action),
        )
    }
}
