package dev.sk2andy.materialbrowser.blocking

import android.content.Context
import java.io.ByteArrayOutputStream

class ContentBlocker(context: Context) {
    private val appContext = context.applicationContext
    private val requestBlocker by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        // Privacy Browser parses allow rules before block rules for the same WebView limitation:
        // https://www.stoutner.com/privacy-browser-android/filter-lists/
        RequestBlocker(
            hostRules = loadLines(
                "blocked_hosts.txt",
                "easylist_blocked_hosts.txt",
                "uassets_blocked_hosts.txt",
            ),
            blockedHostPairs = loadLines("uassets_blocked_host_pairs.txt"),
            allowedHostPairs = loadLines(
                "easylist_allowed_host_pairs.txt",
                "uassets_allowed_host_pairs.txt",
            ),
        )
    }
    private val consentCss by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadConsentCss(appContext)
    }
    private val consentScripts = linkedMapOf<List<String>, String>()
    val consentScript: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConsentBlockerScript.create(consentCss)
    }
    val consentCleanupScript: String = ConsentBlockerScript.cleanupScript
    val consentRemovalScript: String = ConsentBlockerScript.removalScript

    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean =
        requestBlocker.shouldBlock(requestUrl, pageUrl)

    @Synchronized
    fun consentScriptFor(pausedHosts: Collection<String>): String {
        val key = pausedHosts.asSequence()
            .mapNotNull(PrivacyRequestSanitizer::normalizeHost)
            .distinct()
            .sorted()
            .toList()
        consentScripts[key]?.let { return it }
        return ConsentBlockerScript.create(consentCss, key).also { script ->
            if (consentScripts.size >= MAX_CONSENT_SCRIPT_VARIANTS) {
                consentScripts.remove(consentScripts.keys.first())
            }
            consentScripts[key] = script
        }
    }

    private fun loadLines(vararg assetNames: String): Sequence<String> = buildList {
        assetNames.forEach { assetName ->
            appContext.assets.open(assetName).bufferedReader().useLines { lines ->
                lines.forEach(::add)
            }
        }
    }.asSequence()

    private fun loadConsentCss(context: Context): ByteArray = ByteArrayOutputStream().use { output ->
        listOf("easylist_cookie_banner.css", "cookie_banner_overrides.css").forEach { assetName ->
            context.assets.open(assetName).use { it.copyTo(output) }
            output.write('\n'.code)
        }
        output.toByteArray()
    }

    private companion object {
        const val MAX_CONSENT_SCRIPT_VARIANTS = 24
    }
}
