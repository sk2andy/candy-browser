package dev.sk2andy.materialbrowser.browser.gecko

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class GeckoExtensionRepository(
    private val runtime: GeckoExtensionRuntime,
    permissionPrompt: GeckoExtensionPermissionPrompt,
) {
    private val mutationMutex = Mutex()

    @Volatile
    private var currentSnapshot = GeckoExtensionSnapshot(emptyList())

    init {
        runtime.setPermissionPrompt(guardedPrompt(permissionPrompt))
        runtime.setChangeListener {
            when (refresh()) {
                is GeckoExtensionReadResult.Loaded -> Unit
                is GeckoExtensionReadResult.Failed -> Unit
            }
        }
    }

    fun snapshot(): GeckoExtensionSnapshot = currentSnapshot

    suspend fun refresh(): GeckoExtensionReadResult = mutationMutex.withLock {
        try {
            val loaded = GeckoExtensionRules.canonicalSnapshot(runtime.listInstalled())
            currentSnapshot = loaded
            GeckoExtensionReadResult.Loaded(loaded)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            GeckoExtensionReadResult.Failed(error)
        }
    }

    suspend fun installSignedXpi(
        rawUri: String,
        context: GeckoExtensionManagementContext,
    ): GeckoExtensionMutationResult {
        GeckoExtensionRules.managementRejection(context)?.let { rejection ->
            return GeckoExtensionMutationResult.Rejected(rejection)
        }
        val uri = GeckoExtensionRules.normalizeSignedXpiUri(rawUri)
            ?: return GeckoExtensionMutationResult.Rejected(
                GeckoExtensionRejection.InvalidSignedXpiUri,
            )
        return mutate {
            val installed = runtime.installSignedXpi(
                uri = uri,
                installationMethod = INSTALLATION_METHOD_MANAGER,
            )
            check(!installed.isBuiltIn) { "Signed XPI install returned a built-in extension" }
            applyExtension(installed)
        }
    }

    suspend fun enable(
        extensionId: String,
        context: GeckoExtensionManagementContext,
    ): GeckoExtensionMutationResult = mutateExisting(extensionId, context) {
        applyExtension(runtime.enable(extensionId))
    }

    suspend fun disable(
        extensionId: String,
        context: GeckoExtensionManagementContext,
    ): GeckoExtensionMutationResult = mutateExisting(extensionId, context) {
        applyExtension(runtime.disable(extensionId))
    }

    suspend fun update(
        extensionId: String,
        context: GeckoExtensionManagementContext,
    ): GeckoExtensionMutationResult = mutateExisting(extensionId, context) {
        applyExtension(runtime.update(extensionId))
    }

    suspend fun setAllowedInPrivateBrowsing(
        extensionId: String,
        allowed: Boolean,
        context: GeckoExtensionManagementContext,
    ): GeckoExtensionMutationResult = mutateExisting(extensionId, context) {
        applyExtension(runtime.setAllowedInPrivateBrowsing(extensionId, allowed))
    }

    suspend fun uninstall(
        extensionId: String,
        context: GeckoExtensionManagementContext,
    ): GeckoExtensionMutationResult = mutateExisting(extensionId, context) {
        runtime.uninstall(extensionId)
        currentSnapshot = GeckoExtensionRules.canonicalSnapshot(
            currentSnapshot.extensions.filterNot { extension -> extension.id == extensionId },
        )
        GeckoExtensionMutationResult.Applied(extensionId, currentSnapshot)
    }

    private suspend fun mutateExisting(
        extensionId: String,
        context: GeckoExtensionManagementContext,
        mutation: suspend () -> GeckoExtensionMutationResult,
    ): GeckoExtensionMutationResult {
        GeckoExtensionRules.managementRejection(context)?.let { rejection ->
            return GeckoExtensionMutationResult.Rejected(rejection)
        }
        return mutationMutex.withLock {
            val extension = currentSnapshot.extension(extensionId)
                ?: return@withLock GeckoExtensionMutationResult.Rejected(
                    GeckoExtensionRejection.ExtensionNotFound,
                )
            if (extension.isBuiltIn) {
                return@withLock GeckoExtensionMutationResult.Rejected(
                    GeckoExtensionRejection.BuiltInExtensionProtected,
                )
            }
            try {
                mutation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                GeckoExtensionMutationResult.Failed(error)
            }
        }
    }

    private suspend fun mutate(
        mutation: suspend () -> GeckoExtensionMutationResult,
    ): GeckoExtensionMutationResult = mutationMutex.withLock {
        try {
            mutation()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            GeckoExtensionMutationResult.Failed(error)
        }
    }

    private fun applyExtension(extension: GeckoExtension): GeckoExtensionMutationResult {
        val updated = GeckoExtensionRules.canonicalSnapshot(
            currentSnapshot.extensions.filterNot { current -> current.id == extension.id } + extension,
        )
        currentSnapshot = updated
        return GeckoExtensionMutationResult.Applied(extension.id, updated)
    }

    private fun guardedPrompt(
        prompt: GeckoExtensionPermissionPrompt,
    ) = GeckoExtensionPermissionPrompt { request ->
        val normalized = GeckoExtensionRules.normalizePermissionRequest(request)
            ?: return@GeckoExtensionPermissionPrompt GeckoExtensionPermissionDecision.Denied
        val decision = try {
            prompt.decide(normalized)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            GeckoExtensionPermissionDecision.Denied
        }
        GeckoExtensionRules.restrictPermissionDecision(normalized, decision)
    }

    private companion object {
        const val INSTALLATION_METHOD_MANAGER = "manager"
    }
}
