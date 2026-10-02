package dev.sk2andy.materialbrowser.browser

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.MainActivity
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
class InlineMediaPlayerSeekSettingsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        assertTrue(ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong()))
    }

    @After
    fun tearDown() = clearPreferences()

    @Test
    fun controllerNormalizesDirectionsAndKeepsValuesThroughOffAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(InlineMediaPlayerSeekSettings(10, 10), controller.inlineMediaPlayerSeekSettings)
                controller.updateInlineMediaPlayerSeekSettings(InlineMediaPlayerSeekSettings(15, 30))
                assertEquals(InlineMediaPlayerSeekSettings(15, 30), controller.inlineMediaPlayerSeekSettings)
                controller.updateInlineMediaPlayerMode(InlineMediaPlayerMode.Disabled)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                assertEquals(InlineMediaPlayerMode.Disabled, controller.inlineMediaPlayerMode)
                assertEquals(InlineMediaPlayerSeekSettings(15, 30), controller.inlineMediaPlayerSeekSettings)
                controller.updateInlineMediaPlayerSeekSettings(InlineMediaPlayerSeekSettings(-1, 60))
                assertEquals(InlineMediaPlayerSeekSettings(10, 60), controller.inlineMediaPlayerSeekSettings)
                controller.updateInlineMediaPlayerMode(InlineMediaPlayerMode.Automatic)
                assertEquals(InlineMediaPlayerSeekSettings(10, 60), controller.inlineMediaPlayerSeekSettings)
            }
            assertEquals(
                InlineMediaPlayerSeekSettings(10, 60),
                BrowserSessionStore(context).loadInlineMediaPlayerSeekSettings(),
            )
        }
    }

    private fun clearPreferences() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
