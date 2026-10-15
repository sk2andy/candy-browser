package dev.sk2andy.materialbrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkPeekSwipeActionTest {
    @Test
    fun `missing and unknown values default to background tab`() {
        assertEquals(LinkPeekSwipeAction.OpenBackground, LinkPeekSwipeAction.fromWireValue(null))
        assertEquals(LinkPeekSwipeAction.OpenBackground, LinkPeekSwipeAction.fromWireValue("unknown"))
    }

    @Test
    fun `wire values restore every action`() {
        LinkPeekSwipeAction.entries.forEach { action ->
            assertEquals(action, LinkPeekSwipeAction.fromWireValue(action.wireValue))
        }
    }

    @Test
    fun `persisted action ids keep their destinations`() {
        assertEquals(
            LinkPeekSwipeAction.OpenBackground,
            LinkPeekSwipeAction.fromWireValue("open_background"),
        )
        assertEquals(
            LinkPeekSwipeAction.OpenForeground,
            LinkPeekSwipeAction.fromWireValue("open_foreground"),
        )
        assertEquals(
            LinkPeekSwipeAction.OpenNewStack,
            LinkPeekSwipeAction.fromWireValue("open_new_stack"),
        )
        assertEquals(
            LinkPeekSwipeAction.OpenPrivate,
            LinkPeekSwipeAction.fromWireValue("open_private"),
        )
        assertEquals(LinkPeekSwipeAction.Copy, LinkPeekSwipeAction.fromWireValue("copy"))
        assertEquals(LinkPeekSwipeAction.Share, LinkPeekSwipeAction.fromWireValue("share"))
        assertEquals(LinkPeekSwipeAction.Favorite, LinkPeekSwipeAction.fromWireValue("favorite"))
        assertEquals(LinkPeekSwipeAction.Snooze, LinkPeekSwipeAction.fromWireValue("snooze"))
        assertEquals(LinkPeekSwipeAction.ReaderLater, LinkPeekSwipeAction.fromWireValue("reader_later"))
    }
}
