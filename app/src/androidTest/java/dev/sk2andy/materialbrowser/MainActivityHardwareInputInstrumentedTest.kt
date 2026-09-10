package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserHardwareInputRules
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.data.ReleaseNotesStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityHardwareInputInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).apply {
            saveStartupAnimationEnabled(false)
            saveAndroidBrowserEngineKind(AndroidBrowserEngineKind.SystemWebView)
        }
        ReleaseNotesStore(context).markHandled(BuildConfig.VERSION_CODE.toLong())
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun tabShortcutsCycleCreateCloseAndConsumeRepeats() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            scenario.onActivity { activity ->
                val controller = activity.browserControllerForTesting()
                val firstTabId = controller.selectedTabId
                val secondTabId = controller.createTab()
                controller.selectTab(firstTabId)
                val expectedNextTabId = requireNotNull(
                    BrowserHardwareInputRules.adjacentTabId(
                        tabIds = controller.activeTabs.map { tab -> tab.id },
                        selectedTabId = firstTabId,
                        forward = true,
                    ),
                )

                assertTrue(activity.dispatchKeyEvent(keyDown(KeyEvent.KEYCODE_TAB)))
                assertEquals(expectedNextTabId, controller.selectedTabId)
                assertTrue(activity.dispatchKeyEvent(keyUp(KeyEvent.KEYCODE_TAB)))

                val tabCountBeforeCreate = controller.tabs.size
                assertTrue(activity.dispatchKeyEvent(keyDown(KeyEvent.KEYCODE_T)))
                val createdTabId = controller.selectedTabId
                assertEquals(tabCountBeforeCreate + 1, controller.tabs.size)
                assertTrue(activity.dispatchKeyEvent(keyDown(KeyEvent.KEYCODE_T, repeatCount = 1)))
                assertEquals(tabCountBeforeCreate + 1, controller.tabs.size)
                assertTrue(activity.dispatchKeyEvent(keyUp(KeyEvent.KEYCODE_T)))

                assertTrue(activity.dispatchKeyEvent(keyDown(KeyEvent.KEYCODE_W)))
                assertFalse(controller.tabs.any { tab -> tab.id == createdTabId })
                assertTrue(activity.dispatchKeyEvent(keyUp(KeyEvent.KEYCODE_W)))
                assertTrue(controller.tabs.any { tab -> tab.id == secondTabId })
            }
        }
    }

    @Test
    fun mouseBackButtonPressIsConsumedWithoutHistory() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).setAction(TEST_ACTIVITY_ACTION),
        ).use { scenario ->
            scenario.onActivity { activity ->
                val event = mouseButtonEvent(
                    action = MotionEvent.ACTION_BUTTON_PRESS,
                    buttonState = MotionEvent.BUTTON_BACK,
                )
                try {
                    assertTrue(activity.dispatchGenericMotionEvent(event))
                } finally {
                    event.recycle()
                }
                val release = mouseButtonEvent(
                    action = MotionEvent.ACTION_BUTTON_RELEASE,
                    buttonState = 0,
                )
                try {
                    assertTrue(activity.dispatchGenericMotionEvent(release))
                } finally {
                    release.recycle()
                }
            }
        }
    }

    private fun keyDown(keyCode: Int, repeatCount: Int = 0): KeyEvent {
        val eventTime = SystemClock.uptimeMillis()
        return KeyEvent(
            eventTime,
            eventTime,
            KeyEvent.ACTION_DOWN,
            keyCode,
            repeatCount,
            KeyEvent.META_CTRL_ON,
        )
    }

    private fun keyUp(keyCode: Int): KeyEvent {
        val eventTime = SystemClock.uptimeMillis()
        return KeyEvent(
            eventTime,
            eventTime,
            KeyEvent.ACTION_UP,
            keyCode,
            0,
            KeyEvent.META_CTRL_ON,
        )
    }

    private fun mouseButtonEvent(action: Int, buttonState: Int): MotionEvent {
        val eventTime = SystemClock.uptimeMillis()
        return MotionEvent.obtain(
            eventTime,
            eventTime,
            action,
            1,
            arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_MOUSE
                },
            ),
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    x = 1f
                    y = 1f
                },
            ),
            0,
            buttonState,
            1f,
            1f,
            1,
            0,
            InputDevice.SOURCE_MOUSE,
            0,
        )
    }

    private companion object {
        const val TEST_ACTIVITY_ACTION =
            "dev.sk2andy.materialbrowser.test.MAIN_ACTIVITY_HARDWARE_INPUT"
    }
}
