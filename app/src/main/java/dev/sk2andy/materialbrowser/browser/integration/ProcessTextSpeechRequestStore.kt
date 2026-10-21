package dev.sk2andy.materialbrowser.browser.integration

import java.util.UUID

/** One-use speech requests keep private selections out of activity extras and saved state. */
internal class ProcessTextSpeechRequestStore {
    private data class Pending(val text: String, val createdAtMillis: Long)
    private val pending = linkedMapOf<String, Pending>()

    @Synchronized
    fun put(text: CharSequence, nowMillis: Long): String? {
        val bounded = ProcessTextRules.selectedText(text, ProcessTextRules.MAX_SPEECH_CHARACTERS) ?: return null
        prune(nowMillis)
        while (pending.size >= MAX_PENDING_REQUESTS) pending.remove(pending.keys.first())
        val token = UUID.randomUUID().toString()
        pending[token] = Pending(bounded, nowMillis)
        return token
    }

    @Synchronized
    fun take(token: String?, nowMillis: Long): String? {
        prune(nowMillis)
        if (token == null || token.length > 128) return null
        return pending.remove(token)?.text
    }

    private fun prune(nowMillis: Long) {
        pending.entries.removeAll { (_, item) ->
            nowMillis < item.createdAtMillis || nowMillis - item.createdAtMillis > MAX_AGE_MILLIS
        }
    }

    private companion object {
        const val MAX_PENDING_REQUESTS = 8
        const val MAX_AGE_MILLIS = 60_000L
    }
}

internal val processTextSpeechRequests = ProcessTextSpeechRequestStore()
