package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import java.util.concurrent.Executors

internal object WebViewStartupRules {
    fun shouldStartAsynchronously(
        isColdStart: Boolean,
        hasIncomingBrowserRequest: Boolean,
        isWebViewProcessUnused: Boolean,
    ): Boolean = isColdStart && hasIncomingBrowserRequest && isWebViewProcessUnused
}

internal object WebViewProcessStartup {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<StartupCallbacks>()
    private var state = State.Unused

    val isPending: Boolean
        get() = state == State.Pending

    val isUnused: Boolean
        get() = state == State.Unused

    val shouldDeferWebViewRuntime: Boolean
        get() = state == State.Pending || state == State.Failed

    fun start(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (state != State.Unused) return
        state = State.Pending
        val executor = Executors.newSingleThreadExecutor()
        val domainWarmUpExecutor = Executors.newSingleThreadExecutor()
        domainWarmUpExecutor.execute {
            runCatching { SiteDomainRules.normalizedDomain(DOMAIN_WARM_UP_HOST) }
        }
        val config = WebViewStartUpConfig.Builder(executor).build()
        WebViewCompat.startUpWebView(
            context.applicationContext,
            config,
            object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                override fun onResult(result: WebViewStartUpResult) {
                    domainWarmUpExecutor.execute {
                        completeSuccess {
                            executor.shutdown()
                            domainWarmUpExecutor.shutdown()
                        }
                    }
                }

                override fun onError(error: WebViewStartupException) {
                    completeFailure {
                        executor.shutdown()
                        domainWarmUpExecutor.shutdown()
                    }
                }
            },
        )
    }

    fun whenReady(
        onReady: () -> Unit,
        onFailure: () -> Unit = {},
    ) {
        check(Looper.myLooper() == Looper.getMainLooper())
        when (state) {
            State.Ready -> onReady()
            State.Failed -> onFailure()
            State.Pending -> callbacks += StartupCallbacks(onReady, onFailure)
            State.Unused -> Unit
        }
    }

    fun markReady() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (state == State.Unused) state = State.Ready
    }

    private fun completeSuccess(afterCompletion: () -> Unit) {
        mainHandler.post {
            if (state == State.Pending) {
                state = State.Ready
                val pendingCallbacks = callbacks.toList()
                callbacks.clear()
                pendingCallbacks.forEach { callback -> callback.onReady() }
            }
            afterCompletion()
        }
    }

    private fun completeFailure(afterCompletion: () -> Unit) {
        mainHandler.post {
            if (state == State.Pending) {
                state = State.Failed
                val pendingCallbacks = callbacks.toList()
                callbacks.clear()
                pendingCallbacks.forEach { callback -> callback.onFailure() }
            }
            afterCompletion()
        }
    }

    private enum class State {
        Unused,
        Pending,
        Ready,
        Failed,
    }

    private data class StartupCallbacks(
        val onReady: () -> Unit,
        val onFailure: () -> Unit,
    )

    private const val DOMAIN_WARM_UP_HOST = "example.com"
}
