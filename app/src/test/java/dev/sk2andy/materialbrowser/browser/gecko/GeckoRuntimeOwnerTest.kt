package dev.sk2andy.materialbrowser.browser.gecko

import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

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

    private class FakeRuntimeHandle : GeckoRuntimeHandle {
        override val extensions = FakeExtensionRuntime()
        override val toppings = FakeToppingHostRuntime()

        override fun createSession(
            profileId: String,
            isPrivate: Boolean,
            privacyPolicy: GeckoPrivacyPolicy,
            privacyEventSink: GeckoPrivacyEventSink,
        ): GeckoBrowserSession = error("Not used")
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
