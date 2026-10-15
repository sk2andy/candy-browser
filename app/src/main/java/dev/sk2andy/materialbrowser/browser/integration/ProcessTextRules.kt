package dev.sk2andy.materialbrowser.browser.integration

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.SearchEngine
import dev.sk2andy.materialbrowser.browser.isSynced
import java.util.UUID

internal object ProcessTextRules {
    const val MAX_RESEARCH_CHARACTERS = 2_000
    const val MAX_SPEECH_CHARACTERS = 20_000
    const val ACTION_RESEARCH = "dev.sk2andy.materialbrowser.action.RESEARCH_SELECTED_TEXT"
    const val EXTRA_REQUEST_TOKEN = "selected_text_request_token"

    fun selectedText(value: CharSequence?, maximumCharacters: Int): String? {
        if (value == null || maximumCharacters <= 0 || value.length > maximumCharacters) return null
        return value.toString().filter { !it.isISOControl() || it == '\n' || it == '\t' }
            .trim().takeIf(String::isNotEmpty)
    }

    fun eligibleProfiles(
        profiles: List<BrowserProfile>,
        activeProfileId: String,
        profilesEnabled: Boolean,
    ): List<BrowserProfile> = profiles.filter {
        !it.isSynced && it.protection == null && (profilesEnabled || it.id == activeProfileId)
    }

    fun researchUrl(text: String, searchEngine: SearchEngine, searxngInstanceUrl: String = ""): String? {
        val query = selectedText(text, MAX_RESEARCH_CHARACTERS) ?: return null
        return BrowserUriPolicy.normalizeHttpUrl(searchEngine.buildSearchUrl(query, searxngInstanceUrl = searxngInstanceUrl))
    }
}

internal data class ProcessTextResearchRequest(val text: String, val profileId: String)

/** One-use requests keep selected text out of activity launch extras and saved state. */
internal class ProcessTextResearchRequestStore {
    private data class Pending(val request: ProcessTextResearchRequest, val createdAtMillis: Long)
    private val pending = linkedMapOf<String, Pending>()

    @Synchronized
    fun put(request: ProcessTextResearchRequest, nowMillis: Long): String? {
        val text = ProcessTextRules.selectedText(request.text, ProcessTextRules.MAX_RESEARCH_CHARACTERS) ?: return null
        if (request.profileId.isBlank() || request.profileId.length > 128 ||
            request.profileId.any { it.isWhitespace() || it.isISOControl() }
        ) return null
        prune(nowMillis)
        while (pending.size >= MAX_PENDING_REQUESTS) pending.remove(pending.keys.first())
        val token = UUID.randomUUID().toString()
        pending[token] = Pending(request.copy(text = text), nowMillis)
        return token
    }

    @Synchronized
    fun take(token: String?, nowMillis: Long): ProcessTextResearchRequest? {
        prune(nowMillis)
        if (token == null || token.length > 128) return null
        return pending.remove(token)?.request
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

internal val processTextResearchRequests = ProcessTextResearchRequestStore()
