package dev.sk2andy.materialbrowser.shared.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressBarLongPressActionRulesTest {
    @Test
    fun `stable ids round trip and unknown values preserve reader default`() {
        AddressBarLongPressAction.entries.forEach { action ->
            assertEquals(action, AddressBarLongPressAction.fromStableId(action.stableId))
        }

        assertEquals(
            AddressBarLongPressAction.OpenReader,
            AddressBarLongPressAction.fromStableId("future_action"),
        )
    }

    @Test
    fun `catalog is grouped in reading navigation page tab browser and candy order`() {
        assertEquals(
            listOf(
                AddressBarLongPressActionSection.Reading,
                AddressBarLongPressActionSection.Navigation,
                AddressBarLongPressActionSection.Page,
                AddressBarLongPressActionSection.Tab,
                AddressBarLongPressActionSection.Browser,
                AddressBarLongPressActionSection.Candy,
            ),
            AddressBarLongPressAction.entries.map(AddressBarLongPressAction::section).distinct(),
        )
    }

    @Test
    fun `private page rejects persistent reader favorite capsule and snooze actions`() {
        val context = availableContext.copy(
            isPrivate = true,
            canToggleFavorite = false,
            canCreateSiteCapsule = false,
            canSnoozeTab = false,
        )

        assertFalse(
            AddressBarLongPressActionRules.isAvailable(
                AddressBarLongPressAction.SaveReaderOffline,
                context,
            ),
        )
        assertFalse(
            AddressBarLongPressActionRules.isAvailable(
                AddressBarLongPressAction.ToggleFavorite,
                context,
            ),
        )
        assertFalse(
            AddressBarLongPressActionRules.isAvailable(
                AddressBarLongPressAction.CreateSiteCapsule,
                context,
            ),
        )
        assertFalse(
            AddressBarLongPressActionRules.isAvailable(
                AddressBarLongPressAction.SnoozeTab,
                context,
            ),
        )
        assertTrue(
            AddressBarLongPressActionRules.isAvailable(
                AddressBarLongPressAction.CopyUrl,
                context,
            ),
        )
    }

    @Test
    fun `context requirements disable only actions that need missing state`() {
        val context = availableContext.copy(
            hasPage = false,
            hasHttpPage = false,
            canGoBack = false,
            canOpenReader = false,
            canToggleFavorite = false,
            canToggleDesktopView = false,
            canParkAddressBar = false,
            canCreateSiteCapsule = false,
            canCreatePrivateTab = false,
            canSnoozeTab = false,
            hasMoveTargetProfile = false,
        )

        assertFalse(AddressBarLongPressActionRules.isAvailable(AddressBarLongPressAction.Reload, context))
        assertFalse(AddressBarLongPressActionRules.isAvailable(AddressBarLongPressAction.GoBack, context))
        assertFalse(AddressBarLongPressActionRules.isAvailable(AddressBarLongPressAction.MoveToProfile, context))
        assertTrue(AddressBarLongPressActionRules.isAvailable(AddressBarLongPressAction.NewTab, context))
        assertFalse(
            AddressBarLongPressActionRules.isAvailable(
                AddressBarLongPressAction.NewPrivateTab,
                context,
            ),
        )
        assertFalse(AddressBarLongPressActionRules.isAvailable(AddressBarLongPressAction.CopyUrl, context))
        assertFalse(AddressBarLongPressActionRules.isAvailable(AddressBarLongPressAction.SnoozeTab, context))
    }

    private companion object {
        val availableContext = AddressBarLongPressContext(
            hasPage = true,
            hasHttpPage = true,
            isPrivate = false,
            canGoBack = true,
            canOpenReader = true,
            canToggleFavorite = true,
            canToggleDesktopView = true,
            canParkAddressBar = true,
            canCreateSiteCapsule = true,
            canCreatePrivateTab = true,
            canSnoozeTab = true,
            hasMoveTargetProfile = true,
        )
    }
}
