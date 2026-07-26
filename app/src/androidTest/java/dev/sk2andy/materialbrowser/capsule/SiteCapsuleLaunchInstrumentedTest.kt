package dev.sk2andy.materialbrowser.capsule

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.WebViewProfileRules
import dev.sk2andy.materialbrowser.browser.WebViewProfileAssignment
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.SiteCapsuleStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteCapsuleLaunchInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @After
    fun clearState() {
        SiteCapsuleStore(context).save(emptyList())
        context.getSharedPreferences("browser_session", 0).edit().clear().commit()
    }

    @Test
    fun launcherIntentContainsOnlyOpaqueIdAndExplicitCandyComponent() {
        val capsule = capsule()
        val intent = CapsuleShortcutPublisher(context).launchIntent(capsule.id)

        assertEquals(CapsuleIntentRules.ACTION_OPEN_CAPSULE, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(capsule.id, intent.getStringExtra(CapsuleIntentRules.EXTRA_CAPSULE_ID))
        assertNull(intent.data)
        assertEquals(setOf(CapsuleIntentRules.EXTRA_CAPSULE_ID), intent.extras?.keySet())
        assertFalse(intent.toUri(0).contains(capsule.startUrl))
        assertFalse(intent.toUri(0).contains(capsule.profileId))
    }

    @Test
    fun launcherIntentOpensRealCapsuleWithAssignedWebViewProfile() {
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        val profile = BrowserProfile("capsule-work", "💼", isolationEnabled = true)
        val capsule = capsule()
        seed(capsule, profile)

        ActivityScenario.launch<MainActivity>(CapsuleShortcutPublisher(context).launchIntent(capsule.id))
            .use { scenario ->
                onView(isAssignableFrom(WebView::class.java)).check(matches(isDisplayed()))
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(capsule.id, controller.activeCapsuleId)
                    assertEquals(profile.id, controller.selectedTab.profileId)
                    assertFalse(controller.selectedTab.isIncognito)
                    val webView = findWebView(activity.window.decorView)
                        ?: error("Capsule WebView not attached")
                    if (supported) {
                        assertEquals(
                            WebViewProfileRules.isolatedProfileName(profile.id),
                            WebViewCompat.getProfile(webView).name,
                        )
                    } else {
                        assertEquals(
                            WebViewProfileAssignment.Default,
                            WebViewProfileRules.assignment(
                                tab = controller.selectedTab,
                                profiles = listOf(profile),
                                multiProfileSupported = false,
                            ),
                        )
                    }
                }
            }
    }

    @Test
    fun unknownLauncherIdOpensNormalNonIncognitoHome() {
        val capsule = capsule()
        seed(capsule, BrowserProfile(capsule.profileId, "💼"))
        val intent = CapsuleShortcutPublisher(context)
            .launchIntent("5505f282-1644-46bb-b87b-5e371380305d")

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertNull(controller.activeCapsuleId)
                assertFalse(controller.selectedTab.isIncognito)
                assertTrue(controller.selectedTab.isFreshBlankForTest())
            }
        }
    }

    @Test
    fun capsuleTabBindingSurvivesActivityRecreation() {
        val capsule = capsule().copy(startUrl = "https://example.com/start")
        val profile = BrowserProfile(capsule.profileId, "💼")
        seed(capsule, profile)

        ActivityScenario.launch<MainActivity>(CapsuleShortcutPublisher(context).launchIntent(capsule.id))
            .use { scenario ->
                lateinit var originalTabId: String
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    originalTabId = controller.selectedTabId
                    controller.submitAddress("https://example.com/inside")
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertEquals(capsule.id, controller.activeCapsuleId)
                    assertEquals(originalTabId, controller.selectedTabId)
                }
            }
    }

    private fun capsule() = SiteCapsule(
        id = "04a74ad8-7533-460c-bfbf-a135968940d5",
        name = "Mail",
        startUrl = "https://mail.example",
        profileId = "capsule-work",
        ownsDedicatedProfile = true,
        isolatedStorageRequested = true,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private fun seed(capsule: SiteCapsule, profile: BrowserProfile) {
        SiteCapsuleStore(context).save(listOf(capsule))
        val tab = BrowserTab(
            id = "1ca2f4ee-511a-4591-9772-c59d86051a54",
            lastAccessedAt = 1L,
            profileId = profile.id,
        )
        BrowserSessionStore(context).apply {
            saveProfiles(listOf(profile.copy(selectedTabId = tab.id)), profile.id)
            saveTabs(listOf(tab), tab.id)
        }
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { index ->
            findWebView(view.getChildAt(index))
        }
        else -> null
    }

    private fun BrowserTab.isFreshBlankForTest(): Boolean =
        url == "about:blank" && title.isBlank() && !isIncognito
}
