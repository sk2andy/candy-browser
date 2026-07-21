package dev.sk2andy.materialbrowser.blocking

import android.content.Context
import java.io.ByteArrayOutputStream

class ContentBlocker(context: Context) {
    private val appContext = context.applicationContext
    private val requestBlocker by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        // Privacy Browser parses allow rules before block rules for the same WebView limitation:
        // https://www.stoutner.com/privacy-browser-android/filter-lists/
        RequestBlocker(
            hostRules = loadLines("blocked_hosts.txt", "easylist_blocked_hosts.txt"),
            allowedHostPairs = loadLines("easylist_allowed_host_pairs.txt"),
        )
    }
    val consentScript: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConsentBlockerScript.create(loadConsentCss(appContext))
    }
    val consentCleanupScript: String = ConsentBlockerScript.cleanupScript
    val consentRemovalScript: String = ConsentBlockerScript.removalScript

    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean =
        requestBlocker.shouldBlock(requestUrl, pageUrl)

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
}
