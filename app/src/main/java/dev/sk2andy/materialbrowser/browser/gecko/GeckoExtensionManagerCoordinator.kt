package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class GeckoExtensionManagerMessage {
    InvalidSignedXpi,
    ActionRejected,
    ActionFailed,
}

internal data class GeckoExtensionManagerState(
    val snapshot: GeckoExtensionSnapshot = GeckoExtensionSnapshot(emptyList()),
    val managementContext: GeckoExtensionManagementContext =
        GeckoExtensionManagementContext(profileId = DEFAULT_PROFILE_ID, isPrivate = false),
    val busy: Boolean = false,
    val message: GeckoExtensionManagerMessage? = null,
    val permissionRequest: GeckoExtensionPermissionRequest? = null,
) {
    val canManage: Boolean
        get() = !busy && !managementContext.isPrivate

    private companion object {
        const val DEFAULT_PROFILE_ID = "default"
    }
}

/**
 * Activity-scoped state owner for the process-wide Gecko extension runtime.
 *
 * The browser tab only supplies the management boundary. Installed extensions remain owned by
 * Gecko's process profile and mutations are therefore rejected while a private tab is selected.
 */
internal class GeckoExtensionManagerCoordinator(
    runtime: GeckoExtensionRuntime,
    private val scope: CoroutineScope,
    private val onPageRuntimeChanged: () -> Unit = {},
) {
    private val repository = GeckoExtensionRepository(runtime, ::requestPermission)
    private val extensionRuntime = runtime
    private var permissionDecision: CompletableDeferred<GeckoExtensionPermissionDecision>? = null
    private var visible = false

    var state by mutableStateOf(GeckoExtensionManagerState())
        private set

    fun open(context: GeckoExtensionManagementContext): Job? {
        visible = false
        denyPendingPermission()
        visible = true
        state = state.copy(
            managementContext = context,
            message = null,
            permissionRequest = null,
        )
        return refresh()
    }

    fun refresh(): Job? {
        if (state.busy) return null
        state = state.copy(busy = true, message = null)
        return scope.launch {
            state = when (val result = repository.refresh()) {
                is GeckoExtensionReadResult.Loaded -> state.copy(
                    snapshot = result.snapshot,
                    busy = false,
                )
                is GeckoExtensionReadResult.Failed -> state.copy(
                    busy = false,
                    message = GeckoExtensionManagerMessage.ActionFailed,
                )
            }
        }
    }

    fun install(rawUri: String): Job? = mutate {
        repository.installSignedXpi(rawUri, state.managementContext)
    }

    fun setEnabled(extension: GeckoExtension, enabled: Boolean): Job? = mutate {
        if (enabled) {
            repository.enable(extension.id, state.managementContext)
        } else {
            repository.disable(extension.id, state.managementContext)
        }
    }

    fun setAllowedInPrivateBrowsing(extension: GeckoExtension, allowed: Boolean): Job? = mutate(
        reloadSelectedPage = false,
    ) {
        repository.setAllowedInPrivateBrowsing(extension.id, allowed, state.managementContext)
    }

    fun update(extension: GeckoExtension): Job? = mutate {
        repository.update(extension.id, state.managementContext)
    }

    fun uninstall(extension: GeckoExtension): Job? = mutate {
        repository.uninstall(extension.id, state.managementContext)
    }

    fun completePermissionRequest(granted: Boolean, allowPrivate: Boolean) {
        val deferred = permissionDecision ?: return
        deferred.complete(
            GeckoExtensionPermissionDecision(
                grantPermissions = granted,
                allowInPrivateBrowsing = granted && allowPrivate,
            ),
        )
        permissionDecision = null
        state = state.copy(permissionRequest = null)
    }

    fun dismiss() {
        visible = false
        denyPendingPermission()
        state = state.copy(permissionRequest = null)
    }

    fun close() {
        dismiss()
        extensionRuntime.setPermissionPrompt { GeckoExtensionPermissionDecision.Denied }
        extensionRuntime.setChangeListener { }
    }

    private fun mutate(
        reloadSelectedPage: Boolean = true,
        mutation: suspend () -> GeckoExtensionMutationResult,
    ): Job? {
        if (!state.canManage) return null
        state = state.copy(busy = true, message = null)
        return scope.launch {
            state = when (val result = mutation()) {
                is GeckoExtensionMutationResult.Applied -> {
                    if (reloadSelectedPage) onPageRuntimeChanged()
                    state.copy(
                        snapshot = result.snapshot,
                        busy = false,
                    )
                }
                is GeckoExtensionMutationResult.Rejected -> state.copy(
                    busy = false,
                    message = when (result.reason) {
                        GeckoExtensionRejection.InvalidSignedXpiUri ->
                            GeckoExtensionManagerMessage.InvalidSignedXpi
                        else -> GeckoExtensionManagerMessage.ActionRejected
                    },
                )
                is GeckoExtensionMutationResult.Failed -> state.copy(
                    busy = false,
                    message = GeckoExtensionManagerMessage.ActionFailed,
                )
            }
        }
    }

    private suspend fun requestPermission(
        request: GeckoExtensionPermissionRequest,
    ): GeckoExtensionPermissionDecision {
        if (!visible || permissionDecision != null) {
            return GeckoExtensionPermissionDecision.Denied
        }
        val deferred = CompletableDeferred<GeckoExtensionPermissionDecision>()
        permissionDecision = deferred
        state = state.copy(permissionRequest = request)
        return try {
            deferred.await()
        } finally {
            if (permissionDecision === deferred) {
                permissionDecision = null
                state = state.copy(permissionRequest = null)
            }
        }
    }

    private fun denyPendingPermission() {
        permissionDecision?.complete(GeckoExtensionPermissionDecision.Denied)
        permissionDecision = null
    }

    companion object {
        fun create(
            context: Context,
            scope: CoroutineScope,
            onPageRuntimeChanged: () -> Unit = {},
        ): GeckoExtensionManagerCoordinator =
            GeckoExtensionManagerCoordinator(
                runtime = GeckoRuntimeOwner.getOrCreate(context.applicationContext).extensions,
                scope = scope,
                onPageRuntimeChanged = onPageRuntimeChanged,
            )
    }
}
