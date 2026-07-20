package dev.sk2andy.materialbrowser.blocking

import android.content.Context
import java.io.ByteArrayOutputStream

class ContentBlocker(context: Context) {
    private val requestBlocker = RequestBlocker(
        context.assets.open("blocked_hosts.txt").bufferedReader().useLines { it.toList().asSequence() },
    )
    val consentScript: String = ConsentBlockerScript.create(loadConsentCss(context))
    val consentCleanupScript: String = ConsentBlockerScript.cleanupScript
    val consentRemovalScript: String = ConsentBlockerScript.removalScript

    fun shouldBlock(requestUrl: String, pageUrl: String?): Boolean =
        requestBlocker.shouldBlock(requestUrl, pageUrl)

    private fun loadConsentCss(context: Context): ByteArray = ByteArrayOutputStream().use { output ->
        listOf("easylist_cookie_banner.css", "cookie_banner_overrides.css").forEach { assetName ->
            context.assets.open(assetName).use { it.copyTo(output) }
            output.write('\n'.code)
        }
        output.toByteArray()
    }
}
