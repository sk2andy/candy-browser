package dev.sk2andy.materialbrowser.browser.gecko

import androidx.annotation.UiThread
import java.util.IdentityHashMap
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

internal interface GeckoExtensionChromeHost {
    fun currentSessionIdentity(): GeckoExtensionSessionIdentity?

    fun isCurrentSession(identity: GeckoExtensionSessionIdentity): Boolean

    /**
     * Creates Candy tab state around GeckoView's required unopened tabs.create session.
     *
     * Hosts must adopt [session] before returning so Candy and Gecko reference the same renderer.
     */
    fun createTab(
        request: GeckoExtensionCreateTabRequest,
        session: GeckoSession,
    ): String?

    fun updateTab(request: GeckoExtensionUpdateTabRequest): Boolean

    fun closeTab(target: GeckoExtensionSessionIdentity): Boolean

    fun openPopup(
        popup: GeckoExtensionPopupIdentity,
        session: GeckoSession,
        toggle: Boolean,
    ): Boolean

    fun closePopup(popup: GeckoExtensionPopupIdentity)

    fun openOptionsPage(
        extensionId: String,
        owner: GeckoExtensionSessionIdentity,
        url: String,
        openInTab: Boolean,
    ): String?

    fun onActionsChanged(actions: List<GeckoExtensionActionState>)

}

/**
 * Public-GeckoView-API edge for user WebExtension chrome.
 *
 * Candy owns tab, popup and download UI. Gecko owns extension code and calls these delegates when
 * that code needs embedder state. Every callback is revalidated against current extension/session
 * identity before it can mutate Candy state.
 */
