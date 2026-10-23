package dev.sk2andy.materialbrowser.reader

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PocketSpeechModelFilesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `verified complete archive installs and detects later corruption`() {
        val archive = archive("models/model.onnx" to "abc", "voices/voice.wav" to "voice")
        val directory = File(temporaryFolder.newFolder(), "de")
        val files = PocketSpeechModelFiles(directory, language(archive))
        assertFalse(files.isReady())
        assertTrue(files.installVerifiedArchive(archive))
        assertTrue(files.isReady())
        assertEquals("voice", File(directory, "voices/voice.wav").readText())
        File(directory, "models/model.onnx").writeText("abd")
        assertFalse(files.isReady())
    }

    @Test
    fun `wrong archive hash never replaces installed model`() {
        val archive = archive("models/model.onnx" to "abc", "voices/voice.wav" to "voice")
        val directory = File(temporaryFolder.newFolder(), "de")
        val language = language(archive)
        val files = PocketSpeechModelFiles(directory, language)
        assertTrue(files.installVerifiedArchive(archive))
        assertFalse(PocketSpeechModelFiles(directory, language.copy(sha256 = "0".repeat(64)))
            .installVerifiedArchive(archive))
        assertTrue(files.isReady())
    }

    @Test
    fun `unlisted entries cannot be extracted`() {
        val archive = archive("models/model.onnx" to "abc", "voices/voice.wav" to "voice", "unexpected" to "x")
        val directory = File(temporaryFolder.newFolder(), "de")
        assertFalse(PocketSpeechModelFiles(directory, language(archive)).installVerifiedArchive(archive))
        assertFalse(directory.exists())
        assertFalse(File(directory.parentFile, "de.installing").exists())
    }

    @Test
    fun `missing voice or oversized model never becomes ready`() {
        listOf(
            archive("models/model.onnx" to "abc"),
            archive("models/model.onnx" to "abcd", "voices/voice.wav" to "voice"),
        ).forEach { archive ->
            val directory = File(temporaryFolder.newFolder(), "de")
            val files = PocketSpeechModelFiles(directory, language(archive))
            assertFalse(files.installVerifiedArchive(archive))
            assertFalse(files.isReady())
        }
    }

    @Test
    fun `archive path traversal is rejected even if listed in metadata`() {
        val archive = archive("../outside" to "abc")
        val parent = temporaryFolder.newFolder()
        val directory = File(parent, "de")
        val language = language(archive).copy(files = listOf(artifact("../outside", "abc")))
        assertFalse(PocketSpeechModelFiles(directory, language).installVerifiedArchive(archive))
        assertFalse(File(parent, "outside").exists())
        assertFalse(directory.exists())
    }

    @Test
    fun `cancelled installation preserves existing language`() {
        val archive = archive("models/model.onnx" to "abc", "voices/voice.wav" to "voice")
        val directory = File(temporaryFolder.newFolder(), "de")
        val files = PocketSpeechModelFiles(directory, language(archive))
        assertTrue(files.installVerifiedArchive(archive))
        val cancellation = PocketSpeechDownloadCancellation().apply { cancel() }
        assertFalse(files.installVerifiedArchive(archive, cancellation))
        assertTrue(files.isReady())
    }

    @Test
    fun `interrupted install cleanup preserves verified model`() {
        val archive = archive("models/model.onnx" to "abc", "voices/voice.wav" to "voice")
        val parent = temporaryFolder.newFolder()
        val files = PocketSpeechModelFiles(File(parent, "de"), language(archive))
        assertTrue(files.installVerifiedArchive(archive))
        File(parent, "de.installing").mkdirs()
        File(parent, "de.installing/unfinished").writeText("partial")
        File(parent, "de.zip.part").writeText("partial")
        assertTrue(files.clearIncomplete())
        assertFalse(File(parent, "de.installing").exists())
        assertFalse(File(parent, "de.zip.part").exists())
        assertTrue(files.isReady())
    }

    @Test
    fun `deleting one language removes partial files but preserves other languages`() {
        val parent = temporaryFolder.newFolder()
        val directory = File(parent, "de").apply { mkdirs() }
        File(parent, "de.installing").mkdirs()
        File(parent, "de.zip.part").writeText("partial")
        val otherLanguage = File(parent, "en").apply { mkdirs() }
        File(otherLanguage, "keep").writeText("model")
        val archive = archive("models/model.onnx" to "abc", "voices/voice.wav" to "voice")
        val files = PocketSpeechModelFiles(directory, language(archive))
        assertTrue(files.delete())
        assertFalse(directory.exists())
        assertFalse(File(parent, "de.installing").exists())
        assertFalse(File(parent, "de.zip.part").exists())
        assertEquals("model", File(otherLanguage, "keep").readText())
    }

    private fun language(archive: File) = PocketSpeechLanguage(
        code = "de",
        languageTag = "de-DE",
        voiceId = "voice",
        archiveName = "test.zip",
        downloadBytes = archive.length(),
        sha256 = hash(archive.readBytes()),
        files = listOf(artifact("models/model.onnx", "abc"), artifact("voices/voice.wav", "voice")),
    )

    private fun artifact(name: String, content: String) =
        PocketSpeechArtifact(name, content.toByteArray().size.toLong(), hash(content.toByteArray()))

    private fun archive(vararg entries: Pair<String, String>): File = temporaryFolder.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
