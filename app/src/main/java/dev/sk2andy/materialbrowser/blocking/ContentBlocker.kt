package dev.sk2andy.materialbrowser.blocking

import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.concurrent.CompletableFuture

class ContentBlocker(context: Context) {
    private val appContext = context.applicationContext
    private val candyDefaultRules by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BundledCandyRules.parseOrEmpty(readAssetOrEmpty("candy_default_rules.txt"))
    }
    private val requestBlocker =
        // Privacy Browser parses allow rules before block rules for the same WebView limitation:
        // https://www.stoutner.com/privacy-browser-android/filter-lists/
        RequestBlocker(
            hostRules = loadLines(
                "blocked_hosts.txt",
                "uassets_blocked_hosts.txt",
            ),
            indexedHostRules = SortedHostIndex.from(
                appContext.assets.open("easylist_blocked_hosts.txt").use { it.readBytes() },
            ),
            blockedHostPairs = loadLines("uassets_blocked_host_pairs.txt"),
            allowedHostPairs = loadLines(
                "easylist_allowed_host_pairs.txt",
                "uassets_allowed_host_pairs.txt",
            ),
        )
    private val consentCss by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadConsentCss(appContext)
    }
    private val consentScriptFuture by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CompletableFuture.supplyAsync {
            ConsentBlockerScript.create(
                cssBytes = consentCss,
                siteRules = candyDefaultRules.cookieCosmeticRules,
            )
        }
    }
    val consentScript: String
        get() = consentScriptFuture.join()
    val consentRemovalScript: String = ConsentBlockerScript.removalScript

    fun prepareConsentScript() {
        consentScriptFuture
    }

    fun consentScriptIfReady(): String? = if (consentScriptFuture.isDone) {
        runCatching { consentScriptFuture.getNow("") }.getOrNull()?.takeIf(String::isNotEmpty)
    } else {
        null
    }

    fun onConsentScriptReady(action: (String) -> Unit) {
        consentScriptFuture.thenAccept { script ->
            if (script.isNotEmpty()) action(script)
        }
    }

    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean =
        requestBlocker.shouldBlock(requestUrl, pageUrl)

    fun shouldBlockHosts(requestHost: String?, pageHost: String?): Boolean =
        requestBlocker.shouldBlockHosts(requestHost, pageHost)

    fun adCosmeticSelectors(pageUrl: String?): List<String> =
        candyDefaultRules.adCosmeticSelectors(pageUrl)

    val adCosmeticRules: List<CandyRule>
        get() = candyDefaultRules.adCosmeticRules

    private fun loadLines(vararg assetNames: String): Sequence<String> = sequence {
        assetNames.forEach { assetName ->
            appContext.assets.open(assetName).bufferedReader().use { reader ->
                yieldAll(reader.lineSequence())
            }
        }
    }

    private fun readAssetOrEmpty(assetName: String): String = runCatching {
        appContext.assets.open(assetName).bufferedReader().use { it.readText() }
    }.getOrDefault("")

    private fun loadConsentCss(context: Context): ByteArray = ByteArrayOutputStream().use { output ->
        listOf("easylist_cookie_banner.css", "cookie_banner_overrides.css").forEach { assetName ->
            context.assets.open(assetName).use { it.copyTo(output) }
            output.write('\n'.code)
        }
        output.toByteArray()
    }
}
