package dev.sk2andy.materialbrowser.browser.guest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestBubbleRulesTest {
    @Test
    fun `new session marker has no guest process claim`() {
        val session = GuestBubbleRules.decodeMarker("1\n$id\nactive\n-\n-")

        assertEquals(GuestBubbleSession(id, GuestBubblePhase.Active), session)
    }

    @Test
    fun `closing marker retains exact guest process identity`() {
        val session = GuestBubbleRules.decodeMarker("1\n$id\nclosing\n123\n456")

        assertEquals(GuestBubbleSession(id, GuestBubblePhase.Closing, 123, 456L), session)
        assertEquals("1\n$id\nclosing\n123\n456", GuestBubbleRules.encodeMarker(requireNotNull(session)))
    }

    @Test
    fun `malformed marker cannot grant owner access or closing state`() {
        listOf(
            "",
            "1\n$id\nactive\n-",
            "2\n$id\nactive\n-\n-",
            "1\ninvalid\nactive\n-\n-",
            "1\n$id\nunlocked\n-\n-",
            "1\n$id\nclosing\n0\n456",
            "1\n$id\nclosing\n123\n-1",
            "1\n$id\nclosing\n123\n-",
            "1\n$id\nclosing\n-\n456",
            "1\n$id\nclosing\n2147483648\n456",
            "1\n$id\nclosing\n123\n9223372036854775808",
            "1\n$id\nclosing\n123\n456\nextra",
            "x".repeat(GuestBubbleRules.MAX_MARKER_BYTES + 1),
        ).forEach { marker -> assertNull(marker, GuestBubbleRules.decodeMarker(marker)) }
        assertEquals(GuestBubblePhase.Active, GuestBubbleRules.recoverySession.phase)
    }

    @Test
    fun `only exact app guest process is eligible for guest data deletion`() {
        assertTrue(GuestBubbleRules.isGuestProcess("dev.candy", "dev.candy:guestBubble"))
        listOf(null, "dev.candy", "dev.candy:guestCleanup", "dev.other:guestBubble", "dev.candy:guestBubbleExtra")
            .forEach { name -> assertFalse(GuestBubbleRules.isGuestProcess("dev.candy", name)) }
    }

    private companion object {
        const val id = "fd10837a-3405-4771-95a0-8b877cab718b"
    }
}