internal class GeckoViewExtensionChrome(
    private val runtime: org.mozilla.geckoview.GeckoRuntime,
    private val controller: WebExtensionController,
    private val host: GeckoExtensionChromeHost,
    private val downloadTransfers: GeckoDownloadTransferManager,
) : AutoCloseable {
    private data class BoundSession(
        val identity: GeckoExtensionSessionIdentity,
        val session: GeckoSession,
    )

    private data class RawAction(
        val state: GeckoExtensionActionState,
        val action: WebExtension.Action,
        val identity: GeckoExtensionSessionIdentity?,
    )

    private data class PopupBinding(
        val identity: GeckoExtensionPopupIdentity,
        val session: GeckoSession,
    )

    private val extensions = linkedMapOf<String, WebExtension>()
    private val sessions = IdentityHashMap<GeckoSession, BoundSession>()
    private val defaultActions = mutableMapOf<GeckoExtensionActionKey, RawAction>()
    private val sessionActions = mutableMapOf<GeckoExtensionActionKey, RawAction>()
    private var popupGeneration = 0L
    private var activePopup: PopupBinding? = null
    private var closed = false

    @UiThread
    fun reconcile(installed: List<WebExtension>) {
        if (closed) return
        val visible = installed.filter { extension ->
            GeckoExtensionRules.isVisibleToUserManager(extension.id)
        }.associateBy(WebExtension::id)
        val removedIds = extensions.keys - visible.keys
        removedIds.forEach { extensionId -> removeExtension(extensionId) }
        visible.values.forEach { extension ->
            if (extensions[extension.id] !== extension) bindExtension(extension)
        }
        closePopupUnlessCurrent()
        publishActions()
    }

    @UiThread
    fun attachSession(
        session: GeckoSession,
        identity: GeckoExtensionSessionIdentity,
    ) {
        if (closed) return
        sessions[session]?.let { previous -> clearSessionDelegates(previous.session) }
        sessions.entries
            .filter { (boundSession, binding) ->
                boundSession !== session && binding.identity.tabId == identity.tabId
            }
            .forEach { (boundSession, _) ->
                clearSessionDelegates(boundSession)
                sessions.remove(boundSession)
            }
        sessionActions.keys.removeAll { key -> key.tabId == identity.tabId }
        sessions[session] = BoundSession(identity, session)
        extensions.values.forEach { extension -> bindSession(extension, session, identity) }
        closePopupUnlessCurrent()
        publishActions()
    }

    @UiThread
    fun detachSession(session: GeckoSession) {
        val binding = sessions.remove(session) ?: return
        clearSessionDelegates(session)
        if (sessions.values.none { current -> current.identity.tabId == binding.identity.tabId }) {
            sessionActions.keys.removeAll { key -> key.tabId == binding.identity.tabId }
        }
        closePopupUnlessCurrent()
        publishActions()
    }

    @UiThread
    fun onSelectedSessionChanged() {
        closePopupUnlessCurrent()
        publishActions()
    }

    @UiThread
    fun click(key: GeckoExtensionActionKey): Boolean {
        if (closed) return false
        val extension = extensionSnapshot(key.extensionId) ?: return false
        val owner = host.currentSessionIdentity()
        if (!extension.enabled || owner?.isPrivate == true && !extension.allowedInPrivateBrowsing) {
            return false
        }
        val raw = sessionActions[key] ?: defaultActions[key.copy(tabId = null)] ?: return false
        if (raw.identity != null && raw.identity != owner) return false
        if (!raw.state.enabled) return false
        raw.action.click()
        return true
    }

    @UiThread
    fun dismissPopup() {
        val popup = activePopup ?: return
        activePopup = null
        closePopup(popup)
    }

    @UiThread
    override fun close() {
        if (closed) return
        closed = true
        activePopup?.let(::closePopup)
        activePopup = null
        sessions.keys.toList().forEach(::clearSessionDelegates)
        sessions.clear()
        extensions.values.forEach(::clearExtensionDelegates)
        extensions.clear()
        defaultActions.clear()
        sessionActions.clear()
        host.onActionsChanged(emptyList())
    }

    private fun bindExtension(extension: WebExtension) {
        extensions[extension.id]?.let(::clearExtensionDelegates)
        defaultActions.keys.removeAll { key -> key.extensionId == extension.id }
        sessionActions.keys.removeAll { key -> key.extensionId == extension.id }
        extensions[extension.id] = extension
        extension.setActionDelegate(actionDelegate(extension, identity = null))
        extension.setTabDelegate(tabDelegate(extension))
        extension.setDownloadDelegate(downloadDelegate(extension))
        sessions.values.forEach { binding ->
            bindSession(extension, binding.session, binding.identity)
        }
    }

    private fun bindSession(
        extension: WebExtension,
        session: GeckoSession,
        identity: GeckoExtensionSessionIdentity,
    ) {
        val sessionController = session.webExtensionController
        sessionController.setActionDelegate(extension, actionDelegate(extension, identity))
        sessionController.setTabDelegate(extension, sessionTabDelegate(extension, identity))
    }

    private fun actionDelegate(
        extension: WebExtension,
        identity: GeckoExtensionSessionIdentity?,
    ) = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
            source: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action,
        ) = updateAction(source, session, action, GeckoExtensionActionKind.Browser, identity)

        override fun onPageAction(
            source: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action,
        ) = updateAction(source, session, action, GeckoExtensionActionKind.Page, identity)

        override fun onOpenPopup(
            source: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> = popupResult(source, action, identity, toggle = false)

        override fun onTogglePopup(
            source: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> = popupResult(source, action, identity, toggle = true)
    }

    private fun tabDelegate(extension: WebExtension) = object : WebExtension.TabDelegate {
        override fun onNewTab(
            source: WebExtension,
            details: WebExtension.CreateTabDetails,
        ): GeckoResult<GeckoSession> {
            val extensionSnapshot = extensionSnapshot(extension.id)
                ?: return deniedSessionResult("Extension unavailable")
            val request = GeckoExtensionCreateTabRequest(
                extensionId = source.id,
                source = host.currentSessionIdentity(),
                url = details.url,
                active = details.active != false,
                pinned = details.pinned == true,
                index = details.index,
                cookieStoreId = details.cookieStoreId,
                discarded = details.discarded == true,
                openInReaderMode = details.openInReaderMode == true,
            )
            val allowed = GeckoExtensionChromeRules.validateCreateTab(request, extensionSnapshot)
            if (allowed !is GeckoExtensionTabRequestResult.Allowed) {
                return deniedSessionResult("Extension tab request rejected")
            }
            val owner = allowed.value.source
                ?: return deniedSessionResult("Extension tab request has no Candy owner")
            val created = GeckoSession(
                org.mozilla.geckoview.GeckoSessionSettings.Builder()
                    .contextId(
                        GeckoProfileStorageRules.contextId(
                            profileId = owner.profileId,
                            isolationEnabled = owner.isolationEnabled,
                            isPrivate = owner.isPrivate,
                        ),
                    )
                    .usePrivateMode(owner.isPrivate)
                    .build(),
            )
            val tabId = host.createTab(allowed.value, created)
                ?: return deniedSessionResult("Candy could not create extension tab")
            val bound = sessions.values.firstOrNull { binding ->
                binding.identity.tabId == tabId && host.isCurrentSession(binding.identity)
            }?.session
            if (bound !== created || created.isOpen) {
                return deniedSessionResult("Candy did not adopt unopened extension tab session")
            }
            return GeckoResult.fromValue(created)
        }

        override fun onOpenOptionsPage(source: WebExtension) {
            val snapshot = extensionSnapshot(extension.id) ?: return
            val owner = host.currentSessionIdentity() ?: return
            if (owner.isPrivate && !snapshot.allowedInPrivateBrowsing) return
            val url = GeckoExtensionChromeRules.normalizeOptionsPageUrl(
                baseUrl = snapshot.baseUrl,
                optionsPageUrl = snapshot.optionsPageUrl,
            ) ?: return
            host.openOptionsPage(
                extensionId = source.id,
                owner = owner,
                url = url,
                openInTab = snapshot.opensOptionsPageInTab,
            )
        }
    }

    private fun sessionTabDelegate(
        extension: WebExtension,
        identity: GeckoExtensionSessionIdentity,
    ) = object : WebExtension.SessionTabDelegate {
        override fun onUpdateTab(
            source: WebExtension,
            session: GeckoSession,
            details: WebExtension.UpdateTabDetails,
        ): GeckoResult<AllowOrDeny> {
            val snapshot = extensionSnapshot(extension.id) ?: return GeckoResult.deny()
            val request = GeckoExtensionUpdateTabRequest(
                extensionId = source.id,
                target = identity,
                url = details.url,
                active = details.active,
                pinned = details.pinned,
                highlighted = details.highlighted,
                muted = details.muted,
                autoDiscardable = details.autoDiscardable,
            )
            val current = sessions[session]?.identity?.takeIf(host::isCurrentSession)
            val allowed = GeckoExtensionChromeRules.validateUpdateTab(request, snapshot, current)
            return GeckoResult.fromValue(
                if (allowed is GeckoExtensionTabRequestResult.Allowed && host.updateTab(allowed.value)) {
                    AllowOrDeny.ALLOW
                } else {
                    AllowOrDeny.DENY
                },
            )
        }

        override fun onCloseTab(
            source: WebExtension?,
            session: GeckoSession,
        ): GeckoResult<AllowOrDeny> {
            val snapshot = extensionSnapshot(extension.id) ?: return GeckoResult.deny()
            val current = sessions[session]?.identity?.takeIf(host::isCurrentSession)
            val rejected = GeckoExtensionChromeRules.mayCloseTab(snapshot, identity, current)
            return GeckoResult.fromValue(
                if (rejected == null && host.closeTab(identity)) {
                    AllowOrDeny.ALLOW
                } else {
                    AllowOrDeny.DENY
                },
            )
        }
    }

    private fun downloadDelegate(extension: WebExtension) = object : WebExtension.DownloadDelegate {
        @UiThread
        override fun onDownload(
            source: WebExtension,
            request: WebExtension.DownloadRequest,
        ): GeckoResult<WebExtension.DownloadInitData>? {
            val snapshot = extensionSnapshot(extension.id) ?: return null
            val owner = host.currentSessionIdentity()
            if (!snapshot.enabled || owner?.isPrivate == true && !snapshot.allowedInPrivateBrowsing) {
                return null
            }
            val validOwner = owner ?: return null
            val ownerSession = sessions.values.firstOrNull { binding ->
                binding.identity == validOwner && host.isCurrentSession(binding.identity)
            }?.session ?: return null
            val result = GeckoResult<WebExtension.DownloadInitData>()
            var active: Pair<WebExtension.Download, GeckoExtensionDownloadInfo>? = null
            var cancellation: GeckoDownloadCancellation? = null
            cancellation = downloadTransfers.start(
                transfer = GeckoDownloadTransferRequest(
                    owner = GeckoDownloadOwner(validOwner.profileId, validOwner.isPrivate, ownerSession),
                    request = request.request,
                    fetchFlags = request.downloadFlags,
                    suggestedFileName = request.filename,
                    allowHttpErrors = request.allowHttpErrors,
                ),
                listener = object : GeckoDownloadTransferListener {
                    @UiThread
                    override fun onStarted(start: GeckoDownloadTransferStart) {
                        val download = controller.createDownload(start.id) ?: run {
                            result.completeExceptionally(
                                IllegalStateException("Gecko rejected Candy download identity"),
                            )
                            cancellation?.cancel()
                            return
                        }
                        val info = GeckoExtensionDownloadInfo(start)
                        active = download to info
                        result.complete(WebExtension.DownloadInitData(download, info))
                    }

                    @UiThread
                    override fun onProgress(bytesReceived: Long, totalBytes: Long) {
                        active?.let { (download, info) ->
                            info.bytesReceived = bytesReceived
                            download.update(info)
                        }
                    }

                    @UiThread
                    override fun onComplete(bytesReceived: Long) {
                        active?.let { (download, info) ->
                            info.bytesReceived = bytesReceived
                            info.endTimeMillis = System.currentTimeMillis()
                            info.downloadState = WebExtension.Download.STATE_COMPLETE
                            download.update(info)
                        }
                    }

                    @UiThread
                    override fun onFailed(reason: GeckoDownloadFailure) {
                        val current = active
                        if (current == null) {
                            result.completeExceptionally(IllegalStateException("Gecko download failed: $reason"))
                        } else {
                            val (download, info) = current
                            info.endTimeMillis = System.currentTimeMillis()
                            info.downloadState = WebExtension.Download.STATE_INTERRUPTED
                            info.interruptReason = reason.toInterruptReason()
                            download.update(info)
                        }
                    }
                },
            ) ?: return result
            val activeCancellation = cancellation
            result.setCancellationDelegate(object : GeckoResult.CancellationDelegate {
                override fun cancel(): GeckoResult<Boolean> {
                    activeCancellation.cancel()
                    return GeckoResult.fromValue(true)
                }
            })
            return result
        }
    }

    private fun updateAction(
        extension: WebExtension,
        session: GeckoSession?,
        action: WebExtension.Action,
        kind: GeckoExtensionActionKind,
        delegateIdentity: GeckoExtensionSessionIdentity?,
    ) {
        if (closed || extensions[extension.id] !== extension) return
        val identity = session?.let(sessions::get)?.identity ?: delegateIdentity
        if (session != null && identity == null) return
        val key = GeckoExtensionActionKey(extension.id, identity?.tabId, kind)
        val state = GeckoExtensionChromeRules.normalizeAction(
            key = key,
            title = action.title,
            enabled = action.enabled,
            badgeText = action.badgeText,
            badgeBackgroundColor = action.badgeBackgroundColor,
            badgeTextColor = action.badgeTextColor,
        ) ?: return
        val target = if (identity == null) defaultActions else sessionActions
        target[key] = RawAction(state, action, identity)
        action.icon?.getBitmap(48)?.accept({ bitmap ->
            val current = target[key] ?: return@accept
            if (current.action !== action || current.identity != identity) return@accept
            target[key] = current.copy(state = current.state.copy(icon = bitmap))
            publishActions()
        }, { /* A missing icon falls back to Candy's stable action glyph. */ })
        publishActions()
    }

    private fun popupResult(
        extension: WebExtension,
        action: WebExtension.Action,
        delegateIdentity: GeckoExtensionSessionIdentity?,
        toggle: Boolean,
    ): GeckoResult<GeckoSession> {
        val snapshot = extensionSnapshot(extension.id)
            ?: return deniedSessionResult("Extension unavailable")
        val owner = delegateIdentity ?: host.currentSessionIdentity()
            ?: return deniedSessionResult("No selected Candy tab")
        if (!host.isCurrentSession(owner)) return deniedSessionResult("Stale Candy tab")
        if (!snapshot.enabled || owner.isPrivate && !snapshot.allowedInPrivateBrowsing) {
            return deniedSessionResult("Extension unavailable in selected tab")
        }
        if (action.enabled == false) return deniedSessionResult("Extension action disabled")
        activePopup?.let { previous ->
            activePopup = null
            closePopup(previous)
        }
        val popup = GeckoExtensionPopupIdentity(
            extensionId = extension.id,
            owner = owner,
            generation = ++popupGeneration,
        )
        val popupSession = GeckoSession(
            org.mozilla.geckoview.GeckoSessionSettings.Builder()
                .contextId(
                    GeckoProfileStorageRules.contextId(
                        profileId = owner.profileId,
                        isolationEnabled = owner.isolationEnabled,
                        isPrivate = owner.isPrivate,
                    ),
                )
                .usePrivateMode(owner.isPrivate)
                .build(),
        ).also { session -> session.open(runtime) }
        if (!host.openPopup(popup, popupSession, toggle)) {
            popupSession.close()
            return deniedSessionResult("Candy could not present extension popup")
        }
        activePopup = PopupBinding(popup, popupSession)
        return GeckoResult.fromValue(popupSession)
    }

    private fun publishActions() {
        val owner = host.currentSessionIdentity()
        val states = extensions.values.map { extension ->
            extensionSnapshot(extension.id)
        }.filterNotNull()
            .filter { extension ->
                extension.enabled && (owner?.isPrivate != true || extension.allowedInPrivateBrowsing)
            }
            .flatMap { extension ->
                GeckoExtensionActionKind.entries.mapNotNull { kind ->
                    val default = defaultActions[GeckoExtensionActionKey(extension.id, null, kind)]?.state
                    val override = owner?.tabId?.let { tabId ->
                        sessionActions[GeckoExtensionActionKey(extension.id, tabId, kind)]
                            ?.takeIf { action -> action.identity == owner }
                            ?.state
                    }
                    GeckoExtensionChromeRules.resolveAction(default, override)
                }
            }
            .filter(GeckoExtensionActionState::enabled)
            .sortedWith(
                compareBy<GeckoExtensionActionState> { state -> state.title.lowercase() }
                    .thenBy { state -> state.key.extensionId }
                    .thenBy { state -> state.key.kind.ordinal },
            )
        host.onActionsChanged(states)
    }

    private fun closePopupUnlessCurrent() {
        val popup = activePopup ?: return
        if (
            GeckoExtensionChromeRules.mayKeepPopup(
                popup = popup.identity,
                currentOwner = host.currentSessionIdentity(),
                extension = extensionSnapshot(popup.identity.extensionId),
            )
        ) return
        activePopup = null
        closePopup(popup)
    }

    private fun closePopup(popup: PopupBinding) {
        host.closePopup(popup.identity)
        popup.session.close()
    }

    private fun removeExtension(extensionId: String) {
        extensions.remove(extensionId)?.let(::clearExtensionDelegates)
        defaultActions.keys.removeAll { key -> key.extensionId == extensionId }
        sessionActions.keys.removeAll { key -> key.extensionId == extensionId }
    }

    private fun clearExtensionDelegates(extension: WebExtension) {
        extension.setActionDelegate(null)
        extension.setTabDelegate(null)
        extension.setDownloadDelegate(null)
    }

    private fun clearSessionDelegates(session: GeckoSession) {
        extensions.values.forEach { extension ->
            session.webExtensionController.setActionDelegate(extension, null)
            session.webExtensionController.setTabDelegate(extension, null)
        }
    }

    private fun extensionSnapshot(extensionId: String): GeckoExtension? =
        extensions[extensionId]?.toCandyChromeExtension()

    private fun deniedSessionResult(message: String): GeckoResult<GeckoSession> =
        GeckoResult.fromException(IllegalStateException(message))
}

