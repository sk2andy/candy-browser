package dev.sk2andy.materialbrowser.sync

data class SyncEncryptedValue(
    val nonce: String,
    val ciphertext: String,
)

data class SyncRecoveryKdf(
    val salt: String,
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
)

data class SyncRecoveryEnvelope(
    val cryptoVersion: Int = 1,
    val nonce: String,
    val ciphertext: String,
)

data class SyncBootstrap(
    val workspaceId: String,
    val serverEpoch: String,
    val initialized: Boolean,
    val kdf: SyncRecoveryKdf,
    val recoveryEnvelope: SyncRecoveryEnvelope?,
)

data class SyncDeviceIconDescriptor(
    val catalogId: String,
    val accentHue: Int,
)

data class SyncDeviceIconDefinition(
    val id: String,
    val emoji: String,
    val label: String,
)

data class SyncDeviceRecord(
    val deviceId: String,
    val publicKey: String,
    val encryptedName: SyncEncryptedValue,
    val encryptedIcon: SyncEncryptedValue?,
    val capabilities: Set<String>,
    val status: SyncDeviceStatus,
    val createdAt: String,
    val lastSeenAt: String,
)

enum class SyncDeviceStatus {
    Active,
    Revoked,
}

data class SyncTab(
    val candyId: String,
    val windowId: Int,
    val index: Int,
    val groupId: Int?,
    val active: Boolean,
    val pinned: Boolean,
    val title: String,
    val url: String,
)

data class SyncTabSnapshot(
    val capturedAt: String,
    val tabs: List<SyncTab>,
)

data class SyncEncryptedChange(
    val changeId: String,
    val writerDeviceId: String,
    val targetDeviceId: String,
    val baseRevision: Long,
    val revision: Long?,
    val nonce: String,
    val ciphertext: String,
)

data class SyncPullPage(
    val changes: List<SyncEncryptedChange>,
    val nextCursor: String,
    val hasMore: Boolean,
)

data class SyncServerSnapshot(
    val changes: List<SyncEncryptedChange>,
    val cursor: String,
)

data class SyncProfile(
    val deviceId: String,
    val displayName: String,
    val icon: SyncDeviceIconDescriptor,
    val revision: Long,
    val tabs: List<SyncTab>,
    val lastSeenAt: String,
)

data class SyncCache(
    val cursor: String,
    val profiles: Map<String, SyncProfile>,
    val pendingMutations: List<SyncPendingMutation> = emptyList(),
    val preparedWrites: Map<String, SyncEncryptedChange> = emptyMap(),
)

sealed interface SyncPendingMutation {
    val mutationId: String
    val targetDeviceId: String

    data class Open(
        override val mutationId: String,
        override val targetDeviceId: String,
        val tab: SyncTab,
        val isPrivate: Boolean = false,
    ) : SyncPendingMutation

    data class Navigate(
        override val mutationId: String,
        override val targetDeviceId: String,
        val candyId: String,
        val title: String,
        val url: String,
    ) : SyncPendingMutation

    data class Close(
        override val mutationId: String,
        override val targetDeviceId: String,
        val candyId: String,
    ) : SyncPendingMutation

    data class Reorder(
        override val mutationId: String,
        override val targetDeviceId: String,
        val orderedCandyIds: List<String>,
    ) : SyncPendingMutation

    data class SetPinned(
        override val mutationId: String,
        override val targetDeviceId: String,
        val candyId: String,
        val pinned: Boolean,
    ) : SyncPendingMutation
}

data class SyncConnectionSettings(
    val endpoint: String,
    val username: String,
    val deviceName: String,
    val iconCatalogId: String,
    val iconAccentHue: Int,
)

enum class SyncStatus {
    Unconfigured,
    Enrolling,
    Ready,
    Syncing,
    Offline,
    AuthError,
    CryptoError,
    Incompatible,
}

data class SyncRepositoryState(
    val settings: SyncConnectionSettings?,
    val status: SyncStatus,
    val profiles: List<SyncProfile>,
    val pendingCount: Int,
    val lastCursor: String?,
    val lastSuccessAt: String?,
)

sealed interface SyncMutationResult {
    data class Applied(val profile: SyncProfile) : SyncMutationResult
    data object AlreadyApplied : SyncMutationResult
    data object MissingTab : SyncMutationResult
    data object InvalidTab : SyncMutationResult
}

sealed interface SyncWriteOutcome {
    data class Synced(val profile: SyncProfile, val cursor: String) : SyncWriteOutcome
    data class Conflict(val latest: SyncProfile?, val retryable: Boolean) : SyncWriteOutcome
    data class Rejected(val reason: String) : SyncWriteOutcome
    data class Failed(val retryable: Boolean) : SyncWriteOutcome
}

sealed interface SyncEnrollmentOutcome {
    data object Enrolled : SyncEnrollmentOutcome
    data object InvalidConfiguration : SyncEnrollmentOutcome
    data object AuthenticationFailed : SyncEnrollmentOutcome
    data object WrongPassphrase : SyncEnrollmentOutcome
    data object IncompatibleServer : SyncEnrollmentOutcome
    data object Failed : SyncEnrollmentOutcome
}

data class SyncVaultSecrets(
    val workspaceId: String,
    val deviceId: String,
    val deviceToken: String,
    val workspaceKey: ByteArray,
    val devicePrivateKeyPkcs8: ByteArray,
) {
    fun copyForUse(): SyncVaultSecrets = copy(
        workspaceKey = workspaceKey.copyOf(),
        devicePrivateKeyPkcs8 = devicePrivateKeyPkcs8.copyOf(),
    )

    fun clear() {
        workspaceKey.fill(0)
        devicePrivateKeyPkcs8.fill(0)
    }

    override fun equals(other: Any?): Boolean = other is SyncVaultSecrets &&
        workspaceId == other.workspaceId &&
        deviceId == other.deviceId &&
        deviceToken == other.deviceToken &&
        workspaceKey.contentEquals(other.workspaceKey) &&
        devicePrivateKeyPkcs8.contentEquals(other.devicePrivateKeyPkcs8)

    override fun hashCode(): Int = listOf(
        workspaceId,
        deviceId,
        deviceToken,
        workspaceKey.contentHashCode(),
        devicePrivateKeyPkcs8.contentHashCode(),
    ).hashCode()
}

data class SyncDeviceIdentity(
    val privateKeyPkcs8: ByteArray,
    val publicKeySpki: ByteArray,
    val fingerprint: String,
)
