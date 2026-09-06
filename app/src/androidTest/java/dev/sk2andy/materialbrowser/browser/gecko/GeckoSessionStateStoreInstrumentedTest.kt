package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.GeckoSessionStateStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoSessionStateStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun regularSnapshotRoundTripsAndCanBeDeleted() {
        val store = GeckoSessionStateStore(context)
        val tabId = UUID.randomUUID().toString()
        try {
            val snapshot = GeckoSessionStateSnapshot(tabId, "profile", "{state}")
            assertEquals(snapshot, snapshot.takeIf(store::save)?.let { store.load(tabId) })
        } finally {
            store.delete(tabId)
        }
        assertNull(store.load(tabId))
    }

    @Test
    fun malformedSnapshotsNeverPersist() {
        val store = GeckoSessionStateStore(context)
        val tabId = UUID.randomUUID().toString()
        try {
            assertEquals(false, store.save(GeckoSessionStateSnapshot(tabId, "profile", "")))
            assertNull(store.load(tabId))
        } finally {
            store.delete(tabId)
        }
    }
}
