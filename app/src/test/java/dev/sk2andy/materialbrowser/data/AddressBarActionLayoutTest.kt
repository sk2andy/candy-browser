package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressBarActionLayoutTest {
    @Test
    fun `wire values round trip and unknown values are rejected`() {
        AddressBarAction.entries.forEach { action ->
            assertEquals(action, AddressBarAction.fromWireValue(action.wireValue))
        }
        assertNull(AddressBarAction.fromWireValue(null))
        assertNull(AddressBarAction.fromWireValue("unknown"))
    }

    @Test
    fun `default keeps tabs before and new tab after address`() {
        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Tabs),
                afterAddress = listOf(AddressBarAction.NewTab),
            ),
            AddressBarActionLayout.Default,
        )
    }

    @Test
    fun `normalization keeps first unique actions up to global limit`() {
        val normalized = AddressBarActionLayoutRules.normalize(
            AddressBarActionLayout(
                beforeAddress = listOf(
                    AddressBarAction.Tabs,
                    AddressBarAction.Favorite,
                    AddressBarAction.Tabs,
                ),
                afterAddress = listOf(
                    AddressBarAction.NewTab,
                    AddressBarAction.Favorite,
                    AddressBarAction.Share,
                ),
            ),
        )

        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Tabs, AddressBarAction.Favorite),
                afterAddress = listOf(AddressBarAction.NewTab),
            ),
            normalized,
        )
    }

    @Test
    fun `wire lists ignore missing unknown and duplicate values`() {
        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Back),
                afterAddress = listOf(AddressBarAction.Reload, AddressBarAction.Forward),
            ),
            AddressBarActionLayoutRules.fromWireLists(
                beforeAddress = listOf(null, "back", "broken"),
                afterAddress = listOf("reload", "back", "forward", "print"),
            ),
        )
        assertEquals(
            AddressBarActionLayout(emptyList(), emptyList()),
            AddressBarActionLayoutRules.fromWireLists(null, null),
        )
    }

    @Test
    fun `available actions exclude placed actions in enum order`() {
        val layout = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Tabs),
            afterAddress = listOf(AddressBarAction.NewTab),
        )

        assertEquals(
            AddressBarAction.entries.filterNot {
                it == AddressBarAction.Tabs || it == AddressBarAction.NewTab
            },
            AddressBarActionLayoutRules.available(layout),
        )
    }

    @Test
    fun `dynamic slots hide trailing actions before leading actions`() {
        val layout = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Back, AddressBarAction.Tabs),
            afterAddress = listOf(AddressBarAction.NewTab),
        )

        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Back, AddressBarAction.Tabs),
                afterAddress = emptyList(),
            ),
            AddressBarActionLayoutRules.reserveDynamicSlots(layout, dynamicSlotCount = 1),
        )
        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Back),
                afterAddress = emptyList(),
            ),
            AddressBarActionLayoutRules.reserveDynamicSlots(layout, dynamicSlotCount = 2),
        )
    }

    @Test
    fun `temporarily hidden actions preserve configured order`() {
        val layout = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Tabs),
            afterAddress = listOf(AddressBarAction.NewTab, AddressBarAction.CloseTab),
        )
        val visible = AddressBarActionLayoutRules.reserveDynamicSlots(layout, 1)

        assertEquals(
            listOf(AddressBarAction.CloseTab),
            AddressBarActionLayoutRules.temporarilyHiddenActions(layout, visible),
        )
    }

    @Test
    fun `insert clamps destination and rejects duplicates or overflow`() {
        val withFavorite = AddressBarActionLayoutRules.insert(
            layout = AddressBarActionLayout.Default,
            action = AddressBarAction.Favorite,
            side = AddressBarActionSide.BeforeAddress,
            requestedIndex = Int.MIN_VALUE,
        )
        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Favorite, AddressBarAction.Tabs),
                afterAddress = listOf(AddressBarAction.NewTab),
            ),
            withFavorite,
        )
        assertEquals(
            withFavorite,
            AddressBarActionLayoutRules.insert(
                layout = withFavorite,
                action = AddressBarAction.Tabs,
                side = AddressBarActionSide.AfterAddress,
                requestedIndex = 0,
            ),
        )
        assertEquals(
            withFavorite,
            AddressBarActionLayoutRules.insert(
                layout = withFavorite,
                action = AddressBarAction.Print,
                side = AddressBarActionSide.AfterAddress,
                requestedIndex = Int.MAX_VALUE,
            ),
        )
    }

    @Test
    fun `move reorders across sides and ignores unavailable action`() {
        val layout = AddressBarActionLayout(
            beforeAddress = listOf(AddressBarAction.Back, AddressBarAction.Tabs),
            afterAddress = listOf(AddressBarAction.NewTab),
        )
        val moved = AddressBarActionLayoutRules.move(
            layout = layout,
            action = AddressBarAction.Back,
            side = AddressBarActionSide.AfterAddress,
            requestedIndex = Int.MAX_VALUE,
        )

        assertEquals(
            AddressBarActionLayout(
                beforeAddress = listOf(AddressBarAction.Tabs),
                afterAddress = listOf(AddressBarAction.NewTab, AddressBarAction.Back),
            ),
            moved,
        )
        assertEquals(
            moved,
            AddressBarActionLayoutRules.move(
                layout = moved,
                action = AddressBarAction.Print,
                side = AddressBarActionSide.BeforeAddress,
                requestedIndex = 0,
            ),
        )
    }

    @Test
    fun `remove clears action from either side`() {
        assertEquals(
            AddressBarActionLayout(
                beforeAddress = emptyList(),
                afterAddress = listOf(AddressBarAction.NewTab),
            ),
            AddressBarActionLayoutRules.remove(
                layout = AddressBarActionLayout.Default,
                action = AddressBarAction.Tabs,
            ),
        )
    }
}
