package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.integration.ProcessTextRules
import dev.sk2andy.materialbrowser.browser.integration.processTextResearchRequests

internal object ProcessTextResearchIntentHandler {
    fun open(context: Context, controller: BrowserController, intent: Intent): Boolean {
        if (intent.action != ProcessTextRules.ACTION_RESEARCH) return false
        val token = runCatching { intent.getStringExtra(ProcessTextRules.EXTRA_REQUEST_TOKEN) }.getOrNull()
        intent.removeExtra(ProcessTextRules.EXTRA_REQUEST_TOKEN)
        val request = processTextResearchRequests.take(token, SystemClock.elapsedRealtime())
        val eligibleProfiles = ProcessTextRules.eligibleProfiles(
            controller.profiles, controller.activeProfileId, controller.profilesEnabled,
        )
        if (request == null || eligibleProfiles.none { it.id == request.profileId }) {
            Toast.makeText(context, R.string.process_text_unavailable, Toast.LENGTH_LONG).show()
            return true
        }
        val url = ProcessTextRules.researchUrl(request.text, controller.searchEngine, controller.searxngSettings.instanceUrl)
        if (url == null || request.profileId != controller.activeProfileId && !controller.selectProfile(request.profileId)) {
            Toast.makeText(context, R.string.process_text_unavailable, Toast.LENGTH_LONG).show()
            return true
        }
        controller.dismissExternalLinkPreview()
        controller.leaveSiteCapsule()
        controller.createTab(initialUrl = url, isIncognito = false)
        return true
    }
}
