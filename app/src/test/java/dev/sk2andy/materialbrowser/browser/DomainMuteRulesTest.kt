package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMuteRulesTest {
    @Test
    fun subdomainsShareRegistrableDomainMuteState() {
        assertEquals(
            "example.co.uk",
            DomainMuteRules.domainForUrl("https://music.news.example.co.uk/player"),
        )
        assertTrue(
            DomainMuteRules.isMuted(
                "https://video.example.co.uk/watch",
                setOf("example.co.uk"),
            ),
        )
    }

    @Test
    fun privateSuffixTenantsKeepIndependentMuteState() {
        assertEquals(
            "alice.web.app",
            DomainMuteRules.domainForUrl("https://media.alice.web.app/player"),
        )
        assertFalse(
            DomainMuteRules.isMuted(
                "https://bob.web.app/watch",
                setOf("alice.web.app"),
            ),
        )
    }

    @Test
    fun unrelatedAndNonWebPagesStayUnmuted() {
        assertFalse(DomainMuteRules.isMuted("https://notexample.com", setOf("example.com")))
        assertNull(DomainMuteRules.domainForUrl(BLANK_URL))
        assertNull(DomainMuteRules.domainForUrl("file:///tmp/video.html"))
    }

    @Test
    fun mutedStateIsCanonicalBoundedAndReversible() {
        val initial = (1..64).map { index -> "site$index.example" }

        val muted = DomainMuteRules.withMutedState(initial, "MEDIA.Example", muted = true)
        val unmuted = DomainMuteRules.withMutedState(muted, "media.example", muted = false)

        assertEquals(64, muted.size)
        assertEquals("media.example", muted.last())
        assertFalse("site64.example" in muted)
        assertFalse("media.example" in unmuted)
    }
}
