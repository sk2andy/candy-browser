package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.gecko.GeckoSessionStateSnapshot
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoSessionStateStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun persistedStateRoundTripsAcrossStoreInstancesAndDeletionIsTabScoped() {
        val first = snapshot()
        val second = snapshot()
        val store = GeckoSessionStateStore(context)
        try {
            assertTrue(store.save(first))
            assertTrue(store.save(second))
            val reopened = GeckoSessionStateStore(context)
            assertEquals(first, reopened.load(first.tabId))
            reopened.delete(first.tabId)
            assertNull(reopened.load(first.tabId))
            assertEquals(second, reopened.load(second.tabId))
        } finally {
            store.delete(first.tabId)
            store.delete(second.tabId)
        }
    }

    @Test
    fun invalidIdentifiersAndCorruptFilesCannotBecomeRestoredState() {
        val valid = snapshot()
        val store = GeckoSessionStateStore(context)
        try {
            assertFalse(store.save(valid.copy(tabId = "../outside")))
            assertNull(store.load("../outside"))
            assertTrue(store.save(valid))
            val file = File(context.noBackupFilesDir, "gecko_session_states/${valid.tabId}.json")
            file.writeText("not-json")
            assertNull(store.load(valid.tabId))
        } finally {
            store.delete(valid.tabId)
        }
    }

    private fun snapshot() = GeckoSessionStateSnapshot(
        tabId = UUID.randomUUID().toString(),
        profileId = "work",
        encodedState = "{\"history\":[]}",
    )
}
