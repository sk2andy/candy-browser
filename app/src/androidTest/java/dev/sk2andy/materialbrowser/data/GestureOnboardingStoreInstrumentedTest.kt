package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GestureOnboardingStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences(GestureOnboardingStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val browserSessionPreferences by lazy {
        context.getSharedPreferences(
            GestureOnboardingStore.BROWSER_SESSION_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
    }

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        browserSessionPreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        browserSessionPreferences.edit().clear().commit()
    }

    @Test
    fun completionPersistsAcrossStoreInstances() {
        assertTrue(GestureOnboardingStore(context).shouldShow())

        GestureOnboardingStore(context).markCompleted()

        assertTrue(GestureOnboardingStore(context).isCompleted())
        assertFalse(GestureOnboardingStore(context).shouldShow())
    }

    @Test
    fun existingBrowserSessionIsMigratedWithoutShowingOnboarding() {
        browserSessionPreferences.edit().putString("tabs", "[]").commit()

        val store = GestureOnboardingStore(context)

        assertFalse(store.shouldShow())
        assertTrue(store.isCompleted())
    }

    @Test
    fun incompleteOnboardingSurvivesAColdRestartWithBrowserSessionData() {
        assertTrue(GestureOnboardingStore(context).shouldShow())
        browserSessionPreferences.edit().putString("tabs", "[]").commit()

        assertTrue(GestureOnboardingStore(context).shouldShow())
        assertFalse(GestureOnboardingStore(context).isCompleted())
    }

    @Test
    fun updatedTutorialIsShownAfterAnOlderVersionWasCompleted() {
        preferences.edit()
            .putInt(
                GestureOnboardingStore.KEY_COMPLETED_VERSION,
                GestureOnboardingStore.CURRENT_VERSION - 1,
            )
            .commit()
        browserSessionPreferences.edit().putString("tabs", "[]").commit()

        val store = GestureOnboardingStore(context)

        assertTrue(store.shouldShow())
        assertFalse(store.isCompleted())
    }
}
