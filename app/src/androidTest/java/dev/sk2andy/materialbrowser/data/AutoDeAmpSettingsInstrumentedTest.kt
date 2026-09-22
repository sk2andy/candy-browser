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
class AutoDeAmpSettingsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
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
    fun settingDefaultsOnAndRoundTrips() {
        val store = BrowserSessionStore(context)

        assertTrue(store.loadAutoDeAmpEnabled())
        store.saveAutoDeAmpEnabled(false)
        assertFalse(store.loadAutoDeAmpEnabled())
        store.saveAutoDeAmpEnabled(true)
        assertTrue(store.loadAutoDeAmpEnabled())
    }
}
