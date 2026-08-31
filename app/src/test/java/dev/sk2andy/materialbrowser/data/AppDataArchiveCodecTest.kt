package dev.sk2andy.materialbrowser.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppDataArchiveCodecTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `export writes manifest first and round trips persistent data`() {
        val dataDirectory = temporaryFolder.newFolder("source").toPath()
        Files.createDirectories(dataDirectory.resolve("shared_prefs"))
        Files.write(
            dataDirectory.resolve("shared_prefs/settings.xml"),
            "settings".toByteArray(StandardCharsets.UTF_8),
        )
        Files.createDirectories(dataDirectory.resolve("databases/empty"))
        Files.write(dataDirectory.resolve("databases/browser.db"), byteArrayOf(0, 1, 2, 3))
        Files.createDirectories(dataDirectory.resolve("no_backup/candy_trails"))
        Files.write(
            dataDirectory.resolve("no_backup/candy_trails/trail.json"),
            "trail".toByteArray(StandardCharsets.UTF_8),
        )
        Files.write(
            dataDirectory.resolve("no_backup/candy_recall.db"),
            "sensitive recall text".toByteArray(StandardCharsets.UTF_8),
        )
        Files.write(
            dataDirectory.resolve("no_backup/candy_recall.db-wal"),
            "sensitive recall wal".toByteArray(StandardCharsets.UTF_8),
        )
        Files.createDirectories(dataDirectory.resolve("cache"))
        Files.write(
            dataDirectory.resolve("cache/disposable"),
            "ignore".toByteArray(StandardCharsets.UTF_8),
        )
        Files.createDirectories(dataDirectory.resolve("app_textures"))
        Files.write(
            dataDirectory.resolve("app_textures/texture"),
            "ignore".toByteArray(StandardCharsets.UTF_8),
        )

        val archiveBytes = ByteArrayOutputStream().also { output ->
            AppDataArchiveCodec.export(dataDirectory, manifest(), output)
        }.toByteArray()

        ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
            assertEquals(AppDataArchiveRules.MANIFEST_ENTRY_NAME, zip.nextEntry.name)
            val manifestJson = JSONObject(String(zip.readBytes(), StandardCharsets.UTF_8))
            assertEquals(1, manifestJson.getInt("formatVersion"))
            assertEquals("dev.example.browser", manifestJson.getString("packageName"))
            assertEquals("1.2.3", manifestJson.getString("appVersionName"))
            assertEquals(42L, manifestJson.getLong("appVersionCode"))
            assertEquals("125.0", manifestJson.getString("webViewVersion"))
            assertEquals(35, manifestJson.getInt("sdkInt"))
            assertEquals(1_723_456_789_000L, manifestJson.getLong("exportedAtEpochMillis"))
        }

        val inspection = AppDataArchiveCodec.inspect(ByteArrayInputStream(archiveBytes))
        assertEquals(manifest(), inspection.manifest)
        assertEquals(setOf("databases", "no_backup", "shared_prefs"), inspection.archivedRootNames)
        assertFalse(inspection.entries.any { entry -> entry.relativePath.startsWith("cache") })
        assertFalse(inspection.entries.any { entry -> entry.relativePath.startsWith("app_textures") })
        assertFalse(inspection.entries.any { entry -> entry.relativePath.contains("candy_recall.db") })
        assertTrue(inspection.entries.any { entry -> entry.relativePath.endsWith("trail.json") })

        val target = temporaryFolder.newFolder("target").toPath()
        val extraction = AppDataArchiveCodec.extract(ByteArrayInputStream(archiveBytes), target)
        assertEquals(manifest(), extraction.manifest)
        assertEquals(
            "settings",
            String(
                Files.readAllBytes(target.resolve("shared_prefs/settings.xml")),
                StandardCharsets.UTF_8,
            ),
        )
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), Files.readAllBytes(target.resolve("databases/browser.db")))
        assertTrue(Files.isDirectory(target.resolve("databases/empty")))
        assertEquals(
            "trail",
            String(
                Files.readAllBytes(target.resolve("no_backup/candy_trails/trail.json")),
                StandardCharsets.UTF_8,
            ),
        )
        assertFalse(Files.exists(target.resolve("no_backup/candy_recall.db")))
        assertFalse(Files.exists(target.resolve("cache")))
    }

    @Test
    fun `export rejects symbolic links in persistent data`() {
        val dataDirectory = temporaryFolder.newFolder("symlink-source").toPath()
        val external = temporaryFolder.newFile("external.txt").toPath()
        Files.write(external, "secret".toByteArray(StandardCharsets.UTF_8))
        val link = dataDirectory.resolve("linked.txt")
        val created = runCatching { Files.createSymbolicLink(link, external) }.isSuccess
        assumeTrue("Symbolic links unavailable", created)

        val failure = assertThrows(AppDataArchiveException::class.java) {
            AppDataArchiveCodec.export(dataDirectory, manifest(), ByteArrayOutputStream())
        }

        assertEquals(AppDataArchiveFailure.SymbolicLink, failure.failure)
    }

    @Test
    fun `inspect rejects unsafe and duplicate entry paths`() {
        val unsafeNames = listOf(
            "settings.xml",
            "/data/settings.xml",
            "data/../settings.xml",
            "data/./settings.xml",
            "data//settings.xml",
            "data\\settings.xml",
            "data/C:/settings.xml",
            "data/cache/settings.xml",
        )
        unsafeNames.forEach { unsafeName ->
            val failure = assertThrows(AppDataArchiveException::class.java) {
                AppDataArchiveCodec.inspect(
                    ByteArrayInputStream(zipOf(unsafeName to byteArrayOf(1))),
                )
            }
            assertEquals(unsafeName, AppDataArchiveFailure.InvalidEntryPath, failure.failure)
        }

        val duplicate = zipOf(
            "data/settings" to byteArrayOf(1),
            "data/settings/" to byteArrayOf(),
        )
        val duplicateFailure = assertThrows(AppDataArchiveException::class.java) {
            AppDataArchiveCodec.inspect(ByteArrayInputStream(duplicate))
        }
        assertEquals(AppDataArchiveFailure.DuplicateEntry, duplicateFailure.failure)

        val childBeforeParentFile = zipOf(
            "data/settings/value" to byteArrayOf(1),
            "data/settings" to byteArrayOf(2),
        )
        val hierarchyFailure = assertThrows(AppDataArchiveException::class.java) {
            AppDataArchiveCodec.inspect(ByteArrayInputStream(childBeforeParentFile))
        }
        assertEquals(AppDataArchiveFailure.InvalidEntryPath, hierarchyFailure.failure)
    }

    @Test
    fun `inspect requires bounded first manifest and supported format`() {
        val notFirst = rawZipOf("data/settings.xml" to byteArrayOf(1))
        assertEquals(
            AppDataArchiveFailure.ManifestNotFirst,
            assertThrows(AppDataArchiveException::class.java) {
                AppDataArchiveCodec.inspect(ByteArrayInputStream(notFirst))
            }.failure,
        )

        val oversizedManifest = rawZipOf(
            AppDataArchiveRules.MANIFEST_ENTRY_NAME to
                ByteArray(AppDataArchiveRules.MAX_MANIFEST_BYTES + 1) { 'x'.code.toByte() },
        )
        assertEquals(
            AppDataArchiveFailure.ManifestTooLarge,
            assertThrows(AppDataArchiveException::class.java) {
                AppDataArchiveCodec.inspect(ByteArrayInputStream(oversizedManifest))
            }.failure,
        )

        val futureManifest = manifestJson(formatVersion = 2)
        assertEquals(
            AppDataArchiveFailure.UnsupportedFormatVersion,
            assertThrows(AppDataArchiveException::class.java) {
                AppDataArchiveCodec.inspect(
                    ByteArrayInputStream(rawZipOf(AppDataArchiveRules.MANIFEST_ENTRY_NAME to futureManifest)),
                )
            }.failure,
        )

        val emptyArchive = rawZipOf(
            AppDataArchiveRules.MANIFEST_ENTRY_NAME to manifestJson(),
        )
        assertEquals(
            AppDataArchiveFailure.EmptyArchive,
            assertThrows(AppDataArchiveException::class.java) {
                AppDataArchiveCodec.inspect(ByteArrayInputStream(emptyArchive))
            }.failure,
        )
    }

    @Test
    fun `inspect reads every entry and rejects bad crc`() {
        val marker = "unique-payload-for-crc".toByteArray()
        val validArchive = storedZipOf(
            AppDataArchiveRules.MANIFEST_ENTRY_NAME to manifestJson(),
            "data/files/value.bin" to marker,
        )
        val markerOffset = validArchive.indexOf(marker)
        assertTrue(markerOffset >= 0)
        validArchive[markerOffset] = (validArchive[markerOffset].toInt() xor 1).toByte()

        val failure = assertThrows(AppDataArchiveException::class.java) {
            AppDataArchiveCodec.inspect(ByteArrayInputStream(validArchive))
        }

        assertEquals(AppDataArchiveFailure.ArchiveIo, failure.failure)
    }

    @Test
    fun `extract requires empty target and validates paths again`() {
        val nonEmptyTarget = temporaryFolder.newFolder("non-empty").toPath()
        Files.write(
            nonEmptyTarget.resolve("existing"),
            "keep".toByteArray(StandardCharsets.UTF_8),
        )
        val validArchive = zipOf("data/shared_prefs/settings.xml" to byteArrayOf(1))

        val targetFailure = assertThrows(AppDataArchiveException::class.java) {
            AppDataArchiveCodec.extract(ByteArrayInputStream(validArchive), nonEmptyTarget)
        }
        assertEquals(AppDataArchiveFailure.TargetNotEmpty, targetFailure.failure)
        assertEquals(
            "keep",
            String(Files.readAllBytes(nonEmptyTarget.resolve("existing")), StandardCharsets.UTF_8),
        )

        val emptyTarget = temporaryFolder.newFolder("empty").toPath()
        val unsafeArchive = zipOf("data/../../outside" to byteArrayOf(1))
        val pathFailure = assertThrows(AppDataArchiveException::class.java) {
            AppDataArchiveCodec.extract(ByteArrayInputStream(unsafeArchive), emptyTarget)
        }
        assertEquals(AppDataArchiveFailure.InvalidEntryPath, pathFailure.failure)
        assertFalse(Files.exists(emptyTarget.parent.resolve("outside")))

        val futureTarget = temporaryFolder.newFolder("future-target").toPath()
        val futureArchive = rawZipOf(
            AppDataArchiveRules.MANIFEST_ENTRY_NAME to manifestJson(formatVersion = 2),
            "data/files/value" to byteArrayOf(1),
        )
        val futureFailure = assertThrows(AppDataArchiveException::class.java) {
            AppDataArchiveCodec.extract(ByteArrayInputStream(futureArchive), futureTarget)
        }
        assertEquals(AppDataArchiveFailure.UnsupportedFormatVersion, futureFailure.failure)
        assertFalse(Files.newDirectoryStream(futureTarget).use { entries -> entries.iterator().hasNext() })
    }

    private fun manifest() = AppDataArchiveManifest(
        packageName = "dev.example.browser",
        appVersionName = "1.2.3",
        appVersionCode = 42L,
        webViewVersion = "125.0",
        sdkInt = 35,
        exportedAtEpochMillis = 1_723_456_789_000L,
    )

    private fun manifestJson(formatVersion: Int = 1): ByteArray = JSONObject()
        .put("formatVersion", formatVersion)
        .put("packageName", "dev.example.browser")
        .put("appVersionName", "1.2.3")
        .put("appVersionCode", 42L)
        .put("webViewVersion", "125.0")
        .put("sdkInt", 35)
        .put("exportedAtEpochMillis", 1_723_456_789_000L)
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray = rawZipOf(
        AppDataArchiveRules.MANIFEST_ENTRY_NAME to manifestJson(),
        *entries,
    )

    private fun rawZipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun storedZipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, content) ->
                    val crc = CRC32().apply { update(content) }
                    zip.putNextEntry(ZipEntry(name).apply {
                        method = ZipEntry.STORED
                        size = content.size.toLong()
                        compressedSize = content.size.toLong()
                        this.crc = crc.value
                    })
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        for (start in 0..size - needle.size) {
            if (needle.indices.all { offset -> this[start + offset] == needle[offset] }) return start
        }
        return -1
    }
}
