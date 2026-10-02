package dev.sk2andy.materialbrowser.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeckoLogStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `capture paths create local directory and stay unique`() {
        val directory = File(temporaryFolder.root, "gecko_logs")
        val store = GeckoLogStore(directory)
        val first = File(store.newCapturePath())
        val second = File(store.newCapturePath())

        assertEquals(directory, first.parentFile)
        assertTrue(directory.isDirectory)
        assertTrue(first.name.startsWith("capture-"))
        assertFalse(first.exists())
        assertFalse(first == second)
        assertEquals("", store.snapshot())
    }

    @Test
    fun `snapshot reads only direct native files and keeps module output`() {
        val directory = temporaryFolder.newFolder()
        val native = File(directory, "parent.moz_log").apply { writeText("nsHttp URL=https://example.org/\n") }
        File(directory, "unrelated.txt").writeText("ignored")
        File(directory, "nested").mkdir()
        File(directory, "nested/child.moz_log").writeText("nested ignored")
        val store = GeckoLogStore(directory)

        assertEquals("\n[Gecko log: parent.moz_log]\nnsHttp URL=https://example.org/\n", store.snapshot())
        assertEquals(native.length(), store.totalBytes())
    }

    @Test
    fun `snapshot limits UTF8 export including file headers and malformed bytes`() {
        val directory = temporaryFolder.newFolder()
        File(directory, "large.moz_log").outputStream().use { output ->
            output.write(ByteArray(GeckoLoggingRules.MAX_EXPORT_BYTES + 100) { 0xff.toByte() })
        }
        val store = GeckoLogStore(directory)

        assertTrue(store.snapshot().toByteArray(Charsets.UTF_8).size <= GeckoLoggingRules.MAX_EXPORT_BYTES)
        assertEquals("\n[Gecko log: large.moz_log]\n", store.snapshot())
        File(directory, "large.moz_log").writeText("a".repeat(GeckoLoggingRules.MAX_EXPORT_BYTES + 100))
        assertEquals(GeckoLoggingRules.MAX_EXPORT_BYTES, store.snapshot().toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `bounded export prioritizes newest segment over older full log`() {
        val directory = temporaryFolder.newFolder()
        File(directory, "old.moz_log").apply {
            writeText("a".repeat(GeckoLoggingRules.MAX_EXPORT_BYTES))
            setLastModified(1L)
        }
        File(directory, "new.moz_log").apply {
            writeText("latest diagnostic\n")
            setLastModified(2L)
        }

        val snapshot = GeckoLogStore(directory).snapshot()

        assertTrue(snapshot.startsWith("\n[Gecko log: new.moz_log]\nlatest diagnostic\n"))
        assertEquals(GeckoLoggingRules.MAX_EXPORT_BYTES, snapshot.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `trim retains four newest capture segments including child files`() {
        val directory = temporaryFolder.newFolder()
        val store = GeckoLogStore(directory)
        val paths = (1..6).map { index ->
            val prefix = "capture-$index-00000000-0000-0000-0000-000000000000"
            File(directory, "$prefix.moz_log").apply {
                writeText("segment $index\n")
                setLastModified(index.toLong())
            }
        }
        val child = File(directory, paths.last().name.removeSuffix(".moz_log") + "-child.42.moz_log")
        child.writeText("child 6\n")
        child.setLastModified(6L)

        store.trim()

        assertFalse(paths[0].exists())
        assertFalse(paths[1].exists())
        assertTrue(paths.drop(2).all(File::exists))
        assertTrue(child.exists())
        assertEquals(5, directory.listFiles().orEmpty().size)
        assertTrue(store.snapshot().contains("child 6"))
    }

    @Test
    fun `new capture reserves one of four retained segment slots`() {
        val directory = temporaryFolder.newFolder()
        repeat(4) { index ->
            File(directory, "segment$index.moz_log").apply {
                writeText("$index")
                setLastModified(index + 1L)
            }
        }

        GeckoLogStore(directory).newCapturePath()

        assertFalse(File(directory, "segment0.moz_log").exists())
        assertEquals(3, directory.listFiles().orEmpty().size)
    }

    @Test
    fun `trim drops oldest whole segments until retained native files fit byte budget`() {
        val directory = temporaryFolder.newFolder()
        val oldest = File(directory, "old.moz_log").apply {
            writeBytes(ByteArray(4 * 1024 * 1024))
            setLastModified(1L)
        }
        val middle = File(directory, "middle.moz_log").apply {
            writeBytes(ByteArray(4 * 1024 * 1024))
            setLastModified(2L)
        }
        val newest = File(directory, "new.moz_log").apply {
            writeBytes(ByteArray(4 * 1024 * 1024))
            setLastModified(3L)
        }
        val store = GeckoLogStore(directory)

        store.trim()

        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
        assertEquals(GeckoLoggingRules.MAX_EXPORT_BYTES.toLong(), store.totalBytes())
    }

    @Test
    fun `oversized stopped capture retains latest bytes within local budget`() {
        val directory = temporaryFolder.newFolder()
        val log = File(directory, "oversized.moz_log").apply {
            outputStream().use { output ->
                output.write("old prefix\n".toByteArray())
                output.write(ByteArray(GeckoLoggingRules.MAX_EXPORT_BYTES - 12) { 'a'.code.toByte() })
                output.write("latest diagnostic\n".toByteArray())
            }
        }
        val store = GeckoLogStore(directory)

        store.trim()

        assertTrue(log.exists())
        assertEquals(GeckoLoggingRules.MAX_EXPORT_BYTES.toLong(), store.totalBytes())
        assertTrue(log.readText().endsWith("latest diagnostic\n"))
        assertFalse(log.readText().contains("old prefix"))
    }

    @Test
    fun `oversized newest group keeps useful tails across native process files`() {
        val directory = temporaryFolder.newFolder()
        val prefix = "capture-1-00000000-0000-0000-0000-000000000000"
        val parent = File(directory, "$prefix.moz_log").apply {
            writeText("a".repeat(GeckoLoggingRules.MAX_EXPORT_BYTES) + "parent tail\n")
        }
        val child = File(directory, "$prefix-child.1.moz_log").apply {
            writeText("b".repeat(GeckoLoggingRules.MAX_EXPORT_BYTES) + "child tail\n")
        }
        val store = GeckoLogStore(directory)

        store.trim()

        assertEquals(GeckoLoggingRules.MAX_EXPORT_BYTES.toLong(), store.totalBytes())
        assertTrue(parent.readText().endsWith("parent tail\n"))
        assertTrue(child.readText().endsWith("child tail\n"))
        assertEquals((GeckoLoggingRules.MAX_EXPORT_BYTES / 2).toLong(), parent.length())
        assertEquals((GeckoLoggingRules.MAX_EXPORT_BYTES / 2).toLong(), child.length())
        assertTrue(store.snapshot().contains("parent tail\n"))
        assertTrue(store.snapshot().contains("child tail\n"))
    }

    @Test
    fun `snapshot ignores symlinks and clear removes links without touching targets`() {
        val directory = temporaryFolder.newFolder()
        val external = temporaryFolder.newFile().apply { writeText("external secret") }
        val link = File(directory, "linked.moz_log")
        Files.createSymbolicLink(link.toPath(), external.toPath())
        val store = GeckoLogStore(directory)

        assertEquals("", store.snapshot())
        assertEquals(0L, store.totalBytes())
        assertTrue(store.clear())
        assertEquals("external secret", external.readText())
        assertFalse(Files.exists(link.toPath()))
    }

    @Test
    fun `symlinked log directory never exposes or deletes target contents`() {
        val external = temporaryFolder.newFolder()
        val secret = File(external, "secret.moz_log").apply { writeText("secret") }
        val link = File(temporaryFolder.root, "linked-directory")
        Files.createSymbolicLink(link.toPath(), external.toPath())
        val store = GeckoLogStore(link)

        assertEquals("", store.snapshot())
        assertEquals(0L, store.totalBytes())
        assertFalse(store.clear())
        assertTrue(secret.exists())
        assertTrue(runCatching { store.newCapturePath() }.isFailure)
    }

    @Test
    fun `clear reports incomplete deletion and preserves nested files`() {
        val directory = temporaryFolder.newFolder()
        File(directory, "ordinary.moz_log").writeText("removed")
        File(directory, "nested").mkdir()
        val nested = File(directory, "nested/child.moz_log").apply { writeText("kept") }
        val store = GeckoLogStore(directory)

        assertFalse(store.clear())
        assertFalse(File(directory, "ordinary.moz_log").exists())
        assertEquals("kept", nested.readText())
    }
}
