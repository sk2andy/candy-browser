package dev.sk2andy.materialbrowser.browser.gecko

import kotlinx.coroutines.CancellationException

internal enum class GeckoDefaultExtensionDelivery {
    Bundled,
    Remote,
}

internal data class GeckoDefaultExtension(
    val id: String,
    val name: String,
    val version: String,
    val installUri: String,
    val sha256: String,
    val size: Long,
    val delivery: GeckoDefaultExtensionDelivery,
    val assetPath: String?,
)

internal enum class GeckoDefaultExtensionState {
    Provisioned,
    Removed,
}

internal interface GeckoDefaultExtensionStateStore {
    suspend fun read(extensionId: String): GeckoDefaultExtensionState?

    suspend fun write(extensionId: String, state: GeckoDefaultExtensionState): Boolean
}

internal fun interface GeckoDefaultExtensionAssetVerifier {
    suspend fun verify(extension: GeckoDefaultExtension): Boolean
}

internal interface GeckoDefaultExtensionInstaller {
    suspend fun listInstalled(): List<GeckoExtension>

    suspend fun install(extension: GeckoDefaultExtension): GeckoExtension

    suspend fun uninstall(extensionId: String)
}

internal enum class GeckoDefaultExtensionProvisioningOutcome {
    AlreadyInstalled,
    Installed,
    Removed,
    Failed,
}

internal data class GeckoDefaultExtensionProvisioningResult(
    val outcomes: Map<String, GeckoDefaultExtensionProvisioningOutcome>,
)

