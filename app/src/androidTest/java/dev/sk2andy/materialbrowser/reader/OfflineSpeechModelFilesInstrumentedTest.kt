package dev.sk2andy.materialbrowser.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineSpeechModelFilesInstrumentedTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validDownloadBecomesReadyAndCanBeDeleted() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("abc"))
            server.start()
            val directory = temporaryFolder.newFolder()
            val files = model(directory, server)
            var progress = 0f
            assertTrue(files.download { progress = it })
            assertEquals(1f, progress, 0f)
            assertTrue(files.isReady())
            assertFalse(File(directory, "model.onnx.part").exists())
            assertTrue(files.delete())
            assertFalse(files.isReady())
        }
    }

    @Test
    fun checksumFailureNeverPublishesAndRetrySucceeds() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("abd"))
            server.enqueue(MockResponse().setBody("abc"))
            server.start()
            val directory = temporaryFolder.newFolder()
            val files = model(directory, server)
            assertFalse(files.download {})
            assertFalse(files.isReady())
            assertFalse(File(directory, "model.onnx").exists())
            assertFalse(File(directory, "model.onnx.part").exists())
            assertTrue(files.download {})
            assertTrue(files.isReady())
        }
    }

    @Test
    fun oversizedDownloadNeverPublishes() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody("abcd", 2))
            server.start()
            val directory = temporaryFolder.newFolder()
            val files = model(directory, server)
            assertFalse(files.download {})
            assertFalse(files.isReady())
            assertFalse(File(directory, "model.onnx").exists())
            assertFalse(File(directory, "model.onnx.part").exists())
        }
    }

    private fun model(directory: File, server: MockWebServer): OfflineSpeechModelFiles = OfflineSpeechModelFiles(
        directory,
        listOf(OfflineSpeechModelFiles.Artifact(
            "model.onnx", server.url("/model.onnx").toString(), 3,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        )),
    )
}
