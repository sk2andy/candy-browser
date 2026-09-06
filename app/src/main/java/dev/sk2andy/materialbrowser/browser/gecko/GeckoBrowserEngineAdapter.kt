package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollListener
import dev.sk2andy.materialbrowser.browser.BrowserEngineScrollMetrics
import dev.sk2andy.materialbrowser.browser.actions.BrowserContentTargetListener
import dev.sk2andy.materialbrowser.browser.actions.WebContentTarget
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptMenuCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommandType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineSessionPort
import org.mozilla.geckoview.GeckoSession

/** Android view-host edge kept separate from the engine-neutral shared session port. */
internal interface BrowserEngineViewPort {
    fun createView(context: Context): View

    fun awaitContentPresented(listener: () -> Unit)

    fun releaseView(view: View)

    fun capturePreview(
        targetWidthPx: Int,
        visibleViewHeightPx: Int,
        maximumTargetHeightPx: Int,
        onComplete: (Bitmap?) -> Unit,
    ): BrowserEnginePreviewCapture?

    fun findInPage(
        query: String,
        forward: Boolean,
        onComplete: (GeckoFindResult?) -> Unit,
    )

    fun clearFindInPage()

    fun printPage(): Boolean

    fun setDesktopMode(enabled: Boolean)

    fun scrollMetrics(): BrowserEngineScrollMetrics? = null

    fun scrollToVerticalOffset(offsetPx: Int) = Unit

    /** Opaque Gecko state for same-device resume; never a cross-engine archive payload. */
    fun sessionStateSnapshot(): String? = null

    fun restoreSessionState(encodedState: String): Boolean = false

    fun loadExtensionUrl(url: String): Boolean = false
}

/** One Android browser tab: shared commands plus the platform renderer host. */
internal interface AndroidBrowserEngineSessionPort :
    BrowserEngineSessionPort,
    BrowserEngineViewPort {
    fun setActive(active: Boolean)

    fun setMediaStateListener(listener: GeckoMediaSessionStateListener?)

    fun setScrollListener(listener: BrowserEngineScrollListener?)

    fun setContentTargetListener(listener: BrowserContentTargetListener?)

    /** Delivers an engine-originated content target through the normal callback chain. */
    @VisibleForTesting
    fun dispatchContentTargetForTesting(target: WebContentTarget) = Unit

    fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?)

    fun setNewSessionListener(listener: GeckoNewSessionListener?) = Unit

    fun setDownloadResponseListener(listener: GeckoDownloadResponseListener?) = Unit

    fun startContextDownload(
        request: GeckoContextDownloadRequest,
        listener: GeckoDownloadTransferListener,
    ): GeckoDownloadCancellation? = null

    fun setFilePromptListener(listener: GeckoFilePromptListener?) = Unit

    fun setAndroidPermissionRequestListener(
        listener: GeckoAndroidPermissionRequestListener?,
    ) = Unit

    fun setContentPermissionRequestListener(
        listener: GeckoContentPermissionRequestListener?,
    ) = Unit

    fun setMediaPermissionRequestListener(listener: GeckoMediaPermissionRequestListener?) = Unit

    fun setAuthPromptListener(listener: GeckoAuthPromptListener?) = Unit

    fun setWebPromptListener(listener: GeckoWebPromptListener?) = Unit

    fun setVideoAutoplayBlocked(blocked: Boolean)

    fun setAudioMuted(muted: Boolean)

    fun executeMediaCommand(command: GeckoMediaCommand)

    fun seekMedia(positionMillis: Long)

    fun notifyPictureInPictureModeChanged(inPictureInPicture: Boolean) = Unit

    fun setPictureInPicturePlaybackExpected(expected: Boolean) = Unit

    fun goToHistoryIndex(index: Int)

    fun historyUrlAtOffset(offset: Int): String?

    fun extractPageForReader(onComplete: (String?) -> Unit)

    fun updatePrivacyPolicy(
        policy: GeckoPrivacyPolicy,
        reloadOnCookiePermissionChange: Boolean = false,
        onReady: () -> Unit = {},
    )
}

internal fun interface BrowserEngineEventSink {
    fun onEngineEvent(event: BrowserEngineEvent)
}

