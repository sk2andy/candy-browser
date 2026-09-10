package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcProtectionRulesTest {
    @Test
    fun `unknown stored mode falls back to IP protection`() {
        assertEquals(
            WebRtcProtectionMode.ProtectIpAddresses,
            WebRtcProtectionMode.fromStableId("future-mode"),
        )
    }

    @Test
    fun `Gecko IP protection requires proxy-only connections`() {
        val policy = WebRtcProtectionRules.geckoPolicy(
            WebRtcProtectionMode.ProtectIpAddresses,
        )

        assertTrue(policy.peerConnectionsEnabled)
        assertEquals("proxy_only", policy.ipHandlingPolicy)
    }

    @Test
    fun `Gecko strict mode disables peer connections`() {
        val policy = WebRtcProtectionRules.geckoPolicy(WebRtcProtectionMode.Block)

        assertFalse(policy.peerConnectionsEnabled)
        assertNull(policy.ipHandlingPolicy)
    }

    @Test
    fun `System WebView blocks both protected modes`() {
        assertFalse(
            WebRtcProtectionRules.blocksSystemWebViewPeerConnections(
                WebRtcProtectionMode.Standard,
            ),
        )
        assertTrue(
            WebRtcProtectionRules.blocksSystemWebViewPeerConnections(
                WebRtcProtectionMode.ProtectIpAddresses,
            ),
        )
        assertTrue(
            WebRtcProtectionRules.blocksSystemWebViewPeerConnections(
                WebRtcProtectionMode.Block,
            ),
        )
    }
}
