package dev.sk2andy.materialbrowser.browser.translation

import android.app.PendingIntent
import android.content.Context
import android.icu.util.ULocale
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.translation.TranslationCapability
import android.view.translation.TranslationContext
import android.view.translation.TranslationManager
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationResponseValue
import android.view.translation.TranslationSpec
import android.view.translation.Translator
import java.util.concurrent.Executors

internal data class InlineTranslationAvailability(
    val pairs: List<InlineTranslationPair>,
    val settings: PendingIntent?,
)

internal interface InlineTranslationBackend {
    fun availability(onComplete: (InlineTranslationAvailability) -> Unit)
    fun translate(text: String, pair: InlineTranslationPair, onComplete: (String?) -> Unit)
    fun cancel()
    fun close()
}

/** Android's configured on-device translation service owns language model installation. */
internal class AndroidInlineTranslator(context: Context) : InlineTranslationBackend {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0L
    private var closed = false
    private var translator: Translator? = null
    private var cancellation: CancellationSignal? = null
    private var timeout: Runnable? = null

    override fun availability(onComplete: (InlineTranslationAvailability) -> Unit) {
        cancel()
        if (closed) return
        val request = generation
        worker.execute {
            val availability = runCatching {
                val manager = context.getSystemService(TranslationManager::class.java)
                val pairs = manager?.getOnDeviceTranslationCapabilities(
                    TranslationSpec.DATA_FORMAT_TEXT,
                    TranslationSpec.DATA_FORMAT_TEXT,
                ).orEmpty().filter { it.state == TranslationCapability.STATE_ON_DEVICE }.map {
                    InlineTranslationPair(it.sourceSpec.locale.toLanguageTag(), it.targetSpec.locale.toLanguageTag())
                }
                InlineTranslationAvailability(
                    InlineTranslationRules.pairs(pairs),
                    manager?.onDeviceTranslationSettingsActivityIntent,
                )
            }.getOrElse { InlineTranslationAvailability(emptyList(), null) }
            handler.post { if (!closed && generation == request) onComplete(availability) }
        }
    }

    override fun translate(text: String, pair: InlineTranslationPair, onComplete: (String?) -> Unit) {
        cancel()
        if (closed) return
        val request = generation
        fun finish(value: String?) {
            if (closed || generation != request) return
            cancel()
            onComplete(InlineTranslationRules.result(value))
        }
        if (InlineTranslationRules.source(text) == null) {
            finish(null)
            return
        }
        timeout = Runnable { finish(null) }.also {
            handler.postDelayed(it, InlineTranslationRules.TIMEOUT_MILLIS)
        }
        val manager = context.getSystemService(TranslationManager::class.java)
        if (manager == null) {
            finish(null)
            return
        }
        val translationContext = TranslationContext.Builder(
            TranslationSpec(ULocale.forLanguageTag(pair.source), TranslationSpec.DATA_FORMAT_TEXT),
            TranslationSpec(ULocale.forLanguageTag(pair.target), TranslationSpec.DATA_FORMAT_TEXT),
        ).build()
        runCatching {
            manager.createOnDeviceTranslator(translationContext, context.mainExecutor) { created ->
                if (closed || generation != request) {
                    created?.destroy()
                    return@createOnDeviceTranslator
                }
                if (created == null) {
                    finish(null)
                    return@createOnDeviceTranslator
                }
                translator = created
                cancellation = CancellationSignal()
                val payload = TranslationRequest.Builder()
                    .setTranslationRequestValues(listOf(TranslationRequestValue.forText(text)))
                    .build()
                runCatching {
                    created.translate(payload, cancellation, context.mainExecutor) { response ->
                        if (response.isFinalResponse) {
                            val value = response.translationResponseValues[0]
                            finish(value?.text?.toString().takeIf {
                                response.translationStatus == TranslationResponse.TRANSLATION_STATUS_SUCCESS &&
                                    value?.statusCode == TranslationResponseValue.STATUS_SUCCESS
                            })
                        }
                    }
                }.onFailure { finish(null) }
            }
        }.onFailure { finish(null) }
    }

    override fun cancel() {
        generation++
        timeout?.let(handler::removeCallbacks)
        timeout = null
        cancellation?.cancel()
        cancellation = null
        translator?.let { runCatching { it.destroy() } }
        translator = null
    }

    override fun close() {
        closed = true
        cancel()
        worker.shutdownNow()
    }
}