/** Creates Gecko-backed ports without exposing GeckoRuntime or GeckoSession to browser chrome. */
internal class GeckoBrowserEngineSessionFactory(
    private val runtime: GeckoRuntimeHandle,
) {
    private val extensionSessionGenerations = mutableMapOf<String, Long>()
    private val extensionSessionIdentities = mutableMapOf<String, GeckoExtensionSessionIdentity>()
    private val preparedSessions = mutableMapOf<String, GeckoSession>()

    constructor(context: Context) : this(GeckoRuntimeOwner.getOrCreate(context.applicationContext))

    @UiThread
    fun reconcileToppings(scripts: List<UserScript>) {
        runtime.toppings.reconcile(scripts)
    }

    fun setToppingHostStateListener(listener: (GeckoToppingHostState) -> Unit) {
        runtime.toppings.setStateListener(listener)
    }

    fun setToppingInteractionDelegate(delegate: GeckoToppingInteractionDelegate) {
        runtime.toppings.setInteractionDelegate(delegate)
    }

    fun invokeToppingMenuCommand(command: UserScriptMenuCommand) {
        runtime.toppings.invokeMenuCommand(command)
    }

    fun clearToppingValues(scriptId: String) {
        runtime.toppings.clearValues(scriptId)
    }

    fun clearBrowsingData(
        data: GeckoBrowsingData,
        onComplete: (Boolean) -> Unit = {},
    ) = runtime.clearBrowsingData(data, onComplete)

    fun clearAllData(onComplete: (Boolean) -> Unit = {}) = runtime.clearAllData(onComplete)

    fun requestProfileDataDeletion(profileId: String): Boolean =
        runtime.requestProfileDataDeletion(profileId)

    @UiThread
    fun setBlockThirdPartyCookies(blocked: Boolean) {
        runtime.setBlockThirdPartyCookies(blocked)
    }

    @UiThread
    fun setExtensionChromeHost(host: GeckoExtensionChromeHost?) {
        runtime.extensions.setChromeHost(host)
    }

    @UiThread
    fun clickExtensionAction(key: GeckoExtensionActionKey): Boolean =
        runtime.extensions.clickChromeAction(key)

    @UiThread
    fun dismissExtensionPopup() {
        runtime.extensions.dismissChromePopup()
    }

    @UiThread
    fun notifySelectedExtensionTabChanged() {
        runtime.extensions.onSelectedChromeSessionChanged()
    }

    fun extensionSessionIdentity(tabId: String): GeckoExtensionSessionIdentity? =
        extensionSessionIdentities[tabId]

    /** Prepares an unopened Gecko-owned session for the next [create] of this Candy tab. */
    @UiThread
    fun prepareSession(
        tabId: String,
        session: GeckoSession,
    ): Boolean {
        if (
            tabId.isBlank() ||
            session.isOpen ||
            extensionSessionIdentities.containsKey(tabId) ||
            preparedSessions.containsKey(tabId)
        ) {
            return false
        }
        preparedSessions[tabId] = session
        return true
    }

    @UiThread
    fun create(
        tabId: String,
        profileId: String,
        isolationEnabled: Boolean = false,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        privacyEventSink: GeckoPrivacyEventSink = GeckoPrivacyEventSink { },
        trailHistoryEventSink: GeckoCandyTrailHistoryEventSink =
            GeckoCandyTrailHistoryEventSink { _, _ -> },
        eventSink: BrowserEngineEventSink,
    ): AndroidBrowserEngineSessionPort {
        require(tabId.isNotBlank()) { "A Gecko engine session needs a tab ID" }
        val preparedSession = preparedSessions.remove(tabId)
        val session = if (preparedSession == null) {
            runtime.createSession(
                profileId = profileId,
                isolationEnabled = isolationEnabled,
                isPrivate = isPrivate,
                privacyPolicy = privacyPolicy,
                privacyEventSink = privacyEventSink,
            )
        } else {
            runtime.adoptExtensionSession(
                session = preparedSession,
                profileId = profileId,
                isolationEnabled = isolationEnabled,
                isPrivate = isPrivate,
                privacyPolicy = privacyPolicy,
                privacyEventSink = privacyEventSink,
            ) ?: error("Prepared extension session does not match Candy tab context")
        }.also { created ->
            val generation = extensionSessionGenerations.getOrDefault(tabId, 0L) + 1L
            extensionSessionGenerations[tabId] = generation
            val identity = GeckoExtensionSessionIdentity(
                tabId = tabId,
                profileId = profileId,
                isPrivate = isPrivate,
                generation = generation,
                isolationEnabled = isolationEnabled,
            )
            extensionSessionIdentities[tabId] = identity
            created.bindExtensionTab(tabId, generation)
        }
        return GeckoBrowserEngineSessionAdapter(
            tabId = tabId,
            session = session,
            eventSink = eventSink,
            trailHistoryEventSink = trailHistoryEventSink,
            onActiveChanged = { active ->
                runtime.toppings.setActiveTab(tabId.takeIf { active })
            },
        )
    }
}

