package dev.sk2andy.materialbrowser.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDataArchiveStagingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `stage validates archive and returns opaque file name`() {
        val data = temporaryFolder.newFolder("data")
        File(data, "files/value").apply {
            parentFile?.mkdirs()
            writeText("saved")
        }
        val archive = ByteArrayOutputStream().also { output ->
            AppDataArchiveCodec.export(data.toPath(), manifest(), output)
        }.toByteArray()
        val staging = temporaryFolder.newFolder("staging")

        val staged = AppDataArchiveStaging.stage(ByteArrayInputStream(archive), staging)

        assertEquals(1, staged.inspection.entries.count { !it.isDirectory })
        assertTrue(requireNotNull(AppDataArchiveStaging.resolve(staging, staged.fileName)).isFile)
        assertNull(AppDataArchiveStaging.resolve(staging, "../archive.zip"))
    }

    private fun manifest() = AppDataArchiveManifest(
        packageName = "dev.sk2andy.materialbrowser",
        appVersionName = "1.0",
        appVersionCode = 1L,
        webViewVersion = "1",
        sdkInt = 35,
        exportedAtEpochMillis = 1L,
    )
}
