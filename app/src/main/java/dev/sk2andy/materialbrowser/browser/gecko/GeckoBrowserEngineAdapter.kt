package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.annotation.UiThread
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommand
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommandType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineSessionPort

/** Android view-host edge kept separate from the engine-neutral shared session port. */
internal interface BrowserEngineViewPort {
    fun createView(context: Context): View

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
}

/** One Android browser tab: shared commands plus the platform renderer host. */
internal interface AndroidBrowserEngineSessionPort :
    BrowserEngineSessionPort,
    BrowserEngineViewPort {
    fun setActive(active: Boolean)

    fun updatePrivacyPolicy(
        policy: GeckoPrivacyPolicy,
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
    constructor(context: Context) : this(GeckoRuntimeOwner.getOrCreate(context.applicationContext))

    @UiThread
    fun reconcileToppings(scripts: List<UserScript>) {
        runtime.toppings.reconcile(scripts)
    }

    fun setToppingHostStateListener(listener: (GeckoToppingHostState) -> Unit) {
        runtime.toppings.setStateListener(listener)
    }

    fun clearAllData(onComplete: (Boolean) -> Unit = {}) {
        runtime.clearAllData(onComplete)
    }

    @UiThread
    fun create(
        tabId: String,
        profileId: String,
        isPrivate: Boolean,
        privacyPolicy: GeckoPrivacyPolicy = GeckoPrivacyPolicy.Disabled,
        privacyEventSink: GeckoPrivacyEventSink = GeckoPrivacyEventSink { },
        eventSink: BrowserEngineEventSink,
    ): AndroidBrowserEngineSessionPort {
        require(tabId.isNotBlank()) { "A Gecko engine session needs a tab ID" }
        return GeckoBrowserEngineSessionAdapter(
            tabId = tabId,
            session = runtime.createSession(
                profileId = profileId,
                isPrivate = isPrivate,
                privacyPolicy = privacyPolicy,
                privacyEventSink = privacyEventSink,
            ),
            eventSink = eventSink,
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
) : AndroidBrowserEngineSessionPort {
    private var previousState = GeckoBrowserSessionState()
    private var closed = false
    private var stopRequested = false

    init {
        require(tabId.isNotBlank()) { "A Gecko engine session needs a tab ID" }
        session.setStateListener(::onStateChanged)
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
    override fun releaseView(view: View) {
        session.releaseView(view)
    }

    @UiThread
    override fun setActive(active: Boolean) {
        if (!closed) session.setActive(active)
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
    override fun printPage(): Boolean = !closed && session.printPage()

    override fun updatePrivacyPolicy(policy: GeckoPrivacyPolicy, onReady: () -> Unit) {
        if (closed) return
        session.updatePrivacyPolicy(policy, onReady)
    }

    @UiThread
    override fun setDesktopMode(enabled: Boolean) {
        if (!closed) session.setDesktopMode(enabled)
    }

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
)
