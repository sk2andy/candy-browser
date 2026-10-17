package dev.sk2andy.materialbrowser.browser.formdraft

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy

internal data class FormDraftField(val key: String, val value: String)

internal data class FormDraft(
    val profileId: String,
    val url: String,
    val fields: List<FormDraftField>,
    val updatedAt: Long,
)

internal data class FormDraftSnapshot(
    val url: String,
    val documentId: String,
    val fields: List<FormDraftField>,
    val ready: Boolean,
    val submitted: Boolean,
)

internal object FormDraftRules {
    const val MAX_PAGES = 20
    const val MAX_FIELDS = 16
    const val MAX_FIELD_CHARS = 16_000
    const val MAX_TOTAL_CHARS = 64_000
    const val MAX_WIRE_CHARS = 150_000
    const val LIFETIME_MILLIS = 24 * 60 * 60 * 1_000L

    fun pageUrl(value: String?): String? = value
        ?.takeIf { it.length <= 4_096 }
        ?.let(BrowserUriPolicy::normalizeHttpUrl)

    fun fields(values: List<FormDraftField>): List<FormDraftField>? {
        if (values.size > MAX_FIELDS || values.map { it.key }.distinct().size != values.size) return null
        if (values.any { it.key.isBlank() || it.key.length > 1_024 || it.key.any(Char::isISOControl) ||
                it.value.length > MAX_FIELD_CHARS || '\u0000' in it.value }) return null
        return values.takeIf { it.sumOf { field -> field.value.length } <= MAX_TOTAL_CHARS }
    }

    fun sanitize(draft: FormDraft, now: Long): FormDraft? {
        if (draft.profileId.isBlank() || draft.profileId.length > 128 ||
            draft.profileId.any(Char::isISOControl) || draft.updatedAt <= 0 ||
            draft.updatedAt > now || now - draft.updatedAt >= LIFETIME_MILLIS) return null
        val url = pageUrl(draft.url) ?: return null
        val fields = fields(draft.fields)?.filter { it.value.isNotBlank() } ?: return null
        return draft.copy(url = url, fields = fields).takeIf { fields.isNotEmpty() }
    }

    fun prune(drafts: List<FormDraft>, now: Long): List<FormDraft> = drafts
        .mapNotNull { sanitize(it, now) }
        .sortedWith(compareByDescending<FormDraft> { it.updatedAt }.thenBy { it.profileId }.thenBy { it.url })
        .distinctBy { it.profileId to it.url }
        .take(MAX_PAGES)
}
