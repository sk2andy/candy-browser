package dev.sk2andy.materialbrowser.browser.gecko

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoDefaultExtensionProvisionerTest {
    @Test
    fun `fresh catalog installs bundled defaults once`() = runBlocking {
        val state = FakeStateStore()
        val installer = FakeInstaller()
        val verifier = FakeAssetVerifier()
        val provisioner = provisioner(state, verifier, installer)

        val result = provisioner.provision()

        assertEquals(
            mapOf(
                U_BLOCK.id to GeckoDefaultExtensionProvisioningOutcome.Installed,
                COOKIE_HELPER.id to GeckoDefaultExtensionProvisioningOutcome.Installed,
            ),
            result.outcomes,
        )
        assertEquals(listOf(U_BLOCK.id, COOKIE_HELPER.id), installer.installCalls)
        assertEquals(
            mapOf(
                U_BLOCK.id to GeckoDefaultExtensionState.Provisioned,
                COOKIE_HELPER.id to GeckoDefaultExtensionState.Provisioned,
            ),
            state.values,
        )
    }

    @Test
    fun `existing disabled default is not duplicated or reenabled`() = runBlocking {
        val existing = installedExtension(U_BLOCK).copy(enabled = false)
        val installer = FakeInstaller(installed = linkedMapOf(U_BLOCK.id to existing))

        val result = provisioner(installer = installer).provision()

        assertEquals(
            GeckoDefaultExtensionProvisioningOutcome.AlreadyInstalled,
            result.outcomes[U_BLOCK.id],
        )
        assertTrue(installer.installCalls.none { extensionId -> extensionId == U_BLOCK.id })
        assertFalse(installer.installed.getValue(U_BLOCK.id).enabled)
    }

    @Test
    fun `older signed default is upgraded in place with its settings`() = runBlocking {
        val existing = installedExtension(U_BLOCK).copy(
            version = "1.75.0",
            enabled = false,
            allowedInPrivateBrowsing = true,
        )
        val installer = FakeInstaller(installed = linkedMapOf(U_BLOCK.id to existing))
        val provisioner = provisioner(installer = installer)

        val result = provisioner.provision()

        assertEquals(GeckoDefaultExtensionProvisioningOutcome.Updated, result.outcomes[U_BLOCK.id])
        assertEquals(existing.copy(version = U_BLOCK.version), installer.installed[U_BLOCK.id])
        assertEquals(listOf(U_BLOCK.id), installer.upgradeCalls)
        assertTrue(installer.uninstallCalls.isEmpty())
        assertEquals(
            GeckoDefaultExtensionProvisioningOutcome.AlreadyInstalled,
            provisioner.provision().outcomes[U_BLOCK.id],
        )
        assertEquals(listOf(U_BLOCK.id), installer.upgradeCalls)
    }

    @Test
    fun `newer equivalent and unrecognized versions are left installed`() = runBlocking {
        for (version in listOf("1.100.0", "1.76.4.1", "1.76.4.0", "1.76.5b1", null)) {
            val existing = installedExtension(U_BLOCK).copy(version = version)
            val installer = FakeInstaller(installed = linkedMapOf(U_BLOCK.id to existing))

            val result = provisioner(installer = installer).provision()

            assertEquals(GeckoDefaultExtensionProvisioningOutcome.AlreadyInstalled, result.outcomes[U_BLOCK.id])
            assertEquals(existing, installer.installed[U_BLOCK.id])
            assertTrue(U_BLOCK.id !in installer.installCalls)
            assertTrue(installer.uninstallCalls.isEmpty())
        }
    }

    @Test
    fun `failed bundle verification leaves the older install available`() = runBlocking {
        val existing = installedExtension(U_BLOCK).copy(version = "1.75.0")
        val installer = FakeInstaller(installed = linkedMapOf(U_BLOCK.id to existing))

        val result = provisioner(
            verifier = FakeAssetVerifier(valid = false),
            installer = installer,
        ).provision()

        assertEquals(GeckoDefaultExtensionProvisioningOutcome.Failed, result.outcomes[U_BLOCK.id])
        assertEquals(existing, installer.installed[U_BLOCK.id])
        assertTrue(installer.installCalls.isEmpty())
        assertTrue(installer.uninstallCalls.isEmpty())
    }

    @Test
    fun `missing provisioned default retries after a persistence checkpoint gap`() = runBlocking {
        val state = FakeStateStore(
            U_BLOCK.id to GeckoDefaultExtensionState.Provisioned,
        )
        val installer = FakeInstaller()

        val result = provisioner(state = state, installer = installer).provision()

        assertEquals(GeckoDefaultExtensionProvisioningOutcome.Installed, result.outcomes[U_BLOCK.id])
        assertEquals(GeckoDefaultExtensionState.Provisioned, state.values[U_BLOCK.id])
        assertTrue(installer.installCalls.any { extensionId -> extensionId == U_BLOCK.id })
    }

    @Test
    fun `explicit removal tombstone prevents installation`() = runBlocking {
        val state = FakeStateStore(U_BLOCK.id to GeckoDefaultExtensionState.Removed)
        val installer = FakeInstaller()

        val result = provisioner(state = state, installer = installer).provision()

        assertEquals(GeckoDefaultExtensionProvisioningOutcome.Removed, result.outcomes[U_BLOCK.id])
        assertTrue(installer.installCalls.none { extensionId -> extensionId == U_BLOCK.id })
    }

    @Test
    fun `removal tombstone wins over a stale Gecko registry entry`() = runBlocking {
        val state = FakeStateStore(U_BLOCK.id to GeckoDefaultExtensionState.Removed)
        val installer = FakeInstaller(
            installed = linkedMapOf(U_BLOCK.id to installedExtension(U_BLOCK).copy(version = "1.75.0")),
        )

        val result = provisioner(state = state, installer = installer).provision()

        assertEquals(GeckoDefaultExtensionProvisioningOutcome.Removed, result.outcomes[U_BLOCK.id])
        assertEquals(listOf(U_BLOCK.id), installer.uninstallCalls)
        assertEquals(GeckoDefaultExtensionState.Removed, state.values[U_BLOCK.id])
    }

    @Test
    fun `removal intent is persisted before runtime uninstall is allowed`() = runBlocking {
        val state = FakeStateStore()
        val provisioner = provisioner(state = state)

        assertTrue(provisioner.markRemovalRequested(U_BLOCK.id))

        assertEquals(GeckoDefaultExtensionState.Removed, state.values[U_BLOCK.id])
    }

    @Test
    fun `successful explicit reinstall clears removal tombstone`() = runBlocking {
        val state = FakeStateStore(U_BLOCK.id to GeckoDefaultExtensionState.Removed)
        val provisioner = provisioner(state = state)

        assertTrue(provisioner.markUserInstallSucceeded(U_BLOCK.id))

        assertEquals(GeckoDefaultExtensionState.Provisioned, state.values[U_BLOCK.id])
    }

    @Test
    fun `provisioned retired default is removed before its replacement installs`() = runBlocking {
        val state = FakeStateStore(
            RETIRED_COOKIE_HELPER.id to GeckoDefaultExtensionState.Provisioned,
        )
        val installer = FakeInstaller(
            installed = linkedMapOf(
                RETIRED_COOKIE_HELPER.id to installedExtension(RETIRED_COOKIE_HELPER),
            ),
        )

        provisioner(
            state = state,
            installer = installer,
            retiredExtensionIds = setOf(RETIRED_COOKIE_HELPER.id),
        ).provision()

        assertEquals(listOf(RETIRED_COOKIE_HELPER.id), installer.uninstallCalls)
        assertEquals(
            GeckoDefaultExtensionState.Removed,
            state.values[RETIRED_COOKIE_HELPER.id],
        )
        assertTrue(installer.installCalls.contains(COOKIE_HELPER.id))
    }

    @Test
    fun `failed bundled integrity check does not invoke Gecko install`() = runBlocking {
        val verifier = FakeAssetVerifier(valid = false)
        val installer = FakeInstaller()

        val result = provisioner(verifier = verifier, installer = installer).provision()

        assertEquals(GeckoDefaultExtensionProvisioningOutcome.Failed, result.outcomes[U_BLOCK.id])
        assertTrue(installer.installCalls.none { extensionId -> extensionId == U_BLOCK.id })
        assertTrue(installer.installCalls.none { extensionId -> extensionId == COOKIE_HELPER.id })
    }

    @Test
    fun `unexpected installed identity is removed and not persisted`() = runBlocking {
        val state = FakeStateStore()
        val installer = FakeInstaller(
            installResult = installedExtension(U_BLOCK).copy(id = "unexpected@example.com"),
        )

        val result = GeckoDefaultExtensionProvisioner(
            extensions = listOf(U_BLOCK),
            stateStore = state,
            assetVerifier = FakeAssetVerifier(),
            installer = installer,
        ).provision()

        assertEquals(GeckoDefaultExtensionProvisioningOutcome.Failed, result.outcomes[U_BLOCK.id])
        assertEquals(listOf("unexpected@example.com"), installer.uninstallCalls)
        assertFalse(state.values.containsKey(U_BLOCK.id))
    }

    @Test
    fun `new default must be signed persistent enabled and private denied`() {
        val valid = installedExtension(U_BLOCK)

        assertTrue(GeckoDefaultExtensionRules.matchesPackage(U_BLOCK, valid))
        assertTrue(GeckoDefaultExtensionRules.matchesFreshInstall(valid))
        assertFalse(GeckoDefaultExtensionRules.matchesPackage(U_BLOCK, valid.copy(signedState = 1)))
        assertFalse(GeckoDefaultExtensionRules.matchesPackage(U_BLOCK, valid.copy(temporary = true)))
        assertFalse(
            GeckoDefaultExtensionRules.matchesFreshInstall(valid.copy(allowedInPrivateBrowsing = true)),
        )
    }

    @Test
    fun `startup retries failed defaults with a bounded same-process cooldown`() = runBlocking {
        val state = FakeStateStore()
        val verifier = FakeAssetVerifier(valid = false)
        val installer = FakeInstaller()
        var nowNanos = 0L
        val startup = GeckoDefaultExtensionStartup(
            scope = this,
            runAfterInternalHostInitialization = { action -> action(true) },
            provisionerFactory = { provisioner(state, verifier, installer) },
            nanoTime = { nowNanos },
        )

        startup.await()
        assertEquals(
            GeckoDefaultExtensionProvisioningOutcome.Failed,
            startup.result?.outcomes?.get(U_BLOCK.id),
        )

        val verificationCallsAfterStartup = verifier.calls
        startup.retryFailedIfDue()
        assertEquals(verificationCallsAfterStartup, verifier.calls)

        nowNanos += 30_000_000_000L
        var advancedDuringRetry = false
        verifier.onVerify = {
            if (!advancedDuringRetry) {
                nowNanos += 30_000_000_000L
                advancedDuringRetry = true
            }
        }
        startup.retryFailedIfDue()
        val verificationCallsAfterRetry = verifier.calls
        verifier.onVerify = {}
        verifier.valid = true
        startup.retryFailedIfDue()
        assertEquals(verificationCallsAfterRetry, verifier.calls)

        nowNanos += 30_000_000_000L
        startup.retryFailedIfDue()
        assertEquals(
            GeckoDefaultExtensionProvisioningOutcome.Installed,
            startup.result?.outcomes?.get(U_BLOCK.id),
        )
    }

    @Test
    fun `startup inventory failure retries after cooldown and preserves installed extensions`() = runBlocking {
        val installer = FakeInstaller(linkedMapOf(U_BLOCK.id to installedExtension(U_BLOCK)))
        installer.listFailure = IllegalStateException("Gecko not ready")
        var nowNanos = 0L
        val startup = GeckoDefaultExtensionStartup(
            scope = this,
            runAfterInternalHostInitialization = { it(true) },
            provisionerFactory = { provisioner(installer = installer) },
            nanoTime = { nowNanos },
        )

        assertTrue(runCatching { startup.await() }.exceptionOrNull() is GeckoDefaultExtensionInventoryException)
        assertEquals(1, installer.listCalls)
        installer.listFailure = null
        assertTrue(runCatching { startup.await() }.exceptionOrNull() is GeckoDefaultExtensionInventoryException)
        assertEquals(1, installer.listCalls)

        nowNanos += 30_000_000_000L
        startup.await()
        startup.await()
        assertEquals(2, installer.listCalls)
        assertEquals(listOf(COOKIE_HELPER.id), installer.installCalls)
        assertTrue(installer.uninstallCalls.isEmpty())
        assertEquals(GeckoDefaultExtensionProvisioningOutcome.AlreadyInstalled, startup.result?.outcomes?.get(U_BLOCK.id))
    }

    @Test
    fun `startup inventory retry stops after three failed attempts`() = runBlocking {
        val installer = FakeInstaller().apply { listFailure = IllegalStateException("Gecko unavailable") }
        var nowNanos = 0L
        val startup = GeckoDefaultExtensionStartup(
            scope = this,
            runAfterInternalHostInitialization = { it(true) },
            provisionerFactory = { provisioner(installer = installer) },
            nanoTime = { nowNanos },
        )

        repeat(6) {
            assertTrue(runCatching { startup.await() }.exceptionOrNull() is GeckoDefaultExtensionInventoryException)
            nowNanos += 30_000_000_000L
        }
        assertEquals(4, installer.listCalls)
        assertTrue(installer.installCalls.isEmpty())
    }

    @Test
    fun `failed internal privacy initialization never retries provisioning`() = runBlocking {
        var factoryCalls = 0
        val startup = GeckoDefaultExtensionStartup(
            scope = this,
            runAfterInternalHostInitialization = { it(false) },
            provisionerFactory = { factoryCalls++; provisioner() },
        )

        repeat(2) {
            assertTrue(runCatching { startup.await() }.exceptionOrNull() is IllegalStateException)
        }
        assertEquals(0, factoryCalls)
    }

    @Test
    fun `failure after a provisioning mutation is not automatically replayed`() = runBlocking {
        val failure = IllegalStateException("State read failed after first install")
        val state = object : GeckoDefaultExtensionStateStore {
            override suspend fun read(extensionId: String): GeckoDefaultExtensionState? {
                if (extensionId == COOKIE_HELPER.id) throw failure
                return null
            }
            override suspend fun write(extensionId: String, state: GeckoDefaultExtensionState) = true
        }
        val installer = FakeInstaller()
        var nowNanos = 0L
        val startup = GeckoDefaultExtensionStartup(
            scope = this,
            runAfterInternalHostInitialization = { it(true) },
            provisionerFactory = {
                GeckoDefaultExtensionProvisioner(listOf(U_BLOCK, COOKIE_HELPER), state, FakeAssetVerifier(), installer)
            },
            nanoTime = { nowNanos },
        )

        assertSame(failure, runCatching { startup.await() }.exceptionOrNull())
        nowNanos += 30_000_000_000L
        assertSame(failure, runCatching { startup.await() }.exceptionOrNull())
        assertEquals(1, installer.listCalls)
        assertEquals(listOf(U_BLOCK.id), installer.installCalls)
    }

    @Test
    fun `inventory cancellation is not classified as retryable failure`() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val installer = FakeInstaller().apply { listFailure = cancellation }
        assertSame(cancellation, runCatching { provisioner(installer = installer).provision() }.exceptionOrNull())
        assertTrue(installer.installCalls.isEmpty())
    }

    @Test
    fun `timed out manager reader does not cancel shared startup`() = runBlocking {
        val releaseFactory = CompletableDeferred<Unit>()
        val installer = FakeInstaller()
        val startup = GeckoDefaultExtensionStartup(
            scope = this,
            runAfterInternalHostInitialization = { it(true) },
            provisionerFactory = {
                releaseFactory.await()
                provisioner(installer = installer)
            },
        )
        val reader = async { withTimeoutOrNull(1) { startup.await(); true } }
        yield()
        assertEquals(null, reader.await())
        releaseFactory.complete(Unit)
        startup.await()
        assertEquals(1, installer.listCalls)
        assertEquals(listOf(U_BLOCK.id, COOKIE_HELPER.id), installer.installCalls)
    }

    @Test
    fun `cancelled retry is not replayed or exposed as cancellation to later readers`() = runBlocking {
        val installer = FakeInstaller().apply { listFailure = IllegalStateException("Not ready") }
        var nowNanos = 0L
        val startup = GeckoDefaultExtensionStartup(
            scope = this,
            runAfterInternalHostInitialization = { it(true) },
            provisionerFactory = { provisioner(installer = installer) },
            nanoTime = { nowNanos },
        )
        assertTrue(runCatching { startup.await() }.exceptionOrNull() is GeckoDefaultExtensionInventoryException)
        val cancellation = CancellationException("Reader cancelled")
        installer.listFailure = cancellation
        nowNanos += 30_000_000_000L
        assertSame(cancellation, runCatching { startup.await() }.exceptionOrNull())
        installer.listFailure = null
        nowNanos += 30_000_000_000L
        val subsequentFailure = runCatching { startup.await() }.exceptionOrNull()
        assertTrue(subsequentFailure is IllegalStateException)
        assertFalse(subsequentFailure is CancellationException)
        assertSame(cancellation, subsequentFailure?.cause)
        assertEquals(2, installer.listCalls)
        assertTrue(installer.installCalls.isEmpty())
    }

    @Test
    fun `cancelled retry of reported default failures never replays a dispatched install`() = runBlocking {
        val verifier = FakeAssetVerifier(valid = false)
        val installer = FakeInstaller()
        val installStarted = CompletableDeferred<Unit>()
        var nowNanos = 0L
        val startup = GeckoDefaultExtensionStartup(
            scope = this,
            runAfterInternalHostInitialization = { it(true) },
            provisionerFactory = { provisioner(verifier = verifier, installer = installer) },
            nanoTime = { nowNanos },
        )
        startup.await()
        assertEquals(GeckoDefaultExtensionProvisioningOutcome.Failed, startup.result?.outcomes?.get(U_BLOCK.id))
        assertTrue(installer.installCalls.isEmpty())
        verifier.valid = true
        installer.onInstall = {
            installStarted.complete(Unit)
            awaitCancellation()
        }
        nowNanos += 30_000_000_000L

        val reader = launch { startup.retryFailedIfDue() }
        installStarted.await()
        reader.cancelAndJoin()
        nowNanos += 30_000_000_000L

        installer.onInstall = {}
        val subsequentFailure = runCatching { startup.retryFailedIfDue() }.exceptionOrNull()
        assertTrue(subsequentFailure is IllegalStateException)
        assertTrue(subsequentFailure?.cause is CancellationException)
        assertEquals(2, installer.listCalls)
        assertEquals(listOf(U_BLOCK.id), installer.installCalls)
    }

    private fun provisioner(
        state: FakeStateStore = FakeStateStore(),
        verifier: FakeAssetVerifier = FakeAssetVerifier(),
        installer: FakeInstaller = FakeInstaller(),
        retiredExtensionIds: Set<String> = emptySet(),
    ) = GeckoDefaultExtensionProvisioner(
        extensions = listOf(U_BLOCK, COOKIE_HELPER),
        stateStore = state,
        assetVerifier = verifier,
        installer = installer,
        retiredExtensionIds = retiredExtensionIds,
    )

    private class FakeStateStore(
        vararg initial: Pair<String, GeckoDefaultExtensionState>,
    ) : GeckoDefaultExtensionStateStore {
        val values = linkedMapOf(*initial)

        override suspend fun read(extensionId: String): GeckoDefaultExtensionState? =
            values[extensionId]

        override suspend fun write(
            extensionId: String,
            state: GeckoDefaultExtensionState,
        ): Boolean {
            values[extensionId] = state
            return true
        }
    }

    private class FakeAssetVerifier(
        var valid: Boolean = true,
    ) : GeckoDefaultExtensionAssetVerifier {
        var calls = 0
        var onVerify: () -> Unit = {}

        override suspend fun verify(extension: GeckoDefaultExtension): Boolean {
            calls++
            onVerify()
            return extension.delivery == GeckoDefaultExtensionDelivery.Remote || valid
        }
    }

    private class FakeInstaller(
        val installed: LinkedHashMap<String, GeckoExtension> = linkedMapOf(),
        private val installResult: GeckoExtension? = null,
    ) : GeckoDefaultExtensionInstaller {
        val installCalls = mutableListOf<String>()
        val upgradeCalls = mutableListOf<String>()
        val uninstallCalls = mutableListOf<String>()

        var listCalls = 0
        var listFailure: Throwable? = null
        var onInstall: suspend () -> Unit = {}

        override suspend fun listInstalled(): List<GeckoExtension> {
            listCalls++
            listFailure?.let { throw it }
            return installed.values.toList()
        }

        override suspend fun install(
            extension: GeckoDefaultExtension,
            replaceOlder: Boolean,
        ): GeckoExtension {
            installCalls += extension.id
            onInstall()
            val result = if (replaceOlder) {
                upgradeCalls += extension.id
                installed.getValue(extension.id).copy(version = extension.version)
            } else {
                installedExtension(extension)
            }
            return (installResult ?: result).also { result ->
                installed[result.id] = result
            }
        }

        override suspend fun uninstall(extensionId: String) {
            uninstallCalls += extensionId
            installed.remove(extensionId)
        }
    }

    private companion object {
        val U_BLOCK = defaultExtension(
            id = "uBlock0@raymondhill.net",
            name = "uBlock Origin",
            delivery = GeckoDefaultExtensionDelivery.Bundled,
            installUri = "resource://android/assets/gecko_default_extensions/ublock.xpi",
            assetPath = "gecko_default_extensions/ublock.xpi",
        ).copy(version = "1.76.4")
        val COOKIE_HELPER = defaultExtension(
            id = "idcac-pub@guus.ninja",
            name = "I still don't care about cookies",
            delivery = GeckoDefaultExtensionDelivery.Bundled,
            installUri = "resource://android/assets/gecko_default_extensions/cookies.xpi",
            assetPath = "gecko_default_extensions/cookies.xpi",
        )
        val RETIRED_COOKIE_HELPER = defaultExtension(
            id = "jid1-KKzOGWgsW3Ao4Q@jetpack",
            name = "I don't care about cookies",
            delivery = GeckoDefaultExtensionDelivery.Remote,
            installUri =
                "https://addons.mozilla.org/firefox/downloads/file/1/cookies-1.0.xpi",
            assetPath = null,
        )

        fun defaultExtension(
            id: String,
            name: String,
            delivery: GeckoDefaultExtensionDelivery,
            installUri: String,
            assetPath: String?,
        ) = GeckoDefaultExtension(
            id = id,
            name = name,
            version = "1.0",
            installUri = installUri,
            sha256 = "a".repeat(64),
            size = 1_024,
            delivery = delivery,
            assetPath = assetPath,
        )

        fun installedExtension(extension: GeckoDefaultExtension) = GeckoExtension(
            id = extension.id,
            name = extension.name,
            version = extension.version,
            enabled = true,
            allowedInPrivateBrowsing = false,
            isBuiltIn = false,
            temporary = false,
            signedState = 2,
        )
    }
}
