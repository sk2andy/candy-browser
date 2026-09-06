package dev.sk2andy.materialbrowser.browser.gecko

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking

@RunWith(AndroidJUnit4::class)
class GeckoRuntimeSettingsInstrumentedTest {
    @Test
    fun userCaTrustRequiresExplicitBuildChannelOptIn() {
        val blocking = ContentBlocking.Settings.Builder().build()

        assertFalse(GeckoRuntimeSettingsFactory.create(blocking, false).enterpriseRootsEnabled)
        assertTrue(GeckoRuntimeSettingsFactory.create(blocking, true).enterpriseRootsEnabled)
        assertEquals(
            BuildConfig.TRUST_USER_CERTIFICATES,
            GeckoRuntimeSettingsFactory.create(blocking).enterpriseRootsEnabled,
        )
    }
}
