package dev.sk2andy.materialbrowser.browser.credentials

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import androidx.credentials.CredentialManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserEngineSessionFactory
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
                val view = session.createView(activity) as GeckoView

                assertTrue(view.autofillEnabled)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_YES, view.importantForAutofill)
                assertSame(activity, view.activityContextDelegate?.activityContext)

                session.releaseView(view)
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
                val view = session.createView(activity) as GeckoView

                assertEquals(false, view.autofillEnabled)
                assertEquals(
                    View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                    view.importantForAutofill,
                )

                session.releaseView(view)
                session.execute(BrowserEngineCommands.close())
            }
        }
    }
}
