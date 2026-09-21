package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsOverHttpsSettingsTest {
    @Test
    fun `built in providers resolve to strict HTTPS endpoints`() {
        assertEquals(
            "https://cloudflare-dns.com/dns-query",
            DnsOverHttpsRules.endpoint(settings(DnsOverHttpsProvider.Cloudflare)),
        )
        assertEquals(
            "https://dns.google/dns-query",
            DnsOverHttpsRules.endpoint(settings(DnsOverHttpsProvider.Google)),
        )
        assertEquals(
            "https://dns.quad9.net/dns-query",
            DnsOverHttpsRules.endpoint(settings(DnsOverHttpsProvider.Quad9)),
        )
    }

    @Test
    fun `system provider has no browser scoped endpoint`() {
        assertNull(DnsOverHttpsRules.endpoint(DnsOverHttpsSettings()))
    }

    @Test
    fun `custom endpoint normalizes HTTPS host and preserves path`() {
        assertEquals(
            "https://dns.nextdns.io/abc123",
            DnsOverHttpsRules.normalizedCustomEndpoint(
                "  HTTPS://DNS.NextDNS.IO/abc123  ",
            ),
        )
    }

    @Test
    fun `custom endpoint accepts explicit TLS port and international host`() {
        assertEquals(
            "https://xn--bcher-kva.example:8443/dns-query",
            DnsOverHttpsRules.normalizedCustomEndpoint(
                "https://bücher.example:8443/dns-query",
            ),
        )
    }

    @Test
    fun `custom endpoint rejects unsafe or ambiguous URLs`() {
        listOf(
            "",
            "http://dns.example/dns-query",
            "https://user:password@dns.example/dns-query",
            "https://dns.example/dns-query?token=secret",
            "https://dns.example/dns-query#fragment",
            "https://dns.example/profiles/../dns-query",
            "https://dns.example:-1/dns-query",
            "https://dns.example:/dns-query",
            "https://dns.example:not-a-port/dns-query",
            "https:///dns-query",
            "not a URL",
        ).forEach { candidate ->
            assertNull(candidate, DnsOverHttpsRules.normalizedCustomEndpoint(candidate))
        }
    }

    @Test
    fun `sanitize rejects invalid custom setting instead of silently enabling another resolver`() {
        assertEquals(
            DnsOverHttpsRules.Default,
            DnsOverHttpsRules.sanitize(
                settings(
                    provider = DnsOverHttpsProvider.Custom,
                    customEndpoint = "http://dns.example/dns-query",
                ),
            ),
        )
    }

    @Test
    fun `provider parser fails closed to system DNS`() {
        assertEquals(DnsOverHttpsProvider.System, DnsOverHttpsProvider.fromStableId("future"))
        assertEquals(DnsOverHttpsProvider.System, DnsOverHttpsProvider.fromStableId(null))
    }

    @Test
    fun `switching preset keeps a valid custom endpoint for later reuse`() {
        assertEquals(
            DnsOverHttpsSettings(
                provider = DnsOverHttpsProvider.Cloudflare,
                customEndpoint = "https://dns.nextdns.io/abc123",
            ),
            DnsOverHttpsRules.sanitize(
                DnsOverHttpsSettings(
                    provider = DnsOverHttpsProvider.Cloudflare,
                    customEndpoint = " HTTPS://DNS.NextDNS.IO/abc123 ",
                ),
            ),
        )
    }

    private fun settings(
        provider: DnsOverHttpsProvider,
        customEndpoint: String = "stale",
    ) = DnsOverHttpsSettings(provider, customEndpoint)
}
