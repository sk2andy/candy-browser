package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailFork
import dev.sk2andy.materialbrowser.browser.CandyTrailForkLifecycle
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CandyTrailStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: CandyTrailStore

    @Before
    fun setUp() {
        store = CandyTrailStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun graphRoundTripsAcrossStoreInstances() {
        val tabId = UUID.randomUUID().toString()
        val trail = CandyTrail(
            tabId = tabId,
            nodes = listOf(
                node("n0", null, "https://a.example", 1L),
                node("n1", "n0", "https://b.example", 2L),
                node("n2", "n0", "https://c.example", 3L),
            ),
            currentNodeId = "n2",
            nextOrdinal = 3L,
            forks = listOf(
                CandyTrailFork(
                    id = "f0",
                    originTabId = tabId,
                    originNodeId = "n0",
                    destinationTabId = UUID.randomUUID().toString(),
                    profileId = "profile",
                    isIncognito = false,
                    url = "https://a.example",
                    title = "A",
                    createdAt = 4L,
                    updatedAt = 4L,
                    lifecycle = CandyTrailForkLifecycle.Open,
                ),
            ),
            nextForkOrdinal = 1L,
        )

        assertTrue(store.save(tabId, trail))
        assertEquals(trail, CandyTrailStore(context).load(tabId))
    }

    @Test
    fun corruptFileIsRejectedAndDeleted() {
        val tabId = UUID.randomUUID().toString()
        val file = store.fileFor(tabId)!!
        file.parentFile!!.mkdirs()
        file.writeText("not-json")

        assertNull(store.load(tabId))
        assertFalse(file.exists())
    }

    @Test
    fun versionOneGraphMigratesWithoutForks() {
        val tabId = UUID.randomUUID().toString()
        val file = store.fileFor(tabId)!!
        file.parentFile!!.mkdirs()
        file.writeText(
            JSONObject()
                .put("version", 1)
                .put("tabId", tabId)
                .put("currentNodeId", "n0")
                .put("nextOrdinal", 1L)
                .put(
                    "nodes",
                    JSONArray().put(
                        JSONObject()
                            .put("id", "n0")
                            .put("parentId", null)
                            .put("url", "https://legacy.example")
                            .put("title", "Legacy")
                            .put("visitedAt", 1L),
                    ),
                )
                .toString(),
        )

        val migrated = store.load(tabId)

        assertEquals(1, migrated!!.nodes.size)
        assertTrue(migrated.forks.isEmpty())
        assertEquals(0L, migrated.nextForkOrdinal)
    }

    @Test
    fun pruneDeletesOrphanAndKeepsOpenTab() {
        val keptId = UUID.randomUUID().toString()
        val orphanId = UUID.randomUUID().toString()
        assertTrue(store.save(keptId, CandyTrail(keptId)))
        assertTrue(store.save(orphanId, CandyTrail(orphanId)))

        store.prune(setOf(keptId))

        assertTrue(store.fileFor(keptId)!!.exists())
        assertFalse(store.fileFor(orphanId)!!.exists())
    }

    @Test
    fun repositoryNeverWritesIncognitoJourney() {
        val tabId = UUID.randomUUID().toString()
        val tab = BrowserTab(
            id = tabId,
            isIncognito = true,
            lastAccessedAt = 1L,
            url = "https://private.example",
        )
        val trail = CandyTrail(
            tabId = tabId,
            nodes = listOf(node("n0", null, tab.url, 1L)),
            currentNodeId = "n0",
            nextOrdinal = 1L,
            forks = listOf(
                CandyTrailFork(
                    id = "f0",
                    originTabId = tabId,
                    originNodeId = "n0",
                    destinationTabId = UUID.randomUUID().toString(),
                    profileId = tab.profileId,
                    isIncognito = true,
                    url = tab.url,
                    title = "Private fork",
                    createdAt = 2L,
                    updatedAt = 2L,
                    lifecycle = CandyTrailForkLifecycle.Open,
                ),
            ),
            nextForkOrdinal = 1L,
        )
        val completed = CountDownLatch(1)
        val repository = CandyTrailRepository.get(context)

        repository.save(tab, trail)
        repository.restore(
            tabs = listOf(tab),
            onLoaded = { _, _ -> throw AssertionError("Incognito journey was restored") },
            onComplete = completed::countDown,
        )

        assertTrue("Repository operation timed out", completed.await(10, TimeUnit.SECONDS))
        assertFalse(store.fileFor(tabId)!!.exists())
    }

    private fun node(id: String, parentId: String?, url: String, at: Long) = CandyTrailNode(
        id = id,
        parentId = parentId,
        url = url,
        title = url,
        visitedAt = at,
    )
}
