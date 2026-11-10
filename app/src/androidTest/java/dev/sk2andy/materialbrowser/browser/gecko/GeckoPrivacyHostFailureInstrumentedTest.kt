package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoPrivacyHostFailureInstrumentedTest {
    @Test
    fun privacyHostFailureDoesNotRestartNavigation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val policy = GeckoPrivacyPolicy.Disabled
        val events = mutableListOf<BrowserEngineEventType>()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val runtime = GeckoRuntimeOwner.getOrCreate(context)
            val session = runtime.createSession(
                profileId = "privacy-host-failure",
                isPrivate = false,
                privacyPolicy = policy,
            )
            val host = session.javaClass.getDeclaredField("privacyHost").apply {
                isAccessible = true
            }.get(session)
            val failure = host.javaClass.getDeclaredField("failureDescription").apply {
                isAccessible = true
            }
            try {
                GeckoBrowserEngineSessionAdapter(
                    tabId = "tab-1",
                    session = session,
                    eventSink = { event ->
                        events += event.type
                        if (event.type == BrowserEngineEventType.NavigationStarted &&
                            events.count { it == BrowserEngineEventType.NavigationStarted } == 1
                        ) {
                            session.setInlineVideoPresentation(null, false) { }
                            session.updatePrivacyPolicy(policy)
                        }
                    },
                )
                failure.set(host, "Privacy host unavailable")
                session.updatePrivacyPolicy(policy)

                assertEquals(listOf(BrowserEngineEventType.NavigationFailed), events)
            } finally {
                failure.set(host, null)
                session.close()
            }
        }
    }
}
