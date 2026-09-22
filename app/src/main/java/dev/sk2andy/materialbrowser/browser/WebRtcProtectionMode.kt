package dev.sk2andy.materialbrowser.browser

enum class WebRtcProtectionMode(val stableId: String) {
    Standard("standard"),
    HideLocalNetworkIp("hide_local_network_ip"),
    DisableNonProxiedUdp("disable_non_proxied_udp"),
    ProtectIpAddresses("protect_ip_addresses"),
    Block("block"),
    ;

    companion object {
        val Default = ProtectIpAddresses

        fun fromStableId(value: String?): WebRtcProtectionMode =
            entries.firstOrNull { mode -> mode.stableId == value } ?: Default
    }
}

internal data class GeckoWebRtcPolicy(
    val peerConnectionsEnabled: Boolean,
    val ipHandlingPolicy: String?,
)

internal object WebRtcProtectionRules {
    fun geckoPolicy(mode: WebRtcProtectionMode): GeckoWebRtcPolicy = when (mode) {
        WebRtcProtectionMode.Standard -> GeckoWebRtcPolicy(
            peerConnectionsEnabled = true,
            ipHandlingPolicy = null,
        )
        WebRtcProtectionMode.HideLocalNetworkIp -> GeckoWebRtcPolicy(
            peerConnectionsEnabled = true,
            ipHandlingPolicy = "default_public_interface_only",
        )
        WebRtcProtectionMode.DisableNonProxiedUdp -> GeckoWebRtcPolicy(
            peerConnectionsEnabled = true,
            ipHandlingPolicy = "disable_non_proxied_udp",
        )
        WebRtcProtectionMode.ProtectIpAddresses -> GeckoWebRtcPolicy(
            peerConnectionsEnabled = true,
            ipHandlingPolicy = "proxy_only",
        )
        WebRtcProtectionMode.Block -> GeckoWebRtcPolicy(
            peerConnectionsEnabled = false,
            ipHandlingPolicy = null,
        )
    }

    fun blocksSystemWebViewPeerConnections(mode: WebRtcProtectionMode): Boolean =
        mode != WebRtcProtectionMode.Standard
}
