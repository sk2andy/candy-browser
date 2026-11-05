package dev.sk2andy.materialbrowser.browser.credentials

import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.GetCredentialRequest
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserEngineSessionFactory
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
class AndroidCredentialPromptHostInstrumentedTest {
    @Test
    @SdkSuppress(maxSdkVersion = 33)
    fun passwordRequestsAreDismissedWhileNativeAutofillRemainsEnabled() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = requireNotNull(AndroidCredentialPromptHost.create(activity))
                val identity = CredentialPromptIdentity("tab", "local", "https://example.com", 1, 1)
                var saveResult: Boolean? = null
                var selectionCompleted = false
                var selectedLogin: CredentialLogin? = CredentialLogin("test", "test")

                host.saveLogin(CredentialLoginSavePrompt(identity, CredentialLogin("test", "test"))) {
                    saveResult = it
                }
                assertEquals(false, saveResult)
                host.selectLogin(CredentialLoginSelectPrompt(identity, emptySet())) {
                    selectionCompleted = true
                    selectedLogin = it
                }

                assertTrue(selectionCompleted)
                assertNull(selectedLogin)

                val session = GeckoBrowserEngineSessionFactory(activity.applicationContext).create(
                    tabId = "credential-regular-tab",
                    profileId = "local",
                    isPrivate = false,
                    contentKind = BrowserEngineContentKind.RegularTab,
                    eventSink = BrowserEngineEventSink { },
                )
                val root = session.createView(activity)
                val view = root.findGeckoView()
                assertTrue(view.autofillEnabled)
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_YES, view.importantForAutofill)
                session.releaseView(root)
                session.execute(BrowserEngineCommands.close())
                host.close()
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 34)
    fun passwordRequestsReachCredentialManagerOnAndroid14() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val calls = mutableListOf<String>()
                val origins = mutableListOf<String?>()
                val manager = Proxy.newProxyInstance(
                    CredentialManager::class.java.classLoader,
                    arrayOf(CredentialManager::class.java),
                ) { _, method, arguments ->
                    calls += method.name
                    when (method.name) {
                        "createCredentialAsync" -> origins += (arguments[1] as CreatePasswordRequest).origin
                        "getCredentialAsync" -> origins += (arguments[1] as GetCredentialRequest).origin
                    }
                    null
                } as CredentialManager
                val host = AndroidCredentialPromptHost(activity, manager)
                val identity = CredentialPromptIdentity("tab", "local", "https://example.com", 1, 1)

                host.saveLogin(CredentialLoginSavePrompt(identity, CredentialLogin("test", "test"))) {}
                host.selectLogin(CredentialLoginSelectPrompt(identity, emptySet())) {}

                assertEquals(listOf("createCredentialAsync", "getCredentialAsync"), calls)
                assertEquals(listOf("https://example.com", "https://example.com"), origins)
                host.close()
            }
        }
    }

    private fun View.findGeckoView(): GeckoView {
        if (this is GeckoView) return this
        if (this !is ViewGroup) error("GeckoView descendant is missing")
        for (index in 0 until childCount) {
            runCatching { getChildAt(index).findGeckoView() }.getOrNull()?.let { return it }
        }
        error("GeckoView descendant is missing")
    }
}
