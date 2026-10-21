package dev.sk2andy.materialbrowser.reader

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineSpeechModelFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing or partial model cannot become ready`() {
        val directory = temporaryFolder.newFolder()
        val model = files(directory)
        assertFalse(model.isReady())
        File(directory, "model.onnx.part").writeText("abc")
        assertFalse(model.isReady())
        File(directory, "model.onnx").writeText("ab")
        assertFalse(model.isReady())
    }

    @Test
    fun `same sized corruption is rejected after earlier verification`() {
        val directory = temporaryFolder.newFolder()
        val model = files(directory)
        File(directory, "model.onnx").writeText("abc")
        assertTrue(model.isReady())
        File(directory, "model.onnx").writeText("abd")
        assertFalse(model.isReady())
    }

    @Test
    fun `delete removes installed and unfinished downloads`() {
        val directory = temporaryFolder.newFolder()
        val model = files(directory)
        File(directory, "model.onnx").writeText("abc")
        File(directory, "model.onnx.part").writeText("ab")
        assertTrue(model.delete())
        assertFalse(directory.exists())
        assertFalse(model.isReady())
        assertTrue(model.delete())
    }

    private fun files(directory: File): OfflineSpeechModelFiles = OfflineSpeechModelFiles(
        directory,
        listOf(OfflineSpeechModelFiles.Artifact(
            name = "model.onnx",
            url = "https://example.invalid/model.onnx",
            size = 3,
            sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        )),
    )
}
