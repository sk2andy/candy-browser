package dev.sk2andy.materialbrowser.sync

/** Platform-owned durable boundary. Settings never contain vault secrets. */
interface SyncSettingsStore {
    fun load(): SyncConnectionSettings?
    fun save(value: SyncConnectionSettings): Boolean
    fun clear(): Boolean
}

/** Platform-owned secret boundary. Implementations must zero returned secret copies after use. */
interface SyncVaultStore {
    fun load(): SyncVaultSecrets?
    fun save(value: SyncVaultSecrets): Boolean
    fun clear()
}

/** Platform-owned encrypted cache and durable outbox boundary. */
interface SyncCacheStore {
    fun load(): SyncCache
    fun save(value: SyncCache): Boolean
    fun clear()
}

interface SyncRecoveryKeyDeriver {
    @Throws(Exception::class)
    fun derive(passphrase: ByteArray, kdf: SyncRecoveryKdf): ByteArray
}

interface SyncCryptoProvider {
    @Throws(Exception::class)
    fun randomBytes(size: Int): ByteArray
    fun newChangeId(): String
    fun nowIso8601(): String
    @Throws(Exception::class)
    fun generateDeviceIdentity(): SyncDeviceIdentity
    @Throws(Exception::class)
    fun fingerprint(publicKeySpki: ByteArray): String
    @Throws(Exception::class)
    fun createRecoveryEnvelope(recoveryKey: ByteArray, workspaceKey: ByteArray, workspaceId: String): SyncRecoveryEnvelope
    fun unlockRecoveryEnvelope(recoveryKey: ByteArray, envelope: SyncRecoveryEnvelope, workspaceId: String): ByteArray?
    @Throws(Exception::class)
    fun encryptDeviceName(workspaceKey: ByteArray, workspaceId: String, fingerprint: String, name: String): SyncEncryptedValue
    @Throws(Exception::class)
    fun decryptDeviceName(workspaceKey: ByteArray, workspaceId: String, fingerprint: String, encrypted: SyncEncryptedValue): String
    @Throws(Exception::class)
    fun encryptDeviceIcon(workspaceKey: ByteArray, workspaceId: String, fingerprint: String, descriptor: SyncDeviceIconDescriptor): SyncEncryptedValue
    @Throws(Exception::class)
    fun decryptDeviceIcon(workspaceKey: ByteArray, workspaceId: String, fingerprint: String, encrypted: SyncEncryptedValue): SyncDeviceIconDescriptor
    @Throws(Exception::class)
    fun encryptTabSnapshot(workspaceKey: ByteArray, metadata: SyncEncryptedChange, snapshot: SyncTabSnapshot): SyncEncryptedChange
    @Throws(Exception::class)
    fun decryptTabSnapshot(workspaceKey: ByteArray, change: SyncEncryptedChange): SyncTabSnapshot
    @Throws(Exception::class)
    fun encryptTabMutation(workspaceKey: ByteArray, metadata: SyncEncryptedDelta, mutation: SyncPendingMutation): SyncEncryptedDelta
    @Throws(Exception::class)
    fun decryptTabMutation(workspaceKey: ByteArray, change: SyncEncryptedDelta): SyncPendingMutation
}

data class SyncEnrollmentResponse(
    val workspaceId: String,
    val deviceId: String,
    val token: String,
    val cursor: String,
)

data class SyncPutResponse(val revision: Long, val cursor: String)

class SyncTransportException(
    val statusCode: Int?,
    val problemCode: String?,
    cause: Throwable? = null,
) : Exception("Candy Sync transport failed", cause)

interface SyncTransport {
    @Throws(Exception::class)
    fun discover()
    @Throws(Exception::class)
    fun bootstrap(username: String, password: ByteArray): SyncBootstrap
    @Throws(Exception::class)
    fun enroll(username: String, password: ByteArray, identity: SyncDeviceIdentity, encryptedName: SyncEncryptedValue, encryptedIcon: SyncEncryptedValue, recoveryEnvelope: SyncRecoveryEnvelope?): SyncEnrollmentResponse
    @Throws(Exception::class)
    fun listDevices(token: String): List<SyncDeviceRecord>
    @Throws(Exception::class)
    fun pull(token: String, cursor: String): SyncPullPage
    @Throws(Exception::class)
    fun snapshot(token: String): SyncServerSnapshot
    @Throws(Exception::class)
    fun putTabs(token: String, change: SyncEncryptedChange): SyncPutResponse
    @Throws(Exception::class)
    fun acknowledge(token: String, cursor: String)
    fun supportsTabMutationsV2(): Boolean = false
    fun supportsRealtime(): Boolean = false
    @Throws(Exception::class)
    fun pullDeltas(token: String, cursor: String): SyncDeltaPullPage = throw UnsupportedOperationException("Protocol v2 is unavailable")
    @Throws(Exception::class)
    fun pushDelta(token: String, change: SyncEncryptedDelta): SyncPutResponse = throw UnsupportedOperationException("Protocol v2 is unavailable")
    @Throws(Exception::class)
    fun requestRealtimeTicket(token: String): SyncRealtimeTicket = throw UnsupportedOperationException("Realtime is unavailable")
    @Throws(Exception::class)
    fun connectRealtime(ticket: SyncRealtimeTicket, onEvent: (SyncRealtimeEvent) -> Unit, onClosed: (Throwable?) -> Unit): AutoCloseable = throw UnsupportedOperationException("Realtime is unavailable")
}

/** Serializes repository work and gives native hosts a foreground-only delayed reconnect hook. */
interface SyncDispatch {
    fun dispatch(block: () -> Unit)
    fun dispatchAfter(seconds: Long, block: () -> Unit)
}
