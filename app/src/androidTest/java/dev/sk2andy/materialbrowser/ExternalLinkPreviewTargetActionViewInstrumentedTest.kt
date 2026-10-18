package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileProtectionSession
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalLinkPreviewTargetActionViewInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store = BrowserSessionStore(context)
    private val preferences = context.getSharedPreferences(
        BrowserSessionStore.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        store.saveStartupAnimationEnabled(false)
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        ProfileProtectionSession.forget("home")
        ProfileProtectionSession.forget("work")
    }

    @After
    fun tearDown() {
        ProfileProtectionSession.forget("home")
        ProfileProtectionSession.forget("work")
        preferences.edit().clear().commit()
    }

    @Test
    fun actionViewReusesSavedPreviewProfileAcrossRepeatedLinksAndActiveProfileSwitches() {
        store.saveProfiles(testProfiles(), activeProfileId = "home")
        store.saveExternalLinkPreviewEnabled(true)
        store.saveExternalLinkPreviewProfileId("work")

        val firstActivity = startExternalView(FIRST_URL, clearTask = true)
        try {
            var controller = firstActivity.browserControllerForTesting()
            assertEquals("work", controller.externalLinkPreviewState?.targetProfileId)
            assertEquals("home", controller.activeProfileId)

            instrumentation.runOnMainSync {
                assertTrue(controller.selectProfile("work"))
                assertTrue(controller.selectProfile("home"))
            }
            assertEquals("home", controller.activeProfileId)

            instrumentation.runOnMainSync {
                firstActivity.startActivity(
                    externalViewIntent(SECOND_URL)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
            awaitPreviewUrl(firstActivity, SECOND_URL)
            controller = firstActivity.browserControllerForTesting()
            assertEquals(SECOND_URL, controller.externalLinkPreviewState?.currentUrl)
            assertEquals("work", controller.externalLinkPreviewState?.targetProfileId)
            assertEquals("home", controller.activeProfileId)
            assertEquals("work", BrowserSessionStore(context).loadExternalLinkPreviewProfileId())
        } finally {
            instrumentation.runOnMainSync { firstActivity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun actionViewFallsBackToActiveProfileWhenSavedTargetWasDeleted() {
        store.saveProfiles(testProfiles().take(1), activeProfileId = "home")
        store.saveExternalLinkPreviewEnabled(true)
        store.saveExternalLinkPreviewProfileId("deleted")

        val activity = startExternalView(FIRST_URL, clearTask = true)
        try {
            val controller = activity.browserControllerForTesting()
            assertEquals("home", controller.externalLinkPreviewState?.targetProfileId)
            assertEquals("deleted", store.loadExternalLinkPreviewProfileId())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun startExternalView(url: String, clearTask: Boolean = false): MainActivity {
        return instrumentation.startActivitySync(externalViewIntent(url, clearTask)) as MainActivity
    }

    private fun externalViewIntent(url: String, clearTask: Boolean = false): Intent {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or if (clearTask) {
            Intent.FLAG_ACTIVITY_CLEAR_TASK
        } else {
            0
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(context.packageName)
            .addFlags(flags)
    }

    private fun awaitPreviewUrl(activity: MainActivity, url: String) {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        var observed = false
        while (!observed && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync {
                observed = activity.browserControllerForTesting()
                    .externalLinkPreviewState?.currentUrl == url
            }
            if (!observed) SystemClock.sleep(20L)
        }
        assertTrue("Expected external preview for $url", observed)
    }

    private fun testProfiles() = listOf(
        BrowserProfile(id = "home", emoji = "🏠"),
        BrowserProfile(id = "work", emoji = "💼"),
    )

    private companion object {
        const val FIRST_URL = "https://example.com/issue-309/first"
        const val SECOND_URL = "https://example.com/issue-309/second"
    }
}
