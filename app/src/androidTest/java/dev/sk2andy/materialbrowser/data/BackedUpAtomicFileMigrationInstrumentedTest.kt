package dev.sk2andy.materialbrowser.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackedUpAtomicFileMigrationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fileName = "backup_migration_test.json"
    private val target by lazy { File(context.filesDir, fileName) }
    private val legacy by lazy { File(context.noBackupFilesDir, fileName) }

    @Before
    @After
    fun cleanFiles() {
        listOf(target, legacy).forEach { file ->
            file.delete()
            File("${file.path}.bak").delete()
        }
    }

    @Test
    fun legacyStateMovesToAutoBackupDirectory() {
        legacy.writeText("saved")

        val migrated = BackedUpAtomicFileMigration.fromNoBackupDirectory(
            context = context,
            fileName = fileName,
            maxBytes = 32,
        )

        assertEquals(target.canonicalPath, migrated.baseFile.canonicalPath)
        assertEquals("saved", migrated.openRead().bufferedReader().use { it.readText() })
        assertTrue(target.isFile)
        assertFalse(legacy.exists())
    }
}
