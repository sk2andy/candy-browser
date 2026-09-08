package dev.sk2andy.materialbrowser.browser.gecko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val blocking = ContentBlocking.Settings.Builder().build()

            assertFalse(GeckoRuntimeSettingsFactory.create(blocking, false).enterpriseRootsEnabled)
            assertTrue(GeckoRuntimeSettingsFactory.create(blocking, true).enterpriseRootsEnabled)
            assertEquals(
                BuildConfig.TRUST_USER_CERTIFICATES,
                GeckoRuntimeSettingsFactory.create(blocking).enterpriseRootsEnabled,
            )
        }
    }

    @Test
    fun websiteFontSizeUsesManualRuntimeScaling() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val settings = GeckoRuntimeSettingsFactory.create(
                ContentBlocking.Settings.Builder().build(),
            )

            assertFalse(settings.automaticFontSizeAdjustment)
            settings.fontSizeFactor = 1.55f
            assertEquals(1.55f, settings.fontSizeFactor, 0f)
        }
    }
}
