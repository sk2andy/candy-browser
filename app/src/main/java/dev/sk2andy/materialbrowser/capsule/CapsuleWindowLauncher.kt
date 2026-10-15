package dev.sk2andy.materialbrowser.capsule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.SiteCapsuleActivity
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

object CapsuleWindowLauncher {
    const val ACTION_OPEN_IN_BROWSER = "dev.sk2andy.materialbrowser.action.OPEN_CAPSULE_IN_BROWSER"
    const val EXTRA_TARGET_URL = "dev.sk2andy.materialbrowser.extra.CAPSULE_TARGET_URL"

    fun intent(
        context: Context,
        capsule: SiteCapsule,
        targetUrl: String? = null,
    ): Intent =
        Intent(context, SiteCapsuleActivity::class.java)
            .setAction(CapsuleIntentRules.ACTION_OPEN_CAPSULE)
            .setData(Uri.Builder().scheme("candy-capsule").authority(capsule.id).build())
            .putExtra(CapsuleIntentRules.EXTRA_CAPSULE_ID, capsule.id)
            .putExtra(EXTRA_TARGET_URL, BrowserUriPolicy.normalizeHttpUrl(targetUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)

    fun openInBrowser(activity: Activity, capsule: SiteCapsule, targetUrl: String) {
        val safeUrl = BrowserUriPolicy.normalizeHttpUrl(targetUrl) ?: return
        activity.startActivity(
            Intent(activity, MainActivity::class.java)
                .setAction(ACTION_OPEN_IN_BROWSER)
                .putExtra(CapsuleIntentRules.EXTRA_CAPSULE_ID, capsule.id)
                .putExtra(EXTRA_TARGET_URL, safeUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }
}
