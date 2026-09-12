package dev.sk2andy.materialbrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkPeekActionLayoutTest {
    @Test
    fun `wire values round trip and unknown values are rejected`() {
        LinkPeekAction.entries.forEach { action ->
            assertEquals(action, LinkPeekAction.fromWireValue(action.wireValue))
        }
        assertNull(LinkPeekAction.fromWireValue(null))
        assertNull(LinkPeekAction.fromWireValue("unknown"))
    }

    @Test
    fun `default keeps copy private and share actions`() {
        assertEquals(
            LinkPeekActionLayout(
                actions = listOf(
                    LinkPeekAction.Copy,
                    LinkPeekAction.OpenPrivate,
                    LinkPeekAction.Share,
                ),
            ),
            LinkPeekActionLayout.Default,
        )
    }

    @Test
    fun `normalization pads and truncates positions without filling them`() {
        assertEquals(
            LinkPeekActionLayout(
                actions = listOf(LinkPeekAction.Favorite, null, null),
            ),
            LinkPeekActionLayoutRules.normalize(
                LinkPeekActionLayout(actions = listOf(LinkPeekAction.Favorite)),
            ),
        )
        assertEquals(
            LinkPeekActionLayout(
                actions = listOf(
                    LinkPeekAction.Snooze,
                    LinkPeekAction.ReaderLater,
                    LinkPeekAction.OpenForeground,
                ),
            ),
            LinkPeekActionLayoutRules.normalize(
                LinkPeekActionLayout(
                    actions = listOf(
                        LinkPeekAction.Snooze,
                        LinkPeekAction.ReaderLater,
                        LinkPeekAction.OpenForeground,
                        LinkPeekAction.Copy,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `normalization replaces later duplicates with empty slots`() {
        assertEquals(
            LinkPeekActionLayout(
                actions = listOf(LinkPeekAction.Favorite, null, LinkPeekAction.Share),
            ),
            LinkPeekActionLayoutRules.normalize(
                LinkPeekActionLayout(
                    actions = listOf(
                        LinkPeekAction.Favorite,
                        LinkPeekAction.Favorite,
                        LinkPeekAction.Share,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `wire values preserve null and unknown positions`() {
        assertEquals(
            LinkPeekActionLayout(
                actions = listOf(LinkPeekAction.Share, null, null),
            ),
            LinkPeekActionLayoutRules.fromWireValues(
                listOf("share", "broken", "share"),
            ),
        )
        assertEquals(
            LinkPeekActionLayout(actions = listOf(null, null, null)),
            LinkPeekActionLayoutRules.fromWireValues(null),
        )
    }

    @Test
    fun `available actions exclude only occupied normalized slots`() {
        val layout = LinkPeekActionLayout(
            actions = listOf(LinkPeekAction.Favorite, null, LinkPeekAction.OpenForeground),
        )

        assertEquals(
            LinkPeekAction.entries.filterNot {
                it == LinkPeekAction.Favorite || it == LinkPeekAction.OpenForeground
            },
            LinkPeekActionLayoutRules.available(layout),
        )
    }

    @Test
    fun `placing selected action moves only into empty target`() {
        assertEquals(
            LinkPeekActionLayout(
                actions = listOf(null, LinkPeekAction.Copy, LinkPeekAction.Share),
            ),
            LinkPeekActionLayoutRules.place(
                layout = LinkPeekActionLayout(
                    actions = listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share),
                ),
                action = LinkPeekAction.Copy,
                targetActionIndex = 1,
            ),
        )
        assertEquals(
            LinkPeekActionLayout.Default,
            LinkPeekActionLayoutRules.place(
                layout = LinkPeekActionLayout.Default,
                action = LinkPeekAction.Share,
                targetActionIndex = 0,
            ),
        )
    }

    @Test
    fun `placing palette action fills empty target without replacing occupied target`() {
        val layout = LinkPeekActionLayout(
            actions = listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share),
        )
        assertEquals(
            LinkPeekActionLayout(
                actions = listOf(
                    LinkPeekAction.Copy,
                    LinkPeekAction.Favorite,
                    LinkPeekAction.Share,
                ),
            ),
            LinkPeekActionLayoutRules.place(
                layout = layout,
                action = LinkPeekAction.Favorite,
                targetActionIndex = 1,
            ),
        )
        assertEquals(
            layout,
            LinkPeekActionLayoutRules.place(
                layout = layout,
                action = LinkPeekAction.Favorite,
                targetActionIndex = 0,
            ),
        )
    }

    @Test
    fun `removing action empties its position without shifting neighbors`() {
        assertEquals(
            LinkPeekActionLayout(
                actions = listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share),
            ),
            LinkPeekActionLayoutRules.remove(
                layout = LinkPeekActionLayout.Default,
                action = LinkPeekAction.OpenPrivate,
            ),
        )
    }

    @Test
    fun `placing at invalid target leaves normalized layout unchanged`() {
        val empty = LinkPeekActionLayout(actions = listOf(null, null, null))
        assertEquals(
            empty,
            LinkPeekActionLayoutRules.place(
                layout = empty,
                action = LinkPeekAction.Favorite,
                targetActionIndex = LinkPeekActionLayoutRules.CONFIGURABLE_ACTION_COUNT,
            ),
        )
    }

    @Test
    fun `runtime projection keeps empty positions and fixed plus penultimate`() {
        val layout = LinkPeekActionLayout(
            actions = listOf(LinkPeekAction.Copy, null, LinkPeekAction.Share),
        )

        assertEquals(
            listOf(
                LinkPeekActionSlot.Action(LinkPeekAction.Copy, actionIndex = 0),
                LinkPeekActionSlot.Empty(actionIndex = 1),
                LinkPeekActionSlot.FixedPlus,
                LinkPeekActionSlot.Action(LinkPeekAction.Share, actionIndex = 2),
            ),
            LinkPeekActionLayoutRules.runtimeSlots(layout),
        )
        assertEquals(
            LinkPeekActionSlot.FixedPlus,
            LinkPeekActionLayoutRules.runtimeSlots(layout)[
                LinkPeekActionLayoutRules.FIXED_PLUS_SLOT_INDEX
            ],
        )
        assertEquals(
            LinkPeekActionLayoutRules.TOOLBAR_SLOT_COUNT,
            LinkPeekActionLayoutRules.runtimeSlots(layout).size,
        )
    }
}
