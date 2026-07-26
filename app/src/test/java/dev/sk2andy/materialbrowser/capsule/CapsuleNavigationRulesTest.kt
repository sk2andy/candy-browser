package dev.sk2andy.materialbrowser.capsule

import org.junit.Assert.assertEquals
import org.junit.Test

class CapsuleNavigationRulesTest {
    @Test
    fun `same origin includes default ports but not scheme or nondefault port changes`() {
        val capsule = capsule(CapsuleNavigationMode.SameOrigin, "https://shop.example.com/home")

        assertEquals(stay, decide(capsule, "https://shop.example.com:443/cart"))
        assertEquals(full, decide(capsule, "http://shop.example.com/cart"))
        assertEquals(full, decide(capsule, "https://shop.example.com:8443/cart"))
        assertEquals(full, decide(capsule, "https://checkout.example.com/cart"))
    }

    @Test
    fun `registrable domain uses public suffix and IDN normalization`() {
        val capsule = capsule(
            CapsuleNavigationMode.SameRegistrableDomain,
            "https://account.example.co.uk",
        )

        assertEquals(stay, decide(capsule, "https://checkout.example.co.uk/pay"))
        assertEquals(full, decide(capsule, "https://example.co.uk.evil.test"))
        assertEquals(
            CapsuleNavigationRules.siteKey("bücher.example"),
            CapsuleNavigationRules.siteKey("xn--bcher-kva.example"),
        )
        val unicodeCapsule = capsule(
            CapsuleNavigationMode.SameOrigin,
            "https://bücher.example/home",
        )
        assertEquals(stay, decide(unicodeCapsule, "https://xn--bcher-kva.example/cart"))
        assertEquals(full, decide(unicodeCapsule, "https://evilé.example/cart"))
        assertEquals(full, decide(unicodeCapsule, "https://foo_bar/cart"))
    }

    @Test
    fun `all links keeps web links while special schemes use existing routing`() {
        val capsule = capsule(CapsuleNavigationMode.AllLinks, "https://example.com")

        assertEquals(stay, decide(capsule, "https://elsewhere.example/path"))
        assertEquals(
            CapsuleNavigationDecision.UseExistingUriPolicy,
            decide(capsule, "mailto:hello@example.com"),
        )
    }

    private fun capsule(mode: CapsuleNavigationMode, url: String) = SiteCapsule(
        id = "04a74ad8-7533-460c-bfbf-a135968940d5",
        name = "Example",
        startUrl = url,
        profileId = "profile",
        navigationMode = mode,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private fun decide(capsule: SiteCapsule, target: String) =
        CapsuleNavigationRules.decide(capsule, target)

    private val stay = CapsuleNavigationDecision.StayInCapsule
    private val full = CapsuleNavigationDecision.OpenInFullCandy
}
