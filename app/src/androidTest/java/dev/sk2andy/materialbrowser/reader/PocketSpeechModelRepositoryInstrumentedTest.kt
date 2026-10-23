package dev.sk2andy.materialbrowser.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketSpeechModelRepositoryInstrumentedTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun restoresLanguagesAndCleansInterruptedDownloadsWithoutNetwork() {
        MockWebServer().use { server ->
            server.start()
            val root = temporaryFolder.newFolder()
            val archive = archive()
            val languages = listOf(language("de", archive, server), language("en", archive, server))
            languages.forEach { language ->
                File(root, "${language.code}/models").mkdirs()
                File(root, "${language.code}/models/model.onnx").writeText("abc")
            }
            File(root, "de.zip.part").writeText("unfinished")
            File(root, "en.installing").mkdirs()
            File(root, "en.installing/unfinished").writeText("unfinished")
            withRepository(root, languages) { repository ->
                waitUntil { repository.state.loaded }
                onMain {
                    assertTrue(repository.state.languages.getValue("de").ready)
                    assertTrue(repository.state.languages.getValue("en").ready)
                    assertTrue(repository.state.languages.values.none { it.busy })
                }
                assertFalse(File(root, "de.zip.part").exists())
                assertFalse(File(root, "en.installing").exists())
                assertEquals(0, server.requestCount)
                onMain {
                    repository.delete("de")
                    repository.cancelDownload("de")
                    assertTrue(repository.state.languages.getValue("de").busy)
                }
                waitUntil { !repository.state.languages.getValue("de").busy }
                onMain {
                    assertFalse(repository.state.languages.getValue("de").ready)
                    assertTrue(repository.state.languages.getValue("en").ready)
                }
                assertEquals("abc", File(root, "en/models/model.onnx").readText())
                assertEquals(0, server.requestCount)
            }
        }
    }

    @Test
    fun cancellingDownloadCannotOverwriteSuccessfulRetry() {
        MockWebServer().use { server ->
            server.start()
            val archive = archive()
            // Keep the read pending without outliving MockWebServer's five-second close timeout.
            server.enqueue(MockResponse().setBody(Buffer().write(archive)).setBodyDelay(2, TimeUnit.SECONDS))
            server.enqueue(MockResponse().setBody(Buffer().write(archive)))
            val root = temporaryFolder.newFolder()
            val language = language("de", archive, server)
            withRepository(root, listOf(language)) { repository ->
                waitUntil { repository.state.loaded }
                assertEquals(0, server.requestCount)
                onMain { repository.download("de") }
                checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                onMain {
                    repository.cancelDownload("de")
                    assertFalse(repository.state.languages.getValue("de").busy)
                    repository.download("de")
                }
                waitUntil { repository.state.languages.getValue("de").ready }
                onMain {
                    val result = repository.state.languages.getValue("de")
                    assertFalse(result.busy)
                    assertFalse(result.error)
                }
                assertEquals(2, server.requestCount)
                assertEquals("abc", File(root, "de/models/model.onnx").readText())
                assertFalse(File(root, "de.zip.part").exists())
                assertFalse(File(root, "de.installing").exists())
            }
        }
    }

    private fun withRepository(
        root: File,
        languages: List<PocketSpeechLanguage>,
        block: (PocketSpeechModelRepository) -> Unit,
    ) {
        val io = Executors.newSingleThreadExecutor()
        lateinit var repository: PocketSpeechModelRepository
        onMain { repository = PocketSpeechModelRepository(context, io, root, languages) }
        try {
            block(repository)
        } finally {
            onMain { languages.forEach { repository.cancelDownload(it.code) } }
            io.shutdown()
            assertTrue(io.awaitTermination(15, TimeUnit.SECONDS))
        }
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            var complete = false
            onMain { complete = predicate() }
            if (complete) return
            Thread.sleep(20)
        }
        error("Pocket model state did not settle")
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun language(code: String, bytes: ByteArray, server: MockWebServer) = PocketSpeechLanguage(
        code = code,
        languageTag = if (code == "de") "de-DE" else "en-US",
        voiceId = "fixture",
        archiveName = "$code.zip",
        downloadBytes = bytes.size.toLong(),
        sha256 = hash(bytes),
        files = listOf(PocketSpeechArtifact("models/model.onnx", 3L, hash("abc".toByteArray()))),
        url = server.url("/$code.zip").toString(),
    )

    private fun archive(): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("models/model.onnx"))
            zip.write("abc".toByteArray())
            zip.closeEntry()
        }
        bytes.toByteArray()
    }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
