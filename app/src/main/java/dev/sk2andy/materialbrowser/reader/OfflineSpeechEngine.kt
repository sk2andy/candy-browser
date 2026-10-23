package dev.sk2andy.materialbrowser.reader

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Random

/** CPU-only Supertonic 3 inference. Own and close serially on the speech executor. */
internal class OfflineSpeechEngine(private val files: OfflineSpeechModelFiles) : LocalSpeechEngine {
    override val sampleRate: Int = 44_100
    private val environment = OrtEnvironment.getEnvironment()
    private val sessions = mutableListOf<OrtSession>()
    private var indexer: IntArray? = null
    private var voice: Voice? = null
    private var closed = false

    override fun synthesize(text: String, isCancelled: () -> Boolean): FloatArray {
        check(!closed)
        val prepared = OfflineSpeechTextRules.prepare(text)
        if (prepared.isEmpty()) return FloatArray(0)
        checkActive(isCancelled)
        prepare(isCancelled)
        val vocabulary = requireNotNull(indexer)
        val ids = prepared.codePoints().toArray().map { point ->
            vocabulary.getOrElse(point) { -1 }.takeIf { it >= 0 }
                ?.toLong() ?: vocabulary[' '.code].toLong()
        }.toLongArray()
        val style = requireNotNull(voice)
        return TensorScope().use { scope ->
            val textIds = scope.own(OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())))
            val textMask = scope.tensor(FloatArray(ids.size) { 1f }, 1, 1, ids.size.toLong())
            val styleDp = scope.tensor(style.dp, *style.dpShape)
            val styleTtl = scope.tensor(style.ttl, *style.ttlShape)
            val duration = sessions[0].run(mapOf("text_ids" to textIds, "style_dp" to styleDp, "text_mask" to textMask)).use {
                (it[0] as OnnxTensor).floatBuffer.get()
            }
            // Reject invalid predictions instead of allocating unbounded audio or silently truncating it.
            check(duration.isFinite() && duration > 0f && duration <= MAX_SECONDS_PER_CHUNK)
            checkActive(isCancelled)
            val encoded = scope.own(sessions[1].run(mapOf("text_ids" to textIds, "style_ttl" to styleTtl, "text_mask" to textMask)))
            val textEmbedding = encoded[0] as OnnxTensor
            val sampleCount = (duration * sampleRate).toInt()
            val latentLength = (sampleCount + LATENT_CHUNK_SIZE - 1) / LATENT_CHUNK_SIZE
            val latentShape = longArrayOf(1, LATENT_DIMENSIONS.toLong(), latentLength.toLong())
            val random = Random()
            var latent = FloatArray(LATENT_DIMENSIONS * latentLength) { random.nextGaussian().toFloat() }
            val latentMask = scope.tensor(FloatArray(latentLength) { 1f }, 1, 1, latentLength.toLong())
            val totalSteps = scope.tensor(floatArrayOf(STEPS.toFloat()), 1)
            repeat(STEPS) { step ->
                checkActive(isCancelled)
                TensorScope().use { iteration ->
                    val noisy = iteration.tensor(latent, *latentShape)
                    val currentStep = iteration.tensor(floatArrayOf(step.toFloat()), 1)
                    sessions[2].run(mapOf(
                        "noisy_latent" to noisy,
                        "text_emb" to textEmbedding,
                        "style_ttl" to styleTtl,
                        "latent_mask" to latentMask,
                        "text_mask" to textMask,
                        "current_step" to currentStep,
                        "total_step" to totalSteps,
                    )).use { result ->
                        val buffer = (result[0] as OnnxTensor).floatBuffer
                        check(buffer.remaining() == latent.size)
                        buffer.get(latent)
                    }
                }
            }
            checkActive(isCancelled)
            val finalLatent = scope.tensor(latent, *latentShape)
            sessions[3].run(mapOf("latent" to finalLatent)).use { result ->
                checkActive(isCancelled)
                val buffer = (result[0] as OnnxTensor).floatBuffer
                check(buffer.remaining() >= sampleCount)
                FloatArray(sampleCount).also { samples ->
                    buffer.get(samples)
                    check(samples.all(Float::isFinite))
                    samples.indices.forEach { index -> samples[index] = samples[index].coerceIn(-1f, 1f) }
                }
            }
        }
    }

    private fun prepare(isCancelled: () -> Boolean) {
        if (sessions.isNotEmpty()) return
        check(files.isReady())
        val indexBytes = ByteBuffer.wrap(files.file("unicode_indexer.bin").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        indexer = IntArray(indexBytes.remaining() / Int.SIZE_BYTES) { indexBytes.int }
        voice = readVoice()
        try {
            OrtSession.SessionOptions().use { options ->
                options.setInterOpNumThreads(1)
                options.setIntraOpNumThreads(2)
                // These models have dynamic sequence lengths; avoid retaining large arena allocations.
                options.setMemoryPatternOptimization(false)
                options.setCPUArenaAllocator(false)
                for (name in MODEL_FILES) {
                    checkActive(isCancelled)
                    sessions += environment.createSession(files.file(name).absolutePath, options)
                }
            }
        } catch (error: Exception) {
            sessions.forEach(OrtSession::close)
            sessions.clear()
            throw error
        }
    }

    private fun readVoice(): Voice {
        val data = ByteBuffer.wrap(files.file("voice.bin").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val ttlShape = LongArray(3) { data.long }
        val dpShape = LongArray(3) { data.long }
        check(ttlShape.contentEquals(longArrayOf(10, 50, 256)))
        check(dpShape.contentEquals(longArrayOf(10, 8, 16)))
        // Fixed first preset (F1). Other presets share the same multilingual model.
        val ttlCount = (ttlShape[1] * ttlShape[2]).toInt()
        val dpCount = (dpShape[1] * dpShape[2]).toInt()
        check(data.remaining() == (ttlCount + dpCount) * 10 * Float.SIZE_BYTES)
        val ttl = FloatArray(ttlCount) { data.float }
        data.position(6 * Long.SIZE_BYTES + ttlCount * 10 * Float.SIZE_BYTES)
        val dp = FloatArray(dpCount) { data.float }
        return Voice(ttl, longArrayOf(1, ttlShape[1], ttlShape[2]), dp, longArrayOf(1, dpShape[1], dpShape[2]))
    }

    private fun checkActive(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Speech cancelled")
    }

    override fun close() {
        closed = true
        sessions.forEach(OrtSession::close)
        sessions.clear()
        indexer = null
        voice = null
    }

    private inner class TensorScope : AutoCloseable {
        private val resources = mutableListOf<AutoCloseable>()
        fun <T : AutoCloseable> own(value: T): T = value.also { resources += it }
        fun tensor(values: FloatArray, vararg shape: Long): OnnxTensor =
            own(OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), shape))
        override fun close() { resources.asReversed().forEach(AutoCloseable::close) }
    }

    private data class Voice(val ttl: FloatArray, val ttlShape: LongArray, val dp: FloatArray, val dpShape: LongArray)

    companion object {
        // Dimensions from the checksum-pinned tts.json, not the web demo's sample rate.
        private const val LATENT_CHUNK_SIZE = 512 * 6
        private const val LATENT_DIMENSIONS = 24 * 6
        private const val MAX_SECONDS_PER_CHUNK = 45f
        private const val STEPS = 8
        private val MODEL_FILES = listOf(
            "duration_predictor.int8.onnx", "text_encoder.int8.onnx",
            "vector_estimator.int8.onnx", "vocoder.int8.onnx",
        )
    }
}
