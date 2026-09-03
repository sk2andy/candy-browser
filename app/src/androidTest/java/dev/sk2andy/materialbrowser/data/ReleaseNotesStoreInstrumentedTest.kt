package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleaseNotesStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences by lazy {
        context.getSharedPreferences(ReleaseNotesStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
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
    fun presentedVersionPersistsAndNeverMovesBackwards() {
        val store = ReleaseNotesStore(context)
        assertNull(store.lastPresentedVersionCode())

        assertTrue(store.markPresented(32_000L))
        assertEquals(32_000L, ReleaseNotesStore(context).lastPresentedVersionCode())

        assertTrue(store.markPresented(31_000L))
        assertEquals(32_000L, ReleaseNotesStore(context).lastPresentedVersionCode())
    }

    @Test
    fun corruptPresentedVersionIsTreatedAsUnseenAndReplaced() {
        preferences.edit().putString("last_presented_version_code", "corrupt").commit()
        val store = ReleaseNotesStore(context)

        assertNull(store.lastPresentedVersionCode())
        assertTrue(store.markPresented(32_000L))
        assertEquals(32_000L, store.lastPresentedVersionCode())
    }
}
