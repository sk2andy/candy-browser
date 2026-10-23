package dev.sk2andy.materialbrowser.reader

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/** Public, pinned language packs only. No article text or generated audio reaches disk or network. */
internal class PocketSpeechModelFiles(
    private val directory: File,
    private val language: PocketSpeechLanguage,
) {
    private val archive get() = File(directory.parentFile, "${directory.name}.zip.part")
    private val staging get() = File(directory.parentFile, "${directory.name}.installing")

    fun isReady(): Boolean = language.files.all { artifact ->
        matches(File(directory, artifact.name), artifact.size, artifact.sha256)
    }

    fun download(
        cancellation: PocketSpeechDownloadCancellation,
        onProgress: (Float) -> Unit,
    ): Boolean = try {
        check(directory.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        cancellation.check()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.MINUTES)
            .build()
        val call = client.newCall(Request.Builder().url(language.url).build())
        cancellation.attach(call)
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("Model download failed")
            val body = response.body ?: throw IOException("Missing model data")
            if (body.contentLength() >= 0 && body.contentLength() != language.downloadBytes) {
                throw IOException("Unexpected model size")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            body.byteStream().use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        cancellation.check()
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > language.downloadBytes) throw IOException("Model too large")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(received.toFloat() / language.downloadBytes)
                    }
                    output.fd.sync()
                }
            }
            if (received != language.downloadBytes || digest.digest().hex() != language.sha256) {
                throw IOException("Invalid model integrity")
            }
        }
        installVerifiedArchive(archive, cancellation)
    } catch (_: IOException) {
        false
    } catch (_: IllegalStateException) {
        false
    } catch (_: SecurityException) {
        false
    } finally {
        cancellation.attach(null)
        archive.delete()
        staging.deleteRecursively()
    }

    /** Also verifies the archive when called directly by contract tests. */
    internal fun installVerifiedArchive(
        source: File,
        cancellation: PocketSpeechDownloadCancellation = PocketSpeechDownloadCancellation(),
    ): Boolean {
        if (!matches(source, language.downloadBytes, language.sha256, cancellation)) return false
        return try {
            if (staging.exists() && !staging.deleteRecursively()) throw IOException("Cannot clear model staging")
            if (!staging.mkdirs()) throw IOException("Cannot stage model")
            val artifacts = language.files.associateBy(PocketSpeechArtifact::name)
            val seen = mutableSetOf<String>()
            ZipInputStream(source.inputStream().buffered()).use { zip ->
                while (true) {
                    cancellation.check()
                    val entry = zip.nextEntry ?: break
                    val artifact = artifacts[entry.name] ?: throw IOException("Unexpected model entry")
                    if (entry.isDirectory || !seen.add(entry.name)) throw IOException("Duplicate model entry")
                    val target = File(staging, entry.name)
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        throw IOException("Invalid model path")
                    }
                    if (entry.size >= 0 && entry.size != artifact.size) throw IOException("Unexpected entry size")
                    if (target.parentFile?.let { it.isDirectory || it.mkdirs() } != true) {
                        throw IOException("Cannot create model directory")
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var received = 0L
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            cancellation.check()
                            val count = zip.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > artifact.size) throw IOException("Model entry too large")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                    if (received != artifact.size || digest.digest().hex() != artifact.sha256) {
                        throw IOException("Invalid model entry integrity")
                    }
                    zip.closeEntry()
                }
            }
            if (seen != artifacts.keys) throw IOException("Missing model entries")
            cancellation.check()
            if (directory.exists() && !directory.deleteRecursively()) throw IOException("Cannot replace model")
            if (!staging.renameTo(directory)) throw IOException("Cannot save model")
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Process death can leave temporary files behind; remove them before publishing restored state. */
    fun clearIncomplete(): Boolean = try {
        val stagingDeleted = !staging.exists() || staging.deleteRecursively()
        val archiveDeleted = !archive.exists() || archive.delete()
        stagingDeleted && archiveDeleted
    } catch (_: SecurityException) {
        false
    }

    /** Repository holds this language's inference lock while deleting. */
    fun delete(): Boolean = try {
        val installedDeleted = !directory.exists() || directory.deleteRecursively()
        val stagingDeleted = !staging.exists() || staging.deleteRecursively()
        val archiveDeleted = !archive.exists() || archive.delete()
        installedDeleted && stagingDeleted && archiveDeleted
    } catch (_: SecurityException) {
        false
    }

    private fun matches(
        target: File,
        size: Long,
        sha256: String,
        cancellation: PocketSpeechDownloadCancellation? = null,
    ): Boolean {
        if (!target.isFile || target.length() != size) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    cancellation?.check()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().hex() == sha256
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

/** Cancellation also interrupts a blocked HTTP read, without interrupting the shared file executor. */
internal class PocketSpeechDownloadCancellation {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var call: Call? = null

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        call?.cancel()
    }

    fun attach(value: Call?) {
        call = value
        if (cancelled.get()) value?.cancel()
    }

    fun check() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Model work cancelled")
        }
    }
}
