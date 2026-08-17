package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogEntry
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogParser
import dev.sk2andy.materialbrowser.browser.userscript.ToppingCatalogRules
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection

internal sealed interface ToppingRemoteFailure {
    data class HttpStatus(val code: Int) : ToppingRemoteFailure

    data object ResponseTooLarge : ToppingRemoteFailure

    data object InvalidSource : ToppingRemoteFailure

    data object Network : ToppingRemoteFailure
}

internal sealed interface ToppingRemoteResult {
    data class Success(val bytes: ByteArray) : ToppingRemoteResult

    data class Failure(val reason: ToppingRemoteFailure) : ToppingRemoteResult
}

internal interface ToppingCatalogRemoteSource {
    fun fetchManifest(): ToppingRemoteResult

    fun fetchTopping(entry: ToppingCatalogEntry): ToppingRemoteResult
}

internal class GitHubToppingCatalogClient : ToppingCatalogRemoteSource {
    override fun fetchManifest(): ToppingRemoteResult = fetch(
        url = MANIFEST_URL,
        maxBytes = ToppingCatalogParser.MAX_MANIFEST_BYTES,
        accept = "application/json",
    )

    override fun fetchTopping(entry: ToppingCatalogEntry): ToppingRemoteResult {
        if (
            !ToppingCatalogRules.isValidId(entry.id) ||
            entry.source != ToppingCatalogRules.sourcePath(entry.id)
        ) {
            return ToppingRemoteResult.Failure(ToppingRemoteFailure.InvalidSource)
        }
        return fetch(
            url = RAW_BASE_URL + entry.source,
            maxBytes = UserScriptParser.MAX_SOURCE_BYTES,
            accept = "text/javascript, text/plain;q=0.9",
        )
    }

    private fun fetch(
        url: String,
        maxBytes: Int,
        accept: String,
    ): ToppingRemoteResult {
        val uri = runCatching { URI(url) }.getOrNull()
        if (!isTrustedRawUri(uri)) {
            return ToppingRemoteResult.Failure(ToppingRemoteFailure.InvalidSource)
        }
        val connection = runCatching {
            requireNotNull(uri).toURL().openConnection() as HttpsURLConnection
        }.getOrElse { return ToppingRemoteResult.Failure(ToppingRemoteFailure.Network) }
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code != HttpsURLConnection.HTTP_OK) {
                return ToppingRemoteResult.Failure(ToppingRemoteFailure.HttpStatus(code))
            }
            if (connection.contentLengthLong > maxBytes) {
                return ToppingRemoteResult.Failure(ToppingRemoteFailure.ResponseTooLarge)
            }
            readBounded(connection, maxBytes)
        } catch (_: Exception) {
            ToppingRemoteResult.Failure(ToppingRemoteFailure.Network)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(
        connection: HttpsURLConnection,
        maxBytes: Int,
    ): ToppingRemoteResult {
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) {
                    return ToppingRemoteResult.Failure(ToppingRemoteFailure.ResponseTooLarge)
                }
                output.write(buffer, 0, count)
            }
        }
        return ToppingRemoteResult.Success(output.toByteArray())
    }

    internal companion object {
        const val RAW_BASE_URL =
            "https://raw.githubusercontent.com/sk2andy/candy-browser-toppings/main/"
        const val MANIFEST_URL = RAW_BASE_URL + "catalog.json"
        private const val USER_AGENT = "Candy-Browser-Android"
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val READ_TIMEOUT_MILLIS = 8_000
        private const val BUFFER_BYTES = 8 * 1_024
        private const val RAW_HOST = "raw.githubusercontent.com"
        private const val RAW_PATH_PREFIX = "/sk2andy/candy-browser-toppings/main/"

        private fun isTrustedRawUri(uri: URI?): Boolean =
            uri?.scheme == "https" &&
                uri.host == RAW_HOST &&
                uri.port == -1 &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                uri.rawPath.startsWith(RAW_PATH_PREFIX) &&
                uri.rawPath.substring(RAW_PATH_PREFIX.length).let { relativePath ->
                    relativePath == "catalog.json" || TOPPING_PATH.matches(relativePath)
                }

        private val TOPPING_PATH =
            Regex("""^toppings/[a-z0-9]+(?:-[a-z0-9]+)*\.user\.js$""")
    }
}
