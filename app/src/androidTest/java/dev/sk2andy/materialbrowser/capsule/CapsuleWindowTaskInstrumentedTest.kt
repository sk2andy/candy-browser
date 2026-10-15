package dev.sk2andy.materialbrowser.capsule

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.SiteCapsuleActivity
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.SiteCapsuleStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapsuleWindowTaskInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val capsuleId = "04a74ad8-7533-460c-bfbf-a135968940d5"

    @Before
    fun setUp() {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
    }

    @After
    fun tearDown() {
        SiteCapsuleStore(context).save(emptyList())
        clearPreferences()
    }

    @Test
    fun capsuleWindowGetsOwnTaskAndDoesNotReplaceMainBrowserSession() {
        ActivityScenario.launch(MainActivity::class.java).use { main ->
            var mainTaskId = -1
            var mainTabId = ""
            lateinit var capsule: SiteCapsule
            main.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                mainTaskId = activity.taskId
                mainTabId = controller.selectedTabId
                capsule = SiteCapsule(
                    id = capsuleId,
                    name = "Independent Capsule",
                    startUrl = "http://127.0.0.1:1/capsule",
                    profileId = controller.activeProfileId,
                    opensInOwnWindow = true,
                    createdAtMillis = 1L,
                    updatedAtMillis = 1L,
                )
                SiteCapsuleStore(context).save(listOf(capsule))
            }
            ActivityScenario.launch<SiteCapsuleActivity>(
                CapsuleWindowLauncher.intent(context, capsule),
            ).use { window ->
                window.onActivity { activity ->
                    val controller = activity.browserControllerForTesting()
                    assertNotEquals(mainTaskId, activity.taskId)
                    assertTrue(activity.isTaskRoot)
                    assertEquals(capsuleId, controller.activeCapsuleId)
                    assertNotEquals(mainTabId, controller.selectedTabId)
                    assertEquals(mainTabId, BrowserSessionStore(context).loadTabs().second)
                    assertEquals(
                        controller.selectedTabId,
                        BrowserSessionStore(context, capsuleId).loadTabs().second,
                    )
                }
                main.onActivity { activity ->
                    assertEquals(mainTabId, activity.browserControllerForTesting().selectedTabId)
                }
                val store = BrowserSessionStore(context)
                val (profiles, activeProfileId) = store.loadProfiles()
                store.saveProfiles(
                    profiles.map { profile ->
                        if (profile.id == capsule.profileId) {
                            profile.copy(protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded))
                        } else {
                            profile
                        }
                    },
                    activeProfileId,
                )
                val deadline = SystemClock.uptimeMillis() + 5_000L
                while (window.state != Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(50L)
                }
                assertEquals(Lifecycle.State.DESTROYED, window.state)
                assertTrue(BrowserSessionStore(context, capsuleId).loadTabs().first.isEmpty())
            }
        }
    }

    private fun clearPreferences() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            BrowserSessionStore.capsulePreferencesName(capsuleId),
            GestureOnboardingStore.PREFERENCES_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
