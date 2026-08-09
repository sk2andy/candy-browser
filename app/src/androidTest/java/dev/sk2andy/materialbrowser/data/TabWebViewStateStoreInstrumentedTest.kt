package dev.sk2andy.materialbrowser.data

import android.os.Bundle
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabWebViewStateStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = TabWebViewStateStore(context)
    private val tabId = UUID.randomUUID().toString()
    private val otherTabId = UUID.randomUUID().toString()

    @Before
    fun setUp() {
        store.delete(tabId)
        store.delete(otherTabId)
    }

    @After
    fun tearDown() {
        store.delete(tabId)
        store.delete(otherTabId)
    }

    @Test
    fun stateRoundTripsAcrossStoreInstances() {
        val state = Bundle().apply {
            putString("currentUrl", "https://example.com/b")
            putStringArrayList(
                "history",
                arrayListOf("https://example.com/a", "https://example.com/b"),
            )
        }

        assertTrue(store.save(tabId, state))

        val restored = TabWebViewStateStore(context).load(tabId)
        assertEquals("https://example.com/b", restored?.getString("currentUrl"))
        assertEquals(
            listOf("https://example.com/a", "https://example.com/b"),
            restored?.getStringArrayList("history"),
        )
    }

    @Test
    fun repositoryFlushMakesQueuedStateDurable() {
        val repository = TabWebViewStateRepository.get(context)
        repository.delete(tabId)
        assertTrue(repository.flush())

        repository.save(tabId, Bundle().apply { putString("currentUrl", "https://example.com") })

        assertTrue(repository.flush())
        assertEquals("https://example.com", store.load(tabId)?.getString("currentUrl"))
    }

    @Test
    fun corruptStateIsRejectedAndDeleted() {
        val file = requireNotNull(store.fileFor(tabId))
        require(file.parentFile?.mkdirs() == true || file.parentFile?.isDirectory == true)
        file.writeText("not a parcel")

        assertNull(store.load(tabId))
        assertFalse(file.exists())
    }

    @Test
    fun pruneKeepsOpenTabAndDeletesOrphanState() {
        assertTrue(store.save(tabId, Bundle().apply { putInt("index", 1) }))
        assertTrue(store.save(otherTabId, Bundle().apply { putInt("index", 2) }))

        store.prune(setOf(tabId))

        assertEquals(1, store.load(tabId)?.getInt("index"))
        assertNull(store.load(otherTabId))
    }

    @Test
    fun recoversPreviousStateAfterInterruptedAtomicWrite() {
        assertTrue(store.save(tabId, Bundle().apply { putInt("index", 1) }))
        val atomicFile = AtomicFile(requireNotNull(store.fileFor(tabId)))
        atomicFile.startWrite().use { interrupted ->
            interrupted.write("partial replacement".toByteArray())
            interrupted.fd.sync()
        }

        store.prune(setOf(tabId))

        assertEquals(1, store.load(tabId)?.getInt("index"))
    }
}
