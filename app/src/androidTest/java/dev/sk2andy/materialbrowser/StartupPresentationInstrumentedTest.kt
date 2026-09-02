package dev.sk2andy.materialbrowser

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.GestureOnboardingStore
import dev.sk2andy.materialbrowser.ui.AddressBarTestTags
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupPresentationInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    init {
        clearPreferences()
        GestureOnboardingStore(context).markCompleted()
        BrowserSessionStore(context).saveStartupAnimationEnabled(false)
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        composeRule.activityRule.scenario.close()
        clearPreferences()
    }

    @Test
    fun disabledAnimationOpensFocusedAddressEditorOnLauncherStart() {
        composeRule.onNodeWithTag("candy_splash").assertDoesNotExist()
        composeRule.onNodeWithTag(AddressBarTestTags.Editor)
            .assertIsDisplayed()
            .assertIsFocused()
        assertImeVisible()

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.cd_close_address_input),
        ).performClick()
        composeRule.onNodeWithTag(AddressBarTestTags.Editor).assertDoesNotExist()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClass(activity, MainActivity::class.java),
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                composeRule.activity.window.decorView.hasWindowFocus()
        }

        composeRule.onNodeWithTag(AddressBarTestTags.Editor)
            .assertIsDisplayed()
            .assertIsFocused()
        assertImeVisible()
    }

    private fun assertImeVisible() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
    }

    private fun clearPreferences() {
        listOf(
            BrowserSessionStore.PREFERENCES_NAME,
            GestureOnboardingStore.PREFERENCES_NAME,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
