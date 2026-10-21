package dev.sk2andy.materialbrowser.reader

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Public model assets only. Article and selected text never enter the downloader. */
internal class OfflineSpeechModelFiles(
    private val directory: File,
    private val artifacts: List<Artifact> = ARTIFACTS,
) {
    fun file(name: String): File = File(directory, name)

    // Validate on the repository's background executor, never on the UI thread.
    fun isReady(): Boolean = artifacts.all { matches(file(it.name), it) }

    fun download(onProgress: (Float) -> Unit): Boolean = try {
        check(directory.isDirectory || directory.mkdirs())
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.MINUTES)
            .build()
        val totalBytes = artifacts.sumOf(Artifact::size)
        var completedBytes = 0L
        for (artifact in artifacts) {
            checkInterrupted()
            val target = file(artifact.name)
            if (!matches(target, artifact)) {
                val temporary = file("${artifact.name}.part")
                try {
                    client.newCall(Request.Builder().url(artifact.url).build()).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Model download failed")
                        val body = response.body ?: throw IOException("Missing model data")
                        val contentLength = body.contentLength()
                        if (contentLength >= 0 && contentLength != artifact.size) {
                            throw IOException("Unexpected model size")
                        }
                        val digest = MessageDigest.getInstance("SHA-256")
                        var received = 0L
                        body.byteStream().use { input ->
                            temporary.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    checkInterrupted()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    received += count
                                    if (received > artifact.size) throw IOException("Model too large")
                                    digest.update(buffer, 0, count)
                                    output.write(buffer, 0, count)
                                    onProgress((completedBytes + received).toFloat() / totalBytes)
                                }
                                output.fd.sync()
                            }
                        }
                        if (received != artifact.size || digest.digest().hex() != artifact.sha256) {
                            throw IOException("Invalid model integrity")
                        }
                    }
                    if (!temporary.renameTo(target)) throw IOException("Cannot save model")
                } finally {
                    temporary.delete()
                }
            }
            completedBytes += artifact.size
            onProgress(completedBytes.toFloat() / totalBytes)
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: IllegalStateException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Owning repository stops inference before deleting these files. */
    fun delete(): Boolean = try {
        !directory.exists() || directory.deleteRecursively()
    } catch (_: SecurityException) {
        false
    }

    private fun matches(target: File, artifact: Artifact): Boolean {
        if (!target.isFile || target.length() != artifact.size) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    checkInterrupted()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().hex() == artifact.sha256
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Model work interrupted")
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    internal data class Artifact(val name: String, val url: String, val size: Long, val sha256: String)

    companion object {
        const val MODEL_ID = "supertonic-3-int8-cca5a0e"
        const val DOWNLOAD_BYTES = 145_310_775L
        const val LICENSE_URL = "https://huggingface.co/supertone-oss-archive/supertonic-3/" +
            "blob/aafc6e32416a594460b32413efc49d7fe4ce6d46/LICENSE"
        private const val BASE_URL = "https://huggingface.co/csukuangfj2/" +
            "sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4"
        private val ARTIFACTS = listOf(
            modelArtifact("duration_predictor.int8.onnx", 3_700_147,
                "c3eb91414d5ff8a7a239b7fe9e34e7e2bf8a8140d8375ffb14718b1c639325db"),
            modelArtifact("text_encoder.int8.onnx", 36_416_150,
                "c7befd5ea8c3119769e8a6c1486c4edc6a3bc8365c67621c881bbb774b9902ff"),
            modelArtifact("vector_estimator.int8.onnx", 78_400_833,
                "20cd86fa5c6effedfda0e7cffe5b0569ca401c440a0c3a1d72bf39286c0db3fd"),
            modelArtifact("vocoder.int8.onnx", 25_991_073,
                "e923d60f53f95eb1ce235f1dc33ec56d9c057823c96fa6f8acf98f32b0da6152"),
            modelArtifact("unicode_indexer.bin", 262_144,
                "8402ca48e5189a8950138580b0fff64db6f072f24ac07cd54ba8b2fbb9883b30"),
            modelArtifact("voice.bin", 517_168,
                "67d5209b0ee8ce6c74105ffbe12fe6a7628aea3b4ba2fcb308a4a67938a93ce8"),
            modelArtifact("tts.json", 8_253,
                "42078d3aef1cd43ab43021f3c54f47d2d75ceb4e75f627f118890128b06a0d09"),
            Artifact(
                "LICENSE", LICENSE_URL.replace("/blob/", "/resolve/"), 15_007,
                "0d944a9110fed9a9602d60e0423a272903e7bd21ab060490774efc77c2275e9f",
            ),
        )

        private fun modelArtifact(name: String, size: Long, sha256: String): Artifact =
            Artifact(name, "$BASE_URL/$name", size, sha256)
    }
}
