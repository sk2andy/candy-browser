package dev.sk2andy.materialbrowser.browser.gecko

import android.content.res.Configuration
import dev.sk2andy.materialbrowser.browser.WebRtcProtectionMode
import dev.sk2andy.materialbrowser.browser.engine.BrowserWebContentColorScheme
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mozilla.geckoview.GeckoRuntime

class GeckoRuntimeOwnerTest {
    @Before
    fun setUp() {
        GeckoRuntimeOwner.resetForTesting()
    }

    @After
    fun tearDown() {
        GeckoRuntimeOwner.resetForTesting()
    }

    @Test
    fun `runtime is created once for the complete process`() {
        var creationCount = 0
        val first = GeckoRuntimeOwner.getOrCreate {
            creationCount++
            FakeRuntimeHandle()
        }
        val second = GeckoRuntimeOwner.getOrCreate {
            creationCount++
            FakeRuntimeHandle()
        }

        assertSame(first, second)
        assertEquals(1, creationCount)
    }

    @Test
    fun `session factory forwards global third party cookie blocking`() {
        val runtime = FakeRuntimeHandle()
        val factory = GeckoBrowserEngineSessionFactory(runtime)

        factory.setBlockThirdPartyCookies(false)

        assertFalse(runtime.thirdPartyCookiesBlocked)
    }

    @Test
    fun `session factory forwards global website font size factor`() {
        val runtime = FakeRuntimeHandle()
        val factory = GeckoBrowserEngineSessionFactory(runtime)

        factory.setWebContentFontSizeFactor(1.55f)

        assertEquals(1.55f, runtime.recordedWebContentFontSizeFactor, 0f)
    }

    @Test
    fun `session factory forwards web content color scheme`() {
        val runtime = FakeRuntimeHandle()
        val factory = GeckoBrowserEngineSessionFactory(runtime)

        factory.setWebContentColorScheme(BrowserWebContentColorScheme.Dark)

        assertEquals(BrowserWebContentColorScheme.Dark, runtime.recordedWebContentColorScheme)
    }

    @Test
    fun `session factory forwards host configuration changes`() {
        val runtime = FakeRuntimeHandle()
        val factory = GeckoBrowserEngineSessionFactory(runtime)
        val configuration = Configuration()

        factory.onConfigurationChanged(configuration)

        assertSame(configuration, runtime.recordedConfiguration)
    }

    private class FakeRuntimeHandle : GeckoRuntimeHandle {
        override val extensions = FakeExtensionRuntime()
        override val toppings = FakeToppingHostRuntime()
        var thirdPartyCookiesBlocked = true
        var recordedWebContentFontSizeFactor = 1f
        var recordedWebContentColorScheme = BrowserWebContentColorScheme.System
        var recordedConfiguration: Configuration? = null

        override fun createSession(
            profileId: String,
            isolationEnabled: Boolean,
            isPrivate: Boolean,
            privacyPolicy: GeckoPrivacyPolicy,
            privacyEventSink: GeckoPrivacyEventSink,
        ): GeckoBrowserSession = error("Not used")

        override fun clearBrowsingData(
            data: GeckoBrowsingData,
            onComplete: (Boolean) -> Unit,
        ) = onComplete(true)

        override fun setBlockThirdPartyCookies(blocked: Boolean) {
            thirdPartyCookiesBlocked = blocked
        }

        override fun setWebRtcProtectionMode(mode: WebRtcProtectionMode, onReady: () -> Unit) {
            onReady()
        }

        override fun setWebContentFontSizeFactor(factor: Float) {
            recordedWebContentFontSizeFactor = factor
        }

        override fun setWebContentColorScheme(colorScheme: BrowserWebContentColorScheme) {
            recordedWebContentColorScheme = colorScheme
        }

        override fun onConfigurationChanged(configuration: Configuration) {
            recordedConfiguration = configuration
        }

        override fun bindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate) = Unit

        override fun unbindWebAuthnActivityDelegate(delegate: GeckoRuntime.ActivityDelegate) = Unit
    }

    private class FakeToppingHostRuntime : GeckoToppingHostRuntime {
        override val state = GeckoToppingHostState.Ready

        override fun reconcile(
            scripts: List<UserScript>,
        ) = Unit

        override fun runAfterInitialization(action: () -> Unit) = action()

        override fun setStateListener(listener: (GeckoToppingHostState) -> Unit) {
            listener(state)
        }
    }

    private class FakeExtensionRuntime : GeckoExtensionRuntime {
        override fun setPermissionPrompt(prompt: GeckoExtensionPermissionPrompt) = Unit

        override suspend fun listInstalled(): List<GeckoExtension> = emptyList()

        override suspend fun installSignedXpi(
            uri: String,
            installationMethod: String,
        ): GeckoExtension = error("Not used")

        override suspend fun enable(extensionId: String): GeckoExtension = error("Not used")

        override suspend fun disable(extensionId: String): GeckoExtension = error("Not used")

        override suspend fun update(extensionId: String): GeckoExtension = error("Not used")

        override suspend fun setAllowedInPrivateBrowsing(
            extensionId: String,
            allowed: Boolean,
        ): GeckoExtension = error("Not used")

        override suspend fun uninstall(extensionId: String) = Unit
    }
}
