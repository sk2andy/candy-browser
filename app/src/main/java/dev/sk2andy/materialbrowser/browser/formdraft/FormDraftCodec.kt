package dev.sk2andy.materialbrowser.browser.formdraft

import org.json.JSONArray
import org.json.JSONObject

internal object FormDraftCodec {
    fun snapshot(raw: String?): FormDraftSnapshot? = raw
        ?.takeIf { it.length <= FormDraftRules.MAX_WIRE_CHARS }
        ?.let { value ->
            runCatching {
                val json = JSONObject(value)
                val url = FormDraftRules.pageUrl(json.getString("url")) ?: return null
                val documentId = json.getString("documentId")
                    .takeIf { it.matches(Regex("[a-zA-Z0-9-]{16,64}")) } ?: return null
                FormDraftSnapshot(
                    url = url,
                    documentId = documentId,
                    fields = readFields(json.getJSONArray("fields")) ?: return null,
                    ready = json.optBoolean("ready"),
                    submitted = json.optBoolean("submitted"),
                )
            }.getOrNull()
        }

    fun restore(draft: FormDraft, documentId: String): String = JSONObject()
        .put("url", draft.url)
        .put("documentId", documentId)
        .put("fields", writeFields(draft.fields))
        .toString()

    fun encode(drafts: List<FormDraft>): ByteArray = JSONArray().apply {
        drafts.forEach { draft ->
            put(JSONObject().put("profileId", draft.profileId).put("url", draft.url)
                .put("updatedAt", draft.updatedAt).put("fields", writeFields(draft.fields)))
        }
    }.toString().toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): List<FormDraft> {
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        require(array.length() <= FormDraftRules.MAX_PAGES)
        return (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            FormDraft(
                profileId = json.getString("profileId"),
                url = json.getString("url"),
                fields = requireNotNull(readFields(json.getJSONArray("fields"))),
                updatedAt = json.getLong("updatedAt"),
            )
        }
    }

    private fun readFields(array: JSONArray): List<FormDraftField>? {
        if (array.length() > FormDraftRules.MAX_FIELDS) return null
        return FormDraftRules.fields((0 until array.length()).map { index ->
            val field = array.getJSONObject(index)
            FormDraftField(field.getString("key"), field.getString("value"))
        })
    }

    private fun writeFields(fields: List<FormDraftField>): JSONArray = JSONArray().apply {
        fields.forEach { put(JSONObject().put("key", it.key).put("value", it.value)) }
    }
}
