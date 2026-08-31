package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.RecallRepository
import dev.sk2andy.materialbrowser.recall.RecallDocument
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserControllerRecallSettingsInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val recallRepository by lazy { RecallRepository.get(context) }
    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        activityRule.scenario.onActivity { activity ->
            controller?.destroy()
            controller = null
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
        recallRepository.clearForTesting()
    }

    @Test
    fun disablingRecallDeletesStorageBeforePersistingDisabledState() {
        recallRepository.clearForTesting()
        recallRepository.index(
            RecallDocument(
                profileId = DEFAULT_PROFILE_ID,
                url = "https://example.com/",
                title = "Example",
                text = "sensitive recall phrase",
                visitedAt = 1L,
            ),
        )
        assertTrue(recallRepository.awaitIdleForTesting())

        activityRule.scenario.onActivity { activity ->
            BrowserSessionStore(activity).saveRecallEnabled(true)
            controller = BrowserController(activity).also { browserController ->
                assertTrue(browserController.isRecallEnabled)
                browserController.updateRecallEnabled(false)
                assertTrue(browserController.isRecallEnabled)
            }
        }

        val store = BrowserSessionStore(context)
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (store.loadRecallEnabled() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(25L)
        }

        assertFalse(store.loadRecallEnabled())
        assertTrue(recallRepository.awaitIdleForTesting())
        assertFalse(recallRepository.storageExistsForTesting())
        activityRule.scenario.onActivity {
            assertFalse(requireNotNull(controller).isRecallEnabled)
        }
    }
}
