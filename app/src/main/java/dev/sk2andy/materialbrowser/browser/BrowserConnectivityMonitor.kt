package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

internal class BrowserConnectivityMonitor(
    context: Context,
    private val onOnlineChanged: (Boolean) -> Unit,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : AutoCloseable {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val closed = AtomicBoolean(false)
    private var lastDeliveredStatus: Boolean? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshStatus()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            if (network != connectivityManager.activeNetwork) return
            dispatchStatus(networkCapabilities.isOnline())
        }

        override fun onLost(network: Network) {
            refreshStatus()
        }

        override fun onUnavailable() {
            refreshStatus()
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        refreshStatus()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    fun refresh() {
        refreshStatus()
    }

    private fun refreshStatus() {
        if (closed.get()) return
        val capabilities = connectivityManager.activeNetwork?.let(
            connectivityManager::getNetworkCapabilities,
        )
        dispatchStatus(capabilities?.isOnline() == true)
    }

    private fun dispatchStatus(isOnline: Boolean) {
        if (closed.get()) return
        val delivery = Runnable {
            if (closed.get()) return@Runnable
            val status = BrowserConnectivityRules.statusToDeliver(
                lastDeliveredStatus = lastDeliveredStatus,
                currentStatus = isOnline,
            ) ?: return@Runnable
            lastDeliveredStatus = status
            onOnlineChanged(status)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) delivery.run() else mainHandler.post(delivery)
    }

    private fun NetworkCapabilities.isOnline(): Boolean =
        BrowserConnectivityRules.isOnline(
            hasInternetCapability = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            hasValidatedCapability = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        )
}

internal object BrowserConnectivityRules {
    fun isOnline(
        hasInternetCapability: Boolean,
        hasValidatedCapability: Boolean,
    ): Boolean = hasInternetCapability && hasValidatedCapability

    fun statusToDeliver(
        lastDeliveredStatus: Boolean?,
        currentStatus: Boolean,
    ): Boolean? = currentStatus.takeIf { it != lastDeliveredStatus }
}
