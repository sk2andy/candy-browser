package dev.sk2andy.materialbrowser.browser.integration

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.HistoryClearRequest
import dev.sk2andy.materialbrowser.data.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryActivityContractInstrumentedTest {
    @Test
    fun navigationAndClearRequestsRoundTripTogether() {
        val entry = HistoryEntry(
            url = "https://example.com/",
            title = "Example",
            lastVisitedAt = 1L,
            profileId = "personal",
        )
        val requests = listOf(
            HistoryClearRequest(
                profileIds = setOf("personal", "work"),
                sinceInclusiveMillis = 10L,
                untilExclusiveMillis = 20L,
            ),
            HistoryClearRequest(
                profileIds = setOf("work"),
                sinceInclusiveMillis = 30L,
                untilExclusiveMillis = 40L,
            ),
        )

        val result = HistoryActivityContract.resultIntent(entry, requests)

        assertEquals(
            HistoryNavigationRequest(entry.url, entry.profileId),
            HistoryActivityContract.navigationRequestFrom(result),
        )
        assertEquals(requests, HistoryActivityContract.clearRequestsFrom(result))
    }

    @Test
    fun clearRequestsRoundTripThroughSavedInstanceState() {
        val request = HistoryClearRequest(
            profileIds = setOf("personal", "work"),
            sinceInclusiveMillis = 10L,
            untilExclusiveMillis = 20L,
        )
        val savedInstanceState = Bundle()

        HistoryActivityContract.saveClearRequests(savedInstanceState, listOf(request))

        assertEquals(
            listOf(request),
            HistoryActivityContract.clearRequestsFrom(savedInstanceState),
        )
    }
}