/**
 * Thin Gecko adapter for the shared session contract.
 *
 * It owns neither browser tabs nor chrome state. Those stay in BrowserSessionController; this
 * class only translates commands, renderer binding and Gecko lifecycle callbacks.
 */
internal class GeckoBrowserEngineSessionAdapter(
    override val tabId: String,
    private val session: GeckoBrowserSession,
    private val eventSink: BrowserEngineEventSink,
    trailHistoryEventSink: GeckoCandyTrailHistoryEventSink =
        GeckoCandyTrailHistoryEventSink { _, _ -> },
    private val onActiveChanged: (Boolean) -> Unit = {},
) : AndroidBrowserEngineSessionPort {
    private var previousState = GeckoBrowserSessionState()
    private var closed = false
    private var stopRequested = false
    private val trailHistoryTracker = GeckoCandyTrailHistoryTracker(tabId) { event ->
        trailHistoryEventSink.onHistoryEvent(this, event)
    }

    init {
        require(tabId.isNotBlank()) { "A Gecko engine session needs a tab ID" }
        session.setStateListener(::onStateChanged)
        session.setHistoryStateListener(trailHistoryTracker::onHistoryStateChanged)
    }

    @UiThread
    override fun execute(command: BrowserEngineCommand) {
        if (closed) return
        when (command.type) {
            BrowserEngineCommandType.Load -> load(requireNotNull(command.address))
            BrowserEngineCommandType.Back -> session.goBack()
            BrowserEngineCommandType.Forward -> session.goForward()
            BrowserEngineCommandType.Reload -> session.reload()
            BrowserEngineCommandType.Stop -> {
                stopRequested = true
                session.stop()
            }
            BrowserEngineCommandType.Close -> close()
        }
    }

    @UiThread
    override fun createView(context: Context): View {
        check(!closed) { "Cannot bind a closed browser engine session" }
        return session.createView(context)
    }

    @UiThread
    override fun awaitContentPresented(listener: () -> Unit) {
        if (!closed) session.awaitContentPresented(listener)
    }

    @UiThread
    override fun releaseView(view: View) {
        session.releaseView(view)
    }

    @UiThread
    override fun setActive(active: Boolean) {
        if (!closed) {
            session.setActive(active)
            onActiveChanged(active)
        }
    }

    @UiThread
    override fun setMediaStateListener(listener: GeckoMediaSessionStateListener?) {
        session.setMediaStateListener(if (closed) null else listener)
    }

    @UiThread
    override fun setScrollListener(listener: BrowserEngineScrollListener?) {
        session.setScrollListener(if (closed) null else listener)
    }

    @UiThread
    override fun scrollMetrics(): BrowserEngineScrollMetrics? =
        session.scrollMetrics().takeUnless { closed }

    @UiThread
    override fun scrollToVerticalOffset(offsetPx: Int) {
        if (!closed) session.scrollToVerticalOffset(offsetPx)
    }

    @UiThread
    override fun setContentTargetListener(listener: BrowserContentTargetListener?) {
        session.setContentTargetListener(if (closed) null else listener)
    }

    @VisibleForTesting
    override fun dispatchContentTargetForTesting(target: WebContentTarget) {
        if (!closed) session.dispatchContentTargetForTesting(target)
    }

    @UiThread
    override fun setNavigationRequestListener(listener: GeckoNavigationRequestListener?) {
        session.setNavigationRequestListener(if (closed) null else listener)
    }

    @UiThread
    override fun setNewSessionListener(listener: GeckoNewSessionListener?) {
        session.setNewSessionListener(if (closed) null else listener)
    }

    @UiThread
    override fun setDownloadResponseListener(listener: GeckoDownloadResponseListener?) {
        session.setDownloadResponseListener(if (closed) null else listener)
    }

    override fun startContextDownload(
        request: GeckoContextDownloadRequest,
        listener: GeckoDownloadTransferListener,
    ): GeckoDownloadCancellation? = if (closed) null else session.startContextDownload(request, listener)

    @UiThread
    override fun setFilePromptListener(listener: GeckoFilePromptListener?) {
        session.setFilePromptListener(if (closed) null else listener)
    }

    @UiThread
    override fun setAndroidPermissionRequestListener(
        listener: GeckoAndroidPermissionRequestListener?,
    ) {
        session.setAndroidPermissionRequestListener(if (closed) null else listener)
    }

    @UiThread
    override fun setContentPermissionRequestListener(
        listener: GeckoContentPermissionRequestListener?,
    ) {
        session.setContentPermissionRequestListener(if (closed) null else listener)
    }

    @UiThread
    override fun setMediaPermissionRequestListener(listener: GeckoMediaPermissionRequestListener?) {
        session.setMediaPermissionRequestListener(if (closed) null else listener)
    }

    @UiThread
    override fun setAuthPromptListener(listener: GeckoAuthPromptListener?) {
        session.setAuthPromptListener(if (closed) null else listener)
    }

    @UiThread
    override fun setWebPromptListener(listener: GeckoWebPromptListener?) {
        session.setWebPromptListener(if (closed) null else listener)
    }

    @UiThread
    override fun setVideoAutoplayBlocked(blocked: Boolean) {
        if (!closed) session.setVideoAutoplayBlocked(blocked)
    }

    @UiThread
    override fun setAudioMuted(muted: Boolean) {
        if (!closed) session.setAudioMuted(muted)
    }

    @UiThread
    override fun executeMediaCommand(command: GeckoMediaCommand) {
        if (!closed) session.executeMediaCommand(command)
    }

    @UiThread
    override fun seekMedia(positionMillis: Long) {
        if (!closed) session.seekMedia(positionMillis)
    }

    @UiThread
    override fun notifyPictureInPictureModeChanged(inPictureInPicture: Boolean) {
        if (!closed) session.notifyPictureInPictureModeChanged(inPictureInPicture)
    }

    @UiThread
    override fun setPictureInPicturePlaybackExpected(expected: Boolean) {
        if (!closed) session.setPictureInPicturePlaybackExpected(expected)
    }

    @UiThread
    override fun goToHistoryIndex(index: Int) {
        if (!closed) session.goToHistoryIndex(index)
    }

    override fun historyUrlAtOffset(offset: Int): String? = if (closed) {
        null
    } else {
        session.historyUrlAtOffset(offset)
    }

    @UiThread
    override fun capturePreview(
        targetWidthPx: Int,
        visibleViewHeightPx: Int,
        maximumTargetHeightPx: Int,
        onComplete: (Bitmap?) -> Unit,
    ): BrowserEnginePreviewCapture? {
        if (closed) return null
        return session.capturePreview(
            targetWidthPx = targetWidthPx,
            visibleViewHeightPx = visibleViewHeightPx,
            maximumTargetHeightPx = maximumTargetHeightPx,
            onComplete = onComplete,
        )
    }

    @UiThread
    override fun findInPage(
        query: String,
        forward: Boolean,
        onComplete: (GeckoFindResult?) -> Unit,
    ) {
        if (closed) {
            onComplete(null)
            return
        }
        session.findInPage(query = query, forward = forward, onComplete = onComplete)
    }

    @UiThread
    override fun clearFindInPage() {
        if (!closed) session.clearFindInPage()
    }

    @UiThread
    override fun extractPageForReader(onComplete: (String?) -> Unit) {
        if (closed) {
            onComplete(null)
            return
        }
        session.extractPageForReader(onComplete)
    }

    @UiThread
    override fun printPage(): Boolean = !closed && session.printPage()

    override fun updatePrivacyPolicy(
        policy: GeckoPrivacyPolicy,
        reloadOnCookiePermissionChange: Boolean,
        onReady: () -> Unit,
    ) {
        if (closed) return
        session.updatePrivacyPolicy(policy, reloadOnCookiePermissionChange, onReady)
    }

    @UiThread
    override fun setDesktopMode(enabled: Boolean) {
        if (!closed) session.setDesktopMode(enabled)
    }

    override fun sessionStateSnapshot(): String? = if (closed) null else session.sessionStateSnapshot()

    override fun restoreSessionState(encodedState: String): Boolean =
        !closed && session.restoreSessionState(encodedState)

    override fun loadExtensionUrl(url: String): Boolean =
        !closed && session.loadExtensionUrl(url)

    private fun load(address: String) {
        if (session.loadUrl(address)) return
        eventSink.onEngineEvent(
            previousState.toEngineEvent(
                tabId = tabId,
                type = BrowserEngineEventType.NavigationFailed,
                failureDescription = INVALID_ADDRESS_FAILURE,
            ),
        )
    }

    private fun close() {
        if (closed) return
        closed = true
        session.setStateListener(null)
        session.setHistoryStateListener(null)
        session.setMediaStateListener(null)
        session.setScrollListener(null)
        session.setContentTargetListener(null)
        session.setNavigationRequestListener(null)
        session.setNewSessionListener(null)
        session.setDownloadResponseListener(null)
        session.setFilePromptListener(null)
        session.setAndroidPermissionRequestListener(null)
        session.setContentPermissionRequestListener(null)
        session.setMediaPermissionRequestListener(null)
        session.setAuthPromptListener(null)
        session.setWebPromptListener(null)
        session.close()
        eventSink.onEngineEvent(
            previousState.toEngineEvent(
                tabId = tabId,
                type = BrowserEngineEventType.Closed,
            ),
        )
    }

    private fun onStateChanged(state: GeckoBrowserSessionState) {
        if (closed) return
        if (state.crashed && !previousState.crashed) {
            previousState = state
            closed = true
            session.setStateListener(null)
            session.setHistoryStateListener(null)
            session.setMediaStateListener(null)
            session.setScrollListener(null)
            session.setContentTargetListener(null)
            session.setNavigationRequestListener(null)
            session.setNewSessionListener(null)
            session.setDownloadResponseListener(null)
            session.setFilePromptListener(null)
            session.setAndroidPermissionRequestListener(null)
            session.setContentPermissionRequestListener(null)
            session.setMediaPermissionRequestListener(null)
            session.setAuthPromptListener(null)
            session.setWebPromptListener(null)
            session.close()
            eventSink.onEngineEvent(
                state.toEngineEvent(
                    tabId = tabId,
                    type = BrowserEngineEventType.Crashed,
                    failureDescription = CRASH_FAILURE,
                ),
            )
            return
        }
        val type = when {
            state.isLoading && !previousState.isLoading ->
                BrowserEngineEventType.NavigationStarted
            !state.isLoading && previousState.isLoading && state.lastNavigationSucceeded == true ->
                BrowserEngineEventType.NavigationCommitted
            !state.isLoading && previousState.isLoading &&
                state.lastNavigationSucceeded == false && stopRequested ->
                BrowserEngineEventType.StateChanged
            !state.isLoading && previousState.isLoading &&
                state.lastNavigationSucceeded == false -> BrowserEngineEventType.NavigationFailed
            !state.isLoading && state.lastNavigationSucceeded == false &&
                previousState.lastNavigationSucceeded != false ->
                BrowserEngineEventType.NavigationFailed
            state.hasSharedStateChangeFrom(previousState) -> BrowserEngineEventType.StateChanged
            else -> null
        }
        previousState = state
        if (!state.isLoading) stopRequested = false
        if (type != null) {
            eventSink.onEngineEvent(
                state.toEngineEvent(
                    tabId = tabId,
                    type = type,
                    failureDescription = if (type == BrowserEngineEventType.NavigationFailed) {
                        state.failureDescription ?: LOAD_FAILURE
                    } else {
                        null
                    },
                ),
            )
        }
    }

    private companion object {
        const val INVALID_ADDRESS_FAILURE = "Address rejected by browser URI policy"
        const val LOAD_FAILURE = "Gecko navigation failed"
        const val CRASH_FAILURE = "Gecko content process crashed"
    }
}

private fun GeckoBrowserSessionState.hasSharedStateChangeFrom(
    previous: GeckoBrowserSessionState,
): Boolean =
    url != previous.url ||
        title != previous.title ||
        canGoBack != previous.canGoBack ||
        canGoForward != previous.canGoForward

private fun GeckoBrowserSessionState.toEngineEvent(
    tabId: String,
    type: BrowserEngineEventType,
    failureDescription: String? = null,
): BrowserEngineEvent = BrowserEngineEvent(
    tabId = tabId,
    type = type,
    address = url,
    title = title,
    canGoBack = canGoBack,
    canGoForward = canGoForward,
    failureDescription = failureDescription,
    isLoading = isLoading,
)
