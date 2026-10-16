package dev.sk2andy.materialbrowser.constellations

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Contains public, immutable model assets only; page text never enters the downloader. */
internal class ConstellationModelFiles(private val directory: File) {
    private var verified = false

    fun file(name: String): File = File(directory, name)

    fun isReady(): Boolean {
        if (verified) return ARTIFACTS.all { file(it.name).length() == it.size }
        verified = ARTIFACTS.all { artifact -> matches(file(artifact.name), artifact) }
        return verified
    }

    fun download(onProgress: (Float) -> Unit): Boolean = try {
        check(directory.isDirectory || directory.mkdirs())
        verified = false
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.MINUTES)
            .build()
        val totalBytes = ARTIFACTS.sumOf(Artifact::size)
        var completedBytes = 0L
        for (artifact in ARTIFACTS) {
            checkInterrupted()
            val target = file(artifact.name)
            if (!matches(target, artifact)) {
                val temporary = file("${artifact.name}.part")
                try {
                    val request = Request.Builder().url("$BASE_URL/${artifact.path}").build()
                    client.newCall(request).execute().use { response ->
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
        verified = true
        true
    } catch (_: IOException) {
        false
    } catch (_: IllegalStateException) {
        false
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
        }
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Model work interrupted")
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class Artifact(val name: String, val path: String, val size: Long, val sha256: String)

    companion object {
        const val MODEL_ID = "distiluse-multilingual-v2-qint8-bfe45d0"
        private const val BASE_URL = "https://huggingface.co/sentence-transformers/" +
            "distiluse-base-multilingual-cased-v2/resolve/bfe45d0732ca50787611c0fe107ba278c7f3f889"
        private val ARTIFACTS = listOf(
            Artifact(
                "model.onnx", "onnx/model_qint8_arm64.onnx", 135_336_307,
                "c78c64bb0446ceb7086d2de24c2f4297e7a909eb457b4f68e5fdb6ab19f285c0",
            ),
            Artifact(
                "vocab.txt", "vocab.txt", 995_526,
                "fe0fda7c425b48c516fc8f160d594c8022a0808447475c1a7c6d6479763f310c",
            ),
            Artifact(
                "dense.safetensors", "2_Dense/model.safetensors", 1_575_104,
                "0a21b1ce908e772ebf09f93c20ca09524c32706e9918d9c0169a3f0663b191ed",
            ),
        )
    }
}
