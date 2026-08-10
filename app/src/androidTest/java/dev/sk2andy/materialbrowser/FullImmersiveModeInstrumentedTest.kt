package dev.sk2andy.materialbrowser

import android.content.Context
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullImmersiveModeInstrumentedTest {
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
    fun toggleHidesAndRestoresStatusAndNavigationBars() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateFullImmersiveModeEnabled(true)
            }
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = false))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateFullImmersiveModeEnabled(false)
            }
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))
        }
    }

    private fun awaitSystemBarsVisibility(
        scenario: ActivityScenario<MainActivity>,
        expectedVisible: Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + VISIBILITY_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val matchesExpectedVisibility = AtomicReference(false)
            scenario.onActivity { activity ->
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                matchesExpectedVisibility.set(
                    insets != null &&
                        insets.isVisible(WindowInsetsCompat.Type.statusBars()) == expectedVisible &&
                        insets.isVisible(WindowInsetsCompat.Type.navigationBars()) == expectedVisible,
                )
            }
            if (matchesExpectedVisibility.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private companion object {
        const val VISIBILITY_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
