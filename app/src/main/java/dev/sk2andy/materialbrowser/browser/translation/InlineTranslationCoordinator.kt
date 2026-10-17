package dev.sk2andy.materialbrowser.browser.translation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

internal data class InlineTranslationState(
    val text: String,
    val pairs: List<InlineTranslationPair> = emptyList(),
    val loading: Boolean = true,
    val translating: Boolean = false,
    val failed: Boolean = false,
    val canManageLanguages: Boolean = false,
)

internal class InlineTranslationCoordinator(private val translator: InlineTranslationBackend) {
    constructor(context: Context) : this(AndroidInlineTranslator(context))

    private var closed = false
    private var current: (() -> Boolean)? = null
    private var apply: ((String, (Boolean) -> Unit) -> Unit)? = null
    private var fallback: (() -> Unit)? = null
    private var availability: InlineTranslationAvailability? = null
    private var url = ""
    private var toggleLabel = ""
    private var generation = 0L

    var state by mutableStateOf<InlineTranslationState?>(null)
        private set

    fun open(
        text: String,
        url: String,
        toggleLabel: String,
        isCurrent: () -> Boolean,
        apply: (String, (Boolean) -> Unit) -> Unit,
        fallback: () -> Unit,
    ) {
        if (closed) return
        dismiss()
        val source = InlineTranslationRules.source(text) ?: return
        if (!isCurrent()) return
        this.current = isCurrent
        this.apply = apply
        this.fallback = fallback
        this.url = url
        this.toggleLabel = toggleLabel
        state = InlineTranslationState(source)
        refreshLanguages()
    }

    fun refreshLanguages() {
        if (!validate()) return
        val request = ++generation
        state = state?.copy(loading = true, translating = false, failed = false)
        translator.availability { value ->
            if (request == generation && validate()) {
                availability = value
                state = state?.copy(
                    loading = false,
                    pairs = value.pairs,
                    canManageLanguages = value.settings != null,
                )
            }
        }
    }

    fun translate(pair: InlineTranslationPair) {
        if (!validate()) return
        val snapshot = state ?: return
        if (snapshot.loading || snapshot.translating || pair !in snapshot.pairs) return
        val request = ++generation
        state = snapshot.copy(translating = true, failed = false)
        translator.translate(snapshot.text, pair) { translated ->
            if (request != generation || !validate()) return@translate
            if (translated == null) {
                state = state?.copy(translating = false, failed = true)
            } else {
                val payload = JSONObject()
                    .put("operation", "translate")
                    .put("url", url)
                    .put("original", snapshot.text)
                    .put("replacement", translated)
                    .put("toggleLabel", toggleLabel)
                    .toString()
                apply?.invoke(payload) { applied ->
                    if (request == generation && validate()) {
                        if (applied) dismiss() else state = state?.copy(translating = false, failed = true)
                    }
                }
            }
        }
    }

    fun usePageProvider() {
        if (!validate()) return
        val action = fallback
        dismiss()
        action?.invoke()
    }

    fun manageLanguages() {
        if (!validate()) return
        val settings = availability?.settings ?: return
        runCatching { settings.send() }
    }

    fun validate(): Boolean {
        val valid = !closed && current?.invoke() == true
        if (!valid) dismiss()
        return valid
    }

    fun dismiss() {
        generation++
        translator.cancel()
        state = null
        current = null
        apply = null
        fallback = null
        availability = null
        url = ""
        toggleLabel = ""
    }

    fun close() {
        if (closed) return
        closed = true
        dismiss()
        translator.close()
    }
}
