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
    fun `Gecko local address protection keeps only default public interface`() {
        val policy = WebRtcProtectionRules.geckoPolicy(
            WebRtcProtectionMode.HideLocalNetworkIp,
        )

        assertTrue(policy.peerConnectionsEnabled)
        assertEquals("default_public_interface_only", policy.ipHandlingPolicy)
    }

    @Test
    fun `Gecko non-proxied UDP protection uses matching engine policy`() {
        val policy = WebRtcProtectionRules.geckoPolicy(
            WebRtcProtectionMode.DisableNonProxiedUdp,
        )

        assertTrue(policy.peerConnectionsEnabled)
        assertEquals("disable_non_proxied_udp", policy.ipHandlingPolicy)
    }

    @Test
    fun `Gecko strict mode disables peer connections`() {
        val policy = WebRtcProtectionRules.geckoPolicy(WebRtcProtectionMode.Block)

        assertFalse(policy.peerConnectionsEnabled)
        assertNull(policy.ipHandlingPolicy)
    }

    @Test
    fun `System WebView blocks every protected mode`() {
        assertFalse(
            WebRtcProtectionRules.blocksSystemWebViewPeerConnections(
                WebRtcProtectionMode.Standard,
            ),
        )
        assertTrue(
            WebRtcProtectionRules.blocksSystemWebViewPeerConnections(
                WebRtcProtectionMode.HideLocalNetworkIp,
            ),
        )
        assertTrue(
            WebRtcProtectionRules.blocksSystemWebViewPeerConnections(
                WebRtcProtectionMode.DisableNonProxiedUdp,
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
