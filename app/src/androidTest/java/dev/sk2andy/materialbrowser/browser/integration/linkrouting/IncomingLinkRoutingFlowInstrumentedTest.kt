package dev.sk2andy.materialbrowser.browser.integration.linkrouting

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.StartupAddressFocusMode
import dev.sk2andy.materialbrowser.browser.suggestions.SearchSuggestionProvider
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.LinkRoutingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingLinkRoutingFlowInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = BrowserSessionStore(context)
    private val rules = LinkRoutingStore(context)

    @Before
    fun setUp() {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
        store.apply {
            saveStartupAnimationEnabled(false)
            saveStartupAddressFocusMode(StartupAddressFocusMode.Never)
            saveOpenHomeOnStartupEnabled(false)
            saveSearchSuggestionProvider(SearchSuggestionProvider.None)
            saveRecallEnabled(false)
            saveProfilesEnabled(true)
            saveProfiles(listOf(BrowserProfile("personal", "🍬"), BrowserProfile("work", "💼")), "personal")
            saveExternalLinkPreviewEnabled(true)
            assertTrue(saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView))
        }
        rules.save(
            listOf(LinkRoutingRule("work-rule", "routing.invalid", target = LinkRoutingTarget(LinkRoutingTargetKind.Profile, "work"))),
        )
    }

    @After
    fun tearDown() {
        rules.save(emptyList())
        clearPreferences()
    }

    @Test
    fun incomingAndroidViewUsesConfiguredRegularProfileBeforeExternalPreview() {
        val url = "https://routing.invalid/project"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(context, MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals("work", controller.activeProfileId)
                assertEquals("work", controller.selectedTab.profileId)
                assertEquals(url, controller.selectedTab.url)
                assertFalse(controller.selectedTab.isIncognito)
                assertNull(controller.externalLinkPreviewState)
                assertEquals(1, controller.tabs.count { it.url == url })
            }
        }
    }

    private fun clearPreferences() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
