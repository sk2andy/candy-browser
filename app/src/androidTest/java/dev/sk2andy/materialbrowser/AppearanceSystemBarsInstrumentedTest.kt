package dev.sk2andy.materialbrowser

import android.content.Context
import android.os.SystemClock
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceSystemBarsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences("browser_session", Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun liveAppearanceChangesUpdateSystemBarIconContrast() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateAppearanceSettings(
                    AppearanceSettings(appearanceMode = BrowserAppearanceMode.Light),
                )
            }
            assertTrue(awaitLightSystemBarIcons(scenario, expectedLight = true))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateAppearanceSettings(
                    AppearanceSettings(appearanceMode = BrowserAppearanceMode.Dark),
                )
            }
            assertTrue(awaitLightSystemBarIcons(scenario, expectedLight = false))
        }
    }

    private fun awaitLightSystemBarIcons(
        scenario: ActivityScenario<MainActivity>,
        expectedLight: Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + APPEARANCE_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val matches = AtomicBoolean(false)
            scenario.onActivity { activity ->
                val controller = WindowInsetsControllerCompat(
                    activity.window,
                    activity.window.decorView,
                )
                matches.set(
                    controller.isAppearanceLightStatusBars == expectedLight &&
                        controller.isAppearanceLightNavigationBars == expectedLight,
                )
            }
            if (matches.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private companion object {
        const val APPEARANCE_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
