package dev.sk2andy.materialbrowser.data

import dev.sk2andy.materialbrowser.browser.userscript.ToppingVerifier
import dev.sk2andy.materialbrowser.browser.userscript.UserScript
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptDependencyFetcher
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParseResult
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptRejectionReason
import java.net.URI

internal sealed interface GreasyForkDownloadResult {
    data class Success(val script: UserScript) : GreasyForkDownloadResult
    data object InvalidUrl : GreasyForkDownloadResult
    data object Network : GreasyForkDownloadResult
    data object TooLarge : GreasyForkDownloadResult
    data object InvalidUtf8 : GreasyForkDownloadResult
    data class InvalidScript(val reason: UserScriptRejectionReason) : GreasyForkDownloadResult
}

internal object GreasyForkInstall {
    fun shouldOffer(url: String, sourceUrl: String?, hasUserGesture: Boolean): Boolean {
        if (!isInstallUrl(url)) return false
        if (hasUserGesture) return true
        val source = runCatching { URI(sourceUrl ?: return false) }.getOrNull() ?: return false
        return source.scheme.equals("https", ignoreCase = true) &&
            source.host.equals("greasyfork.org", ignoreCase = true)
    }

    fun isInstallUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("update.greasyfork.org", ignoreCase = true) &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            uri.rawFragment == null &&
            uri.rawPath?.let(INSTALL_PATH::matches) == true
    }

    fun fetch(
        url: String,
        fetcher: UserScriptDependencyFetcher = UserScriptDependencyClient(),
    ): GreasyForkDownloadResult {
        if (!isInstallUrl(url)) return GreasyForkDownloadResult.InvalidUrl
        val bytes = fetcher.fetch(url, UserScriptParser.MAX_SOURCE_BYTES)?.bytes
            ?: return GreasyForkDownloadResult.Network
        if (bytes.size > UserScriptParser.MAX_SOURCE_BYTES) return GreasyForkDownloadResult.TooLarge
        val source = ToppingVerifier.decodeUtf8(bytes) ?: return GreasyForkDownloadResult.InvalidUtf8
        return when (val parsed = UserScriptParser.parse(id = "greasyfork-preview", source = source)) {
            is UserScriptParseResult.Accepted -> GreasyForkDownloadResult.Success(parsed.script)
            is UserScriptParseResult.Rejected -> GreasyForkDownloadResult.InvalidScript(parsed.reason)
        }
    }

    private val INSTALL_PATH = Regex("""/scripts/[0-9]+(?:\.user\.js|/(?:[0-9]+/)?[^/]+\.user\.js)""")
}
