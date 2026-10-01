package dev.sk2andy.materialbrowser.browser.integration

import android.content.IntentFilter
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalWebAppHandlerRulesInstrumentedTest {
    @Test
    fun genericDownloadFiltersDoNotRepresentSiteApps() {
        assertFalse(matches(null, "https://kesha.lnk.to/period"))
        assertFalse(matches(filter(), "https://kesha.lnk.to/period"))
        assertFalse(matches(filter("*"), "https://kesha.lnk.to/period"))
    }

    @Test
    fun exactHostAndDomainWildcardHandlersRemainAvailable() {
        assertTrue(matches(filter("open.spotify.com"), "https://open.spotify.com/track/candy"))
        assertTrue(matches(filter("*.spotify.com"), "https://open.spotify.com/track/candy"))
        assertFalse(matches(filter("*.spotify.com"), "https://spotify.com/track/candy"))
        assertFalse(matches(filter("*.spotify.com"), "https://notspotify.com/track/candy"))
    }

    @Test
    fun unrelatedSpecificHostCannotAuthorizeCatchAllMatch() {
        val filter = filter("*", "open.spotify.com")
        assertFalse(matches(filter, "https://kesha.lnk.to/period"))
        assertTrue(matches(filter, "https://open.spotify.com/track/candy"))
    }

    @Test
    fun matchingAuthorityRetainsAndroidPortRestrictions() {
        val filter = IntentFilter().apply { addDataAuthority("open.spotify.com", "8443") }
        assertTrue(matches(filter, "https://open.spotify.com:8443/track/candy"))
        assertFalse(matches(filter, "https://open.spotify.com/track/candy"))
    }

    private fun matches(filter: IntentFilter?, url: String): Boolean =
        ExternalWebAppHandlerRules.matchesSpecificHost(filter, Uri.parse(url))

    private fun filter(vararg hosts: String): IntentFilter = IntentFilter().apply {
        hosts.forEach { host -> addDataAuthority(host, null) }
    }
}