private class GeckoExtensionDownloadInfo(
    private val start: GeckoDownloadTransferStart,
) : WebExtension.Download.Info {
    var bytesReceived: Long = 0L
    var endTimeMillis: Long? = null
    var downloadState: Int = WebExtension.Download.STATE_IN_PROGRESS
    var interruptReason: Int? = null

    override fun bytesReceived(): Long = bytesReceived
    override fun endTime(): Long? = endTimeMillis
    override fun error(): Int? = interruptReason
    override fun fileExists(): Boolean = downloadState == WebExtension.Download.STATE_COMPLETE
    override fun filename(): String = start.fileName
    override fun fileSize(): Long = start.totalBytes
    override fun mime(): String = start.mimeType
    override fun referrer(): String = start.referrer.orEmpty()
    override fun startTime(): Long = start.startedAtMillis
    override fun state(): Int = downloadState
    override fun totalBytes(): Long = start.totalBytes
}

private fun GeckoDownloadFailure.toInterruptReason(): Int = when (this) {
    GeckoDownloadFailure.InvalidRequest -> WebExtension.Download.INTERRUPT_REASON_NETWORK_INVALID_REQUEST
    GeckoDownloadFailure.Network -> WebExtension.Download.INTERRUPT_REASON_NETWORK_FAILED
    GeckoDownloadFailure.Http -> WebExtension.Download.INTERRUPT_REASON_SERVER_FAILED
    GeckoDownloadFailure.Storage -> WebExtension.Download.INTERRUPT_REASON_FILE_FAILED
    GeckoDownloadFailure.Cancelled -> WebExtension.Download.INTERRUPT_REASON_USER_CANCELED
}

private fun WebExtension.toCandyChromeExtension() = GeckoExtension(
    id = id,
    name = metaData.name,
    version = metaData.version,
    enabled = metaData.enabled,
    allowedInPrivateBrowsing = metaData.allowedInPrivateBrowsing,
    isBuiltIn = isBuiltIn,
    temporary = metaData.temporary,
    disabledFlags = metaData.disabledFlags,
    location = location,
    baseUrl = metaData.baseUrl,
    optionsPageUrl = metaData.optionsPageUrl,
    opensOptionsPageInTab = metaData.openOptionsPageInTab,
)
