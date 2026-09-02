package dev.sk2andy.materialbrowser.data.sync

import dev.sk2andy.materialbrowser.sync.SyncBase64
import dev.sk2andy.materialbrowser.sync.SyncBootstrap
import dev.sk2andy.materialbrowser.sync.SyncCache
import dev.sk2andy.materialbrowser.sync.SyncConnectionSettings
import dev.sk2andy.materialbrowser.sync.SyncCrypto
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDescriptor
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconCatalog
import dev.sk2andy.materialbrowser.sync.SyncDeviceIconDefinition
import dev.sk2andy.materialbrowser.sync.SyncDeviceIdentity
import dev.sk2andy.materialbrowser.sync.SyncDeviceRecord
import dev.sk2andy.materialbrowser.sync.SyncDeviceStatus
import dev.sk2andy.materialbrowser.sync.SyncEncryptedChange
import dev.sk2andy.materialbrowser.sync.SyncEncryptedDelta
import dev.sk2andy.materialbrowser.sync.SyncEncryptedValue
import dev.sk2andy.materialbrowser.sync.SyncEnrollmentOutcome
import dev.sk2andy.materialbrowser.sync.SyncPendingMutation
import dev.sk2andy.materialbrowser.sync.SyncProfile
import dev.sk2andy.materialbrowser.sync.SyncPullPage
import dev.sk2andy.materialbrowser.sync.SyncDeltaPullPage
import dev.sk2andy.materialbrowser.sync.SyncRecoveryEnvelope
import dev.sk2andy.materialbrowser.sync.SyncRealtimeEvent
import dev.sk2andy.materialbrowser.sync.SyncRealtimeTicket
import dev.sk2andy.materialbrowser.sync.SyncServerSnapshot
import dev.sk2andy.materialbrowser.sync.SyncTab
import dev.sk2andy.materialbrowser.sync.SyncTabSnapshot
import dev.sk2andy.materialbrowser.sync.SyncVaultSecrets
import dev.sk2andy.materialbrowser.sync.SyncWriteOutcome
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandySyncRepositoryTest {
    private val crypto = SyncCrypto()
    private val workspaceKey = ByteArray(32) { 7 }
    private val identity = crypto.generateDeviceIdentity()
    private val settings = SyncConnectionSettings(
        endpoint = "https://sync.example/",
        username = "candy",
        deviceName = "Android",
        iconCatalogId = "phone",
        iconAccentHue = 42,
    )
    private val profile = SyncProfile(
        deviceId = "target-device",
        displayName = "Desktop",
        icon = SyncDeviceIconDescriptor("computer", 100),
        revision = 0,
        tabs = emptyList(),
        lastSeenAt = NOW,
    )

    @Test
    fun `state exposes local device identity without private key material`() {
        val repository = repository(
            FakeTransport(deviceRecord(), crypto),
            MemoryCacheStore(SyncCache("cursor-0", mapOf(profile.deviceId to profile))),
        )

        repository.use {
            assertEquals("android-device", it.currentState().currentDeviceId)
        }
    }

    @Test
    fun `lost response retry reuses exact durable ciphertext and change id`() {
        val transport = FakeTransport(deviceRecord(), crypto).apply { failFirstPutOffline = true }
        val cacheStore = MemoryCacheStore(SyncCache("cursor-0", mapOf(profile.deviceId to profile)))
        repository(transport, cacheStore).use { repository ->
            val mutation = SyncPendingMutation.Open("logical-1", profile.deviceId, tab("tab-1"))
            assertTrue(repository.mutate(mutation).get() is SyncWriteOutcome.Failed)
            val prepared = cacheStore.value.preparedWrites.getValue(mutation.mutationId)

            assertTrue(repository.refresh().get())
            assertEquals(2, transport.puts.size)
            assertEquals(transport.puts[0], transport.puts[1])
            assertEquals(prepared, transport.puts[1])
            assertTrue(cacheStore.value.pendingMutations.isEmpty())
            assertTrue(cacheStore.value.preparedWrites.isEmpty())
        }
    }

    @Test
    fun `confirmed conflict pulls latest and creates fresh encrypted attempt`() {
        val latestMetadata = SyncEncryptedChange(
            changeId = "remote-change",
            writerDeviceId = "remote-writer",
            targetDeviceId = profile.deviceId,
            baseRevision = 0,
            revision = null,
            nonce = "",
            ciphertext = "",
        )
        val latest = crypto.encryptTabSnapshot(
            workspaceKey,
            latestMetadata,
            SyncTabSnapshot(NOW, listOf(tab("remote-tab"))),
        ).copy(revision = 1)
        val transport = FakeTransport(deviceRecord(), crypto).apply {
            conflictFirstPut = true
            pulledChanges = listOf(latest)
        }
        repository(
            transport,
            MemoryCacheStore(SyncCache("cursor-0", mapOf(profile.deviceId to profile))),
        ).use { repository ->
            val outcome = repository.mutate(
                SyncPendingMutation.Open("logical-1", profile.deviceId, tab("local-tab")),
            ).get()
            assertTrue(outcome is SyncWriteOutcome.Synced)
            assertEquals(2, transport.puts.size)
            assertNotEquals(transport.puts[0].changeId, transport.puts[1].changeId)
            assertEquals(0, transport.puts[0].baseRevision)
            assertEquals(1, transport.puts[1].baseRevision)
        }
    }

    @Test
    fun `offline open and following navigation replay in durable order`() {
        val transport = FakeTransport(deviceRecord(), crypto).apply { failFirstPutOffline = true }
        val cacheStore = MemoryCacheStore(SyncCache("cursor-0", mapOf(profile.deviceId to profile)))
        repository(transport, cacheStore).use { repository ->
            assertTrue(
                repository.mutate(
                    SyncPendingMutation.Open("logical-open", profile.deviceId, tab("local-tab")),
                ).get() is SyncWriteOutcome.Failed,
            )
            assertTrue(
                repository.mutate(
                    SyncPendingMutation.Navigate(
                        "logical-navigate",
                        profile.deviceId,
                        "local-tab",
                        "Navigated",
                        "https://example.com/navigated",
                    ),
                ).get() is SyncWriteOutcome.Synced,
            )
            assertEquals(3, transport.puts.size)
            assertEquals(0, transport.puts[0].baseRevision)
            assertEquals(transport.puts[0], transport.puts[1])
            assertEquals(1, transport.puts[2].baseRevision)
            assertTrue(cacheStore.value.pendingMutations.isEmpty())
        }
    }

    @Test
    fun `successful put does not skip unapplied pull cursor`() {
        val transport = FakeTransport(deviceRecord(), crypto).apply { putCursor = "epoch.7" }
        val cacheStore = MemoryCacheStore(SyncCache("epoch.5", mapOf(profile.deviceId to profile)))
        repository(transport, cacheStore).use { repository ->
            val outcome = repository.mutate(
                SyncPendingMutation.Open("logical-1", profile.deviceId, tab("local-tab")),
            ).get() as SyncWriteOutcome.Synced

            assertEquals("epoch.5", outcome.cursor)
            assertEquals("epoch.5", cacheStore.value.cursor)
            assertTrue(transport.acknowledged.isEmpty())
        }
    }

    @Test
    fun `cursor reset falls back to paginated pull when snapshot is too large`() {
        val latestMetadata = SyncEncryptedChange(
            changeId = "remote-change",
            writerDeviceId = "remote-writer",
            targetDeviceId = profile.deviceId,
            baseRevision = 0,
            revision = null,
            nonce = "",
            ciphertext = "",
        )
        val latest = crypto.encryptTabSnapshot(
            workspaceKey,
            latestMetadata,
            SyncTabSnapshot(NOW, listOf(tab("remote-tab"))),
        ).copy(revision = 1)
        val transport = FakeTransport(deviceRecord(), crypto).apply {
            resetFirstPull = true
            snapshotTooLarge = true
            pulledChanges = listOf(latest)
        }
        val cacheStore = MemoryCacheStore(SyncCache("old-epoch.4", mapOf(profile.deviceId to profile)))
        repository(transport, cacheStore).use { repository ->
            assertTrue(repository.refresh().get())
            assertEquals(listOf("remote-tab"), cacheStore.value.profiles.getValue(profile.deviceId).tabs.map(SyncTab::candyId))
            assertEquals("epoch.1", cacheStore.value.cursor)
        }
    }

    @Test
    fun `failed outbox save never leaves a volatile mutation`() {
        val transport = FakeTransport(deviceRecord(), crypto)
        val cacheStore = MemoryCacheStore(SyncCache("epoch.0", mapOf(profile.deviceId to profile))).apply {
            failNextSave = true
        }
        repository(transport, cacheStore).use { repository ->
            val outcome = repository.mutate(
                SyncPendingMutation.Open("logical-1", profile.deviceId, tab("local-tab")),
            ).get()
            assertTrue(outcome is SyncWriteOutcome.Failed)
            assertTrue(cacheStore.value.pendingMutations.isEmpty())
            assertEquals(0, repository.currentState().pendingCount)
            assertTrue(transport.puts.isEmpty())
        }
    }

    @Test
    fun `enrollment rejects a short passphrase before network access and wipes inputs`() {
        val transport = FakeTransport(deviceRecord(), crypto)
        val password = "password".toCharArray()
        val passphrase = "too-short".toCharArray()
        repository(transport, MemoryCacheStore(SyncCache("", emptyMap()))).use { repository ->
            assertEquals(SyncEnrollmentOutcome.InvalidConfiguration, repository.enroll(password, passphrase).get())
            assertTrue(password.all { it == '\u0000' })
            assertTrue(passphrase.all { it == '\u0000' })
            assertFalse(transport.discovered)
        }
    }

    @Test
    fun `enrollment rejects reused server password before network access and wipes inputs`() {
        val transport = FakeTransport(deviceRecord(), crypto)
        val password = "same-secret-value".toCharArray()
        val passphrase = "same-secret-value".toCharArray()
        repository(transport, MemoryCacheStore(SyncCache("", emptyMap()))).use { repository ->
            assertEquals(
                SyncEnrollmentOutcome.InvalidConfiguration,
                repository.enroll(password, passphrase).get(),
            )
            assertTrue(password.all { it == '\u0000' })
            assertTrue(passphrase.all { it == '\u0000' })
            assertFalse(transport.discovered)
        }
    }

    @Test
    fun `failed configuration save never enrolls against previous endpoint`() {
        val transport = FakeTransport(deviceRecord(), crypto)
        val settingsStore = MemorySettingsStore(settings).apply { failNextSave = true }
        val password = "new-server-password".toCharArray()
        val passphrase = "new-e2ee-passphrase".toCharArray()
        repository(
            transport = transport,
            cacheStore = MemoryCacheStore(SyncCache("", emptyMap())),
            settingsStore = settingsStore,
        ).use { repository ->
            assertTrue(repository.configure(settings.copy(endpoint = "https://new-sync.example/")))
            assertEquals(
                SyncEnrollmentOutcome.InvalidConfiguration,
                repository.enroll(password, passphrase).get(),
            )
            assertTrue(password.all { it == '\u0000' })
            assertTrue(passphrase.all { it == '\u0000' })
            assertFalse(transport.discovered)
        }
    }

    @Test
    fun `v2 sends encrypted logical mutation without uploading snapshot`() {
        val transport = FakeTransport(deviceRecord(), crypto).apply { deltaMode = true }
        val cacheStore = MemoryCacheStore(SyncCache("epoch.0", mapOf(profile.deviceId to profile)))
        repository(transport, cacheStore).use { repository ->
            val mutation = SyncPendingMutation.Open("logical-1", profile.deviceId, tab("tab-1"))
            val outcome = repository.mutate(mutation).get()

            assertTrue(outcome is SyncWriteOutcome.Synced)
            assertTrue(transport.puts.isEmpty())
            assertEquals(1, transport.deltaPuts.size)
            assertEquals(
                mutation,
                crypto.decryptTabMutation(workspaceKey, transport.deltaPuts.single()),
            )
            assertTrue(cacheStore.value.preparedDeltas.isEmpty())
        }
    }

    @Test
    fun `v2 pull applies encrypted close and advances delta cursor`() {
        val tabbed = profile.copy(tabs = listOf(tab("tab-1")))
        val mutation = SyncPendingMutation.Close("remote-close", profile.deviceId, "tab-1")
        val metadata = SyncEncryptedDelta(
            changeId = "remote-change",
            mutationId = mutation.mutationId,
            workspaceId = "workspace-1",
            writerDeviceId = "remote-writer",
            targetDeviceId = profile.deviceId,
            baseRevision = 0,
            revision = null,
            nonce = "",
            ciphertext = "",
        )
        val remote = crypto.encryptTabMutation(workspaceKey, metadata, mutation).copy(revision = 1)
        val transport = FakeTransport(deviceRecord(), crypto).apply {
            deltaMode = true
            pulledDeltas = listOf(remote)
        }
        val cacheStore = MemoryCacheStore(
            SyncCache("epoch.0", mapOf(profile.deviceId to tabbed), deltaCursor = "epoch.0"),
        )
        repository(transport, cacheStore).use { repository ->
            assertTrue(repository.refresh().get())
            assertTrue(cacheStore.value.profiles.getValue(profile.deviceId).tabs.isEmpty())
            assertEquals(1, cacheStore.value.profiles.getValue(profile.deviceId).revision)
            assertEquals("epoch.1", cacheStore.value.deltaCursor)
        }
    }

    @Test
    fun `v2 stale navigation after close advances revision without resurrecting tab`() {
        val tabbed = profile.copy(tabs = listOf(tab("tab-1")))
        val close = SyncPendingMutation.Close("remote-close", profile.deviceId, "tab-1")
        val navigate = SyncPendingMutation.Navigate(
            "stale-navigation",
            profile.deviceId,
            "tab-1",
            "Stale",
            "https://example.com/stale",
        )
        fun encrypt(mutation: SyncPendingMutation, base: Long) = crypto.encryptTabMutation(
            workspaceKey,
            SyncEncryptedDelta(
                changeId = "change-${mutation.mutationId}",
                mutationId = mutation.mutationId,
                workspaceId = "workspace-1",
                writerDeviceId = "remote-writer",
                targetDeviceId = profile.deviceId,
                baseRevision = base,
                revision = null,
                nonce = "",
                ciphertext = "",
            ),
            mutation,
        ).copy(revision = base + 1)
        val transport = FakeTransport(deviceRecord(), crypto).apply {
            deltaMode = true
            pulledDeltas = listOf(encrypt(close, 0), encrypt(navigate, 1))
        }
        val cacheStore = MemoryCacheStore(
            SyncCache("epoch.0", mapOf(profile.deviceId to tabbed), deltaCursor = "epoch.0"),
        )

        repository(transport, cacheStore).use { repository ->
            assertTrue(repository.refresh().get())
            val result = cacheStore.value.profiles.getValue(profile.deviceId)
            assertTrue(result.tabs.isEmpty())
            assertEquals(2, result.revision)
        }
    }

    @Test
    fun `foreground realtime applies committed delta without waiting for poll`() {
        val tabbed = profile.copy(tabs = listOf(tab("tab-1")))
        val mutation = SyncPendingMutation.Close("live-close", profile.deviceId, "tab-1")
        val encrypted = crypto.encryptTabMutation(
            workspaceKey,
            SyncEncryptedDelta(
                changeId = "live-change",
                mutationId = mutation.mutationId,
                workspaceId = "workspace-1",
                writerDeviceId = "remote-writer",
                targetDeviceId = profile.deviceId,
                baseRevision = 0,
                revision = null,
                nonce = "",
                ciphertext = "",
            ),
            mutation,
        ).copy(revision = 1)
        val transport = FakeTransport(deviceRecord(), crypto).apply {
            deltaMode = true
            realtimeMode = true
        }
        val cacheStore = MemoryCacheStore(
            SyncCache("epoch.0", mapOf(profile.deviceId to tabbed), deltaCursor = "epoch.0"),
        )

        repository(transport, cacheStore).use { repository ->
            repository.startRealtime()
            await { transport.realtimeListener != null }
            transport.emit(SyncRealtimeEvent("epoch.1", encrypted))
            await { cacheStore.value.profiles.getValue(profile.deviceId).revision == 1L }
            assertTrue(cacheStore.value.profiles.getValue(profile.deviceId).tabs.isEmpty())
            repository.stopRealtime()
        }
    }

    private fun repository(
        transport: FakeTransport,
        cacheStore: MemoryCacheStore,
        settingsStore: MemorySettingsStore = MemorySettingsStore(settings),
    ) = CandySyncRepository(
        settingsStore = settingsStore,
        vaultStore = MemoryVaultStore(
            SyncVaultSecrets("workspace-1", "android-device", "token", workspaceKey.copyOf(), identity.privateKeyPkcs8.copyOf()),
        ),
        cacheStore = cacheStore,
        iconCatalog = SyncDeviceIconCatalog(
            listOf(
                SyncDeviceIconDefinition("phone", "📱", "Phone"),
                SyncDeviceIconDefinition("computer", "🖥️", "Computer"),
                SyncDeviceIconDefinition("browser", "🌐", "Browser"),
            ),
        ),
        transportFactory = { transport },
        recoveryKeyDeriver = { _, _ -> error("Not used") },
        clock = Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC),
    )

    private fun deviceRecord(): SyncDeviceRecord {
        val fingerprint = identity.fingerprint
        return SyncDeviceRecord(
            deviceId = profile.deviceId,
            publicKey = SyncBase64.encode(identity.publicKeySpki),
            encryptedName = crypto.encryptDeviceName(workspaceKey, "workspace-1", fingerprint, profile.displayName),
            encryptedIcon = crypto.encryptDeviceIcon(workspaceKey, "workspace-1", fingerprint, profile.icon),
            capabilities = setOf("tabs"),
            status = SyncDeviceStatus.Active,
            createdAt = NOW,
            lastSeenAt = NOW,
        )
    }

    private fun tab(id: String) = SyncTab(
        candyId = id,
        windowId = 0,
        index = 0,
        groupId = null,
        active = true,
        pinned = false,
        title = id,
        url = "https://example.com/$id",
    )

    private class MemorySettingsStore(var value: SyncConnectionSettings?) : SyncSettingsStore {
        var failNextSave = false
        override fun load() = value
        override fun save(value: SyncConnectionSettings): Boolean {
            if (failNextSave) {
                failNextSave = false
                return false
            }
            this.value = value
            return true
        }
        override fun clear(): Boolean = true.also { value = null }
    }

    private class MemoryVaultStore(private var value: SyncVaultSecrets?) : SyncVaultStore {
        override fun load() = value?.copyForUse()
        override fun save(value: SyncVaultSecrets): Boolean = true.also { this.value = value.copyForUse() }
        override fun clear() { value?.clear(); value = null }
    }

    private class MemoryCacheStore(var value: SyncCache) : SyncCacheStore {
        var failNextSave = false
        override fun load() = value
        override fun save(value: SyncCache): Boolean {
            if (failNextSave) {
                failNextSave = false
                return false
            }
            this.value = value
            return true
        }
        override fun clear() { value = SyncCache("", emptyMap()) }
    }

    private class FakeTransport(
        private val device: SyncDeviceRecord,
        private val crypto: SyncCrypto,
    ) : SyncTransport {
        var failFirstPutOffline = false
        var conflictFirstPut = false
        var resetFirstPull = false
        var snapshotTooLarge = false
        var putCursor = "epoch.1"
        var deltaMode = false
        var realtimeMode = false
        var pulledChanges: List<SyncEncryptedChange> = emptyList()
        var pulledDeltas: List<SyncEncryptedDelta> = emptyList()
        val puts = mutableListOf<SyncEncryptedChange>()
        val deltaPuts = mutableListOf<SyncEncryptedDelta>()
        val acknowledged = mutableListOf<String>()
        var discovered = false
        var realtimeListener: ((SyncRealtimeEvent) -> Unit)? = null

        override fun discover() { discovered = true }
        override fun bootstrap(username: String, password: ByteArray): SyncBootstrap = error("Not used")
        override fun enroll(
            username: String,
            password: ByteArray,
            identity: SyncDeviceIdentity,
            encryptedName: SyncEncryptedValue,
            encryptedIcon: SyncEncryptedValue,
            recoveryEnvelope: SyncRecoveryEnvelope?,
        ): SyncEnrollmentResponse = error("Not used")
        override fun listDevices(token: String) = listOf(device)
        override fun pull(token: String, cursor: String): SyncPullPage {
            if (resetFirstPull && cursor.isNotEmpty()) {
                resetFirstPull = false
                throw SyncTransportException(410, "cursor_reset")
            }
            return SyncPullPage(pulledChanges.also { pulledChanges = emptyList() }, "epoch.1", false)
        }
        override fun snapshot(token: String): SyncServerSnapshot {
            if (snapshotTooLarge) throw SyncTransportException(413, "snapshot_too_large")
            return SyncServerSnapshot(emptyList(), "epoch.1")
        }
        override fun putTabs(token: String, change: SyncEncryptedChange): SyncPutResponse {
            puts += change
            if (failFirstPutOffline && puts.size == 1) throw SyncTransportException(null, null)
            if (conflictFirstPut && puts.size == 1) throw SyncTransportException(409, "snapshot_conflict")
            return SyncPutResponse(requireNotNull(change.revision), putCursor)
        }
        override fun acknowledge(token: String, cursor: String) { acknowledged += cursor }
        override fun supportsTabMutationsV2(): Boolean = deltaMode
        override fun pullDeltas(token: String, cursor: String): SyncDeltaPullPage = SyncDeltaPullPage(
            changes = pulledDeltas.also { pulledDeltas = emptyList() },
            nextCursor = "epoch.1",
            hasMore = false,
        )
        override fun pushDelta(token: String, change: SyncEncryptedDelta): SyncPutResponse {
            deltaPuts += change
            if (failFirstPutOffline && deltaPuts.size == 1) throw SyncTransportException(null, null)
            if (conflictFirstPut && deltaPuts.size == 1) throw SyncTransportException(409, "revision_conflict")
            return SyncPutResponse(change.baseRevision + 1, putCursor)
        }
        override fun supportsRealtime(): Boolean = realtimeMode
        override fun requestRealtimeTicket(token: String) = SyncRealtimeTicket("ticket", NOW)
        override fun connectRealtime(
            ticket: SyncRealtimeTicket,
            onEvent: (SyncRealtimeEvent) -> Unit,
            onClosed: (Throwable?) -> Unit,
        ): AutoCloseable {
            realtimeListener = onEvent
            return AutoCloseable { realtimeListener = null }
        }

        fun emit(event: SyncRealtimeEvent) = requireNotNull(realtimeListener).invoke(event)
    }

    private fun await(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            Thread.sleep(5)
        }
        error("Timed out waiting for asynchronous sync")
    }

    private companion object {
        const val NOW = "2026-09-02T10:00:00Z"
    }
}
