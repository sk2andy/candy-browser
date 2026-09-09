package dev.sk2andy.materialbrowser.browser.credentials

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserEngineSessionFactory
import dev.sk2andy.materialbrowser.browser.gecko.GeckoRuntimeOwner
import dev.sk2andy.materialbrowser.browser.gecko.GeckoViewRuntimeHandle
import dev.sk2andy.materialbrowser.browser.gecko.GeckoWebAuthnActivityDelegate
import dev.sk2andy.materialbrowser.browser.gecko.GeckoWebAuthnResultTestActivity
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.InactiveTabLifetime
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoCredentialsInstrumentedTest {
    @Test
    fun bundlesCredentialManagerAndBrowserOriginPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertNotNull(CredentialManager.create(context))
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(
                Manifest.permission.CREDENTIAL_MANAGER_QUERY_CANDIDATE_CREDENTIALS,
            ),
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.CREDENTIAL_MANAGER_SET_ORIGIN),
        )
    }

    @Test
    fun regularGeckoViewExposesNativeAutofillAndActivityContext() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val session = GeckoBrowserEngineSessionFactory(activity.applicationContext).create(
                    tabId = "credential-regular-tab",
                    profileId = "local",
                    isPrivate = false,
                    eventSink = BrowserEngineEventSink { },
                )
                val root = session.createView(activity)
                val view = root.findGeckoView()

                assertTrue(view.autofillEnabled)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_YES, view.importantForAutofill)
                assertSame(activity, view.activityContextDelegate?.activityContext)

                session.releaseView(root)
                session.execute(BrowserEngineCommands.close())
            }
        }
    }

    @Test
    fun privateGeckoViewDoesNotExposePasswordAutofillNodes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val session = GeckoBrowserEngineSessionFactory(activity.applicationContext).create(
                    tabId = "credential-private-tab",
                    profileId = "private",
                    isPrivate = true,
                    eventSink = BrowserEngineEventSink { },
                )
                val root = session.createView(activity)
                val view = root.findGeckoView()

                assertEquals(false, view.autofillEnabled)
                assertEquals(
                    View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                    view.importantForAutofill,
                )

                session.releaseView(root)
                session.execute(BrowserEngineCommands.close())
            }
        }
    }

    @Test
    fun mainActivityBindsAndUnbindsGeckoWebAuthnDelegate() {
        lateinit var runtime: GeckoViewRuntimeHandle

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                runtime = GeckoRuntimeOwner.getOrCreate(activity) as GeckoViewRuntimeHandle
                assertNotNull(runtime.webAuthnActivityDelegateForTesting())
            }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNull(runtime.webAuthnActivityDelegateForTesting())
        }
    }

    @Test
    fun mainActivityCompletesGeckoWebAuthnPendingIntentRoundTrip() {
        lateinit var result: GeckoResult<Intent>
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BrowserSessionStore(context)
        val originalEngine = store.loadAndroidBrowserEngineKind()
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        store.saveStartupAnimationEnabled(false)
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())

        try {
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                ),
            ).use { scenario ->
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(AndroidBrowserEngineKind.GeckoView, controller.browserEngineKind)
                    assertTrue(controller.openUrl(WEB_AUTHN_FIXTURE_URL))
                }
                awaitSelectedGeckoView(scenario)
                scenario.onActivity { activity ->
                    val runtime = GeckoRuntimeOwner.getOrCreate(activity) as GeckoViewRuntimeHandle
                    val delegate = checkNotNull(runtime.webAuthnActivityDelegateForTesting())
                    val request = PendingIntent.getActivity(
                        activity,
                        0,
                        Intent(activity, GeckoWebAuthnResultTestActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                    result = checkNotNull(delegate.onStartActivityForResult(request))
                }

                val response = checkNotNull(result.poll(5_000))
                assertEquals(
                    GeckoWebAuthnResultTestActivity.RESULT_VALUE,
                    response.getStringExtra(GeckoWebAuthnResultTestActivity.EXTRA_RESULT),
                )
            }
        } finally {
            store.saveAndroidBrowserEngineKind(originalEngine)
        }
    }

    @Test
    fun geckoWebAuthnDelegateReturnsSuccessfulActivityResult() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        var launched: PendingIntent? = null
        val delegate = GeckoWebAuthnActivityDelegate { pendingIntent -> launched = pendingIntent }
        val result = delegate.onStartActivityForResult(request)
        val response = Intent("dev.sk2andy.materialbrowser.TEST_WEBAUTHN_RESULT")

        delegate.onHostResumed()
        delegate.onActivityResult(Activity.RESULT_OK, response)

        assertSame(request, launched)
        assertSame(response, result.poll(0))
    }

    @Test
    fun webAuthnResultWaitsForResumedHostAndAcceptsNullSuccessData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val delegate = GeckoWebAuthnActivityDelegate { }
        val result = delegate.onStartActivityForResult(request)

        delegate.onActivityResult(Activity.RESULT_OK, null)
        assertNotNull(runCatching { result.poll(0) }.exceptionOrNull())

        delegate.onHostResumed()
        assertNull(result.poll(0))
    }

    @Test
    fun webAuthnResultRejectsIdentityThatBecomesStaleAfterHostResumes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        var isCurrent = true
        val delegate = GeckoWebAuthnActivityDelegate(
            isPendingRequestCurrent = { isCurrent },
            launch = {},
        )
        val result = delegate.onStartActivityForResult(request)

        delegate.onHostResumed()
        isCurrent = false
        delegate.onActivityResult(Activity.RESULT_OK, null)

        assertTrue(runCatching { result.poll(0) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun webAuthnBufferedResultRejectsIdentityThatBecomesStaleBeforeResume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        var isCurrent = true
        val delegate = GeckoWebAuthnActivityDelegate(
            isPendingRequestCurrent = { isCurrent },
            launch = {},
        )
        val result = delegate.onStartActivityForResult(request)

        delegate.onActivityResult(Activity.RESULT_OK, null)
        isCurrent = false
        delegate.onHostResumed()

        assertTrue(runCatching { result.poll(0) }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun mainActivityPreservesWebAuthnTabWhileProviderStopsBrowser() {
        lateinit var result: GeckoResult<Intent>
        lateinit var originalTabId: String
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BrowserSessionStore(context)
        val originalEngine = store.loadAndroidBrowserEngineKind()
        val originalLifetime = store.loadInactiveTabLifetime()
        store.saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.GeckoView)
        store.saveStartupAnimationEnabled(false)
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        val activityIntent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )

        try {
            ActivityScenario.launch<MainActivity>(activityIntent).use { scenario ->
                scenario.recreate()
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(
                        AndroidBrowserEngineKind.GeckoView,
                        controller.browserEngineKind,
                    )
                    assertTrue(controller.openUrl(WEB_AUTHN_FIXTURE_URL))
                }
                awaitSelectedGeckoView(scenario)
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    controller.updateInactiveTabLifetime(InactiveTabLifetime.Immediately)
                    originalTabId = controller.selectedTabId
                    val runtime = GeckoRuntimeOwner.getOrCreate(activity) as GeckoViewRuntimeHandle
                    val delegate = checkNotNull(runtime.webAuthnActivityDelegateForTesting())
                    val request = PendingIntent.getActivity(
                        activity,
                        1,
                        Intent(activity, GeckoWebAuthnResultTestActivity::class.java)
                            .putExtra(GeckoWebAuthnResultTestActivity.EXTRA_DELAY_MILLIS, 750L)
                            .putExtra(GeckoWebAuthnResultTestActivity.EXTRA_NULL_RESULT, true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    result = checkNotNull(delegate.onStartActivityForResult(request))
                }

                assertNull(result.poll(5_000))
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(originalTabId, controller.selectedTabId)
                    assertTrue(controller.tabs.any { tab -> tab.id == originalTabId })
                    assertTrue(
                        requireNotNull(controller.selectedGeckoViewForTesting()).isAttachedToWindow,
                    )
                    controller.updateInactiveTabLifetime(originalLifetime)
                }
            }
        } finally {
            store.saveInactiveTabLifetime(originalLifetime)
            store.saveAndroidBrowserEngineKind(originalEngine)
        }
    }

    private fun awaitSelectedGeckoView(scenario: ActivityScenario<MainActivity>) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            var ready = false
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                ready = !controller.selectedTab.isLoading &&
                    controller.selectedGeckoViewForTesting()?.isAttachedToWindow == true
            }
            if (ready) return
            SystemClock.sleep(50L)
        }
        assertTrue("Selected GeckoView did not attach", false)
    }

    @Test
    fun closingGeckoWebAuthnDelegateRejectsPendingRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val delegate = GeckoWebAuthnActivityDelegate { }
        val result = delegate.onStartActivityForResult(request)

        delegate.close()

        val failure = runCatching { result.poll(0) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    private fun View.findGeckoView(): GeckoView {
        if (this is GeckoView) return this
        if (this !is ViewGroup) error("GeckoView descendant is missing")
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            runCatching { child.findGeckoView() }.getOrNull()?.let { return it }
        }
        error("GeckoView descendant is missing")
    }

    private companion object {
        const val WEB_AUTHN_FIXTURE_URL = "https://example.com/passkey-fixture"
    }
}
