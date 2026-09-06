package dev.sk2andy.materialbrowser

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.ui.AddressBarTestTags
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullImmersiveModeInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences("browser_session", Context.MODE_PRIVATE)
    }
    private val onboardingPreferences by lazy {
        context.getSharedPreferences(GestureOnboardingStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
        onboardingPreferences.edit()
            .putInt(
                GestureOnboardingStore.KEY_COMPLETED_VERSION,
                GestureOnboardingStore.CURRENT_VERSION,
            )
            .commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        onboardingPreferences.edit().clear().commit()
    }

    @Test
    fun toggleHidesBarsWithoutResizingForImeAndRestoresWindowMode() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))
            val originalSoftInputMode = AtomicReference<Int>()

            scenario.onActivity { activity ->
                originalSoftInputMode.set(activity.window.attributes.softInputMode)
                activity.browserControllerForTesting().updateFullImmersiveModeEnabled(true)

                assertEquals(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                    activity.window.attributes.softInputMode.adjustmentMode(),
                )
                activity.applyFullImmersiveMode(
                    enabled = true,
                    keepWindowFullHeightForIme = true,
                )
            }
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = false))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateFullImmersiveModeEnabled(false)

                assertEquals(
                    originalSoftInputMode.get(),
                    activity.window.attributes.softInputMode,
                )
            }
            assertTrue(awaitSystemBarsVisibility(scenario, expectedVisible = true))
        }
    }

    @Test
    fun immersiveAddressEditorKeepsFullRootHeightAcrossImeTransition() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(AddressBarTestTags.Editor)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(awaitImeVisibility(scenario, expectedVisible = true))

            scenario.onActivity { activity ->
                activity.browserControllerForTesting().updateFullImmersiveModeEnabled(true)
            }
            assertTrue(awaitFullRootHeight(scenario))

            val fullRootHeight = AtomicReference<Int>()
            scenario.onActivity { activity ->
                val content = activity.findViewById<View>(android.R.id.content)
                fullRootHeight.set(content.height)
                assertEquals(
                    activity.windowManager.currentWindowMetrics.bounds.height(),
                    content.height,
                )
            }

            val editorBottomWithIme = composeRule.onNodeWithTag(AddressBarTestTags.Editor)
                .fetchSemanticsNode().boundsInRoot.bottom
            scenario.onActivity { activity ->
                val content = activity.findViewById<View>(android.R.id.content)
                val imeBottom = requireNotNull(ViewCompat.getRootWindowInsets(content))
                    .getInsets(WindowInsetsCompat.Type.ime())
                    .bottom
                assertEquals(fullRootHeight.get(), content.height)
                assertTrue(editorBottomWithIme <= content.height - imeBottom)
                WindowCompat.getInsetsController(activity.window, content)
                    .hide(WindowInsetsCompat.Type.ime())
            }
            assertTrue(awaitImeVisibility(scenario, expectedVisible = false))

            composeRule.waitUntil(timeoutMillis = VISIBILITY_TIMEOUT_MILLIS) {
                composeRule.onNodeWithTag(AddressBarTestTags.Editor)
                    .fetchSemanticsNode().boundsInRoot.bottom > editorBottomWithIme
            }
            scenario.onActivity { activity ->
                assertEquals(
                    fullRootHeight.get(),
                    activity.findViewById<View>(android.R.id.content).height,
                )
            }
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

    private fun awaitImeVisibility(
        scenario: ActivityScenario<MainActivity>,
        expectedVisible: Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + VISIBILITY_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val matchesExpectedVisibility = AtomicReference(false)
            scenario.onActivity { activity ->
                matchesExpectedVisibility.set(
                    ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == expectedVisible,
                )
            }
            if (matchesExpectedVisibility.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun awaitFullRootHeight(scenario: ActivityScenario<MainActivity>): Boolean {
        val deadline = SystemClock.uptimeMillis() + VISIBILITY_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            val rootIsFullHeight = AtomicReference(false)
            scenario.onActivity { activity ->
                rootIsFullHeight.set(
                    activity.findViewById<View>(android.R.id.content).height ==
                        activity.windowManager.currentWindowMetrics.bounds.height(),
                )
            }
            if (rootIsFullHeight.get()) return true
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private companion object {
        const val VISIBILITY_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}

private fun Int.adjustmentMode(): Int =
    this and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
