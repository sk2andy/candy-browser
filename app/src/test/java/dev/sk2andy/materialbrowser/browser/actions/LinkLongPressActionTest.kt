package dev.sk2andy.materialbrowser.browser.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkLongPressActionTest {
    @Test
    fun `missing and unknown values default to Link Peek`() {
        assertEquals(LinkLongPressAction.LinkPeek, LinkLongPressAction.fromStableId(null))
        assertEquals(LinkLongPressAction.LinkPeek, LinkLongPressAction.fromStableId("unknown"))
    }

    @Test
    fun `stable ids restore every action`() {
        LinkLongPressAction.entries.forEach { action ->
            assertEquals(action, LinkLongPressAction.fromStableId(action.stableId))
        }
    }

    @Test
    fun `legacy tab action ids retain their original behavior`() {
        assertEquals(
            LinkLongPressAction.OpenInNewTabInBackground,
            LinkLongPressAction.fromStableId("open_in_new_tab"),
        )
        assertEquals(
            LinkLongPressAction.OpenInPrivateTabInForeground,
            LinkLongPressAction.fromStableId("open_in_private_tab"),
        )
    }
}
