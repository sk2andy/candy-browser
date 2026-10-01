package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession

@RunWith(AndroidJUnit4::class)
class GeckoBootstrapHistoryInstrumentedTest {
    @Test
    fun webPageStartSupersedingBootstrapStartStillPublishesItsCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val session = GeckoRuntimeOwner.getOrCreate(context).createSession(
                profileId = "bootstrap-progress-regression",
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy.Disabled,
            )
            try {
                var latestState: GeckoBrowserSessionState? = null
                session.setStateListener { state -> latestState = state }
                val nativeSession = nativeSession(session)
                val progress = requireNotNull(nativeSession.progressDelegate)
                progress.onPageStart(nativeSession, BOOTSTRAP_URL)
                progress.onPageStart(nativeSession, "https://example.com/current")
                assertTrue(requireNotNull(latestState).isLoading)

                // Gecko's StateTracker overwrites the active start and sends one final stop.
                progress.onPageStop(nativeSession, true)

                val completed = requireNotNull(latestState)
                assertEquals("https://example.com/current", completed.url)
                assertFalse(completed.isLoading)
                assertEquals(true, completed.lastNavigationSucceeded)
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun legacySnapshotWithBootstrapBeforeCurrentWebPageIsRejectedForRestoreAndPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacyState = requireNotNull(
            GeckoSession.SessionState.fromString(
                """
                {"history":{"index":2,"entries":[
                  {"url":"$BOOTSTRAP_URL","title":""},
                  {"url":"https://example.com/current","title":"Current"}
                ]}}
                """.trimIndent(),
            ),
        )
        assertEquals(1, legacyState.currentIndex)
        assertEquals("https://example.com/current", legacyState[legacyState.currentIndex].uri)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val session = GeckoRuntimeOwner.getOrCreate(context).createSession(
                profileId = "bootstrap-history-regression",
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy.Disabled,
            )
            try {
                assertFalse(session.restoreSessionState(requireNotNull(legacyState.toString())))
                val nativeSession = nativeSession(session)
                requireNotNull(nativeSession.historyDelegate)
                    .onHistoryStateChange(nativeSession, legacyState)
                assertNull(session.sessionStateSnapshot())
            } finally {
                session.close()
            }
        }
    }

    private fun nativeSession(session: GeckoBrowserSession): GeckoSession =
        session.javaClass.getDeclaredField("session")
            .apply { isAccessible = true }
            .get(session) as GeckoSession

    private companion object {
        const val BOOTSTRAP_URL =
            "moz-extension://5e6344e7-68a0-4a80-863c-0123456789ab/" +
                "bootstrap.html?token=11111111-2222-4333-8444-555555555555"
    }
}
