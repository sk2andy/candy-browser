package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.PrivacySignalSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacySignalSettingsInstrumentedTest {
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
    fun settingsDefaultEnabledAndRoundTripIndependently() {
        val store = BrowserSessionStore(context)

        assertEquals(PrivacySignalSettings.Default, store.loadPrivacySignalSettings())

        val saved = PrivacySignalSettings(
            doNotTrackEnabled = false,
            globalPrivacyControlEnabled = true,
        )
        store.savePrivacySignalSettings(saved)

        assertEquals(saved, store.loadPrivacySignalSettings())
    }
}
