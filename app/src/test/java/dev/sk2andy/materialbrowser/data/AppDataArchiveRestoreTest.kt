package dev.sk2andy.materialbrowser.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDataArchiveRestoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `restore replaces archived roots and removes newer persistent roots`() {
        val data = temporaryFolder.newFolder("data")
        File(data, "shared_prefs/current.xml").apply {
            parentFile?.mkdirs()
            writeText("current")
        }
        File(data, "future_store/value").apply {
            parentFile?.mkdirs()
            writeText("future")
        }
        File(data, "cache/keep").apply {
            parentFile?.mkdirs()
            writeText("cache")
        }
        val extracted = temporaryFolder.newFolder("extracted")
        File(extracted, "shared_prefs/current.xml").apply {
            parentFile?.mkdirs()
            writeText("restored")
        }
        File(extracted, "files/saved.json").apply {
            parentFile?.mkdirs()
            writeText("saved")
        }
        val backup = File(temporaryFolder.root, "backup")
        val marker = File(temporaryFolder.root, "restore.json")

        assertEquals(
            AppDataArchiveRestoreResult.Completed,
            AppDataArchiveRestore.replacePersistentData(data, extracted, backup, marker),
        )

        assertEquals("restored", File(data, "shared_prefs/current.xml").readText())
        assertEquals("saved", File(data, "files/saved.json").readText())
        assertFalse(File(data, "future_store").exists())
        assertEquals("cache", File(data, "cache/keep").readText())
        assertEquals("current", File(backup, "shared_prefs/current.xml").readText())
        assertFalse(marker.exists())
    }

    @Test
    fun `restore rejects nonempty backup directory before changing data`() {
        val data = temporaryFolder.newFolder("data")
        File(data, "files/current").apply {
            parentFile?.mkdirs()
            writeText("current")
        }
        val extracted = temporaryFolder.newFolder("extracted")
        val backup = temporaryFolder.newFolder("backup")
        val marker = File(temporaryFolder.root, "restore.json")
        File(backup, "unexpected").writeText("value")

        val failure = runCatching {
            AppDataArchiveRestore.replacePersistentData(data, extracted, backup, marker)
        }.exceptionOrNull() as AppDataArchiveRestoreException

        assertEquals(AppDataArchiveRestoreFailure.BackupDirectoryNotEmpty, failure.failure)
        assertEquals("current", File(data, "files/current").readText())
    }

    @Test
    fun `recovery restores old roots and removes partially installed new roots`() {
        val data = temporaryFolder.newFolder("data")
        val work = temporaryFolder.newFolder("work")
        val extracted = File(work, "extracted").apply { mkdirs() }
        val backup = File(work, "backup").apply { mkdirs() }
        File(backup, "shared_prefs/current.xml").apply {
            parentFile?.mkdirs()
            writeText("old")
        }
        File(data, "shared_prefs/current.xml").apply {
            parentFile?.mkdirs()
            writeText("new")
        }
        File(data, "files/new.json").apply {
            parentFile?.mkdirs()
            writeText("new")
        }
        val marker = File(temporaryFolder.root, "restore.json")
        marker.writeText(
            """{"version":1,"extractedDirectory":"${extracted.canonicalPath}","backupDirectory":"${backup.canonicalPath}","roots":[{"name":"shared_prefs","originallyPresent":true},{"name":"files","originallyPresent":false}]}""",
        )

        assertTrue(AppDataArchiveRestore.recoverInterruptedRestore(data, marker))

        assertEquals("old", File(data, "shared_prefs/current.xml").readText())
        assertFalse(File(data, "files").exists())
        assertFalse(marker.exists())
        assertFalse(work.exists())
    }

    @Test
    fun `startup cleanup removes only orphaned restore work`() {
        val state = temporaryFolder.newFolder("transfer-state")
        val orphan = File(state, "restore_00000000-0000-0000-0000-000000000000")
            .apply { mkdirs() }
        val unrelated = File(state, "keep").apply { mkdirs() }
        val marker = File(state, "restore.json")

        AppDataArchiveRestore.cleanupOrphanedWorkDirectories(state, marker)

        assertFalse(orphan.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `recovery finalizes committed journal without rolling imported roots back`() {
        val data = temporaryFolder.newFolder("committed-data")
        File(data, "files/value").apply {
            parentFile?.mkdirs()
            writeText("imported")
        }
        val work = temporaryFolder.newFolder("committed-work")
        val extracted = File(work, "extracted").apply { mkdirs() }
        val backup = File(work, "backup").apply { mkdirs() }
        File(backup, "files/value").apply {
            parentFile?.mkdirs()
            writeText("old")
        }
        val marker = File(temporaryFolder.root, "committed.json")
        marker.writeText(
            """{"version":1,"extractedDirectory":"${extracted.canonicalPath}","backupDirectory":"${backup.canonicalPath}","committed":true,"roots":[{"name":"files","originallyPresent":true}]}""",
        )

        assertTrue(AppDataArchiveRestore.recoverInterruptedRestore(data, marker))

        assertEquals("imported", File(data, "files/value").readText())
        assertFalse(marker.exists())
        assertFalse(work.exists())
    }
}
