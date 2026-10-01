package dev.sk2andy.materialbrowser.browser.gecko

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.HttpsOnlyMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings

@RunWith(AndroidJUnit4::class)
class GeckoHttpsOnlyRuntimeSettingsInstrumentedTest {
    @Test
    fun defaultProtectsRegularAndPrivateTabs() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val settings = GeckoRuntimeSettingsFactory.create(
                ContentBlocking.Settings.Builder().build(),
            )

            assertEquals(GeckoRuntimeSettings.HTTPS_ONLY, settings.allowInsecureConnections)
        }
    }

    @Test
    fun startupHonorsPrivateOnlyPreference() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val settings = GeckoRuntimeSettingsFactory.create(
                contentBlocking = ContentBlocking.Settings.Builder().build(),
                httpsOnlyMode = HttpsOnlyMode.PrivateOnly,
            )

            assertEquals(GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE, settings.allowInsecureConnections)
        }
    }

    @Test
    fun switchingModesClearsPreviousRegularAndPrivatePreferences() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val settings = GeckoRuntimeSettingsFactory.create(
                contentBlocking = ContentBlocking.Settings.Builder().build(),
                httpsOnlyMode = HttpsOnlyMode.Off,
            )

            assertEquals(GeckoRuntimeSettings.ALLOW_ALL, settings.allowInsecureConnections)
            settings.applyHttpsOnlyMode(HttpsOnlyMode.PrivateOnly)
            assertEquals(GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE, settings.allowInsecureConnections)
            settings.applyHttpsOnlyMode(HttpsOnlyMode.AllTabs)
            assertEquals(GeckoRuntimeSettings.HTTPS_ONLY, settings.allowInsecureConnections)
            settings.applyHttpsOnlyMode(HttpsOnlyMode.PrivateOnly)
            assertEquals(GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE, settings.allowInsecureConnections)
            settings.applyHttpsOnlyMode(HttpsOnlyMode.Off)
            assertEquals(GeckoRuntimeSettings.ALLOW_ALL, settings.allowInsecureConnections)
        }
    }
}
