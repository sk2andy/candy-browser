package dev.sk2andy.materialbrowser.blocking

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

sealed interface CandySubscriptionResult {
    data class Preview(val sourceUrl: String, val preview: CandyRulePreview) : CandySubscriptionResult
    data class Error(val reason: String) : CandySubscriptionResult
}

object CandySubscriptionClient {
    fun fetch(sourceUrl: String): CandySubscriptionResult {
        if (!CandyRuleValidator.isSafeHttpsUrl(sourceUrl)) {
            return CandySubscriptionResult.Error("https-required")
        }
        val connection = runCatching {
            URI(sourceUrl).toURL().openConnection() as HttpURLConnection
        }.getOrElse { return CandySubscriptionResult.Error("invalid-url") }
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CandySubscriptionRules.CONNECT_TIMEOUT_MS
            connection.readTimeout = CandySubscriptionRules.READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty("User-Agent", "CandyBrowser-FilterStudio/1")
            val code = connection.responseCode
            if (code !in 200..299) return CandySubscriptionResult.Error("http-$code")
            val contentLength = connection.contentLengthLong
            if (contentLength > CandySubscriptionRules.MAX_BYTES) {
                return CandySubscriptionResult.Error("size-limit")
            }
            val body = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1_024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > CandySubscriptionRules.MAX_BYTES) {
                        return CandySubscriptionResult.Error("size-limit")
                    }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            CandySubscriptionResult.Preview(
                sourceUrl,
                CandySubscriptionRules.validatePreview(sourceUrl, body),
            )
        } catch (_: Exception) {
            CandySubscriptionResult.Error("network")
        } finally {
            connection.disconnect()
        }
    }
}
