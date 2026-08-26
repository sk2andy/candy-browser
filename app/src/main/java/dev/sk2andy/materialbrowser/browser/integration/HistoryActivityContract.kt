package dev.sk2andy.materialbrowser.browser.integration

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.sk2andy.materialbrowser.HistoryActivity
import dev.sk2andy.materialbrowser.data.HistoryClearRequest
import dev.sk2andy.materialbrowser.data.HistoryEntry

internal data class HistoryNavigationRequest(
    val url: String,
    val profileId: String,
)

internal object HistoryActivityContract {
    private const val EXTRA_URL = "history_url"
    private const val EXTRA_PROFILE_ID = "history_profile_id"
    private const val EXTRA_CLEAR_REQUESTS = "history_clear_requests"
    private const val EXTRA_CLEAR_PROFILE_IDS = "profile_ids"
    private const val EXTRA_CLEAR_SINCE = "since"
    private const val EXTRA_CLEAR_UNTIL = "until"

    fun launchIntent(context: Context): Intent = Intent(context, HistoryActivity::class.java)

    fun resultIntent(
        entry: HistoryEntry? = null,
        clearRequests: Collection<HistoryClearRequest> = emptyList(),
    ): Intent = Intent().apply {
        entry?.let { selected ->
            putExtra(EXTRA_URL, selected.url)
            putExtra(EXTRA_PROFILE_ID, selected.profileId)
        }
        putParcelableArrayListExtra(EXTRA_CLEAR_REQUESTS, encodeClearRequests(clearRequests))
    }

    fun saveClearRequests(
        outState: Bundle,
        clearRequests: Collection<HistoryClearRequest>,
    ) {
        outState.putParcelableArrayList(EXTRA_CLEAR_REQUESTS, encodeClearRequests(clearRequests))
    }

    fun navigationRequestFrom(intent: Intent?): HistoryNavigationRequest? {
        intent ?: return null
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf(String::isNotBlank) ?: return null
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return HistoryNavigationRequest(url = url, profileId = profileId)
    }

    @Suppress("DEPRECATION")
    fun clearRequestsFrom(intent: Intent?): List<HistoryClearRequest> = intent
        ?.getParcelableArrayListExtra<Bundle>(EXTRA_CLEAR_REQUESTS)
        .toClearRequests()

    @Suppress("DEPRECATION")
    fun clearRequestsFrom(savedInstanceState: Bundle?): List<HistoryClearRequest> =
        savedInstanceState
            ?.getParcelableArrayList<Bundle>(EXTRA_CLEAR_REQUESTS)
            .toClearRequests()

    private fun encodeClearRequests(
        clearRequests: Collection<HistoryClearRequest>,
    ): ArrayList<Bundle> = ArrayList(clearRequests.map { request ->
        Bundle().apply {
            putStringArrayList(EXTRA_CLEAR_PROFILE_IDS, ArrayList(request.profileIds))
            putLong(EXTRA_CLEAR_SINCE, request.sinceInclusiveMillis)
            putLong(EXTRA_CLEAR_UNTIL, request.untilExclusiveMillis)
        }
    })

    private fun List<Bundle>?.toClearRequests(): List<HistoryClearRequest> = orEmpty()
        .mapNotNull { bundle ->
            val profileIds = bundle.getStringArrayList(EXTRA_CLEAR_PROFILE_IDS)
                ?.filterTo(linkedSetOf(), String::isNotBlank)
                .orEmpty()
            val since = bundle.getLong(EXTRA_CLEAR_SINCE, Long.MIN_VALUE)
            val until = bundle.getLong(EXTRA_CLEAR_UNTIL, Long.MIN_VALUE)
            if (profileIds.isEmpty() || since >= until) return@mapNotNull null
            HistoryClearRequest(
                profileIds = profileIds,
                sinceInclusiveMillis = since,
                untilExclusiveMillis = until,
            )
        }
}