internal class GeckoDefaultExtensionProvisioner(
    private val extensions: List<GeckoDefaultExtension>,
    private val stateStore: GeckoDefaultExtensionStateStore,
    private val assetVerifier: GeckoDefaultExtensionAssetVerifier,
    private val installer: GeckoDefaultExtensionInstaller,
    private val retiredExtensionIds: Set<String> = emptySet(),
) {
    init {
        GeckoDefaultExtensionRules.requireValidCatalog(extensions)
        val currentExtensionIds = extensions.mapTo(mutableSetOf(), GeckoDefaultExtension::id)
        require(retiredExtensionIds.intersect(currentExtensionIds).isEmpty()) {
            "A default Gecko extension cannot also be retired"
        }
    }

    suspend fun provision(): GeckoDefaultExtensionProvisioningResult {
        val installed = installer.listInstalled().associateBy(GeckoExtension::id).toMutableMap()
        uninstallRetiredDefaults(installed)
        val outcomes = linkedMapOf<String, GeckoDefaultExtensionProvisioningOutcome>()
        extensions.forEach { extension ->
            val existing = installed[extension.id]
            if (stateStore.read(extension.id) == GeckoDefaultExtensionState.Removed) {
                outcomes[extension.id] = if (existing == null) {
                    GeckoDefaultExtensionProvisioningOutcome.Removed
                } else if (existing.isBuiltIn || existing.temporary) {
                    GeckoDefaultExtensionProvisioningOutcome.Failed
                } else {
                    try {
                        installer.uninstall(extension.id)
                        installed.remove(extension.id)
                        GeckoDefaultExtensionProvisioningOutcome.Removed
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        GeckoDefaultExtensionProvisioningOutcome.Failed
                    }
                }
                return@forEach
            }
            if (existing != null) {
                val validExisting = !existing.isBuiltIn && !existing.temporary
                if (validExisting) {
                    stateStore.write(extension.id, GeckoDefaultExtensionState.Provisioned)
                }
                outcomes[extension.id] = if (validExisting) {
                    GeckoDefaultExtensionProvisioningOutcome.AlreadyInstalled
                } else {
                    GeckoDefaultExtensionProvisioningOutcome.Failed
                }
                return@forEach
            }

            if (!assetVerifier.verify(extension)) {
                outcomes[extension.id] = GeckoDefaultExtensionProvisioningOutcome.Failed
                return@forEach
            }

            val provisioned = try {
                installer.install(extension)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            if (provisioned == null || !GeckoDefaultExtensionRules.matches(extension, provisioned)) {
                if (provisioned != null && !provisioned.isBuiltIn) {
                    try {
                        installer.uninstall(provisioned.id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        // Best-effort cleanup of a signed but unexpected package.
                    }
                }
                outcomes[extension.id] = GeckoDefaultExtensionProvisioningOutcome.Failed
                return@forEach
            }

            stateStore.write(extension.id, GeckoDefaultExtensionState.Provisioned)
            installed[extension.id] = provisioned
            outcomes[extension.id] = GeckoDefaultExtensionProvisioningOutcome.Installed
        }
        return GeckoDefaultExtensionProvisioningResult(outcomes)
    }

    private suspend fun uninstallRetiredDefaults(installed: MutableMap<String, GeckoExtension>) {
        retiredExtensionIds.forEach { extensionId ->
            if (stateStore.read(extensionId) != GeckoDefaultExtensionState.Provisioned) {
                return@forEach
            }
            val existing = installed[extensionId]
            if (existing == null) {
                stateStore.write(extensionId, GeckoDefaultExtensionState.Removed)
                return@forEach
            }
            if (existing.isBuiltIn || existing.temporary) return@forEach
            try {
                installer.uninstall(extensionId)
                installed.remove(extensionId)
                stateStore.write(extensionId, GeckoDefaultExtensionState.Removed)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Keep the provisioned marker so a later startup retries the bounded migration.
            }
        }
    }

    suspend fun markRemovalRequested(extensionId: String): Boolean {
        if (extensions.none { extension -> extension.id == extensionId }) return true
        return stateStore.write(extensionId, GeckoDefaultExtensionState.Removed)
    }

    suspend fun markUserInstallSucceeded(extensionId: String): Boolean {
        if (extensions.none { extension -> extension.id == extensionId }) return true
        return stateStore.write(extensionId, GeckoDefaultExtensionState.Provisioned)
    }
}

internal object GeckoDefaultExtensionRules {
    fun requireValidCatalog(extensions: List<GeckoDefaultExtension>) {
        require(extensions.isNotEmpty() && extensions.size <= MAX_DEFAULT_EXTENSIONS) {
            "Default Gecko extension catalog must contain 1..$MAX_DEFAULT_EXTENSIONS entries"
        }
        require(extensions.map(GeckoDefaultExtension::id).distinct().size == extensions.size) {
            "Duplicate default Gecko extension ID"
        }
        extensions.forEach { extension ->
            require(isBoundedText(extension.id, MAX_ID_LENGTH)) {
                "Invalid default Gecko extension ID"
            }
            require(isBoundedText(extension.name, MAX_NAME_LENGTH)) {
                "Invalid default Gecko extension name"
            }
            require(isBoundedText(extension.version, MAX_VERSION_LENGTH)) {
                "Invalid default Gecko extension version"
            }
            require(extension.sha256.matches(SHA_256_PATTERN)) {
                "Invalid default Gecko extension SHA-256"
            }
            require(extension.size in 1..MAX_XPI_BYTES) {
                "Invalid default Gecko extension size"
            }
            when (extension.delivery) {
                GeckoDefaultExtensionDelivery.Bundled -> {
                    val assetPath = requireNotNull(extension.assetPath) {
                        "Bundled default Gecko extension needs an asset path"
                    }
                    require(assetPath.matches(ASSET_PATH_PATTERN)) {
                        "Invalid bundled default Gecko extension asset path"
                    }
                    require(extension.installUri == "resource://android/assets/$assetPath") {
                        "Bundled default Gecko extension URI must match its asset path"
                    }
                }
                GeckoDefaultExtensionDelivery.Remote -> {
                    require(extension.assetPath == null) {
                        "Remote default Gecko extension cannot name a bundled asset"
                    }
                    require(GeckoExtensionRules.normalizeSignedXpiUri(extension.installUri) == extension.installUri) {
                        "Remote default Gecko extension needs a canonical HTTPS URI"
                    }
                    require(extension.installUri.startsWith(PINNED_AMO_FILE_PREFIX)) {
                        "Remote default Gecko extension must use a pinned AMO file URI"
                    }
                }
            }
        }
    }

    fun matches(
        expected: GeckoDefaultExtension,
        actual: GeckoExtension,
    ): Boolean = actual.id == expected.id &&
        actual.version == expected.version &&
        actual.enabled &&
        !actual.allowedInPrivateBrowsing &&
        !actual.isBuiltIn &&
        !actual.temporary &&
        actual.signedState >= MIN_TRUSTED_SIGNED_STATE

    private fun isBoundedText(value: String, maxLength: Int): Boolean =
        value.isNotBlank() && value.length <= maxLength && value.none(Char::isISOControl)

    private const val MAX_DEFAULT_EXTENSIONS = 16
    private const val MAX_ID_LENGTH = 512
    private const val MAX_NAME_LENGTH = 512
    private const val MAX_VERSION_LENGTH = 128
    private const val MAX_XPI_BYTES = 16L * 1024L * 1024L
    private const val MIN_TRUSTED_SIGNED_STATE = 2
    private const val PINNED_AMO_FILE_PREFIX =
        "https://addons.mozilla.org/firefox/downloads/file/"
    private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
    private val ASSET_PATH_PATTERN = Regex(
        "gecko_default_extensions/[a-z0-9][a-z0-9._-]*\\.xpi",
    )
}
