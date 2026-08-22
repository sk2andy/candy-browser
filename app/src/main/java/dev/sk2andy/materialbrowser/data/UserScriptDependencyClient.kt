package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyFetch
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyFetcher
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyRules
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class UserScriptDependencyClient : UserScriptDependencyFetcher {
    override fun fetch(url: String, maxBytes: Int): UserScriptDependencyFetch? {
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val parsed = UserScriptDependencyRules.parseUrl(currentUrl) ?: return null
            val host = URL(currentUrl).host
            if (
                parsed.url != currentUrl ||
                !UserScriptDependencyRules.isTrustedFetchHost(host) ||
                !hasOnlyPublicAddresses(host)
            ) return null
            val connection = runCatching {
                URL(currentUrl).openConnection() as HttpsURLConnection
            }.getOrNull() ?: return null
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.instanceFollowRedirects = false
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept-Encoding", "identity")
                val responseCode = connection.responseCode
                if (responseCode in REDIRECT_STATUS_CODES) {
                    if (redirectCount >= MAX_REDIRECTS) return null
                    val location = connection.getHeaderField("Location") ?: return null
                    currentUrl = UserScriptDependencyRules.resolveTrustedRedirect(
                        currentUrl = currentUrl,
                        location = location,
                    ) ?: return null
                    return@repeat
                }
                if (responseCode != HttpURLConnection.HTTP_OK) return null
                val declaredLength = connection.contentLengthLong
                if (declaredLength > maxBytes) {
                    return UserScriptDependencyFetch(ByteArray(maxBytes + 1))
                }
                val bytes = connection.inputStream.use { input -> input.readNBytes(maxBytes + 1) }
                return UserScriptDependencyFetch(
                    bytes = bytes,
                    mimeType = connection.contentType,
                )
            } catch (_: Exception) {
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun hasOnlyPublicAddresses(host: String): Boolean = runCatching {
        val addresses = InetAddress.getAllByName(host)
        addresses.isNotEmpty() && addresses.all { address ->
            !address.isAnyLocalAddress &&
                !address.isLoopbackAddress &&
                !address.isLinkLocalAddress &&
                !address.isSiteLocalAddress &&
                !address.isMulticastAddress
        }
    }.getOrDefault(false)

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 8_000
        const val READ_TIMEOUT_MILLIS = 12_000
        const val MAX_REDIRECTS = 3
        val REDIRECT_STATUS_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
    }
}
