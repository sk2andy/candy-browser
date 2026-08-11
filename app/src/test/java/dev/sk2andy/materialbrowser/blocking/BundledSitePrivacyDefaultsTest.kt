package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledSitePrivacyDefaultsTest {
    @Test
    fun `parser loads both normalized preset types`() {
        val defaults = BundledSitePrivacyDefaults.parse(
            """
            # candy site privacy defaults v2

            force_vertical_scroll\tnews.example
            cookie_banner_removal_disabled\tshop.example
            """.trimIndent().replace("\\t", "\t"),
        )

        assertTrue(defaults.forceVerticalScrolling("NEWS.EXAMPLE"))
        assertFalse(defaults.cookieBannerRemovalDisabled("news.example"))
        assertTrue(defaults.cookieBannerRemovalDisabled("shop.example"))
        assertFalse(defaults.forceVerticalScrolling("shop.example"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parser rejects duplicate host presets`() {
        BundledSitePrivacyDefaults.parse(
            """
            # candy site privacy defaults v2
            force_vertical_scroll\tnews.example
            cookie_banner_removal_disabled\tnews.example
            """.trimIndent().replace("\\t", "\t"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parser rejects noncanonical host`() {
        BundledSitePrivacyDefaults.parse(
            "# candy site privacy defaults v2\nforce_vertical_scroll\tNews.Example",
        )
    }
}
