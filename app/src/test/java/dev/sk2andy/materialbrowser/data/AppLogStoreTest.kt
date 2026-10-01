package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLogStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `logging defaults off and rejects writes`() {
        val directory = temporaryFolder.newFolder()
        val store = AppLogStore(directory)

        assertNull(store.captureGeneration())
        assertFalse(store.append("ignored\n", expectedGeneration = 0L))
        assertEquals("", store.snapshot())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `enabling and reopening retain saved records without enabling new process`() {
        val directory = temporaryFolder.newFolder()
        val store = AppLogStore(directory)
        assertTrue(store.setEnabled(true))
        val generation = requireNotNull(store.captureGeneration())

        assertTrue(store.append("first\n", generation, sync = true))
        assertTrue(store.append("second\n", generation))
        val reopened = AppLogStore(directory)

        assertNull(reopened.captureGeneration())
        assertEquals("first\nsecond\n", reopened.snapshot())
        assertTrue(reopened.setEnabled(true))
        assertTrue(reopened.append("third\n", requireNotNull(reopened.captureGeneration())))
        assertEquals("first\nsecond\nthird\n", reopened.snapshot())
    }

    @Test
    fun `rotation keeps two bounded files and drops oldest records`() {
        val directory = temporaryFolder.newFolder()
        val store = AppLogStore(directory)
        store.setEnabled(true)
        val generation = requireNotNull(store.captureGeneration())
        val firstRecord = "a".repeat(AppLogRules.MAX_RECORD_BYTES)
        val secondRecord = "b".repeat(AppLogRules.MAX_RECORD_BYTES)
        val recordsPerFile = AppLogRules.MAX_FILE_BYTES / AppLogRules.MAX_RECORD_BYTES

        repeat(recordsPerFile) { assertTrue(store.append(firstRecord, generation)) }
        assertEquals(AppLogRules.MAX_FILE_BYTES.toLong(), directory.resolve("current.txt").length())
        assertFalse(directory.resolve("previous.txt").exists())
        repeat(recordsPerFile) { assertTrue(store.append(secondRecord, generation)) }

        assertEquals(
            firstRecord.repeat(recordsPerFile) + secondRecord.repeat(recordsPerFile),
            store.snapshot(),
        )
        assertTrue(store.append("newest\n", generation))

        assertEquals(secondRecord.repeat(recordsPerFile) + "newest\n", store.snapshot())
        assertEquals(2, directory.listFiles().orEmpty().size)
        assertTrue(directory.listFiles().orEmpty().all { it.length() <= AppLogRules.MAX_FILE_BYTES })
        assertTrue(store.snapshot().toByteArray().size <= 2 * AppLogRules.MAX_FILE_BYTES)
    }

    @Test
    fun `clear rejects queued records from old generation and permits fresh writes`() {
        val store = AppLogStore(temporaryFolder.newFolder())
        store.setEnabled(true)
        val oldGeneration = requireNotNull(store.captureGeneration())
        assertTrue(store.append("old\n", oldGeneration))

        assertTrue(store.clear())

        assertEquals("", store.snapshot())
        assertFalse(store.append("stale\n", oldGeneration))
        assertTrue(store.append("new\n", requireNotNull(store.captureGeneration())))
        assertEquals("new\n", store.snapshot())
    }

    @Test
    fun `invalidating pending writes preserves saved logs while rejecting old generation`() {
        val store = AppLogStore(temporaryFolder.newFolder())
        store.setEnabled(true)
        val oldGeneration = requireNotNull(store.captureGeneration())
        assertTrue(store.append("saved\n", oldGeneration))

        store.invalidatePendingWrites()

        assertEquals("saved\n", store.snapshot())
        assertFalse(store.append("queued\n", oldGeneration))
        assertTrue(store.append("new\n", requireNotNull(store.captureGeneration())))
        assertEquals("saved\nnew\n", store.snapshot())
    }

    @Test
    fun `disable deletes logs and old generation stays rejected after reenable`() {
        val directory = temporaryFolder.newFolder()
        val store = AppLogStore(directory)
        store.setEnabled(true)
        val oldGeneration = requireNotNull(store.captureGeneration())
        assertTrue(store.append("old\n", oldGeneration))

        assertTrue(store.setEnabled(false))

        assertNull(store.captureGeneration())
        assertEquals("", store.snapshot())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertFalse(store.append("stale\n", oldGeneration))
        assertTrue(store.setEnabled(true))
        assertFalse(store.append("still stale\n", oldGeneration))
        assertTrue(store.append("new\n", requireNotNull(store.captureGeneration())))
        assertEquals("new\n", store.snapshot())
    }

    @Test
    fun `oversized UTF8 records are rejected without altering saved logs`() {
        val store = AppLogStore(temporaryFolder.newFolder())
        store.setEnabled(true)
        val generation = requireNotNull(store.captureGeneration())
        assertTrue(store.append("kept\n", generation))

        assertFalse(store.append("a".repeat(AppLogRules.MAX_RECORD_BYTES + 1), generation))
        assertFalse(store.append("ä".repeat(AppLogRules.MAX_RECORD_BYTES), generation))

        assertEquals("kept\n", store.snapshot())
    }
}
