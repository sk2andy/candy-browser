package dev.sk2andy.materialbrowser.constellations

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import org.json.JSONObject
import kotlin.math.sqrt
import kotlin.math.tanh

/** Local, CPU-only sentence embedding. Call serially on the owning background executor. */
internal class ConstellationEmbedder(context: Context) : AutoCloseable {
    private val files = ConstellationModelFiles(
        File(context.applicationContext.noBackupFilesDir, "constellation_model"),
    )
    private var session: OrtSession? = null
    private var tokenizer: ConstellationWordPieceTokenizer? = null
    private var denseWeights: FloatArray? = null
    private var denseBias: FloatArray? = null
    private var closed = false

    fun isModelReady(): Boolean = !closed && files.isReady()

    fun downloadModel(onProgress: (Float) -> Unit): Boolean =
        !closed && files.download(onProgress)

    fun embed(title: String, text: String): List<Float>? {
        if (closed || Thread.currentThread().isInterrupted || !files.isReady()) return null
        if (title.isBlank() && text.isBlank()) return null
        return try {
            prepare()
            val windows = requireNotNull(tokenizer).encodeWindows(title, text)
            val combined = FloatArray(EMBEDDING_DIMENSIONS)
            for (ids in windows) {
                if (Thread.currentThread().isInterrupted) return null
                val vector = infer(ids) ?: return null
                for (index in combined.indices) combined[index] += vector[index]
            }
            normalize(combined)?.toList()
        } catch (_: OrtException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: org.json.JSONException) {
            null
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }

    private fun infer(ids: LongArray): FloatArray? {
        val environment = OrtEnvironment.getEnvironment()
        val shape = longArrayOf(1, ids.size.toLong())
        OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), shape).use { inputIds ->
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(LongArray(ids.size) { 1L }),
                shape,
            ).use { attentionMask ->
                requireNotNull(session).run(
                    mapOf("input_ids" to inputIds, "attention_mask" to attentionMask),
                    setOf("last_hidden_state"),
                ).use { result ->
                    val output = result.get("last_hidden_state").orElse(null) as? OnnxTensor ?: return null
                    val buffer = output.floatBuffer ?: return null
                    if (buffer.remaining() != ids.size * HIDDEN_DIMENSIONS) return null
                    val pooled = FloatArray(HIDDEN_DIMENSIONS)
                    repeat(ids.size) {
                        for (index in pooled.indices) pooled[index] += buffer.get() / ids.size
                    }
                    return project(pooled)
                }
            }
        }
    }

    private fun prepare() {
        if (session != null) return
        val vocabulary = files.file("vocab.txt").readLines(Charsets.UTF_8)
        check(vocabulary.size == VOCABULARY_SIZE)
        tokenizer = ConstellationWordPieceTokenizer(vocabulary)
        readDenseLayer()
        OrtSession.SessionOptions().use { options ->
            options.setInterOpNumThreads(1)
            options.setIntraOpNumThreads(2)
            session = OrtEnvironment.getEnvironment().createSession(
                files.file("model.onnx").absolutePath,
                options,
            )
        }
    }

    private fun readDenseLayer() {
        val bytes = files.file("dense.safetensors").readBytes()
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val headerLength = data.long
        check(headerLength in 1..MAX_DENSE_HEADER_BYTES)
        val header = ByteArray(headerLength.toInt())
        data.get(header)
        val metadata = JSONObject(header.toString(Charsets.UTF_8))
        val dataStart = data.position()
        fun readTensor(name: String, dimensions: List<Int>): FloatArray {
            val tensor = metadata.getJSONObject(name)
            check(tensor.getString("dtype") == "F32")
            val shape = tensor.getJSONArray("shape")
            check(shape.length() == dimensions.size)
            dimensions.forEachIndexed { index, value -> check(shape.getInt(index) == value) }
            val count = dimensions.fold(1, Int::times)
            val offsets = tensor.getJSONArray("data_offsets")
            val start = offsets.getInt(0)
            val end = offsets.getInt(1)
            check(start >= 0 && end - start == count * Float.SIZE_BYTES)
            check(end <= bytes.size - dataStart)
            data.position(dataStart + start)
            return FloatArray(count) { data.float }
        }
        denseWeights = readTensor("linear.weight", listOf(EMBEDDING_DIMENSIONS, HIDDEN_DIMENSIONS))
        denseBias = readTensor("linear.bias", listOf(EMBEDDING_DIMENSIONS))
    }

    private fun project(pooled: FloatArray): FloatArray? {
        val weights = requireNotNull(denseWeights)
        val bias = requireNotNull(denseBias)
        val vector = FloatArray(EMBEDDING_DIMENSIONS) { row ->
            var value = bias[row]
            for (column in pooled.indices) value += weights[row * HIDDEN_DIMENSIONS + column] * pooled[column]
            tanh(value)
        }
        return normalize(vector)
    }

    private fun normalize(vector: FloatArray): FloatArray? {
        val length = sqrt(vector.sumOf { it.toDouble() * it })
        if (!length.isFinite() || length <= 0.0) return null
        return FloatArray(vector.size) { (vector[it] / length).toFloat() }
    }

    override fun close() {
        closed = true
        session?.close()
        session = null
        tokenizer = null
        denseWeights = null
        denseBias = null
    }

    companion object {
        const val EMBEDDING_DIMENSIONS = 512
        private const val HIDDEN_DIMENSIONS = 768
        private const val VOCABULARY_SIZE = 119_547
        private const val MAX_DENSE_HEADER_BYTES = 4_096L
    }
}
